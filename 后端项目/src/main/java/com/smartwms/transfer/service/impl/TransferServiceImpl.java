package com.smartwms.transfer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.base.mapper.LocationMapper;
import com.smartwms.base.mapper.ProductMapper;
import com.smartwms.base.mapper.WarehouseMapper;
import com.smartwms.entity.Inventory;
import com.smartwms.entity.InventoryBatch;
import com.smartwms.entity.Location;
import com.smartwms.entity.Product;
import com.smartwms.entity.StockFlow;
import com.smartwms.entity.SysUser;
import com.smartwms.entity.TransferOrder;
import com.smartwms.entity.TransferOrderItem;
import com.smartwms.entity.Warehouse;
import com.smartwms.enums.ConfirmStatus;
import com.smartwms.enums.LocationState;
import com.smartwms.enums.OrderStatus;
import com.smartwms.enums.ResultCode;
import com.smartwms.exception.BizException;
import com.smartwms.inbound.dto.OrderActionDTO;
import com.smartwms.inventory.mapper.InventoryBatchMapper;
import com.smartwms.inventory.mapper.InventoryMapper;
import com.smartwms.inventory.mapper.StockFlowMapper;
import com.smartwms.inventory.vo.StockFlowVO;
import com.smartwms.security.LoginUser;
import com.smartwms.security.WarehouseDataPerm;
import com.smartwms.system.mapper.SysUserMapper;
import com.smartwms.transfer.dto.TransferCreateDTO;
import com.smartwms.transfer.dto.TransferItemDTO;
import com.smartwms.transfer.dto.TransferPageQueryDTO;
import com.smartwms.transfer.dto.TransferUpdateDTO;
import com.smartwms.transfer.mapper.TransferOrderItemMapper;
import com.smartwms.transfer.mapper.TransferOrderMapper;
import com.smartwms.transfer.service.TransferService;
import com.smartwms.transfer.vo.TransferItemVO;
import com.smartwms.transfer.vo.TransferOrderVO;
import com.smartwms.transfer.vo.TransferPostResultVO;
import com.smartwms.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
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
 * 库存调拨服务实现（API-TRF-001~011）。
 * 与出库同构的状态机与三重幂等防护（状态机 40901 + 单号唯一 40904 + 乐观锁 40905），
 * 差异点：
 * 1. 过账（009）为基线第 8 节简化模型——同事务内源仓减 + 目标仓增，每分配行两条流水
 *    （源 OUT flow_type=4 调拨出 / 目标 IN flow_type=3 调拨入），任一失败整体回滚；
 *    过账前置 (location_id, product_id, batch_id) 顺序预锁源仓行（13.5 规则 4 防死锁）；
 * 2. 批次沿用源仓批次：目标仓按 (product_id, warehouse_id, batch_no) 创建或复用 inventory_batch，
 *    目标库存行不存在则新建（uk 冲突 → 40905 整体回滚后可重试）；
 * 3. 提交（006）/审核（007）按 actualQty 做源仓可用库存预检（40902，提示性）；
 * 4. 数据权限（13.7 待确认采纳）：查询可见 = 源仓或目标仓任一在权限内；
 *    写操作（创建/修改/删除/动作/过账）双仓校验，越权 40302。
 * 文档 13.7 其余待确认项：destLocationId 创建必填；confirm_status 未确认0/已确认1 过账置已确认；
 * rejectReason 落 remark（「[驳回] 」段解析）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    /** 目标货位不可用状态：锁定/禁用（可用=空闲/占用 → 40906） */
    private static final Set<Integer> LOCATION_BLOCKED =
            Set.of(LocationState.LOCKED.getCode(), LocationState.DISABLED.getCode());

    private final TransferOrderMapper orderMapper;
    private final TransferOrderItemMapper itemMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryBatchMapper batchMapper;
    private final StockFlowMapper flowMapper;
    private final ProductMapper productMapper;
    private final LocationMapper locationMapper;
    private final WarehouseMapper warehouseMapper;
    private final SysUserMapper userMapper;
    private final WarehouseDataPerm dataPerm;

    // ==================== TRF-001 分页查询 ====================

    @Override
    public PageResult<TransferOrderVO> page(TransferPageQueryDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 20 : Math.min(dto.getPageSize(), 100);

        if (dto.getStatus() != null && (dto.getStatus() < 0 || dto.getStatus() > 5)) {
            throw new BizException(ResultCode.PARAM_INVALID, "status 取值为 0~5");
        }
        dataPerm.checkWarehouse(dto.getSrcWarehouseId());  // 越权 40302（null 不校验）
        dataPerm.checkWarehouse(dto.getDestWarehouseId());
        LocalDate start = parseDate(dto.getStartDate(), "startDate");
        LocalDate end = parseDate(dto.getEndDate(), "endDate");
        if (start != null && end != null && start.isAfter(end)) {
            throw new BizException(ResultCode.PARAM_INVALID, "startDate 不能晚于 endDate");
        }
        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(0L, pageNum, pageSize, List.of());
        }

        LambdaQueryWrapper<TransferOrder> wrapper = new LambdaQueryWrapper<TransferOrder>()
                .eq(dto.getSrcWarehouseId() != null, TransferOrder::getSrcWarehouseId, dto.getSrcWarehouseId())
                .eq(dto.getDestWarehouseId() != null, TransferOrder::getDestWarehouseId, dto.getDestWarehouseId())
                .eq(dto.getStatus() != null, TransferOrder::getStatus, dto.getStatus())
                .ge(start != null, TransferOrder::getCreateTime,
                        start == null ? null : start.atStartOfDay())
                .lt(end != null, TransferOrder::getCreateTime,
                        end == null ? null : end.plusDays(1).atStartOfDay())
                // 可见口径：源仓或目标仓任一在权限内即可见（13.7 待确认采纳「任一」）
                .and(allowed != null, w -> w.in(TransferOrder::getSrcWarehouseId, allowed)
                        .or().in(TransferOrder::getDestWarehouseId, allowed))
                .orderByDesc(TransferOrder::getCreateTime)
                .orderByDesc(TransferOrder::getId);
        if (StringUtils.hasText(dto.getTransferNo())) {
            wrapper.like(TransferOrder::getTransferNo, dto.getTransferNo().trim());
        }
        if (StringUtils.hasText(dto.getKeyword())) {
            String like = "%" + dto.getKeyword().trim() + "%";
            // keyword 覆盖单号与源/目标仓库名（correlated EXISTS 关联仓库表）
            wrapper.and(w -> w.like(TransferOrder::getTransferNo, like)
                    .or().apply("EXISTS (SELECT 1 FROM warehouse w"
                            + " WHERE w.id = src_warehouse_id AND w.deleted = 0"
                            + " AND w.warehouse_name LIKE {0})", like)
                    .or().apply("EXISTS (SELECT 1 FROM warehouse w"
                            + " WHERE w.id = dest_warehouse_id AND w.deleted = 0"
                            + " AND w.warehouse_name LIKE {0})", like));
        }

        Page<TransferOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, toVOList(page.getRecords(), false));
    }

    // ==================== TRF-002 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferOrderVO create(TransferCreateDTO dto) {
        validatePair(dto.getSrcWarehouseId(), dto.getDestWarehouseId());
        // 双仓数据权限（13.6：写操作源/目标仓均须在权限内）
        dataPerm.checkWarehouse(dto.getSrcWarehouseId());
        dataPerm.checkWarehouse(dto.getDestWarehouseId());

        List<TransferOrderItem> items = buildItems(dto.getItems(),
                dto.getSrcWarehouseId(), dto.getDestWarehouseId());

        TransferOrder order = new TransferOrder();
        order.setTransferNo(nextTransferNo());
        order.setSrcWarehouseId(dto.getSrcWarehouseId());
        order.setDestWarehouseId(dto.getDestWarehouseId());
        order.setStatus(OrderStatus.DRAFT.getCode());
        order.setConfirmStatus(ConfirmStatus.UNCONFIRMED.getCode());
        order.setRemark(emptyToNull(dto.getRemark()));
        order.setTotalQty(sumPlan(items));
        order.setVersion(1);
        order.setCreateBy(currentUserId());
        orderMapper.insert(order);

        insertItems(order.getId(), items);
        log.info("新增调拨单成功 orderId={}, transferNo={}, itemCount={}",
                order.getId(), order.getTransferNo(), items.size());
        return detail(order.getId());
    }

    // ==================== TRF-003 详情 / 修改 ====================

    @Override
    public TransferOrderVO detail(Long id) {
        TransferOrder order = requireOrder(id);
        checkVisible(order); // 查询可见口径：源/目标仓任一在权限内
        return toVO(order, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferOrderVO update(Long id, TransferUpdateDTO dto) {
        TransferOrder order = requireOrder(id);
        checkWritePerm(order); // 双仓校验
        Integer status = order.getStatus();
        if (!OrderStatus.DRAFT.getCode().equals(status) && !OrderStatus.REJECTED.getCode().equals(status)) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "仅草稿或已驳回的调拨单允许修改");
        }
        checkVersion(order, dto.getVersion()); // 40905

        validatePair(dto.getSrcWarehouseId(), dto.getDestWarehouseId());
        List<TransferOrderItem> items = buildItems(dto.getItems(),
                dto.getSrcWarehouseId(), dto.getDestWarehouseId());

        // 整体替换明细（物理删除后重建，同事务）
        itemMapper.deleteByOrderId(id);
        insertItems(id, items);

        order.setSrcWarehouseId(dto.getSrcWarehouseId());
        order.setDestWarehouseId(dto.getDestWarehouseId());
        order.setRemark(emptyToNull(dto.getRemark()));
        order.setTotalQty(sumPlan(items));
        // 已驳回保存后回到草稿（4→0）
        if (OrderStatus.REJECTED.getCode().equals(status)) {
            order.setStatus(OrderStatus.DRAFT.getCode());
        }
        order.setUpdateBy(currentUserId());
        saveOrder(order); // 乐观锁 version 自动 +1，并发冲突 40905
        log.info("修改调拨单成功 orderId={}, transferNo={}, itemCount={}", id, order.getTransferNo(), items.size());
        return detail(id);
    }

    // ==================== TRF-005 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Integer version) {
        TransferOrder order = requireOrder(id);
        checkWritePerm(order);
        // 删除口径与出库一致：仅草稿 0 可删（文档 TRF-005）
        if (!OrderStatus.DRAFT.getCode().equals(order.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "仅草稿状态的调拨单允许删除");
        }
        if (version != null) {
            checkVersion(order, version); // 可选 ?version= 乐观锁（40905）
        }
        itemMapper.deleteByOrderId(id);
        orderMapper.deletePhysically(id); // 单据不做逻辑删除
        log.info("删除调拨单成功 orderId={}, transferNo={}", id, order.getTransferNo());
    }

    // ==================== TRF-006 提交 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferOrderVO submit(Long id, OrderActionDTO dto) {
        TransferOrder order = requireOrder(id);
        checkWritePerm(order);
        expectStatus(order, dto.getVersion(), OrderStatus.DRAFT.getCode(), "提交");
        // 完整性预检：明细存在、数量>0、商品/批次/货位有效（40001/40906）
        List<TransferOrderItem> items = buildItems(toItemDTOs(id),
                order.getSrcWarehouseId(), order.getDestWarehouseId());
        // 源仓可用库存预检（提示性，最终以过账为准）：不足 40902
        checkStockAvailable(order.getSrcWarehouseId(), items);

        order.setStatus(OrderStatus.PENDING.getCode());
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(currentUserId());
        saveOrder(order);
        log.info("提交调拨单成功 orderId={}, transferNo={}", id, order.getTransferNo());
        return toVO(order, false);
    }

    // ==================== TRF-007 审核 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferOrderVO audit(Long id, OrderActionDTO dto) {
        TransferOrder order = requireOrder(id);
        checkWritePerm(order); // 审核须同时具备源/目标仓权限（13.5 007 规则）
        expectStatus(order, dto.getVersion(), OrderStatus.PENDING.getCode(), "审核");
        List<TransferOrderItem> items = buildItems(toItemDTOs(id),
                order.getSrcWarehouseId(), order.getDestWarehouseId());
        checkStockAvailable(order.getSrcWarehouseId(), items); // 审核再次预检 40902

        Long userId = currentUserId();
        order.setStatus(OrderStatus.APPROVED.getCode());
        order.setAuditorId(userId);
        order.setAuditTime(LocalDateTime.now());
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(userId);
        saveOrder(order);
        log.info("审核调拨单成功 orderId={}, transferNo={}", id, order.getTransferNo());
        return toVO(order, false);
    }

    // ==================== TRF-008 驳回 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferOrderVO reject(Long id, OrderActionDTO dto) {
        TransferOrder order = requireOrder(id);
        checkWritePerm(order);
        expectStatus(order, dto.getVersion(), OrderStatus.PENDING.getCode(), "驳回");
        if (!StringUtils.hasText(dto.getReason())) {
            throw new BizException(ResultCode.PARAM_INVALID, "驳回时 reason 必填");
        }

        order.setStatus(OrderStatus.REJECTED.getCode());
        // transfer_order 无 reject_reason 字段（13.7 待确认项采纳「落 remark」）
        order.setRemark(appendRemark(order.getRemark(), "[驳回] " + dto.getReason().trim()));
        order.setUpdateBy(currentUserId());
        saveOrder(order);
        log.info("驳回调拨单成功 orderId={}, transferNo={}, reason={}",
                id, order.getTransferNo(), dto.getReason());
        return toVO(order, false);
    }

    // ==================== TRF-009 过账（源仓减 + 目标仓增，同事务） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferPostResultVO post(Long id, OrderActionDTO dto) {
        TransferOrder order = requireOrder(id);
        checkWritePerm(order);
        expectStatus(order, dto.getVersion(), OrderStatus.APPROVED.getCode(), "过账");

        List<TransferOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<TransferOrderItem>()
                .eq(TransferOrderItem::getOrderId, id)
                .orderByAsc(TransferOrderItem::getLineNo));
        if (items.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "调拨单明细为空，无法过账");
        }
        for (TransferOrderItem item : items) {
            if (item.getActualQty() == null || item.getActualQty().signum() <= 0) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "过账实际调拨数量必须大于 0（明细行 " + item.getLineNo() + "）");
            }
        }

        // 规则 4：按 (location_id, product_id, batch_id) 固定顺序预锁源仓库存行，避免与其它过账死锁
        Set<Long> productIds = items.stream()
                .map(TransferOrderItem::getProductId).collect(Collectors.toSet());
        inventoryMapper.selectForUpdateByProducts(order.getSrcWarehouseId(), productIds);

        Long userId = currentUserId();
        LocalDateTime now = LocalDateTime.now();
        List<StockFlowVO> flowVOs = new ArrayList<>(items.size() * 2);
        for (TransferOrderItem item : items) {
            flowVOs.addAll(postOne(order, item, userId, now));
        }

        order.setStatus(OrderStatus.POSTED.getCode());
        order.setConfirmStatus(ConfirmStatus.CONFIRMED.getCode()); // 简化模型：过账即视为目标仓已确认
        order.setPosterId(userId);
        order.setPostTime(now);
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(userId);
        saveOrder(order);
        log.info("调拨单过账成功 orderId={}, transferNo={}, flowCount={}",
                id, order.getTransferNo(), flowVOs.size());

        return TransferPostResultVO.builder()
                .id(order.getId())
                .transferNo(order.getTransferNo())
                .status(order.getStatus())
                .statusDesc(OrderStatus.descOf(order.getStatus()))
                .confirmStatus(ConfirmStatus.toLabel(order.getConfirmStatus()))
                .posterId(order.getPosterId())
                .postTime(order.getPostTime())
                .version(order.getVersion())
                .flows(flowVOs)
                .build();
    }

    /**
     * 单行过账：FIFO 候选（预锁后行锁复用）→ 逐源仓行扣减并同步目标仓增 → 回填明细。
     * 每个源仓分配行对应一次目标仓增（批次沿用：目标仓按 batch_no 复用/创建批次行），
     * 即分配跨批次时每行各写两条流水（源 OUT 调拨出4 / 目标 IN 调拨入3）。
     * 任一行失败（40902/40903/40905/40906）抛出后整体回滚，单据保持 status=2 可重试。
     */
    private List<StockFlowVO> postOne(TransferOrder order, TransferOrderItem item,
                                      Long userId, LocalDateTime now) {
        Long srcWh = order.getSrcWarehouseId();
        Long destWh = order.getDestWarehouseId();
        Product product = requireProduct(item.getProductId());

        // 目标货位终态校验（创建时已校验，防事后被锁定/禁用）：40906/40401
        Location destLoc = locationMapper.selectById(item.getDestLocationId());
        if (destLoc == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "destLocationId 对应货位不存在");
        }
        if (!destWh.equals(destLoc.getWarehouseId())) {
            throw new BizException(ResultCode.PARAM_INVALID, "目标货位不属于目标仓库");
        }
        if (LOCATION_BLOCKED.contains(destLoc.getCurrentStatus())) {
            throw new BizException(ResultCode.LOCATION_UNAVAILABLE,
                    "明细行 " + item.getLineNo() + "：目标货位状态不可用（锁定/禁用）");
        }

        // 指定批次兜底校验（创建时已校验，防批次状态事后变化）并标记优先分配
        Long preferredBatchId = null;
        if (item.getBatchId() != null) {
            InventoryBatch specified = batchMapper.selectById(item.getBatchId());
            if (specified == null || !srcWh.equals(specified.getWarehouseId())
                    || specified.getBatchStatus() == null || specified.getBatchStatus() != 1) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "明细行 " + item.getLineNo() + "：指定批次在源仓不存在或状态异常");
            }
            preferredBatchId = specified.getId();
        }

        // FIFO 候选：expiry_date IS NULL 排最后 → expiry_date ASC → production_date ASC → 批次 id ASC
        List<Inventory> candidates = inventoryMapper.selectFifoCandidates(srcWh, product.getId());
        // 指定源货位：仅从该货位扣减（不跨货位混合，同出库口径）
        if (item.getSrcLocationId() != null) {
            Long locId = item.getSrcLocationId();
            candidates = candidates.stream()
                    .filter(inv -> locId.equals(inv.getLocationId())).toList();
        }
        // 指定批次优先：稳定分区到队首，不足部分按 FIFO 从其它批次补齐（同出库 10.7 采纳口径）
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

        // 逐源仓行扣减，同步目标仓增并各写一条流水
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

            // 1) 源仓减（乐观锁双保险，0 行 → 40905 整体回滚）
            BigDecimal srcBefore = inv.getQty();
            BigDecimal srcAfter = srcBefore.subtract(take);
            if (srcAfter.signum() < 0) { // 兜底拦截（正常不可达）
                throw new BizException(ResultCode.NEGATIVE_STOCK,
                        "库存不能为负（明细行 " + item.getLineNo() + "）");
            }
            inv.setQty(srcAfter);
            inv.setUpdateBy(userId);
            if (inventoryMapper.updateById(inv) == 0) {
                throw new BizException(ResultCode.DUPLICATE_SUBMIT,
                        "库存并发更新冲突，请刷新后重试");
            }
            flows.add(buildFlow(order, product.getId(), inv.getBatchId(), inv.getLocationId(),
                    "OUT", take, srcBefore, srcAfter, 4, srcWh, userId, now)); // 4调拨出

            // 2) 目标仓增（批次沿用源仓批次号，目标仓复用/新建批次行）
            InventoryBatch destBatch = resolveDestBatch(order, product, inv.getBatchId(), userId);
            flows.add(increaseDest(order, product, destBatch, item.getDestLocationId(),
                    take, userId, now));

            need = need.subtract(take);
            usedRows.add(inv);
            takes.add(take);
        }
        if (need.signum() > 0) {
            throw new BizException(ResultCode.INSUFFICIENT_STOCK,
                    "商品「" + product.getProductName() + "」源仓可用库存不足，尚缺 " + need.toPlainString()
                            + "（明细行 " + item.getLineNo() + "）");
        }

        // 回填主批次与源货位（分配量最大，平局取 FIFO 首行）
        int mainIdx = 0;
        for (int i = 1; i < takes.size(); i++) {
            if (takes.get(i).compareTo(takes.get(mainIdx)) > 0) {
                mainIdx = i;
            }
        }
        Inventory main = usedRows.get(mainIdx);
        item.setBatchId(main.getBatchId());
        item.setSrcLocationId(main.getLocationId());
        item.setUpdateBy(userId);
        itemMapper.updateById(item);

        return flows;
    }

    /**
     * 目标仓批次：按 (product_id, warehouse_id, batch_no) 复用或创建（沿用源仓批次号与效期）。
     * FOR UPDATE 锁定批次行避免并发重复建行；uk_batch 冲突 → 40905 整体回滚后可重试。
     */
    private InventoryBatch resolveDestBatch(TransferOrder order, Product product,
                                            Long srcBatchId, Long userId) {
        InventoryBatch srcBatch = batchMapper.selectById(srcBatchId);
        if (srcBatch == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "分配到的源仓批次不存在");
        }
        InventoryBatch destBatch = batchMapper.selectByKeyForUpdate(
                product.getId(), order.getDestWarehouseId(), srcBatch.getBatchNo());
        if (destBatch != null) {
            return destBatch;
        }
        destBatch = new InventoryBatch();
        destBatch.setBatchNo(srcBatch.getBatchNo());
        destBatch.setProductId(product.getId());
        destBatch.setWarehouseId(order.getDestWarehouseId());
        destBatch.setProductionDate(srcBatch.getProductionDate()); // 沿用源仓批次效期
        destBatch.setExpiryDate(srcBatch.getExpiryDate());
        destBatch.setSupplierId(srcBatch.getSupplierId());
        destBatch.setInboundNo(order.getTransferNo()); // 目标仓批次首次建立的来源单号
        destBatch.setBatchStatus(1);
        destBatch.setCreateBy(userId);
        try {
            batchMapper.insert(destBatch);
        } catch (DuplicateKeyException e) {
            throw new BizException(ResultCode.DUPLICATE_SUBMIT,
                    "目标仓批次并发写入冲突，请重试");
        }
        return destBatch;
    }

    /**
     * 目标仓库存行增加：行锁定位 (destWh, destLocationId, productId, destBatchId)，
     * 不存在则新建（qty 初始 take）；uk 冲突/乐观锁 0 行 → 40905 整体回滚。
     * 返回本行 IN 流水（flow_type=3 调拨入，before/after 为目标仓前后值）。
     */
    private StockFlowVO increaseDest(TransferOrder order, Product product, InventoryBatch destBatch,
                                     Long destLocationId, BigDecimal take,
                                     Long userId, LocalDateTime now) {
        Long destWh = order.getDestWarehouseId();
        Inventory dest = inventoryMapper.selectForUpdate(
                destWh, destLocationId, product.getId(), destBatch.getId());

        BigDecimal before;
        BigDecimal after;
        if (dest == null) {
            before = ZERO;
            after = take;
            dest = new Inventory();
            dest.setWarehouseId(destWh);
            dest.setLocationId(destLocationId);
            dest.setProductId(product.getId());
            dest.setBatchId(destBatch.getId());
            dest.setQty(after);
            dest.setLockQty(ZERO);
            dest.setVersion(1);
            dest.setCreateBy(userId);
            try {
                inventoryMapper.insert(dest);
            } catch (DuplicateKeyException e) {
                throw new BizException(ResultCode.DUPLICATE_SUBMIT,
                        "目标仓库存并发写入冲突，请重试");
            }
        } else {
            before = dest.getQty();
            after = before.add(take);
            dest.setQty(after);
            dest.setUpdateBy(userId);
            if (inventoryMapper.updateById(dest) == 0) {
                throw new BizException(ResultCode.DUPLICATE_SUBMIT,
                        "库存并发更新冲突，请刷新后重试");
            }
        }
        return buildFlow(order, product.getId(), destBatch.getId(), destLocationId,
                "IN", take, before, after, 3, destWh, userId, now); // 3调拨入
    }

    /** 调拨流水（ref_type=TRANSFER，源仓 OUT 调拨出4 / 目标仓 IN 调拨入3）。 */
    private StockFlowVO buildFlow(TransferOrder order, Long productId, Long batchId, Long locationId,
                                  String direction, BigDecimal qty, BigDecimal before, BigDecimal after,
                                  Integer flowType, Long warehouseId, Long userId, LocalDateTime now) {
        String flowNo = nextFlowNo();
        StockFlow flow = new StockFlow();
        flow.setFlowNo(flowNo);
        flow.setWarehouseId(warehouseId);
        flow.setProductId(productId);
        flow.setBatchId(batchId);
        flow.setLocationId(locationId);
        flow.setDirection(direction);
        flow.setQty(qty);
        flow.setBeforeQty(before);
        flow.setAfterQty(after);
        flow.setFlowType(flowType);
        flow.setRefType("TRANSFER");
        flow.setRefId(order.getId());
        flow.setRefNo(order.getTransferNo());
        flow.setOperatorId(userId);
        flow.setOperTime(now);
        flow.setCreateBy(userId);
        flowMapper.insert(flow); // uk_flow_no 并发兜底

        return StockFlowVO.builder()
                .flowNo(flowNo)
                .warehouseId(warehouseId)
                .productId(productId)
                .batchId(batchId)
                .locationId(locationId)
                .direction(direction)
                .qty(qty)
                .beforeQty(before)
                .afterQty(after)
                .flowType(flowType)
                .refType("TRANSFER")
                .refId(order.getId())
                .refNo(order.getTransferNo())
                .operTime(now)
                .build();
    }

    // ==================== TRF-010/011 取消与撤回 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferOrderVO cancel(Long id, OrderActionDTO dto) {
        TransferOrder order = requireOrder(id);
        checkWritePerm(order);
        Integer status = order.getStatus();
        checkVersion(order, dto.getVersion());
        boolean allowed = OrderStatus.DRAFT.getCode().equals(status)
                || OrderStatus.PENDING.getCode().equals(status)
                || OrderStatus.REJECTED.getCode().equals(status);
        if (!allowed) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "仅草稿/待审核/已驳回的调拨单允许取消（已审核与已完成不可取消）");
        }
        order.setStatus(OrderStatus.CANCELLED.getCode());
        // 取消原因（文档 body 为 reason）落 remark
        String addition = StringUtils.hasText(dto.getReason())
                ? "[取消] " + dto.getReason().trim() : emptyToNull(dto.getRemark());
        order.setRemark(appendRemark(order.getRemark(), addition));
        order.setUpdateBy(currentUserId());
        saveOrder(order);
        log.info("取消调拨单成功 orderId={}, transferNo={}", id, order.getTransferNo());
        return toVO(order, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransferOrderVO withdraw(Long id, OrderActionDTO dto) {
        TransferOrder order = requireOrder(id);
        checkWritePerm(order);
        Integer status = order.getStatus();
        checkVersion(order, dto.getVersion());
        boolean allowed = OrderStatus.PENDING.getCode().equals(status)
                || OrderStatus.REJECTED.getCode().equals(status);
        if (!allowed) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "仅待审核/已驳回的调拨单允许撤回");
        }
        order.setStatus(OrderStatus.DRAFT.getCode());
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(currentUserId());
        saveOrder(order);
        log.info("撤回调拨单成功 orderId={}, transferNo={}", id, order.getTransferNo());
        return toVO(order, false);
    }

    // ==================== 私有方法 ====================

    /** 单据必须存在（含未删除），否则 40401。 */
    private TransferOrder requireOrder(Long id) {
        TransferOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "调拨单不存在或已删除");
        }
        return order;
    }

    /** 源仓 ≠ 目标仓（40001）。 */
    private void validatePair(Long srcWarehouseId, Long destWarehouseId) {
        if (srcWarehouseId != null && srcWarehouseId.equals(destWarehouseId)) {
            throw new BizException(ResultCode.PARAM_INVALID, "源仓库与目标仓库不能相同");
        }
    }

    /** 查询可见口径：源仓或目标仓任一在权限内即可见，均无则 40302（13.7 待确认采纳「任一」）。 */
    private void checkVisible(TransferOrder order) {
        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        if (allowed == null) {
            return;
        }
        if (!allowed.contains(order.getSrcWarehouseId()) && !allowed.contains(order.getDestWarehouseId())) {
            throw new BizException(ResultCode.NO_WAREHOUSE_PERM, "无数据仓库权限");
        }
    }

    /** 写操作口径：源仓与目标仓均须在权限内，越权 40302（13.3/13.6 双仓校验）。 */
    private void checkWritePerm(TransferOrder order) {
        dataPerm.checkWarehouse(order.getSrcWarehouseId());
        dataPerm.checkWarehouse(order.getDestWarehouseId());
    }

    /** 校验 version 乐观锁（不一致 40905）。 */
    private void checkVersion(TransferOrder order, Integer version) {
        if (version == null || !version.equals(order.getVersion())) {
            throw new BizException(ResultCode.DUPLICATE_SUBMIT, "单据已被其他操作修改，请刷新后重试");
        }
    }

    /** 校验当前状态 + version（状态不符 40901；version 不符 40905）。 */
    private void expectStatus(TransferOrder order, Integer version, Integer expect, String action) {
        checkVersion(order, version);
        if (!expect.equals(order.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "当前状态不允许" + action + "（期望 " + OrderStatus.descOf(expect)
                            + "，实际 " + OrderStatus.descOf(order.getStatus()) + "）");
        }
    }

    /** 乐观锁保存：version 由 @Version 插件 +1；影响 0 行说明并发冲突 → 40905 回滚。 */
    private void saveOrder(TransferOrder order) {
        if (orderMapper.updateById(order) == 0) {
            throw new BizException(ResultCode.DUPLICATE_SUBMIT, "单据已被其他操作修改，请刷新后重试");
        }
    }

    /**
     * 明细 DTO 校验并转实体（TRF-002/004/006/007 复用）：
     * 商品存在（40401）、目标货位属于目标仓且可用（40001/40906）、
     * 源货位属于源仓（40001）、指定批次属于源仓且状态正常（40001）、
     * actualQty 缺省 planQty，生成 lineNo。创建/提交阶段不查库存（预检单独执行）。
     */
    private List<TransferOrderItem> buildItems(List<TransferItemDTO> dtos,
                                               Long srcWarehouseId, Long destWarehouseId) {
        List<TransferOrderItem> items = new ArrayList<>(dtos.size());
        for (int i = 0; i < dtos.size(); i++) {
            TransferItemDTO dto = dtos.get(i);
            int line = i + 1;

            Product product = requireProduct(dto.getProductId());

            // 目标货位：存在、属于目标仓、状态可用（锁定/禁用 → 40906）
            Location destLoc = locationMapper.selectById(dto.getDestLocationId());
            if (destLoc == null) {
                throw new BizException(ResultCode.ORDER_NOT_EXIST, "destLocationId 对应货位不存在");
            }
            if (!destWarehouseId.equals(destLoc.getWarehouseId())) {
                throw new BizException(ResultCode.PARAM_INVALID, "目标货位不属于目标仓库");
            }
            if (LOCATION_BLOCKED.contains(destLoc.getCurrentStatus())) {
                throw new BizException(ResultCode.LOCATION_UNAVAILABLE,
                        "明细行 " + line + "：目标货位状态不可用（锁定/禁用）");
            }

            // 源货位（可选）：存在且属于源仓
            if (dto.getSrcLocationId() != null) {
                Location srcLoc = locationMapper.selectById(dto.getSrcLocationId());
                if (srcLoc == null) {
                    throw new BizException(ResultCode.ORDER_NOT_EXIST, "srcLocationId 对应货位不存在");
                }
                if (!srcWarehouseId.equals(srcLoc.getWarehouseId())) {
                    throw new BizException(ResultCode.PARAM_INVALID, "源货位不属于源仓库");
                }
            }

            // 指定批次（可选）：属于源仓且状态正常
            if (dto.getBatchId() != null) {
                InventoryBatch batch = batchMapper.selectById(dto.getBatchId());
                if (batch == null || !srcWarehouseId.equals(batch.getWarehouseId())
                        || batch.getBatchStatus() == null || batch.getBatchStatus() != 1) {
                    throw new BizException(ResultCode.PARAM_INVALID,
                            "明细行 " + line + "：指定批次在源仓不存在或状态异常");
                }
            }

            if (dto.getActualQty() == null) {
                dto.setActualQty(dto.getPlanQty()); // 实际数量缺省取计划
            }

            TransferOrderItem item = new TransferOrderItem();
            item.setLineNo(line);
            item.setProductId(product.getId());
            item.setBatchId(dto.getBatchId());
            item.setSrcLocationId(dto.getSrcLocationId());
            item.setDestLocationId(dto.getDestLocationId());
            item.setPlanQty(dto.getPlanQty());
            item.setActualQty(dto.getActualQty());
            item.setRemark(emptyToNull(dto.getRemark()));
            item.setCreateBy(currentUserId());
            items.add(item);
        }
        return items;
    }

    /**
     * 源仓可用库存预检（TRF-006 提交 / TRF-007 审核）：按商品汇总 actualQty（过账扣减口径），
     * 与 SUM(qty - lock_qty)（仅批次正常）比较，不足 40902（提示性，最终以过账为准）。
     */
    private void checkStockAvailable(Long srcWarehouseId, List<TransferOrderItem> items) {
        Map<Long, BigDecimal> needByProduct = new LinkedHashMap<>();
        for (TransferOrderItem item : items) {
            needByProduct.merge(item.getProductId(), item.getActualQty(), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> entry : needByProduct.entrySet()) {
            BigDecimal available = inventoryMapper.sumAvailableQty(srcWarehouseId, entry.getKey());
            if (available.compareTo(entry.getValue()) < 0) {
                Product product = productMapper.selectById(entry.getKey());
                String name = product == null ? String.valueOf(entry.getKey()) : product.getProductName();
                throw new BizException(ResultCode.INSUFFICIENT_STOCK,
                        "商品「" + name + "」源仓可用库存不足：需要 " + entry.getValue().toPlainString()
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
    private void insertItems(Long orderId, List<TransferOrderItem> items) {
        for (TransferOrderItem item : items) {
            item.setOrderId(orderId);
            itemMapper.insert(item);
        }
    }

    /** 数据库明细转回 DTO 结构（修改/提交/审核时复用 buildItems 校验）。 */
    private List<TransferItemDTO> toItemDTOs(Long orderId) {
        return itemMapper.selectList(new LambdaQueryWrapper<TransferOrderItem>()
                        .eq(TransferOrderItem::getOrderId, orderId)
                        .orderByAsc(TransferOrderItem::getLineNo)).stream()
                .map(item -> {
                    TransferItemDTO dto = new TransferItemDTO();
                    dto.setProductId(item.getProductId());
                    dto.setBatchId(item.getBatchId());
                    dto.setSrcLocationId(item.getSrcLocationId());
                    dto.setDestLocationId(item.getDestLocationId());
                    dto.setPlanQty(item.getPlanQty());
                    dto.setActualQty(item.getActualQty());
                    dto.setRemark(item.getRemark());
                    return dto;
                })
                .toList();
    }

    private BigDecimal sumPlan(List<TransferOrderItem> items) {
        return items.stream().map(TransferOrderItem::getPlanQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 生成调拨单号：DB + yyyyMMdd + 3位序号（并发由 uk_transfer_no 兜底 40904）。 */
    private String nextTransferNo() {
        String prefix = "DB" + LocalDate.now().format(DAY);
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
            throw new BizException(ResultCode.CODE_EXISTS, "当日调拨单号已达上限（999），请次日再建单");
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

    /** 从 remark 解析最近一次驳回原因（「[驳回] …」段）。 */
    private String extractRejectReason(String remark) {
        if (!StringUtils.hasText(remark)) {
            return null;
        }
        String result = null;
        for (String line : remark.split("\n")) {
            if (line.startsWith("[驳回] ")) {
                result = line.substring("[驳回] ".length());
            }
        }
        return result;
    }

    // ---------- VO 组装 ----------

    /**
     * 单据转 VO；withItems=true 时加载明细（明细反查商品/批次号/源与目标货位编码）。
     * actualQty 始终由明细汇总（主表无该列）；confirmStatus/rejectReason 中文展示。
     */
    private TransferOrderVO toVO(TransferOrder order, boolean withItems) {
        TransferOrderVO vo = new TransferOrderVO();
        vo.setId(order.getId());
        vo.setTransferNo(order.getTransferNo());
        vo.setSrcWarehouseId(order.getSrcWarehouseId());
        vo.setDestWarehouseId(order.getDestWarehouseId());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(OrderStatus.descOf(order.getStatus()));
        vo.setConfirmStatus(ConfirmStatus.toLabel(order.getConfirmStatus()));
        vo.setTotalQty(order.getTotalQty());
        vo.setRemark(order.getRemark());
        vo.setRejectReason(extractRejectReason(order.getRemark()));
        vo.setVersion(order.getVersion());
        vo.setAuditorId(order.getAuditorId());
        vo.setAuditTime(order.getAuditTime());
        vo.setPosterId(order.getPosterId());
        vo.setPostTime(order.getPostTime());
        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());

        // 关联反查
        Warehouse srcWh = warehouseMapper.selectById(order.getSrcWarehouseId());
        vo.setSrcWarehouseName(srcWh == null ? null : srcWh.getWarehouseName());
        Warehouse destWh = warehouseMapper.selectById(order.getDestWarehouseId());
        vo.setDestWarehouseName(destWh == null ? null : destWh.getWarehouseName());
        vo.setAuditorName(username(order.getAuditorId()));
        vo.setPosterName(username(order.getPosterId()));
        vo.setCreateBy(username(order.getCreateBy()));

        if (withItems) {
            List<TransferItemVO> itemVOs = toItemVOs(order.getId());
            vo.setItems(itemVOs);
            vo.setActualQty(itemVOs.stream().map(TransferItemVO::getActualQty)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return vo;
    }

    /**
     * 批量列表 VO：一次查询全部明细汇总 actualQty（主表无该列），不返回 items。
     */
    private List<TransferOrderVO> toVOList(List<TransferOrder> orders, boolean withItems) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Set<Long> orderIds = orders.stream().map(TransferOrder::getId)
                .collect(Collectors.toSet());
        Map<Long, BigDecimal> actualSum = itemMapper.selectList(new LambdaQueryWrapper<TransferOrderItem>()
                        .in(TransferOrderItem::getOrderId, orderIds)).stream()
                .filter(i -> i.getActualQty() != null)
                .collect(Collectors.groupingBy(TransferOrderItem::getOrderId,
                        Collectors.mapping(TransferOrderItem::getActualQty,
                                Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

        List<TransferOrderVO> result = new ArrayList<>(orders.size());
        for (TransferOrder order : orders) {
            TransferOrderVO vo = toVO(order, withItems);
            if (!withItems) {
                vo.setActualQty(actualSum.getOrDefault(order.getId(), ZERO));
            }
            result.add(vo);
        }
        return result;
    }

    /** 明细行转 VO（反查商品/批次号/源与目标货位编码；unit 取商品表，明细表无 unit 列）。 */
    private List<TransferItemVO> toItemVOs(Long orderId) {
        List<TransferOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<TransferOrderItem>()
                .eq(TransferOrderItem::getOrderId, orderId)
                .orderByAsc(TransferOrderItem::getLineNo));
        if (items.isEmpty()) {
            return List.of();
        }
        Set<Long> productIds = new HashSet<>();
        Set<Long> locationIds = new HashSet<>();
        Set<Long> batchIds = new HashSet<>();
        items.forEach(i -> {
            productIds.add(i.getProductId());
            if (i.getSrcLocationId() != null) {
                locationIds.add(i.getSrcLocationId());
            }
            if (i.getDestLocationId() != null) {
                locationIds.add(i.getDestLocationId());
            }
            if (i.getBatchId() != null) {
                batchIds.add(i.getBatchId());
            }
        });
        Map<Long, Product> products = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (x, y) -> x));
        Map<Long, Location> locations = locationIds.isEmpty() ? Map.of()
                : locationMapper.selectBatchIds(locationIds).stream()
                .collect(Collectors.toMap(Location::getId, l -> l, (x, y) -> x));
        Map<Long, InventoryBatch> batches = batchIds.isEmpty() ? Map.of()
                : batchMapper.selectBatchIds(batchIds).stream()
                .collect(Collectors.toMap(InventoryBatch::getId, b -> b, (x, y) -> x));

        List<TransferItemVO> result = new ArrayList<>(items.size());
        for (TransferOrderItem i : items) {
            TransferItemVO vo = new TransferItemVO();
            vo.setId(i.getId());
            vo.setLineNo(i.getLineNo());
            vo.setProductId(i.getProductId());
            Product p = products.get(i.getProductId());
            vo.setProductCode(p == null ? null : p.getProductCode());
            vo.setProductName(p == null ? null : p.getProductName());
            vo.setBatchId(i.getBatchId());
            InventoryBatch b = i.getBatchId() == null ? null : batches.get(i.getBatchId());
            vo.setBatchNo(b == null ? null : b.getBatchNo());
            vo.setSrcLocationId(i.getSrcLocationId());
            Location srcLoc = i.getSrcLocationId() == null ? null : locations.get(i.getSrcLocationId());
            vo.setSrcLocationCode(srcLoc == null ? null : srcLoc.getLocationCode());
            vo.setDestLocationId(i.getDestLocationId());
            Location destLoc = i.getDestLocationId() == null ? null : locations.get(i.getDestLocationId());
            vo.setDestLocationCode(destLoc == null ? null : destLoc.getLocationCode());
            vo.setPlanQty(i.getPlanQty());
            vo.setActualQty(i.getActualQty());
            vo.setUnit(p == null ? null : p.getUnit());
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
