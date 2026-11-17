package com.smartwms.module.stocktake.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartwms.module.storage.mapper.LocationMapper;
import com.smartwms.module.product.mapper.ProductCategoryMapper;
import com.smartwms.module.product.mapper.ProductMapper;
import com.smartwms.module.storage.mapper.StorageAreaMapper;
import com.smartwms.module.warehouse.mapper.WarehouseMapper;
import com.smartwms.entity.Inventory;
import com.smartwms.entity.InventoryBatch;
import com.smartwms.entity.Location;
import com.smartwms.entity.Product;
import com.smartwms.entity.ProductCategory;
import com.smartwms.entity.StockFlow;
import com.smartwms.entity.StocktakeOrder;
import com.smartwms.entity.StocktakeOrderItem;
import com.smartwms.entity.StorageArea;
import com.smartwms.entity.SysUser;
import com.smartwms.entity.Warehouse;
import com.smartwms.common.enums.DiffHandleStatus;
import com.smartwms.common.enums.DiffItemStatus;
import com.smartwms.common.enums.OrderStatus;
import com.smartwms.common.enums.ResultCode;
import com.smartwms.common.enums.StocktakeScope;
import com.smartwms.common.BizException;
import com.smartwms.module.inbound.dto.OrderActionDTO;
import com.smartwms.module.inventory.mapper.InventoryBatchMapper;
import com.smartwms.module.inventory.mapper.InventoryMapper;
import com.smartwms.module.inventory.mapper.StockFlowMapper;
import com.smartwms.module.inventory.vo.StockFlowVO;
import com.smartwms.framework.security.LoginUser;
import com.smartwms.framework.security.WarehouseDataPerm;
import com.smartwms.module.stocktake.dto.StocktakeCreateDTO;
import com.smartwms.module.stocktake.dto.StocktakeInputDTO;
import com.smartwms.module.stocktake.dto.StocktakePageQueryDTO;
import com.smartwms.module.stocktake.mapper.StocktakeOrderItemMapper;
import com.smartwms.module.stocktake.mapper.StocktakeOrderMapper;
import com.smartwms.module.stocktake.service.StocktakeService;
import com.smartwms.module.stocktake.vo.StocktakeCreateVO;
import com.smartwms.module.stocktake.vo.StocktakeItemVO;
import com.smartwms.module.stocktake.vo.StocktakeOrderVO;
import com.smartwms.module.stocktake.vo.StocktakePostResultVO;
import com.smartwms.module.system.mapper.SysUserMapper;
import com.smartwms.common.PageResult;
import com.smartwms.common.PageUtils;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 库存盘点服务实现（API-STK-001~011）。
 * 待确认项口径（12.7）：
 * 1. book_qty 采用「创建时快照」（提交/过账不刷新）；过账时校验当前 qty == book_qty，
 *    不一致视为盘点期间被其它单据过账 → 40907 整体回滚（并发互斥兜底）；
 * 2. 空货位零账面行不生成（stocktake_order_item.product_id 非空，无法落空货位行）；
 * 3. 同仓库多张未过账盘点单不禁止并行，依赖 40907 冲突兜底；
 * 4. rejectReason 落 remark（「[驳回] …」段，出参解析返回）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StocktakeServiceImpl implements StocktakeService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final StocktakeOrderMapper orderMapper;
    private final StocktakeOrderItemMapper itemMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryBatchMapper batchMapper;
    private final StockFlowMapper flowMapper;
    private final WarehouseMapper warehouseMapper;
    private final StorageAreaMapper areaMapper;
    private final LocationMapper locationMapper;
    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final SysUserMapper userMapper;
    private final WarehouseDataPerm dataPerm;

    // ==================== STK-001 分页查询 ====================

    @Override
    public PageResult<StocktakeOrderVO> page(StocktakePageQueryDTO dto) {
        if (dto.getStatus() != null && (dto.getStatus() < 0 || dto.getStatus() > 5)) {
            throw new BizException(ResultCode.PARAM_INVALID, "status 取值为 0~5");
        }
        Integer diffStatus = DiffHandleStatus.toCode(dto.getDiffStatus()); // 非法 40001
        dataPerm.checkWarehouse(dto.getWarehouseId());                     // 越权 40302
        LocalDate start = parseDate(dto.getStartDate(), "startDate");
        LocalDate end = parseDate(dto.getEndDate(), "endDate");
        if (start != null && end != null && start.isAfter(end)) {
            throw new BizException(ResultCode.PARAM_INVALID, "startDate 不能晚于 endDate");
        }
        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(0L, dto.pageNum(), dto.pageSize(), List.of());
        }

        LambdaQueryWrapper<StocktakeOrder> wrapper = new LambdaQueryWrapper<StocktakeOrder>()
                .eq(dto.getWarehouseId() != null, StocktakeOrder::getWarehouseId, dto.getWarehouseId())
                .eq(dto.getAreaId() != null, StocktakeOrder::getAreaId, dto.getAreaId())
                .eq(dto.getStatus() != null, StocktakeOrder::getStatus, dto.getStatus())
                .eq(diffStatus != null, StocktakeOrder::getDiffStatus, diffStatus)
                .ge(start != null, StocktakeOrder::getCreateTime,
                        start == null ? null : start.atStartOfDay())
                .lt(end != null, StocktakeOrder::getCreateTime,
                        end == null ? null : end.plusDays(1).atStartOfDay())
                .in(allowed != null, StocktakeOrder::getWarehouseId, allowed)
                .orderByDesc(StocktakeOrder::getCreateTime)
                .orderByDesc(StocktakeOrder::getId);
        if (StringUtils.hasText(dto.getStocktakeNo())) {
            wrapper.like(StocktakeOrder::getStocktakeNo, dto.getStocktakeNo().trim());
        }
        if (StringUtils.hasText(dto.getKeyword())) {
            String like = "%" + dto.getKeyword().trim() + "%";
            // keyword 覆盖单号与仓库名（correlated EXISTS 关联仓库表）
            wrapper.and(w -> w.like(StocktakeOrder::getStocktakeNo, like)
                    .or().apply("EXISTS (SELECT 1 FROM warehouse wh"
                            + " WHERE wh.id = warehouse_id AND wh.deleted = 0"
                            + " AND wh.warehouse_name LIKE {0})", like));
        }

        return PageUtils.query(orderMapper, dto, wrapper, list -> toVOList(list, false));
    }

    // ==================== STK-002 创建（自动生成明细） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeCreateVO create(StocktakeCreateDTO dto) {
        Integer scope = StocktakeScope.toCode(dto.getScope()); // 非法 40001
        dataPerm.checkWarehouse(dto.getWarehouseId());          // 越权 40302

        // scope=按库区：areaId 必填、存在且属于该仓库
        Long areaId = null;
        if (StocktakeScope.BY_AREA.getCode().equals(scope)) {
            if (dto.getAreaId() == null) {
                throw new BizException(ResultCode.PARAM_INVALID, "scope=按库区 时 areaId 必填");
            }
            StorageArea area = areaMapper.selectById(dto.getAreaId());
            if (area == null) {
                throw new BizException(ResultCode.ORDER_NOT_EXIST, "areaId 对应库区不存在");
            }
            if (!dto.getWarehouseId().equals(area.getWarehouseId())) {
                throw new BizException(ResultCode.PARAM_INVALID, "库区不属于指定仓库");
            }
            areaId = area.getId();
        }

        // 明细生成口径：范围内 inventory（qty > 0）逐行快照（12.7 待确认：创建时快照、不生成空货位行）
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<Inventory>()
                .select(Inventory::getLocationId, Inventory::getProductId,
                        Inventory::getBatchId, Inventory::getQty)
                .eq(Inventory::getWarehouseId, dto.getWarehouseId())
                .gt(Inventory::getQty, 0)
                .in(dto.getFilterProductIds() != null && !dto.getFilterProductIds().isEmpty(),
                        Inventory::getProductId, dto.getFilterProductIds());
        if (areaId != null) {
            Set<Long> locationIds = locationMapper.selectList(new LambdaQueryWrapper<Location>()
                            .select(Location::getId)
                            .eq(Location::getAreaId, areaId)).stream()
                    .map(Location::getId).collect(Collectors.toSet());
            if (locationIds.isEmpty()) {
                throw new BizException(ResultCode.PARAM_INVALID, "盘点范围内无可盘明细（该库区暂无库存货位）");
            }
            wrapper.in(Inventory::getLocationId, locationIds);
        }
        if (dto.getFilterCategoryIds() != null && !dto.getFilterCategoryIds().isEmpty()) {
            Set<Long> categoryIds = new HashSet<>();
            for (Long catId : dto.getFilterCategoryIds()) {
                categoryIds.addAll(categorySubtree(catId));
            }
            Set<Long> productIds = productMapper.selectList(new LambdaQueryWrapper<Product>()
                            .select(Product::getId)
                            .in(Product::getCategoryId, categoryIds)).stream()
                    .map(Product::getId).collect(Collectors.toSet());
            if (productIds.isEmpty()) {
                throw new BizException(ResultCode.PARAM_INVALID, "盘点范围内无可盘明细（分类下无商品库存）");
            }
            wrapper.in(Inventory::getProductId, productIds);
        }
        List<Inventory> rows = inventoryMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "盘点范围内无可盘明细（无 qty > 0 的库存记录）");
        }

        Long userId = currentUserId();
        StocktakeOrder order = new StocktakeOrder();
        order.setStocktakeNo(nextStocktakeNo());
        order.setWarehouseId(dto.getWarehouseId());
        order.setAreaId(areaId);
        order.setScope(scope);
        order.setStatus(OrderStatus.DRAFT.getCode());
        order.setDiffStatus(DiffHandleStatus.UNHANDLED.getCode());
        order.setPlanDate(dto.getPlanDate());
        order.setRemark(emptyToNull(dto.getRemark()));
        order.setTotalDifferQty(ZERO); // 初始无差异
        order.setVersion(1);
        order.setCreateBy(userId);
        orderMapper.insert(order);

        BigDecimal bookTotal = ZERO;
        int lineNo = 0;
        for (Inventory row : rows) {
            lineNo++;
            StocktakeOrderItem item = new StocktakeOrderItem();
            item.setOrderId(order.getId());
            item.setLineNo(lineNo);
            item.setLocationId(row.getLocationId());
            item.setProductId(row.getProductId());
            item.setBatchId(row.getBatchId());
            item.setBookQty(row.getQty());      // 账面数 = 创建时点快照
            item.setActualQty(null);            // 未盘
            item.setDifferQty(ZERO);
            item.setStatus(DiffItemStatus.CONFIRMED.getCode());
            item.setCreateBy(userId);
            itemMapper.insert(item);
            bookTotal = bookTotal.add(row.getQty());
        }
        log.info("创建盘点单成功 orderId={}, stocktakeNo={}, itemCount={}",
                order.getId(), order.getStocktakeNo(), lineNo);
        // 回读获取 DB 默认值（create_time 等由 DEFAULT 生成，插入后实体未回填）
        order = orderMapper.selectById(order.getId());

        return StocktakeCreateVO.builder()
                .id(order.getId())
                .stocktakeNo(order.getStocktakeNo())
                .status(order.getStatus())
                .statusDesc(OrderStatus.descOf(order.getStatus()))
                .scope(StocktakeScope.toLabel(order.getScope()))
                .warehouseId(order.getWarehouseId())
                .areaId(order.getAreaId())
                .itemCount((long) lineNo)
                .bookQtyTotal(bookTotal)
                .planDate(order.getPlanDate())
                .version(order.getVersion())
                .createTime(order.getCreateTime())
                .build();
    }

    // ==================== STK-003 详情 ====================

    @Override
    public StocktakeOrderVO detail(Long id) {
        StocktakeOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        return toVO(order, true);
    }

    // ==================== STK-004 录入实盘数 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeOrderVO input(Long id, StocktakeInputDTO dto) {
        StocktakeOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        Integer status = order.getStatus();
        if (!OrderStatus.DRAFT.getCode().equals(status) && !OrderStatus.REJECTED.getCode().equals(status)) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "仅草稿/已驳回状态允许录入实盘数");
        }
        if (dto.getVersion() != null) {
            checkVersion(order, dto.getVersion()); // 携带 version 时 40905
        }

        // 已有明细按 id 索引（id 必须属于本单，否则 40001）
        Map<Long, StocktakeOrderItem> itemMap = itemMapper.selectList(new LambdaQueryWrapper<StocktakeOrderItem>()
                        .eq(StocktakeOrderItem::getOrderId, id)).stream()
                .collect(Collectors.toMap(StocktakeOrderItem::getId, i -> i, (x, y) -> x));

        Long userId = currentUserId();
        for (StocktakeInputDTO.StocktakeInputItemDTO row : dto.getItems()) {
            StocktakeOrderItem item = itemMap.get(row.getId());
            if (item == null) {
                throw new BizException(ResultCode.PARAM_INVALID, "明细 id 不属于该盘点单：" + row.getId());
            }
            item.setActualQty(row.getActualQty());
            if (row.getDiffReason() != null) {
                item.setDiffReason(emptyToNull(row.getDiffReason()));
            }
            if (row.getStatus() != null) {
                item.setStatus(DiffItemStatus.toCode(row.getStatus())); // 非法 40001
            }
            // differQty = actual - book（服务端重算）
            item.setDifferQty(row.getActualQty().subtract(item.getBookQty()));
            item.setUpdateBy(userId);
            itemMapper.updateById(item);
        }

        // 汇总全部明细差异（total_differ_qty = Σ differ_qty）
        BigDecimal totalDiffer = itemMap.values().stream()
                .map(StocktakeOrderItem::getDifferQty)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
        if (dto.getRemark() != null) {
            order.setRemark(emptyToNull(dto.getRemark()));
        }
        // 录入后回到草稿（0 不变，4 → 0），须重新提交
        order.setStatus(OrderStatus.DRAFT.getCode());
        order.setTotalDifferQty(totalDiffer);
        order.setUpdateBy(userId);
        saveOrder(order);
        log.info("录入实盘数成功 orderId={}, stocktakeNo={}, totalDifferQty={}",
                id, order.getStocktakeNo(), totalDiffer);
        return detail(id);
    }

    // ==================== STK-005 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Integer version) {
        StocktakeOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        if (!OrderStatus.DRAFT.getCode().equals(order.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "仅草稿状态的盘点单允许删除（可先撤回/取消）");
        }
        if (version != null) {
            checkVersion(order, version); // 可选 ?version= 40905
        }
        itemMapper.deleteByOrderId(id);
        orderMapper.deletePhysically(id);
        log.info("删除盘点单成功 orderId={}, stocktakeNo={}", id, order.getStocktakeNo());
    }

    // ==================== STK-006 提交 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeOrderVO submit(Long id, OrderActionDTO dto) {
        StocktakeOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        expectStatus(order, dto.getVersion(), OrderStatus.DRAFT.getCode(), "提交");
        checkActualComplete(id); // 实盘数须全部录入 40001

        order.setStatus(OrderStatus.PENDING.getCode());
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(currentUserId());
        saveOrder(order);
        log.info("提交盘点单成功 orderId={}, stocktakeNo={}", id, order.getStocktakeNo());
        return toVO(order, false);
    }

    // ==================== STK-007 审核（差异授权） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeOrderVO audit(Long id, OrderActionDTO dto) {
        StocktakeOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        expectStatus(order, dto.getVersion(), OrderStatus.PENDING.getCode(), "审核");
        checkActualComplete(id); // 实盘未完整 40001

        Long userId = currentUserId();
        order.setStatus(OrderStatus.APPROVED.getCode());
        order.setAuditorId(userId);
        order.setAuditTime(LocalDateTime.now());
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(userId);
        saveOrder(order);
        log.info("审核盘点单成功 orderId={}, stocktakeNo={}, totalDifferQty={}",
                id, order.getStocktakeNo(), order.getTotalDifferQty());
        return toVO(order, false);
    }

    // ==================== STK-008 驳回 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeOrderVO reject(Long id, OrderActionDTO dto) {
        StocktakeOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        expectStatus(order, dto.getVersion(), OrderStatus.PENDING.getCode(), "驳回");
        if (!StringUtils.hasText(dto.getReason())) {
            throw new BizException(ResultCode.PARAM_INVALID, "驳回时 reason 必填");
        }

        order.setStatus(OrderStatus.REJECTED.getCode());
        // 主表无 reject_reason 列（12.7 待确认采纳「落 remark」）
        order.setRemark(appendRemark(order.getRemark(), "[驳回] " + dto.getReason().trim()));
        order.setUpdateBy(currentUserId());
        saveOrder(order);
        log.info("驳回盘点单成功 orderId={}, stocktakeNo={}, reason={}",
                id, order.getStocktakeNo(), dto.getReason());
        return toVO(order, false);
    }

    // ==================== STK-009 过账（按差异调整库存） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakePostResultVO post(Long id, OrderActionDTO dto) {
        StocktakeOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        expectStatus(order, dto.getVersion(), OrderStatus.APPROVED.getCode(), "过账");

        List<StocktakeOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<StocktakeOrderItem>()
                .eq(StocktakeOrderItem::getOrderId, id)
                .orderByAsc(StocktakeOrderItem::getLineNo));
        for (StocktakeOrderItem item : items) {
            if (item.getActualQty() == null) {
                throw new BizException(ResultCode.PARAM_INVALID,
                        "存在未录入实盘数的明细行（行号 " + item.getLineNo() + "），无法过账");
            }
        }

        Long userId = currentUserId();
        LocalDateTime now = LocalDateTime.now();
        List<StockFlowVO> flowVOs = new ArrayList<>();
        for (StocktakeOrderItem item : items) {
            BigDecimal differ = item.getDifferQty() == null ? ZERO : item.getDifferQty();
            if (differ.signum() == 0) {
                continue; // 差异为 0 的行不产生库存与流水
            }
            flowVOs.addAll(postOne(order, item, differ, userId, now));
        }

        order.setStatus(OrderStatus.POSTED.getCode());
        order.setDiffStatus(DiffHandleStatus.HANDLED.getCode()); // 过账后差异已处理
        order.setPosterId(userId);
        order.setPostTime(now);
        order.setFinishTime(now);
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(userId);
        saveOrder(order);
        log.info("盘点单过账成功 orderId={}, stocktakeNo={}, flowCount={}",
                id, order.getStocktakeNo(), flowVOs.size());

        return StocktakePostResultVO.builder()
                .id(order.getId())
                .stocktakeNo(order.getStocktakeNo())
                .status(order.getStatus())
                .statusDesc(OrderStatus.descOf(order.getStatus()))
                .diffStatus(DiffHandleStatus.toLabel(order.getDiffStatus()))
                .posterId(order.getPosterId())
                .postTime(order.getPostTime())
                .finishTime(order.getFinishTime())
                .version(order.getVersion())
                .flows(flowVOs)
                .build();
    }

    /**
     * 单行差异过账：行锁定位库存 → 快照一致性校验（≠book_qty 则 40907）→ 盘亏预检（40902）
     * → 乐观锁更新（0 行 40905）→ 写盘盈(IN, flow_type=5)/盘亏(OUT, flow_type=6) 流水。
     * 任一行失败整体回滚，单据保持 status=2 可复核后重试。
     */
    private List<StockFlowVO> postOne(StocktakeOrder order, StocktakeOrderItem item,
                                      BigDecimal differ, Long userId, LocalDateTime now) {
        Inventory inv = (item.getLocationId() == null) ? null
                : inventoryMapper.selectForUpdate(order.getWarehouseId(), item.getLocationId(),
                        item.getProductId(), item.getBatchId());
        if (inv == null) {
            // 盘点期间库存行被删除/清零 → 并发冲突兜底
            throw new BizException(ResultCode.STOCKTAKE_CONFLICT,
                    "盘点期间存在并发过账冲突（明细行 " + item.getLineNo() + " 对应库存行不存在）");
        }
        if (inv.getQty() == null || inv.getQty().compareTo(item.getBookQty()) != 0) {
            // 账面快照与当前账面不一致：盘点期间被其它单据过账
            throw new BizException(ResultCode.STOCKTAKE_CONFLICT,
                    "盘点期间存在并发过账冲突（明细行 " + item.getLineNo()
                            + " 账面数 " + item.getBookQty() + "，当前库存 " + inv.getQty() + "）");
        }
        BigDecimal before = inv.getQty();
        BigDecimal after = before.add(differ);
        if (differ.signum() < 0) {
            BigDecimal locked = inv.getLockQty() == null ? ZERO : inv.getLockQty();
            BigDecimal available = before.subtract(locked);
            if (available.compareTo(differ.abs()) < 0) {
                throw new BizException(ResultCode.INSUFFICIENT_STOCK,
                        "盘亏扣减库存不足（明细行 " + item.getLineNo() + "）");
            }
            if (after.signum() < 0) { // 兜底拦截
                throw new BizException(ResultCode.NEGATIVE_STOCK, "库存不能为负");
            }
        }
        inv.setQty(after);
        inv.setUpdateBy(userId);
        if (inventoryMapper.updateById(inv) == 0) { // 乐观锁冲突
            throw new BizException(ResultCode.DUPLICATE_SUBMIT, "库存并发更新冲突，请刷新后重试");
        }

        boolean gain = differ.signum() > 0;
        String flowNo = nextFlowNo();
        StockFlow flow = new StockFlow();
        flow.setFlowNo(flowNo);
        flow.setWarehouseId(order.getWarehouseId());
        flow.setProductId(item.getProductId());
        flow.setBatchId(item.getBatchId());
        flow.setLocationId(item.getLocationId());
        flow.setDirection(gain ? "IN" : "OUT");
        flow.setQty(differ.abs());
        flow.setBeforeQty(before);
        flow.setAfterQty(after);
        flow.setFlowType(gain ? 5 : 6); // 5盘盈 / 6盘亏
        flow.setRefType("STOCKTAKE");
        flow.setRefId(order.getId());
        flow.setRefNo(order.getStocktakeNo());
        flow.setOperatorId(userId);
        flow.setOperTime(now);
        flow.setCreateBy(userId);
        flowMapper.insert(flow); // uk_flow_no 并发兜底

        return List.of(StockFlowVO.builder()
                .flowNo(flowNo)
                .warehouseId(order.getWarehouseId())
                .productId(item.getProductId())
                .batchId(item.getBatchId())
                .locationId(item.getLocationId())
                .direction(flow.getDirection())
                .qty(flow.getQty())
                .beforeQty(before)
                .afterQty(after)
                .flowType(flow.getFlowType())
                .refType(flow.getRefType())
                .refId(order.getId())
                .refNo(order.getStocktakeNo())
                .operTime(now)
                .build());
    }

    // ==================== STK-010/011 取消与撤回 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeOrderVO cancel(Long id, OrderActionDTO dto) {
        StocktakeOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        Integer status = order.getStatus();
        checkVersion(order, dto.getVersion());
        boolean allowed = OrderStatus.DRAFT.getCode().equals(status)
                || OrderStatus.PENDING.getCode().equals(status)
                || OrderStatus.REJECTED.getCode().equals(status);
        if (!allowed) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "仅草稿/待审核/已驳回的盘点单允许取消");
        }
        order.setStatus(OrderStatus.CANCELLED.getCode());
        String addition = StringUtils.hasText(dto.getReason())
                ? "[取消] " + dto.getReason().trim() : emptyToNull(dto.getRemark());
        order.setRemark(appendRemark(order.getRemark(), addition));
        order.setUpdateBy(currentUserId());
        saveOrder(order);
        log.info("取消盘点单成功 orderId={}, stocktakeNo={}", id, order.getStocktakeNo());
        return toVO(order, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StocktakeOrderVO withdraw(Long id, OrderActionDTO dto) {
        StocktakeOrder order = requireOrder(id);
        dataPerm.checkWarehouse(order.getWarehouseId());
        Integer status = order.getStatus();
        checkVersion(order, dto.getVersion());
        if (!OrderStatus.PENDING.getCode().equals(status) && !OrderStatus.REJECTED.getCode().equals(status)) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "仅待审核/已驳回的盘点单允许撤回");
        }
        order.setStatus(OrderStatus.DRAFT.getCode());
        order.setRemark(appendRemark(order.getRemark(), emptyToNull(dto.getRemark())));
        order.setUpdateBy(currentUserId());
        saveOrder(order);
        log.info("撤回盘点单成功 orderId={}, stocktakeNo={}", id, order.getStocktakeNo());
        return toVO(order, false);
    }

    // ==================== 私有方法 ====================

    private StocktakeOrder requireOrder(Long id) {
        StocktakeOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "盘点单不存在或已删除");
        }
        return order;
    }

    private void checkVersion(StocktakeOrder order, Integer version) {
        if (version == null || !version.equals(order.getVersion())) {
            throw new BizException(ResultCode.DUPLICATE_SUBMIT, "单据已被其他操作修改，请刷新后重试");
        }
    }

    private void expectStatus(StocktakeOrder order, Integer version, Integer expect, String action) {
        checkVersion(order, version);
        if (!expect.equals(order.getStatus())) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID,
                    "当前状态不允许" + action + "（期望 " + OrderStatus.descOf(expect)
                            + "，实际 " + OrderStatus.descOf(order.getStatus()) + "）");
        }
    }

    private void saveOrder(StocktakeOrder order) {
        if (orderMapper.updateById(order) == 0) {
            throw new BizException(ResultCode.DUPLICATE_SUBMIT, "单据已被其他操作修改，请刷新后重试");
        }
    }

    /** 实盘数完整校验：存在 actual_qty 为 null 的行返回 40001 并提示未盘行号。 */
    private void checkActualComplete(Long orderId) {
        List<String> uncounted = itemMapper.selectList(new LambdaQueryWrapper<StocktakeOrderItem>()
                        .select(StocktakeOrderItem::getLineNo)
                        .eq(StocktakeOrderItem::getOrderId, orderId)
                        .isNull(StocktakeOrderItem::getActualQty)
                        .orderByAsc(StocktakeOrderItem::getLineNo)).stream()
                .map(StocktakeOrderItem::getLineNo)
                .limit(20)
                .map(String::valueOf)
                .toList();
        if (!uncounted.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "存在未录入实盘数的明细行（行号：" + String.join(", ", uncounted)
                            + (uncounted.size() >= 20 ? " …" : "") + "）");
        }
    }

    /** 分类子树（含自身）BFS；分类不存在 40401。 */
    private Set<Long> categorySubtree(Long rootId) {
        List<ProductCategory> all = categoryMapper.selectList(null);
        if (all.stream().noneMatch(c -> rootId.equals(c.getId()))) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "商品分类不存在");
        }
        Map<Long, List<Long>> children = new HashMap<>();
        for (ProductCategory c : all) {
            children.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId());
        }
        Set<Long> result = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long cid = queue.poll();
            if (result.add(cid)) {
                queue.addAll(children.getOrDefault(cid, List.of()));
            }
        }
        return result;
    }

    /** 生成盘点单号：PD + yyyyMMdd + 3位序号（uk_stocktake_no 兜底 40904）。 */
    private String nextStocktakeNo() {
        String prefix = "PD" + LocalDate.now().format(DAY);
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
            throw new BizException(ResultCode.CODE_EXISTS, "当日盘点单号已达上限（999），请次日再建单");
        }
        return prefix + String.format("%03d", seq);
    }

    /** 生成流水号：LS + yyyyMMdd + 5位序号（uk_flow_no 兜底 40904）。 */
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

    private String appendRemark(String remark, String addition) {
        if (!StringUtils.hasText(addition)) {
            return remark;
        }
        return StringUtils.hasText(remark) ? remark + "\n" + addition : addition;
    }

    // ---------- VO 组装 ----------

    private StocktakeOrderVO toVO(StocktakeOrder order, boolean withItems) {
        StocktakeOrderVO vo = new StocktakeOrderVO();
        vo.setId(order.getId());
        vo.setStocktakeNo(order.getStocktakeNo());
        vo.setWarehouseId(order.getWarehouseId());
        vo.setAreaId(order.getAreaId());
        vo.setScope(StocktakeScope.toLabel(order.getScope()));
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(OrderStatus.descOf(order.getStatus()));
        vo.setDiffStatus(DiffHandleStatus.toLabel(order.getDiffStatus()));
        vo.setPlanDate(order.getPlanDate());
        vo.setTotalDifferQty(order.getTotalDifferQty());
        vo.setFinishTime(order.getFinishTime());
        vo.setRemark(order.getRemark());
        vo.setRejectReason(extractRejectReason(order.getRemark()));
        vo.setVersion(order.getVersion());
        vo.setAuditorId(order.getAuditorId());
        vo.setAuditTime(order.getAuditTime());
        vo.setPosterId(order.getPosterId());
        vo.setPostTime(order.getPostTime());
        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());

        Warehouse wh = warehouseMapper.selectById(order.getWarehouseId());
        vo.setWarehouseName(wh == null ? null : wh.getWarehouseName());
        if (order.getAreaId() != null) {
            StorageArea area = areaMapper.selectById(order.getAreaId());
            vo.setAreaName(area == null ? null : area.getAreaName());
        }
        vo.setAuditorName(username(order.getAuditorId()));
        vo.setPosterName(username(order.getPosterId()));
        vo.setCreateBy(username(order.getCreateBy()));

        if (withItems) {
            vo.setItems(toItemVOs(order.getId()));
        }
        return vo;
    }

    /** 列表 VO：一次查询全部明细不必要（列表不返回 items）。 */
    private List<StocktakeOrderVO> toVOList(List<StocktakeOrder> orders, boolean withItems) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<StocktakeOrderVO> result = new ArrayList<>(orders.size());
        for (StocktakeOrder order : orders) {
            result.add(toVO(order, withItems));
        }
        return result;
    }

    /** 明细出参（反查货位/商品/批次与单位）。 */
    private List<StocktakeItemVO> toItemVOs(Long orderId) {
        List<StocktakeOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<StocktakeOrderItem>()
                .eq(StocktakeOrderItem::getOrderId, orderId)
                .orderByAsc(StocktakeOrderItem::getLineNo));
        if (items.isEmpty()) {
            return List.of();
        }
        Set<Long> locationIds = new HashSet<>();
        Set<Long> productIds = new HashSet<>();
        Set<Long> batchIds = new HashSet<>();
        for (StocktakeOrderItem item : items) {
            locationIds.add(item.getLocationId());
            productIds.add(item.getProductId());
            batchIds.add(item.getBatchId());
        }
        locationIds.remove(null);
        batchIds.remove(null);
        Map<Long, Location> locations = locationIds.isEmpty() ? Map.of()
                : locationMapper.selectBatchIds(locationIds).stream()
                .collect(Collectors.toMap(Location::getId, l -> l, (x, y) -> x));
        Map<Long, Product> products = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (x, y) -> x));
        Map<Long, InventoryBatch> batches = batchIds.isEmpty() ? Map.of()
                : batchMapper.selectBatchIds(batchIds).stream()
                .collect(Collectors.toMap(InventoryBatch::getId, b -> b, (x, y) -> x));

        List<StocktakeItemVO> result = new ArrayList<>(items.size());
        for (StocktakeOrderItem item : items) {
            StocktakeItemVO vo = new StocktakeItemVO();
            vo.setId(item.getId());
            vo.setLineNo(item.getLineNo());
            vo.setLocationId(item.getLocationId());
            Location l = item.getLocationId() == null ? null : locations.get(item.getLocationId());
            vo.setLocationCode(l == null ? null : l.getLocationCode());
            vo.setProductId(item.getProductId());
            Product p = products.get(item.getProductId());
            vo.setProductCode(p == null ? null : p.getProductCode());
            vo.setProductName(p == null ? null : p.getProductName());
            vo.setUnit(p == null ? null : p.getUnit());
            vo.setBatchId(item.getBatchId());
            InventoryBatch b = item.getBatchId() == null ? null : batches.get(item.getBatchId());
            vo.setBatchNo(b == null ? null : b.getBatchNo());
            vo.setBookQty(item.getBookQty());
            vo.setActualQty(item.getActualQty());
            vo.setDifferQty(item.getDifferQty());
            vo.setDiffReason(item.getDiffReason());
            vo.setStatus(DiffItemStatus.toLabel(item.getStatus()));
            result.add(vo);
        }
        return result;
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
