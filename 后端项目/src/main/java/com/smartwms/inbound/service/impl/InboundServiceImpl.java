package com.smartwms.inbound.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.base.mapper.LocationMapper;
import com.smartwms.base.mapper.ProductMapper;
import com.smartwms.base.mapper.SupplierMapper;
import com.smartwms.base.mapper.WarehouseMapper;
import com.smartwms.entity.InboundOrder;
import com.smartwms.entity.InboundOrderItem;
import com.smartwms.entity.Inventory;
import com.smartwms.entity.InventoryBatch;
import com.smartwms.entity.Location;
import com.smartwms.entity.Product;
import com.smartwms.entity.StockFlow;
import com.smartwms.entity.Supplier;
import com.smartwms.entity.SysUser;
import com.smartwms.entity.Warehouse;
import com.smartwms.enums.InboundOrderType;
import com.smartwms.enums.LocationState;
import com.smartwms.enums.OrderStatus;
import com.smartwms.enums.ResultCode;
import com.smartwms.exception.BizException;
import com.smartwms.inbound.dto.InboundCreateDTO;
import com.smartwms.inbound.dto.InboundItemDTO;
import com.smartwms.inbound.dto.InboundPageQueryDTO;
import com.smartwms.inbound.dto.InboundUpdateDTO;
import com.smartwms.inbound.dto.OrderActionDTO;
import com.smartwms.inbound.mapper.InboundOrderItemMapper;
import com.smartwms.inbound.mapper.InboundOrderMapper;
import com.smartwms.inbound.service.InboundService;
import com.smartwms.inbound.vo.InboundItemVO;
import com.smartwms.inbound.vo.InboundOrderVO;
import com.smartwms.inbound.vo.InboundPostResultVO;
import com.smartwms.inventory.mapper.InventoryBatchMapper;
import com.smartwms.inventory.mapper.InventoryMapper;
import com.smartwms.inventory.mapper.StockFlowMapper;
import com.smartwms.inventory.vo.StockFlowVO;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 入库管理服务实现（API-INB-001~011）。
 * 过账为最核心写路径：单事务内 锁批次行 → 锁库存行（FOR UPDATE）+ 乐观锁 version 双保险，
 * 任一行更新冲突（40905）整体回滚，库存与流水不会出现部分写入（文档规则「整体回滚、重试」）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboundServiceImpl implements InboundService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 货位状态：锁定 / 禁用 → 禁止收货（40906） */
    private static final Set<Integer> LOCATION_BLOCKED =
            Set.of(LocationState.LOCKED.getCode(), LocationState.DISABLED.getCode());

    private final InboundOrderMapper orderMapper;
    private final InboundOrderItemMapper itemMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryBatchMapper batchMapper;
    private final StockFlowMapper flowMapper;
    private final ProductMapper productMapper;
    private final LocationMapper locationMapper;
    private final SupplierMapper supplierMapper;
    private final WarehouseMapper warehouseMapper;
    private final SysUserMapper userMapper;
    private final WarehouseDataPerm dataPerm;

    // ==================== INB-001 分页查询 ====================

    @Override
    public PageResult<InboundOrderVO> page(InboundPageQueryDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 20 : Math.min(dto.getPageSize(), 100);

        Integer orderType = InboundOrderType.toCode(dto.getOrderType()); // 非法标签 40001
        dataPerm.checkWarehouse(dto.getWarehouseId());                   // 越权 40302
        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(0L, pageNum, pageSize, List.of());
        }

        LambdaQueryWrapper<InboundOrder> wrapper = new LambdaQueryWrapper<InboundOrder>()
                .eq(dto.getWarehouseId() != null, InboundOrder::getWarehouseId, dto.getWarehouseId())
                .eq(dto.getStatus() != null, InboundOrder::getStatus, dto.getStatus())
                .eq(dto.getSupplierId() != null, InboundOrder::getSupplierId, dto.getSupplierId())
                .eq(orderType != null, InboundOrder::getOrderType, orderType)
                .eq(dto.getExpectDate() != null, InboundOrder::getExpectDate, dto.getExpectDate())
                .ge(dto.getCreateTimeFrom() != null, InboundOrder::getCreateTime,
                        dto.getCreateTimeFrom() == null ? null : dto.getCreateTimeFrom().atStartOfDay())
                .lt(dto.getCreateTimeTo() != null, InboundOrder::getCreateTime,
                        dto.getCreateTimeTo() == null ? null : dto.getCreateTimeTo().plusDays(1).atStartOfDay())
                .in(allowed != null, InboundOrder::getWarehouseId, allowed)
                .orderByDesc(InboundOrder::getCreateTime)
                .orderByDesc(InboundOrder::getId);
        if (StringUtils.hasText(dto.getInboundNo())) {
            wrapper.like(InboundOrder::getInboundNo, dto.getInboundNo().trim());
        }
        if (StringUtils.hasText(dto.getKeyword())) {
            String like = "%" + dto.getKeyword().trim() + "%";
            wrapper.and(w -> w.like(InboundOrder::getInboundNo, like)
                    .or().like(InboundOrder::getSourceOrderNo, like));
        }

        Page<InboundOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, toVOList(page.getRecords(), false));
    }

    // ==================== INB-002 新增 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderVO create(InboundCreateDTO dto) {
        Integer orderType = InboundOrderType.toCode(dto.getOrderType());
        dataPerm.checkWarehouse(dto.getWarehouseId());
        validateSupplier(orderType, dto.getSupplierId(), dto.getWarehouseId());

        List<InboundOrderItem> items = buildItems(dto.getItems(), dto.getWarehouseId(), false);

        InboundOrder order = new InboundOrder();
        order.setInboundNo(nextInboundNo());
        order.setOrderType(orderType);
        order.setSupplierId(dto.getSupplierId());
        order.setSourceOrderNo(emptyToNull(dto.getSourceOrderNo()));
        order.setExpectDate(dto.getExpectDate());
        order.setStatus(OrderStatus.DRAFT.getCode());
        order.setWarehouseId(dto.getWarehouseId());
        order.setRemark(emptyToNull(dto.getRemark()));
        order.setTotalQty(sumPlan(items));
        order.setActualQty(sumActual(items));
        order.setVersion(1);
        order.setCreateBy(currentUserId());
        orderMapper.insert(order);

        insertItems(order.getId(), items);
        log.info("新增入库单成功 orderId={}, inboundNo={}, itemCount={}",
                order.getId(), order.getInboundNo(), items.size());
        return detail(order.getId());
    }

    // ==================== INB-003 详情 / 修改 ====================

    @Override
    public InboundOrderVO detail(Long id) {
        InboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        return toVO(order, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderVO update(Long id, InboundUpdateDTO dto) {
        InboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        Integer status = order.getStatus();
        if (!OrderStatus.DRAFT.getCode().equals(status) && !OrderStatus.REJECTED.getCode().equals(status)) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "仅草稿或已驳回的入库单允许修改");
        }
        checkVersion(order, dto.getVersion()); // 40905

        Integer orderType = InboundOrderType.toCode(dto.getOrderType());
        validateSupplier(orderType, dto.getSupplierId(), dto.getWarehouseId());
        List<InboundOrderItem> items = buildItems(dto.getItems(), dto.getWarehouseId(), false);

        // 整体替换明细（物理删除后重建，同事务）
        itemMapper.deleteByOrderId(id);
        insertItems(id, items);

        order.setOrderType(orderType);
        order.setWarehouseId(dto.getWarehouseId());
        order.setSupplierId(dto.getSupplierId());
        order.setSourceOrderNo(emptyToNull(dto.getSourceOrderNo()));
        order.setExpectDate(dto.getExpectDate());
        order.setRemark(emptyToNull(dto.getRemark()));
        order.setTotalQty(sumPlan(items));
        order.setActualQty(sumActual(items));
        // 已驳回保存后回到草稿（4→0）
        if (OrderStatus.REJECTED.getCode().equals(status)) {
            order.setStatus(OrderStatus.DRAFT.getCode());
        }
        order.setUpdateBy(currentUserId());
        saveOrder(order); // 乐观锁 version 自动 +1，并发冲突 40905
        log.info("修改入库单成功 orderId={}, inboundNo={}, itemCount={}", id, order.getInboundNo(), items.size());
        return detail(id);
    }

    // ==================== INB-004 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        InboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        Integer status = order.getStatus();
        if (!OrderStatus.DRAFT.getCode().equals(status) && !OrderStatus.REJECTED.getCode().equals(status)) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "仅草稿或已驳回的入库单允许删除");
        }
        itemMapper.deleteByOrderId(id);
        orderMapper.deletePhysically(id); // 单据不做逻辑删除
        log.info("删除入库单成功 orderId={}, inboundNo={}", id, order.getInboundNo());
    }

    // ==================== INB-005 提交 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderVO submit(Long id, OrderActionDTO dto) {
        InboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        expectStatus(order, dto.getVersion(), OrderStatus.DRAFT.getCode(), "提交");
        // 完整性预检：明细存在、数量>0、货位/商品/日期有效（40001）
        buildItems(toItemDTOs(id), order.getWarehouseId(), false);

        order.setStatus(OrderStatus.PENDING.getCode());
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(currentUserId());
        saveOrder(order);
        log.info("提交入库单成功 orderId={}, inboundNo={}", id, order.getInboundNo());
        return toVO(order, false);
    }

    // ==================== INB-006 审核 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderVO audit(Long id, OrderActionDTO dto) {
        InboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        expectStatus(order, dto.getVersion(), OrderStatus.PENDING.getCode(), "审核");
        buildItems(toItemDTOs(id), order.getWarehouseId(), false);

        Long userId = currentUserId();
        order.setStatus(OrderStatus.APPROVED.getCode());
        order.setAuditorId(userId);
        order.setAuditTime(LocalDateTime.now());
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(userId);
        saveOrder(order);
        log.info("审核入库单成功 orderId={}, inboundNo={}", id, order.getInboundNo());
        return toVO(order, false);
    }

    // ==================== INB-007 驳回 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderVO reject(Long id, OrderActionDTO dto) {
        InboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        expectStatus(order, dto.getVersion(), OrderStatus.PENDING.getCode(), "驳回");
        if (!StringUtils.hasText(dto.getReason())) {
            throw new BizException(ResultCode.PARAM_INVALID, "驳回时 reason 必填");
        }

        order.setStatus(OrderStatus.REJECTED.getCode());
        // inbound_order 无 reject_reason 字段（文档待确认项采纳「落 remark」）
        order.setRemark(appendRemark(order.getRemark(), "[驳回] " + dto.getReason().trim()));
        order.setUpdateBy(currentUserId());
        saveOrder(order);
        log.info("驳回入库单成功 orderId={}, inboundNo={}, reason={}",
                id, order.getInboundNo(), dto.getReason());
        return toVO(order, false);
    }

    // ==================== INB-008 过账 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundPostResultVO post(Long id, OrderActionDTO dto) {
        InboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        expectStatus(order, dto.getVersion(), OrderStatus.APPROVED.getCode(), "过账");

        List<InboundOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<InboundOrderItem>()
                .eq(InboundOrderItem::getOrderId, id)
                .orderByAsc(InboundOrderItem::getLineNo));
        if (items.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "入库单明细为空，无法过账");
        }
        // 过账复检：数量>0 且货位可用（过账实际数量必须 >0）
        for (InboundOrderItem item : items) {
            if (item.getActualQty() == null || item.getActualQty().signum() <= 0) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "过账实际数量必须大于 0（明细行 " + item.getLineNo() + "）");
            }
            checkLocationUsable(item.getLocationId(), order.getWarehouseId());
        }

        Long userId = currentUserId();
        LocalDateTime now = LocalDateTime.now();
        List<StockFlowVO> flowVOs = new ArrayList<>(items.size());
        for (InboundOrderItem item : items) {
            flowVOs.add(postOne(order, item, userId, now));
        }

        order.setStatus(OrderStatus.POSTED.getCode());
        order.setPosterId(userId);
        order.setPostTime(now);
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(userId);
        saveOrder(order);
        log.info("入库单过账成功 orderId={}, inboundNo={}, flowCount={}", id, order.getInboundNo(), flowVOs.size());

        return InboundPostResultVO.builder()
                .id(order.getId())
                .inboundNo(order.getInboundNo())
                .status(order.getStatus())
                .statusDesc(OrderStatus.descOf(order.getStatus()))
                .posterId(order.getPosterId())
                .postTime(order.getPostTime())
                .version(order.getVersion())
                .flows(flowVOs)
                .build();
    }

    /** 单行过账：锁批次 → 锁库存 → 加库存 → 记流水。 */
    private StockFlowVO postOne(InboundOrder order, InboundOrderItem item,
                                Long userId, LocalDateTime now) {
        Product product = requireProduct(item.getProductId());
        // 批次：batch_flag=0 的商品统一批次 DEFAULT（创建时已规范化）
        InventoryBatch batch = batchMapper.selectByKeyForUpdate(
                product.getId(), order.getWarehouseId(), item.getBatchNo());
        if (batch == null) {
            batch = new InventoryBatch();
            batch.setBatchNo(item.getBatchNo());
            batch.setProductId(product.getId());
            batch.setWarehouseId(order.getWarehouseId());
            batch.setProductionDate(item.getProductionDate());
            batch.setExpiryDate(item.getExpiryDate());
            batch.setSupplierId(order.getSupplierId());
            batch.setInboundNo(order.getInboundNo());
            batch.setBatchStatus(1); // 正常
            batch.setCreateBy(userId);
            batchMapper.insert(batch); // uk_batch 并发兜底（重复则事务回滚重试）
        }

        Inventory inv = inventoryMapper.selectForUpdate(
                order.getWarehouseId(), item.getLocationId(), product.getId(), batch.getId());
        BigDecimal before = BigDecimal.ZERO;
        if (inv == null) {
            inv = new Inventory();
            inv.setWarehouseId(order.getWarehouseId());
            inv.setLocationId(item.getLocationId());
            inv.setProductId(product.getId());
            inv.setBatchId(batch.getId());
            inv.setQty(item.getActualQty());
            inv.setLockQty(BigDecimal.ZERO);
            inv.setVersion(1);
            inv.setCreateBy(userId);
            inventoryMapper.insert(inv);
        } else {
            before = inv.getQty();
            inv.setQty(before.add(item.getActualQty()));
            inv.setUpdateBy(userId);
            // 乐观锁：version 不匹配则 0 行 → 40905 整体回滚
            if (inventoryMapper.updateById(inv) == 0) {
                throw new BizException(ResultCode.DUPLICATE_SUBMIT,
                        "库存并发更新冲突，请刷新后重试");
            }
        }

        String flowNo = nextFlowNo();
        StockFlow flow = new StockFlow();
        flow.setFlowNo(flowNo);
        flow.setWarehouseId(order.getWarehouseId());
        flow.setProductId(product.getId());
        flow.setBatchId(batch.getId());
        flow.setLocationId(item.getLocationId());
        flow.setDirection("IN");
        flow.setQty(item.getActualQty());
        flow.setBeforeQty(before);
        flow.setAfterQty(before.add(item.getActualQty()));
        flow.setFlowType(1); // 1入库
        flow.setRefType("INBOUND");
        flow.setRefId(order.getId());
        flow.setRefNo(order.getInboundNo());
        flow.setOperatorId(userId);
        flow.setOperTime(now);
        flow.setCreateBy(userId);
        flowMapper.insert(flow); // uk_flow_no 并发兜底

        return StockFlowVO.builder()
                .flowNo(flowNo)
                .warehouseId(order.getWarehouseId())
                .productId(product.getId())
                .batchId(batch.getId())
                .locationId(item.getLocationId())
                .direction("IN")
                .qty(item.getActualQty())
                .beforeQty(before)
                .afterQty(before.add(item.getActualQty()))
                .flowType(1)
                .refType("INBOUND")
                .refId(order.getId())
                .refNo(order.getInboundNo())
                .operTime(now)
                .build();
    }

    // ==================== INB-009/010 取消与撤回 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderVO cancel(Long id, OrderActionDTO dto) {
        InboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        Integer status = order.getStatus();
        checkVersion(order, dto.getVersion());
        boolean allowed = OrderStatus.DRAFT.getCode().equals(status)
                || OrderStatus.PENDING.getCode().equals(status)
                || OrderStatus.REJECTED.getCode().equals(status);
        if (!allowed) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "仅草稿/待审核/已驳回的入库单允许取消（已完成不可取消）");
        }
        order.setStatus(OrderStatus.CANCELLED.getCode());
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(currentUserId());
        saveOrder(order);
        log.info("取消入库单成功 orderId={}, inboundNo={}", id, order.getInboundNo());
        return toVO(order, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderVO withdraw(Long id, OrderActionDTO dto) {
        InboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        Integer status = order.getStatus();
        checkVersion(order, dto.getVersion());
        boolean allowed = OrderStatus.PENDING.getCode().equals(status)
                || OrderStatus.REJECTED.getCode().equals(status);
        if (!allowed) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "仅待审核/已驳回的入库单允许撤回");
        }
        order.setStatus(OrderStatus.DRAFT.getCode());
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(currentUserId());
        saveOrder(order);
        log.info("撤回入库单成功 orderId={}, inboundNo={}", id, order.getInboundNo());
        return toVO(order, false);
    }

    // ==================== INB-011 流水查询 ====================

    @Override
    public List<StockFlowVO> flows(Long id) {
        InboundOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        return flowMapper.selectList(new LambdaQueryWrapper<StockFlow>()
                .eq(StockFlow::getRefType, "INBOUND")
                .eq(StockFlow::getRefId, id)
                .orderByAsc(StockFlow::getId)).stream()
                .map(f -> StockFlowVO.builder()
                        .flowNo(f.getFlowNo())
                        .warehouseId(f.getWarehouseId())
                        .productId(f.getProductId())
                        .batchId(f.getBatchId())
                        .locationId(f.getLocationId())
                        .direction(f.getDirection())
                        .qty(f.getQty())
                        .beforeQty(f.getBeforeQty())
                        .afterQty(f.getAfterQty())
                        .flowType(f.getFlowType())
                        .refType(f.getRefType())
                        .refId(f.getRefId())
                        .refNo(f.getRefNo())
                        .operTime(f.getOperTime())
                        .build())
                .toList();
    }

    // ==================== 私有方法 ====================

    /** 单据必须存在（含未删除），否则 40401。 */
    private InboundOrder requireOrder(Long id) {
        InboundOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "入库单不存在或已删除");
        }
        return order;
    }

    /** 校验 version 乐观锁（不一致 40905）。 */
    private void checkVersion(InboundOrder order, Integer version) {
        if (version == null || !version.equals(order.getVersion())) {
            throw new BizException(ResultCode.DUPLICATE_SUBMIT, "单据已被其他操作修改，请刷新后重试");
        }
    }

    /** 校验当前状态 + version（状态不符 40901；version 不符 40905）。 */
    private void expectStatus(InboundOrder order, Integer version, Integer expect, String action) {
        checkVersion(order, version);
        if (!expect.equals(order.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "当前状态不允许" + action + "（期望 " + OrderStatus.descOf(expect)
                            + "，实际 " + OrderStatus.descOf(order.getStatus()) + "）");
        }
    }

    /** 乐观锁保存：version 由 @Version 插件 +1；影响 0 行说明并发冲突 → 40905 回滚。 */
    private void saveOrder(InboundOrder order) {
        if (orderMapper.updateById(order) == 0) {
            throw new BizException(ResultCode.DUPLICATE_SUBMIT, "单据已被其他操作修改，请刷新后重试");
        }
    }

    /** 类型为采购入库时 supplierId 必填（40001）且供应商存在（40401）。 */
    private void validateSupplier(Integer orderType, Long supplierId, Long warehouseId) {
        if (InboundOrderType.PURCHASE.getCode().equals(orderType) && supplierId == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "采购入库必须指定供应商 supplierId");
        }
        if (supplierId != null && supplierMapper.selectById(supplierId) == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "supplierId 对应供应商不存在");
        }
    }

    /**
     * 明细 DTO 校验并转实体（INB-002/003/005/006 复用）：
     * 商品/货位存在（40401）、货位属于该仓库（40001）、货位锁定/禁用（40906）、
     * 保质期日期（40001）、batch_flag=0 置 DEFAULT、flag=1 批次号必填（40001）、
     * unit 缺省商品单位、actualQty 缺省 planQty，生成 lineNo。
     *
     * @param normalizeForPost true 时按过账口径校验（实际数量必填，隐含 >0 校验由调用方完成）
     */
    private List<InboundOrderItem> buildItems(List<InboundItemDTO> dtos, Long warehouseId,
                                              boolean normalizeForPost) {
        List<InboundOrderItem> items = new ArrayList<>(dtos.size());
        for (int i = 0; i < dtos.size(); i++) {
            InboundItemDTO dto = dtos.get(i);

            Product product = requireProduct(dto.getProductId());
            Location location = locationMapper.selectById(dto.getLocationId());
            if (location == null) {
                throw new BizException(ResultCode.ORDER_NOT_EXIST, "locationId 对应货位不存在");
            }
            if (!warehouseId.equals(location.getWarehouseId())) {
                throw new BizException(ResultCode.PARAM_INVALID, "货位不属于指定仓库");
            }
            checkLocationUsable(location, dto.getLocationId());

            Integer shelfLifeFlag = product.getShelfLifeFlag() == null ? 0 : product.getShelfLifeFlag();
            if (shelfLifeFlag == 0) {
                // 非保质期商品：忽略生产/效期（规则4）
                dto.setProductionDate(null);
                dto.setExpiryDate(null);
            } else if (dto.getProductionDate() != null && dto.getExpiryDate() != null
                    && dto.getExpiryDate().isBefore(dto.getProductionDate())) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "明细行 " + (i + 1) + "：生产日期不能晚于过期日期");
            }

            Integer batchFlag = product.getBatchFlag() == null ? 1 : product.getBatchFlag();
            String batchNo;
            if (batchFlag == 0) {
                batchNo = "DEFAULT"; // 规则2
            } else {
                if (!StringUtils.hasText(dto.getBatchNo())) {
                    throw new BizException(ResultCode.PARAM_INVALID,
                            "明细行 " + (i + 1) + "：批次管理商品必须填写批次号");
                }
                batchNo = dto.getBatchNo().trim();
            }

            if (dto.getActualQty() == null) {
                dto.setActualQty(dto.getPlanQty()); // 规则2/3：实收缺省取计划
            }

            InboundOrderItem item = new InboundOrderItem();
            item.setLineNo(i + 1);
            item.setProductId(product.getId());
            item.setLocationId(location.getId());
            item.setBatchNo(batchNo);
            item.setProductionDate(dto.getProductionDate());
            item.setExpiryDate(dto.getExpiryDate());
            item.setPlanQty(dto.getPlanQty());
            item.setActualQty(dto.getActualQty());
            item.setUnit(StringUtils.hasText(dto.getUnit()) ? dto.getUnit().trim() : product.getUnit());
            item.setRemark(emptyToNull(dto.getRemark()));
            item.setCreateBy(currentUserId());
            items.add(item);
        }
        return items;
    }

    /** 货位可用性：存在且状态非锁定/禁用（40906）。 */
    private void checkLocationUsable(Long locationId, Long warehouseId) {
        Location location = locationMapper.selectById(locationId);
        if (location == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "locationId 对应货位不存在");
        }
        if (!warehouseId.equals(location.getWarehouseId())) {
            throw new BizException(ResultCode.PARAM_INVALID, "货位不属于指定仓库");
        }
        checkLocationUsable(location, locationId);
    }

    private void checkLocationUsable(Location location, Long locationId) {
        if (location.getCurrentStatus() != null && LOCATION_BLOCKED.contains(location.getCurrentStatus())) {
            throw new BizException(ResultCode.LOCATION_UNAVAILABLE,
                    "货位 " + locationId + " 当前状态不可用（锁定/禁用），禁止收货");
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
    private void insertItems(Long orderId, List<InboundOrderItem> items) {
        for (InboundOrderItem item : items) {
            item.setOrderId(orderId);
            itemMapper.insert(item);
        }
    }

    /** 数据库明细转回 DTO 结构（修改/提交/审核时复用 buildItems 校验）。 */
    private List<InboundItemDTO> toItemDTOs(Long orderId) {
        return itemMapper.selectList(new LambdaQueryWrapper<InboundOrderItem>()
                        .eq(InboundOrderItem::getOrderId, orderId)
                        .orderByAsc(InboundOrderItem::getLineNo)).stream()
                .map(item -> {
                    InboundItemDTO dto = new InboundItemDTO();
                    dto.setProductId(item.getProductId());
                    dto.setLocationId(item.getLocationId());
                    dto.setBatchNo(item.getBatchNo());
                    dto.setProductionDate(item.getProductionDate());
                    dto.setExpiryDate(item.getExpiryDate());
                    dto.setPlanQty(item.getPlanQty());
                    dto.setActualQty(item.getActualQty());
                    dto.setUnit(item.getUnit());
                    dto.setRemark(item.getRemark());
                    return dto;
                })
                .toList();
    }

    private BigDecimal sumPlan(List<InboundOrderItem> items) {
        return items.stream().map(InboundOrderItem::getPlanQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumActual(List<InboundOrderItem> items) {
        return items.stream().map(InboundOrderItem::getActualQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 生成入库单号：RK + yyyyMMdd + 3位序号（并发由 uk_inbound_no 兜底 40904）。 */
    private String nextInboundNo() {
        String prefix = "RK" + LocalDate.now().format(DAY);
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
            throw new BizException(ResultCode.CODE_EXISTS, "当日入库单号已达上限（999），请次日再建单");
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

    /** 追加备注：已有备注换行拼接。 */
    private String appendRemark(String remark, String addition) {
        if (!StringUtils.hasText(addition)) {
            return remark;
        }
        return StringUtils.hasText(remark) ? remark + "\n" + addition : addition;
    }

    // ---------- VO 组装 ----------

    /** 单据转 VO；withItems=true 时加载明细（明细反查商品/货位编码）。 */
    private InboundOrderVO toVO(InboundOrder order, boolean withItems) {
        InboundOrderVO vo = new InboundOrderVO();
        vo.setId(order.getId());
        vo.setInboundNo(order.getInboundNo());
        vo.setOrderType(InboundOrderType.toLabel(order.getOrderType()));
        vo.setSupplierId(order.getSupplierId());
        vo.setSourceOrderNo(order.getSourceOrderNo());
        vo.setExpectDate(order.getExpectDate());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(OrderStatus.descOf(order.getStatus()));
        vo.setWarehouseId(order.getWarehouseId());
        vo.setTotalQty(order.getTotalQty());
        vo.setActualQty(order.getActualQty());
        vo.setRemark(order.getRemark());
        vo.setVersion(order.getVersion());
        vo.setAuditorId(order.getAuditorId());
        vo.setAuditTime(order.getAuditTime());
        vo.setPosterId(order.getPosterId());
        vo.setPostTime(order.getPostTime());
        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());

        // 关联反查
        if (order.getSupplierId() != null) {
            Supplier s = supplierMapper.selectById(order.getSupplierId());
            vo.setSupplierName(s == null ? null : s.getSupplierName());
        }
        Warehouse wh = warehouseMapper.selectById(order.getWarehouseId());
        vo.setWarehouseName(wh == null ? null : wh.getWarehouseName());
        vo.setAuditorName(username(order.getAuditorId()));
        vo.setPosterName(username(order.getPosterId()));
        vo.setCreateBy(username(order.getCreateBy()));

        if (withItems) {
            vo.setItems(toItemVOs(order.getId()));
        }
        return vo;
    }

    /** 批量列表 VO（明细反查在详情接口完成，列表不带明细）。 */
    private List<InboundOrderVO> toVOList(List<InboundOrder> orders, boolean withItems) {
        return orders.stream().map(o -> toVO(o, withItems)).toList();
    }

    /** 明细行转 VO（反查商品/货位编码）。 */
    private List<InboundItemVO> toItemVOs(Long orderId) {
        List<InboundOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<InboundOrderItem>()
                .eq(InboundOrderItem::getOrderId, orderId)
                .orderByAsc(InboundOrderItem::getLineNo));
        if (items.isEmpty()) {
            return List.of();
        }
        Set<Long> productIds = new HashSet<>();
        Set<Long> locationIds = new HashSet<>();
        items.forEach(i -> {
            productIds.add(i.getProductId());
            locationIds.add(i.getLocationId());
        });
        Map<Long, Product> products = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (x, y) -> x));
        Map<Long, Location> locations = locationMapper.selectBatchIds(locationIds).stream()
                .collect(Collectors.toMap(Location::getId, l -> l, (x, y) -> x));

        List<InboundItemVO> result = new ArrayList<>(items.size());
        for (InboundOrderItem i : items) {
            InboundItemVO vo = new InboundItemVO();
            vo.setId(i.getId());
            vo.setLineNo(i.getLineNo());
            vo.setProductId(i.getProductId());
            Product p = products.get(i.getProductId());
            vo.setProductCode(p == null ? null : p.getProductCode());
            vo.setProductName(p == null ? null : p.getProductName());
            vo.setLocationId(i.getLocationId());
            Location l = locations.get(i.getLocationId());
            vo.setLocationCode(l == null ? null : l.getLocationCode());
            vo.setBatchNo(i.getBatchNo());
            vo.setProductionDate(i.getProductionDate());
            vo.setExpiryDate(i.getExpiryDate());
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
