package com.smartwms.outbound.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.base.mapper.CustomerMapper;
import com.smartwms.base.mapper.LocationMapper;
import com.smartwms.base.mapper.ProductMapper;
import com.smartwms.base.mapper.WarehouseMapper;
import com.smartwms.entity.Customer;
import com.smartwms.entity.Inventory;
import com.smartwms.entity.InventoryBatch;
import com.smartwms.entity.Location;
import com.smartwms.entity.OutboundOrder;
import com.smartwms.entity.OutboundOrderItem;
import com.smartwms.entity.Product;
import com.smartwms.entity.StockFlow;
import com.smartwms.entity.SysUser;
import com.smartwms.entity.Warehouse;
import com.smartwms.enums.OrderStatus;
import com.smartwms.enums.OutboundOrderType;
import com.smartwms.enums.ResultCode;
import com.smartwms.exception.BizException;
import com.smartwms.inbound.dto.OrderActionDTO;
import com.smartwms.inventory.mapper.InventoryBatchMapper;
import com.smartwms.inventory.mapper.InventoryMapper;
import com.smartwms.inventory.mapper.StockFlowMapper;
import com.smartwms.inventory.vo.StockFlowVO;
import com.smartwms.outbound.dto.OutboundCreateDTO;
import com.smartwms.outbound.dto.OutboundItemDTO;
import com.smartwms.outbound.dto.OutboundPageQueryDTO;
import com.smartwms.outbound.dto.OutboundUpdateDTO;
import com.smartwms.outbound.mapper.OutboundOrderItemMapper;
import com.smartwms.outbound.mapper.OutboundOrderMapper;
import com.smartwms.outbound.service.OutboundService;
import com.smartwms.outbound.vo.OutboundItemVO;
import com.smartwms.outbound.vo.OutboundOrderVO;
import com.smartwms.outbound.vo.OutboundPostResultVO;
import com.smartwms.security.LoginUser;
import com.smartwms.security.WarehouseDataPerm;
import com.smartwms.system.mapper.SysUserMapper;
import com.smartwms.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 出库管理服务实现（API-OUT-001~011）。
 * 与入库同构的状态机与三重幂等防护（状态机 40901 + 单号唯一 40904 + 乐观锁 40905），
 * 差异点：
 * 1. 提交（006）/审核（007）做可用库存预检（qty - lock_qty 汇总 ≥ plan_qty，不足 40902，提示性）；
 * 2. 过账（009）按基线 FIFO 口径分配批次（expiry_date IS NULL 排最后 → expiry_date ASC →
 *    production_date ASC → inventory_batch.id ASC），逐库存行 FOR UPDATE 扣减并写 OUT 流水，
 *    任一行失败（40902/40903/40905）整体回滚，单据保持 status=2 可重试。
 * 文档 10.7 待确认项口径：驳回原因落 remark；指定批次不足时允许按 FIFO 混合补齐；
 * 跨货位分配回填主批次（分配量最大，平局取 FIFO 首行）所在货位，不建子表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundServiceImpl implements OutboundService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final OutboundOrderMapper orderMapper;
    private final OutboundOrderItemMapper itemMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryBatchMapper batchMapper;
    private final StockFlowMapper flowMapper;
    private final ProductMapper productMapper;
    private final LocationMapper locationMapper;
    private final CustomerMapper customerMapper;
    private final WarehouseMapper warehouseMapper;
    private final SysUserMapper userMapper;
    private final WarehouseDataPerm dataPerm;

    // ==================== OUT-001 分页查询 ====================

    @Override
    public PageResult<OutboundOrderVO> page(OutboundPageQueryDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 20 : Math.min(dto.getPageSize(), 100);

        Integer orderType = OutboundOrderType.toCode(dto.getOrderType()); // 非法标签 40001
        if (dto.getStatus() != null && (dto.getStatus() < 0 || dto.getStatus() > 5)) {
            throw new BizException(ResultCode.PARAM_INVALID, "status 取值为 0~5");
        }
        dataPerm.checkWarehouse(dto.getWarehouseId());                   // 越权 40302
        LocalDate start = parseDate(dto.getStartDate(), "startDate");
        LocalDate end = parseDate(dto.getEndDate(), "endDate");
        if (start != null && end != null && start.isAfter(end)) {
            throw new BizException(ResultCode.PARAM_INVALID, "startDate 不能晚于 endDate");
        }
        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(0L, pageNum, pageSize, List.of());
        }

        LambdaQueryWrapper<OutboundOrder> wrapper = new LambdaQueryWrapper<OutboundOrder>()
                .eq(dto.getWarehouseId() != null, OutboundOrder::getWarehouseId, dto.getWarehouseId())
                .eq(dto.getStatus() != null, OutboundOrder::getStatus, dto.getStatus())
                .eq(orderType != null, OutboundOrder::getOrderType, orderType)
                .ge(start != null, OutboundOrder::getCreateTime,
                        start == null ? null : start.atStartOfDay())
                .lt(end != null, OutboundOrder::getCreateTime,
                        end == null ? null : end.plusDays(1).atStartOfDay())
                .in(allowed != null, OutboundOrder::getId, allowed)
                .orderByDesc(OutboundOrder::getCreateTime)
                .orderByDesc(OutboundOrder::getId);
        if (StringUtils.hasText(dto.getOutboundNo())) {
            wrapper.like(OutboundOrder::getOutboundNo, dto.getOutboundNo().trim());
        }
        if (StringUtils.hasText(dto.getKeyword())) {
            String like = "%" + dto.getKeyword().trim() + "%";
            wrapper.and(w -> w.like(OutboundOrder::getOutboundNo, like)
                    .or().like(OutboundOrder::getSourceOrderNo, like)
                    // keyword 覆盖客户名称（correlated EXISTS 关联客户表）
                    .or().apply("EXISTS (SELECT 1 FROM customer c"
                            + " WHERE c.id = customer_id AND c.deleted = 0"
                            + " AND c.customer_name LIKE {0})", like));
        }

        Page<OutboundOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, toVOList(page.getRecords(), false));
    }

    // ==================== OUT-002 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutboundOrderVO create(OutboundCreateDTO dto) {
        Integer orderType = OutboundOrderType.toCode(dto.getOrderType());
        dataPerm.checkWarehouse(dto.getWarehouseId());
        validateCustomer(orderType, dto.getCustomerId());

        List<OutboundOrderItem> items = buildItems(dto.getItems(), dto.getWarehouseId());

        OutboundOrder order = new OutboundOrder();
        order.setOutboundNo(nextOutboundNo());
        order.setOrderType(orderType);
        order.setCustomerId(dto.getCustomerId());
        order.setSourceOrderNo(emptyToNull(dto.getSourceOrderNo()));
        order.setExpectDate(dto.getExpectDate());
        order.setStatus(OrderStatus.DRAFT.getCode());
        order.setWarehouseId(dto.getWarehouseId());
        order.setPickUserId(dto.getPickUserId());
        order.setRemark(emptyToNull(dto.getRemark()));
        order.setTotalQty(sumPlan(items));
        order.setVersion(1);
        order.setCreateBy(currentUserId());
        orderMapper.insert(order);

        insertItems(order.getId(), items);
        log.info("新增出库单成功 orderId={}, outboundNo={}, itemCount={}",
                order.getId(), order.getOutboundNo(), items.size());
        return detail(order.getId());
    }

    // ==================== OUT-003 详情 / 修改 ====================

    @Override
    public OutboundOrderVO detail(Long id) {
        OutboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        return toVO(order, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutboundOrderVO update(Long id, OutboundUpdateDTO dto) {
        OutboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        Integer status = order.getStatus();
        if (!OrderStatus.DRAFT.getCode().equals(status) && !OrderStatus.REJECTED.getCode().equals(status)) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "仅草稿或已驳回的出库单允许修改");
        }
        checkVersion(order, dto.getVersion()); // 40905

        Integer orderType = OutboundOrderType.toCode(dto.getOrderType());
        validateCustomer(orderType, dto.getCustomerId());
        List<OutboundOrderItem> items = buildItems(dto.getItems(), dto.getWarehouseId());

        // 整体替换明细（物理删除后重建，同事务）
        itemMapper.deleteByOrderId(id);
        insertItems(id, items);

        order.setOrderType(orderType);
        order.setWarehouseId(dto.getWarehouseId());
        order.setCustomerId(dto.getCustomerId());
        order.setSourceOrderNo(emptyToNull(dto.getSourceOrderNo()));
        order.setExpectDate(dto.getExpectDate());
        order.setPickUserId(dto.getPickUserId());
        order.setRemark(emptyToNull(dto.getRemark()));
        order.setTotalQty(sumPlan(items));
        // 已驳回保存后回到草稿（4→0）
        if (OrderStatus.REJECTED.getCode().equals(status)) {
            order.setStatus(OrderStatus.DRAFT.getCode());
        }
        order.setUpdateBy(currentUserId());
        saveOrder(order); // 乐观锁 version 自动 +1，并发冲突 40905
        log.info("修改出库单成功 orderId={}, outboundNo={}, itemCount={}", id, order.getOutboundNo(), items.size());
        return detail(id);
    }

    // ==================== OUT-005 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Integer version) {
        OutboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        // 出库删除口径比入库严格：仅草稿 0 可删（文档 OUT-005），已驳回需先撤回
        if (!OrderStatus.DRAFT.getCode().equals(order.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "仅草稿状态的出库单允许删除");
        }
        if (version != null) {
            checkVersion(order, version); // 可选 ?version= 乐观锁（40905）
        }
        itemMapper.deleteByOrderId(id);
        orderMapper.deletePhysically(id); // 单据不做逻辑删除
        log.info("删除出库单成功 orderId={}, outboundNo={}", id, order.getOutboundNo());
    }

    // ==================== OUT-006 提交 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutboundOrderVO submit(Long id, OrderActionDTO dto) {
        OutboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        expectStatus(order, dto.getVersion(), OrderStatus.DRAFT.getCode(), "提交");
        // 完整性预检：明细存在、数量>0、商品/批次/货位有效（40001）
        List<OutboundOrderItem> items = buildItems(toItemDTOs(id), order.getWarehouseId());
        // 可用库存预检（提示性，最终以过账为准）：不足 40902
        checkStockAvailable(order.getWarehouseId(), items);

        order.setStatus(OrderStatus.PENDING.getCode());
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(currentUserId());
        saveOrder(order);
        log.info("提交出库单成功 orderId={}, outboundNo={}", id, order.getOutboundNo());
        return toVO(order, false);
    }

    // ==================== OUT-007 审核 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutboundOrderVO audit(Long id, OrderActionDTO dto) {
        OutboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        expectStatus(order, dto.getVersion(), OrderStatus.PENDING.getCode(), "审核");
        List<OutboundOrderItem> items = buildItems(toItemDTOs(id), order.getWarehouseId());
        checkStockAvailable(order.getWarehouseId(), items); // 审核再次预检 40902

        Long userId = currentUserId();
        order.setStatus(OrderStatus.APPROVED.getCode());
        order.setAuditorId(userId);
        order.setAuditTime(LocalDateTime.now());
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(userId);
        saveOrder(order);
        log.info("审核出库单成功 orderId={}, outboundNo={}", id, order.getOutboundNo());
        return toVO(order, false);
    }

    // ==================== OUT-008 驳回 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutboundOrderVO reject(Long id, OrderActionDTO dto) {
        OutboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        expectStatus(order, dto.getVersion(), OrderStatus.PENDING.getCode(), "驳回");
        if (!StringUtils.hasText(dto.getReason())) {
            throw new BizException(ResultCode.PARAM_INVALID, "驳回时 reason 必填");
        }

        order.setStatus(OrderStatus.REJECTED.getCode());
        // outbound_order 无 reject_reason 字段（文档待确认项采纳「落 remark」）
        order.setRemark(appendRemark(order.getRemark(), "[驳回] " + dto.getReason().trim()));
        order.setUpdateBy(currentUserId());
        saveOrder(order);
        log.info("驳回出库单成功 orderId={}, outboundNo={}, reason={}",
                id, order.getOutboundNo(), dto.getReason());
        return toVO(order, false);
    }

    // ==================== OUT-009 过账（FIFO 分配，扣减库存） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutboundPostResultVO post(Long id, OrderActionDTO dto) {
        OutboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        expectStatus(order, dto.getVersion(), OrderStatus.APPROVED.getCode(), "过账");

        List<OutboundOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItem>()
                .eq(OutboundOrderItem::getOrderId, id)
                .orderByAsc(OutboundOrderItem::getLineNo));
        if (items.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "出库单明细为空，无法过账");
        }
        for (OutboundOrderItem item : items) {
            if (item.getActualQty() == null || item.getActualQty().signum() <= 0) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "过账实发数量必须大于 0（明细行 " + item.getLineNo() + "）");
            }
        }

        Long userId = currentUserId();
        LocalDateTime now = LocalDateTime.now();
        List<StockFlowVO> flowVOs = new ArrayList<>(items.size());
        for (OutboundOrderItem item : items) {
            flowVOs.addAll(postOne(order, item, userId, now));
        }

        order.setStatus(OrderStatus.POSTED.getCode());
        order.setPosterId(userId);
        order.setPostTime(now);
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(userId);
        saveOrder(order);
        log.info("出库单过账成功 orderId={}, outboundNo={}, flowCount={}",
                id, order.getOutboundNo(), flowVOs.size());

        return OutboundPostResultVO.builder()
                .id(order.getId())
                .outboundNo(order.getOutboundNo())
                .status(order.getStatus())
                .statusDesc(OrderStatus.descOf(order.getStatus()))
                .posterId(order.getPosterId())
                .postTime(order.getPostTime())
                .version(order.getVersion())
                .flows(flowVOs)
                .build();
    }

    /**
     * 单行过账：FIFO 候选（行锁）→ 逐行扣减（乐观锁双保险）→ 回填明细 → 写 OUT 流水。
     * 可用量口径 qty - lock_qty；不足 40902 / 负库存 40903 抛出后整体回滚。
     */
    private List<StockFlowVO> postOne(OutboundOrder order, OutboundOrderItem item,
                                      Long userId, LocalDateTime now) {
        Product product = requireProduct(item.getProductId());

        // 指定批次兜底校验（创建时已校验，此处防批次状态事后变化）并标记优先分配
        Long preferredBatchId = null;
        if (StringUtils.hasText(item.getBatchNo())) {
            InventoryBatch specified = batchMapper.selectByKeyForUpdate(
                    product.getId(), order.getWarehouseId(), item.getBatchNo());
            if (specified == null || specified.getBatchStatus() == null || specified.getBatchStatus() != 1) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "明细行 " + item.getLineNo() + "：指定批次在本仓库不存在或状态异常");
            }
            preferredBatchId = specified.getId();
        }

        // FIFO 候选：expiry_date IS NULL 排最后 → expiry_date ASC → production_date ASC → 批次 id ASC（FOR UPDATE）
        List<Inventory> candidates = inventoryMapper.selectFifoCandidates(
                order.getWarehouseId(), product.getId());
        // 指定货位：仅从该货位扣减（文档未定义跨货位补齐，不混合）
        if (item.getLocationId() != null) {
            Long locId = item.getLocationId();
            candidates = candidates.stream()
                    .filter(inv -> locId.equals(inv.getLocationId())).toList();
        }
        // 指定批次优先：稳定分区到队首，其余保持 FIFO 顺序
        // （10.7 待确认采纳「允许混合分配」：指定批次不足部分按 FIFO 从其它批次补齐）
        if (preferredBatchId != null) {
            List<Inventory> preferred = new ArrayList<>();
            List<Inventory> rest = new ArrayList<>();
            for (Inventory inv : candidates) {
                (preferredBatchId.equals(inv.getBatchId()) ? preferred : rest).add(inv);
            }
            List<Inventory> merged = new ArrayList<>(preferred);
            merged.addAll(rest);
            candidates = merged;
        }

        // 逐库存行扣减，记录分配明细
        BigDecimal need = item.getActualQty();
        List<Inventory> usedRows = new ArrayList<>(candidates.size());
        List<BigDecimal> takes = new ArrayList<>(candidates.size());
        List<StockFlowVO> flows = new ArrayList<>();
        for (Inventory inv : candidates) {
            if (need.signum() <= 0) {
                break;
            }
            BigDecimal locked = inv.getLockQty() == null ? ZERO : inv.getLockQty();
            BigDecimal available = inv.getQty().subtract(locked);
            if (available.signum() <= 0) {
                continue;
            }
            BigDecimal take = available.min(need);
            BigDecimal before = inv.getQty();
            BigDecimal after = before.subtract(take);
            if (after.signum() < 0) { // 兜底拦截（正常不可达：take ≤ available ≤ qty）
                throw new BizException(ResultCode.NEGATIVE_STOCK,
                        "库存不能为负（明细行 " + item.getLineNo() + "）");
            }
            inv.setQty(after);
            inv.setUpdateBy(userId);
            // 乐观锁：version 不匹配则 0 行 → 40905 整体回滚
            if (inventoryMapper.updateById(inv) == 0) {
                throw new BizException(ResultCode.DUPLICATE_SUBMIT,
                        "库存并发更新冲突，请刷新后重试");
            }
            need = need.subtract(take);
            usedRows.add(inv);
            takes.add(take);
            flows.add(buildOutFlow(order, product, inv, take, before, after, userId, now));
        }
        if (need.signum() > 0) {
            throw new BizException(ResultCode.INSUFFICIENT_STOCK,
                    "商品「" + product.getProductName() + "」可用库存不足，尚缺 " + need.toPlainString()
                            + "（明细行 " + item.getLineNo() + "）");
        }

        // 回填主批次（分配量最大，平局取 FIFO 首行）：batch_id / batch_no / location_id
        int mainIdx = 0;
        for (int i = 1; i < takes.size(); i++) {
            if (takes.get(i).compareTo(takes.get(mainIdx)) > 0) {
                mainIdx = i;
            }
        }
        Inventory main = usedRows.get(mainIdx);
        InventoryBatch mainBatch = batchMapper.selectById(main.getBatchId());
        item.setBatchId(main.getBatchId());
        item.setBatchNo(mainBatch == null ? item.getBatchNo() : mainBatch.getBatchNo());
        item.setLocationId(main.getLocationId());
        item.setUpdateBy(userId);
        itemMapper.updateById(item);

        return flows;
    }

    /** 出库流水（direction=OUT，flow_type=出库2，ref_type=OUTBOUND）。 */
    private StockFlowVO buildOutFlow(OutboundOrder order, Product product, Inventory inv,
                                     BigDecimal take, BigDecimal before, BigDecimal after,
                                     Long userId, LocalDateTime now) {
        String flowNo = nextFlowNo();
        StockFlow flow = new StockFlow();
        flow.setFlowNo(flowNo);
        flow.setWarehouseId(order.getWarehouseId());
        flow.setProductId(product.getId());
        flow.setBatchId(inv.getBatchId());
        flow.setLocationId(inv.getLocationId());
        flow.setDirection("OUT");
        flow.setQty(take);
        flow.setBeforeQty(before);
        flow.setAfterQty(after);
        flow.setFlowType(2); // 2出库
        flow.setRefType("OUTBOUND");
        flow.setRefId(order.getId());
        flow.setRefNo(order.getOutboundNo());
        flow.setOperatorId(userId);
        flow.setOperTime(now);
        flow.setCreateBy(userId);
        flowMapper.insert(flow); // uk_flow_no 并发兜底

        return StockFlowVO.builder()
                .flowNo(flowNo)
                .productId(product.getId())
                .batchId(inv.getBatchId())
                .locationId(inv.getLocationId())
                .direction("OUT")
                .qty(take)
                .beforeQty(before)
                .afterQty(after)
                .flowType(2)
                .refType("OUTBOUND")
                .refId(order.getId())
                .refNo(order.getOutboundNo())
                .operTime(now)
                .build();
    }

    // ==================== OUT-010/011 取消与撤回 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutboundOrderVO cancel(Long id, OrderActionDTO dto) {
        OutboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        Integer status = order.getStatus();
        checkVersion(order, dto.getVersion());
        boolean allowed = OrderStatus.DRAFT.getCode().equals(status)
                || OrderStatus.PENDING.getCode().equals(status)
                || OrderStatus.REJECTED.getCode().equals(status);
        if (!allowed) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "仅草稿/待审核/已驳回的出库单允许取消（已审核与已完成不可取消）");
        }
        order.setStatus(OrderStatus.CANCELLED.getCode());
        // 取消原因（文档 body 为 reason）落 remark
        String addition = StringUtils.hasText(dto.getReason())
                ? "[取消] " + dto.getReason().trim() : emptyToNull(dto.getRemark());
        order.setRemark(appendRemark(order.getRemark(), addition));
        order.setUpdateBy(currentUserId());
        saveOrder(order);
        log.info("取消出库单成功 orderId={}, outboundNo={}", id, order.getOutboundNo());
        return toVO(order, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutboundOrderVO withdraw(Long id, OrderActionDTO dto) {
        OutboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        Integer status = order.getStatus();
        checkVersion(order, dto.getVersion());
        boolean allowed = OrderStatus.PENDING.getCode().equals(status)
                || OrderStatus.REJECTED.getCode().equals(status);
        if (!allowed) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "仅待审核/已驳回的出库单允许撤回");
        }
        order.setStatus(OrderStatus.DRAFT.getCode());
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(currentUserId());
        saveOrder(order);
        log.info("撤回出库单成功 orderId={}, outboundNo={}", id, order.getOutboundNo());
        return toVO(order, false);
    }

    // ==================== 私有方法 ====================

    /** 单据必须存在（含未删除），否则 40401。 */
    private OutboundOrder requireOrder(Long id) {
        OutboundOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "出库单不存在或已删除");
        }
        return order;
    }

    /** 校验 version 乐观锁（不一致 40905）。 */
    private void checkVersion(OutboundOrder order, Integer version) {
        if (version == null || !version.equals(order.getVersion())) {
            throw new BizException(ResultCode.DUPLICATE_SUBMIT, "单据已被其他操作修改，请刷新后重试");
        }
    }

    /** 校验当前状态 + version（状态不符 40901；version 不符 40905）。 */
    private void expectStatus(OutboundOrder order, Integer version, Integer expect, String action) {
        checkVersion(order, version);
        if (!expect.equals(order.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "当前状态不允许" + action + "（期望 " + OrderStatus.descOf(expect)
                            + "，实际 " + OrderStatus.descOf(order.getStatus()) + "）");
        }
    }

    /** 乐观锁保存：version 由 @Version 插件 +1；影响 0 行说明并发冲突 → 40905 回滚。 */
    private void saveOrder(OutboundOrder order) {
        if (orderMapper.updateById(order) == 0) {
            throw new BizException(ResultCode.DUPLICATE_SUBMIT, "单据已被其他操作修改，请刷新后重试");
        }
    }

    /** 类型为销售出库时 customerId 必填（40001）且客户存在（40401）。 */
    private void validateCustomer(Integer orderType, Long customerId) {
        if (OutboundOrderType.SALE.getCode().equals(orderType) && customerId == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "销售出库必须指定客户 customerId");
        }
        if (customerId != null && customerMapper.selectById(customerId) == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "customerId 对应客户不存在");
        }
    }

    /**
     * 明细 DTO 校验并转实体（OUT-002/004/006/007 复用）：
     * 商品存在（40401）、指定批次本仓库存在且正常（40001，batch_flag=0 忽略指定批次）、
     * 指定货位属于该仓库（40001）、actualQty 缺省 planQty，生成 lineNo。
     * 创建/提交阶段不查库存（预检单独执行，过账时最终校验）。
     */
    private List<OutboundOrderItem> buildItems(List<OutboundItemDTO> dtos, Long warehouseId) {
        List<OutboundOrderItem> items = new ArrayList<>(dtos.size());
        for (int i = 0; i < dtos.size(); i++) {
            OutboundItemDTO dto = dtos.get(i);
            int line = i + 1;

            Product product = requireProduct(dto.getProductId());
            Integer batchFlag = product.getBatchFlag() == null ? 1 : product.getBatchFlag();

            String batchNo = null;
            if (StringUtils.hasText(dto.getBatchNo())) {
                if (batchFlag == 0) {
                    // 非批次管理商品：忽略指定批次，过账走 FIFO（DEFAULT 批次）
                    dto.setBatchNo(null);
                } else {
                    batchNo = dto.getBatchNo().trim();
                    InventoryBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<InventoryBatch>()
                            .eq(InventoryBatch::getProductId, product.getId())
                            .eq(InventoryBatch::getWarehouseId, warehouseId)
                            .eq(InventoryBatch::getBatchNo, batchNo));
                    if (batch == null || batch.getBatchStatus() == null || batch.getBatchStatus() != 1) {
                        throw new BizException(ResultCode.PARAM_INVALID,
                                "明细行 " + line + "：指定批次在本仓库不存在或状态异常");
                    }
                }
            }

            if (dto.getLocationId() != null) {
                Location location = locationMapper.selectById(dto.getLocationId());
                if (location == null) {
                    throw new BizException(ResultCode.ORDER_NOT_EXIST, "locationId 对应货位不存在");
                }
                if (!warehouseId.equals(location.getWarehouseId())) {
                    throw new BizException(ResultCode.PARAM_INVALID, "货位不属于指定仓库");
                }
            }

            if (dto.getActualQty() == null) {
                dto.setActualQty(dto.getPlanQty()); // 实发缺省取计划
            }

            OutboundOrderItem item = new OutboundOrderItem();
            item.setLineNo(line);
            item.setProductId(product.getId());
            item.setBatchNo(batchNo);
            item.setPlanQty(dto.getPlanQty());
            item.setActualQty(dto.getActualQty());
            item.setUnit(StringUtils.hasText(dto.getUnit()) ? dto.getUnit().trim() : product.getUnit());
            item.setRemark(emptyToNull(dto.getRemark()));
            item.setCreateBy(currentUserId());
            items.add(item);
        }
        return items;
    }

    /**
     * 可用库存预检（OUT-006 提交 / OUT-007 审核）：按商品汇总 planQty，
     * 与 SUM(qty - lock_qty)（仅批次正常）比较，不足 40902（提示性，最终以过账为准）。
     */
    private void checkStockAvailable(Long warehouseId, List<OutboundOrderItem> items) {
        Map<Long, BigDecimal> needByProduct = new LinkedHashMap<>();
        for (OutboundOrderItem item : items) {
            needByProduct.merge(item.getProductId(), item.getPlanQty(), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> entry : needByProduct.entrySet()) {
            BigDecimal available = inventoryMapper.sumAvailableQty(warehouseId, entry.getKey());
            if (available.compareTo(entry.getValue()) < 0) {
                Product product = productMapper.selectById(entry.getKey());
                String name = product == null ? String.valueOf(entry.getKey()) : product.getProductName();
                throw new BizException(ResultCode.INSUFFICIENT_STOCK,
                        "商品「" + name + "」可用库存不足：需要 " + entry.getValue().toPlainString()
                                + "，可用 " + available.toPlainString());
            }
        }
    }

    /** 商品必须存在，否则 40401。 */
    private Product requireProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "productId 对应商品不存在");
        }
        return product;
    }

    /** 明细实体批量入库（含 orderId）。 */
    private void insertItems(Long orderId, List<OutboundOrderItem> items) {
        for (OutboundOrderItem item : items) {
            item.setOrderId(orderId);
            itemMapper.insert(item);
        }
    }

    /** 数据库明细转回 DTO 结构（修改/提交/审核时复用 buildItems 校验）。 */
    private List<OutboundItemDTO> toItemDTOs(Long orderId) {
        return itemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItem>()
                        .eq(OutboundOrderItem::getOrderId, orderId)
                        .orderByAsc(OutboundOrderItem::getLineNo)).stream()
                .map(item -> {
                    OutboundItemDTO dto = new OutboundItemDTO();
                    dto.setProductId(item.getProductId());
                    dto.setBatchNo(item.getBatchNo());
                    dto.setLocationId(item.getLocationId());
                    dto.setPlanQty(item.getPlanQty());
                    dto.setActualQty(item.getActualQty());
                    dto.setUnit(item.getUnit());
                    dto.setRemark(item.getRemark());
                    return dto;
                })
                .toList();
    }

    private BigDecimal sumPlan(List<OutboundOrderItem> items) {
        return items.stream().map(OutboundOrderItem::getPlanQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 生成出库单号：CK + yyyyMMdd + 3位序号（并发由 uk_outbound_no 兜底 40904）。 */
    private String nextOutboundNo() {
        String prefix = "CK" + LocalDate.now().format(DAY);
        String latest = orderMapper.selectLatestNo(prefix);
        int seq = 1;
        if (latest != null && latest.length() >= prefix.length() + 3) {
            try {
                seq = Integer.parseInt(latest.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                seq = 1;
            }
        }
        if (seq > 999) {
            throw new BizException(ResultCode.CODE_EXISTS, "当日出库单号已达上限（999），请次日再建单");
        }
        return prefix + String.format("%03d", seq);
    }

    /** 生成流水号：LS + yyyyMMdd + 5位序号（并发由 uk_flow_no 兜底 40904）。 */
    private String nextFlowNo() {
        String prefix = "LS" + LocalDate.now().format(DAY);
        String latest = flowMapper.selectLatestNo(prefix);
        int seq = 1;
        if (latest != null && latest.length() >= prefix.length() + 5) {
            try {
                seq = Integer.parseInt(latest.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                seq = 1;
            }
        }
        if (seq > 99999) {
            throw new BizException(ResultCode.CODE_EXISTS, "当日库存流水号已达上限");
        }
        return prefix + String.format("%05d", seq);
    }

    /** 解析 yyyy-MM-dd 日期串（非法 40001）；空白返回 null。 */
    private LocalDate parseDate(String date, String field) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new BizException(ResultCode.PARAM_INVALID, field + " 格式须为 yyyy-MM-dd");
        }
    }

    /** 追加备注：已有备注换行拼接。 */
    private String appendRemark(String remark, String addition) {
        if (!StringUtils.hasText(addition)) {
            return remark;
        }
        return StringUtils.hasText(remark) ? remark + "\n" + addition : addition;
    }

    // ---------- VO 组装 ----------

    /** 单据转 VO；withItems=true 时加载明细（明细反查商品/货位编码）。actualQty 始终由明细汇总。 */
    private OutboundOrderVO toVO(OutboundOrder order, boolean withItems) {
        OutboundOrderVO vo = new OutboundOrderVO();
        vo.setId(order.getId());
        vo.setOutboundNo(order.getOutboundNo());
        vo.setOrderType(OutboundOrderType.toLabel(order.getOrderType()));
        vo.setCustomerId(order.getCustomerId());
        vo.setSourceOrderNo(order.getSourceOrderNo());
        vo.setExpectDate(order.getExpectDate());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(OrderStatus.descOf(order.getStatus()));
        vo.setWarehouseId(order.getWarehouseId());
        vo.setTotalQty(order.getTotalQty());
        vo.setPickUserId(order.getPickUserId());
        vo.setRemark(order.getRemark());
        vo.setVersion(order.getVersion());
        vo.setAuditorId(order.getAuditorId());
        vo.setAuditTime(order.getAuditTime());
        vo.setPosterId(order.getPosterId());
        vo.setPostTime(order.getPostTime());
        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());

        // 关联反查
        if (order.getCustomerId() != null) {
            Customer c = customerMapper.selectById(order.getCustomerId());
            vo.setCustomerName(c == null ? null : c.getCustomerName());
        }
        Warehouse wh = warehouseMapper.selectById(order.getWarehouseId());
        vo.setWarehouseName(wh == null ? null : wh.getWarehouseName());
        vo.setAuditorName(username(order.getAuditorId()));
        vo.setPosterName(username(order.getPosterId()));
        vo.setCreateBy(username(order.getCreateBy()));

        if (withItems) {
            List<OutboundItemVO> itemVOs = toItemVOs(order.getId());
            vo.setItems(itemVOs);
            vo.setActualQty(itemVOs.stream().map(OutboundItemVO::getActualQty)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return vo;
    }

    /**
     * 批量列表 VO：一次查询全部明细汇总 actualQty（主表无该列），不返回 items。
     */
    private List<OutboundOrderVO> toVOList(List<OutboundOrder> orders, boolean withItems) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Set<Long> orderIds = orders.stream().map(OutboundOrder::getId)
                .collect(Collectors.toSet());
        Map<Long, BigDecimal> actualSum = itemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItem>()
                        .in(OutboundOrderItem::getOrderId, orderIds)).stream()
                .filter(i -> i.getActualQty() != null)
                .collect(Collectors.groupingBy(OutboundOrderItem::getOrderId,
                        Collectors.mapping(OutboundOrderItem::getActualQty,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        List<OutboundOrderVO> result = new ArrayList<>(orders.size());
        for (OutboundOrder order : orders) {
            OutboundOrderVO vo = toVO(order, withItems);
            if (!withItems) {
                vo.setActualQty(actualSum.getOrDefault(order.getId(), ZERO));
            }
            result.add(vo);
        }
        return result;
    }

    /** 明细行转 VO（反查商品/货位编码）。 */
    private List<OutboundItemVO> toItemVOs(Long orderId) {
        List<OutboundOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItem>()
                .eq(OutboundOrderItem::getOrderId, orderId)
                .orderByAsc(OutboundOrderItem::getLineNo));
        if (items.isEmpty()) {
            return List.of();
        }
        Set<Long> productIds = new HashSet<>();
        Set<Long> locationIds = new HashSet<>();
        items.forEach(i -> {
            productIds.add(i.getProductId());
            if (i.getLocationId() != null) {
                locationIds.add(i.getLocationId());
            }
        });
        Map<Long, Product> products = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (x, y) -> x));
        Map<Long, Location> locations = locationIds.isEmpty() ? Map.of()
                : locationMapper.selectBatchIds(locationIds).stream()
                .collect(Collectors.toMap(Location::getId, l -> l, (x, y) -> x));

        List<OutboundItemVO> result = new ArrayList<>(items.size());
        for (OutboundOrderItem i : items) {
            OutboundItemVO vo = new OutboundItemVO();
            vo.setId(i.getId());
            vo.setLineNo(i.getLineNo());
            vo.setProductId(i.getProductId());
            Product p = products.get(i.getProductId());
            vo.setProductCode(p == null ? null : p.getProductCode());
            vo.setProductName(p == null ? null : p.getProductName());
            vo.setBatchId(i.getBatchId());
            vo.setBatchNo(i.getBatchNo());
            vo.setLocationId(i.getLocationId());
            Location l = i.getLocationId() == null ? null : locations.get(i.getLocationId());
            vo.setLocationCode(l == null ? null : l.getLocationCode());
            vo.setPlanQty(i.getPlanQty());
            vo.setActualQty(i.getActualQty());
            vo.setUnit(i.getUnit());
            vo.setRemark(i.getRemark());
            result.add(vo);
        }
        return result;
    }

    /** 用户名反查（已删除用户置 null）。 */
    private String username(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? null : user.getUsername();
    }

    private String emptyToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }
}
