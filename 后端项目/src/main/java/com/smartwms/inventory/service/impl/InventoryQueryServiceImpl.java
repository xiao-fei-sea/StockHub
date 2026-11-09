package com.smartwms.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.base.mapper.LocationMapper;
import com.smartwms.base.mapper.ProductCategoryMapper;
import com.smartwms.base.mapper.ProductMapper;
import com.smartwms.base.mapper.StorageAreaMapper;
import com.smartwms.base.mapper.WarehouseMapper;
import com.smartwms.entity.Inventory;
import com.smartwms.entity.InventoryBatch;
import com.smartwms.entity.Location;
import com.smartwms.entity.Product;
import com.smartwms.entity.ProductCategory;
import com.smartwms.entity.StorageArea;
import com.smartwms.entity.StockFlow;
import com.smartwms.entity.SysUser;
import com.smartwms.entity.Warehouse;
import com.smartwms.enums.ResultCode;
import com.smartwms.enums.StockFlowType;
import com.smartwms.exception.BizException;
import com.smartwms.inventory.dto.FlowPageQueryDTO;
import com.smartwms.inventory.dto.InventoryPageQueryDTO;
import com.smartwms.inventory.mapper.InventoryBatchMapper;
import com.smartwms.inventory.mapper.InventoryMapper;
import com.smartwms.inventory.mapper.StockFlowMapper;
import com.smartwms.inventory.service.InventoryQueryService;
import com.smartwms.inventory.vo.FlowItemVO;
import com.smartwms.inventory.vo.InventoryItemVO;
import com.smartwms.inventory.vo.InventorySummaryVO;
import com.smartwms.security.WarehouseDataPerm;
import com.smartwms.system.mapper.SysUserMapper;
import com.smartwms.vo.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 库存查询服务实现（API-INV-001~003，只读接口）。
 * 数据权限口径：warehouseId 已传则越权校验（40302），未传时按授权仓库集合 SQL 过滤；
 * 文档 11.6 待确认项：summary 采纳「固定同时返回 items + byCategory」，分类按一级分类聚合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryQueryServiceImpl implements InventoryQueryService {

    private final InventoryMapper inventoryMapper;
    private final InventoryBatchMapper batchMapper;
    private final StockFlowMapper flowMapper;
    private final ProductMapper productMapper;
    private final LocationMapper locationMapper;
    private final WarehouseMapper warehouseMapper;
    private final StorageAreaMapper areaMapper;
    private final ProductCategoryMapper categoryMapper;
    private final SysUserMapper userMapper;
    private final WarehouseDataPerm dataPerm;

    // ==================== INV-001 库存查询 ====================

    @Override
    public PageResult<InventoryItemVO> page(InventoryPageQueryDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 20 : dto.getPageSize();
        if (pageSize > 100) {
            throw new BizException(ResultCode.PARAM_INVALID, "pageSize 最大 100");
        }
        dataPerm.checkWarehouse(dto.getWarehouseId()); // 越权 40302
        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(0L, pageNum, pageSize, List.of());
        }

        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<Inventory>()
                .eq(dto.getWarehouseId() != null, Inventory::getWarehouseId, dto.getWarehouseId())
                .eq(dto.getProductId() != null, Inventory::getProductId, dto.getProductId())
                .eq(dto.getLocationId() != null, Inventory::getLocationId, dto.getLocationId())
                .gt(Boolean.TRUE.equals(dto.getOnlyPositive()), Inventory::getQty, 0)
                .in(allowed != null, Inventory::getWarehouseId, allowed)
                .orderByAsc(Inventory::getWarehouseId)
                .orderByAsc(Inventory::getId);

        // 分类过滤：含该分类及全部子分类（product_category 无 ancestors 列，BFS 展开）
        if (dto.getCategoryId() != null) {
            Set<Long> categoryIds = categorySubtree(dto.getCategoryId());
            Set<Long> productIds = productMapper.selectList(new LambdaQueryWrapper<Product>()
                            .select(Product::getId)
                            .in(Product::getCategoryId, categoryIds)).stream()
                    .map(Product::getId).collect(Collectors.toSet());
            if (productIds.isEmpty()) {
                return PageResult.of(0L, pageNum, pageSize, List.of());
            }
            wrapper.in(Inventory::getProductId, productIds);
        }
        if (StringUtils.hasText(dto.getBatchNo())) {
            wrapper.apply("EXISTS (SELECT 1 FROM inventory_batch b"
                    + " WHERE b.id = batch_id AND b.deleted = 0"
                    + " AND b.batch_no LIKE {0})", "%" + dto.getBatchNo().trim() + "%");
        }
        if (StringUtils.hasText(dto.getKeyword())) {
            String like = "%" + dto.getKeyword().trim() + "%";
            wrapper.and(w -> w.apply("EXISTS (SELECT 1 FROM product p"
                            + " WHERE p.id = product_id AND p.deleted = 0"
                            + " AND (p.product_name LIKE {0} OR p.product_code LIKE {0}))", like)
                    .or().apply("EXISTS (SELECT 1 FROM location l"
                            + " WHERE l.id = location_id AND l.deleted = 0"
                            + " AND l.location_code LIKE {0})", like));
        }

        Page<Inventory> page = inventoryMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, toItemVOs(page.getRecords()));
    }

    // ==================== INV-002 库存流水查询 ====================

    @Override
    public PageResult<FlowItemVO> flows(FlowPageQueryDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 20 : dto.getPageSize();
        if (pageSize > 100) {
            throw new BizException(ResultCode.PARAM_INVALID, "pageSize 最大 100");
        }
        Integer flowType = StockFlowType.toCode(dto.getFlowType()); // 非法标签 40001
        String direction = normalizeDirection(dto.getDirection());   // 非法值 40001
        LocalDate start = parseDate(dto.getStartDate(), "startDate");
        LocalDate end = parseDate(dto.getEndDate(), "endDate");
        if (start != null && end != null && start.isAfter(end)) {
            throw new BizException(ResultCode.PARAM_INVALID, "startDate 不能晚于 endDate");
        }
        dataPerm.checkWarehouse(dto.getWarehouseId()); // 越权 40302
        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        if (allowed != null && allowed.isEmpty()) {
            return PageResult.of(0L, pageNum, pageSize, List.of());
        }

        LambdaQueryWrapper<StockFlow> wrapper = new LambdaQueryWrapper<StockFlow>()
                .eq(dto.getWarehouseId() != null, StockFlow::getWarehouseId, dto.getWarehouseId())
                .eq(dto.getProductId() != null, StockFlow::getProductId, dto.getProductId())
                .eq(flowType != null, StockFlow::getFlowType, flowType)
                .eq(StringUtils.hasText(direction), StockFlow::getDirection, direction)
                .ge(start != null, StockFlow::getOperTime,
                        start == null ? null : start.atStartOfDay())
                .lt(end != null, StockFlow::getOperTime,
                        end == null ? null : end.plusDays(1).atStartOfDay())
                .in(allowed != null, StockFlow::getWarehouseId, allowed)
                .orderByDesc(StockFlow::getOperTime)
                .orderByDesc(StockFlow::getId);
        if (StringUtils.hasText(dto.getRefNo())) {
            wrapper.like(StockFlow::getRefNo, dto.getRefNo().trim());
        }

        Page<StockFlow> page = flowMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, toFlowVOs(page.getRecords()));
    }

    // ==================== INV-003 库存汇总 ====================

    @Override
    public InventorySummaryVO summary(String groupBy, Long warehouseId) {
        String group = StringUtils.hasText(groupBy) ? groupBy.trim() : "warehouse";
        if (!Set.of("warehouse", "category", "product").contains(group)) {
            throw new BizException(ResultCode.PARAM_INVALID,
                    "groupBy 取值非法，仅允许：warehouse/category/product");
        }
        dataPerm.checkWarehouse(warehouseId); // 越权 40302
        Set<Long> allowed = dataPerm.allowedWarehouseIds();

        // 实时计算（首期不引入缓存）：仅取聚合所需三列
        List<Inventory> rows = (allowed != null && allowed.isEmpty()) ? List.of()
                : inventoryMapper.selectList(new LambdaQueryWrapper<Inventory>()
                .select(Inventory::getWarehouseId, Inventory::getProductId, Inventory::getQty)
                .eq(warehouseId != null, Inventory::getWarehouseId, warehouseId)
                .in(allowed != null, Inventory::getWarehouseId, allowed));

        Set<Long> productIds = rows.stream().map(Inventory::getProductId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Product> products = productIds.isEmpty() ? Map.of()
                : productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (x, y) -> x));
        List<ProductCategory> categories = categoryMapper.selectList(null);
        Map<Long, ProductCategory> categoryMap = categories.stream()
                .collect(Collectors.toMap(ProductCategory::getId, c -> c, (x, y) -> x));

        BigDecimal totalQty = rows.stream().map(Inventory::getQty)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        InventorySummaryVO vo = new InventorySummaryVO();
        vo.setGroupType(group);
        vo.setTotalQty(totalQty);
        vo.setTotalSku((long) productIds.size());

        // items 按 groupBy 维度聚合
        vo.setItems(switch (group) {
            case "category" -> aggregateByRootCategory(rows, products, categoryMap, null);
            case "product" -> aggregateByProduct(rows, products);
            default -> aggregateByWarehouse(rows);
        });
        // 固定同时返回一级分类汇总（11.6 待确认采纳「同时返回」+「一级分类」口径）
        vo.setByCategory(aggregateByRootCategory(rows, products, categoryMap,
                vo.getItems() == null || !"category".equals(group) ? null : vo.getItems()));
        return vo;
    }

    /** 按仓库聚合（按 warehouseId 升序）。 */
    private List<InventorySummaryVO.SummaryItem> aggregateByWarehouse(List<Inventory> rows) {
        Map<Long, Set<Long>> skuByWh = new TreeMap<>();
        Map<Long, BigDecimal> qtyByWh = new TreeMap<>();
        for (Inventory row : rows) {
            Long whId = row.getWarehouseId();
            if (whId == null) {
                continue;
            }
            skuByWh.computeIfAbsent(whId, k -> new HashSet<>()).add(row.getProductId());
            qtyByWh.merge(whId, row.getQty() == null ? BigDecimal.ZERO : row.getQty(), BigDecimal::add);
        }
        Set<Long> whIds = skuByWh.keySet();
        Map<Long, Warehouse> warehouses = whIds.isEmpty() ? Map.of()
                : warehouseMapper.selectBatchIds(whIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, w -> w, (x, y) -> x));

        List<InventorySummaryVO.SummaryItem> items = new ArrayList<>(whIds.size());
        for (Long whId : whIds) {
            InventorySummaryVO.SummaryItem item = new InventorySummaryVO.SummaryItem();
            Warehouse w = warehouses.get(whId);
            item.setWarehouseId(whId);
            item.setWarehouseName(w == null ? null : w.getWarehouseName());
            item.setSkuCount((long) skuByWh.get(whId).size());
            item.setQty(qtyByWh.getOrDefault(whId, BigDecimal.ZERO));
            items.add(item);
        }
        return items;
    }

    /**
     * 按商品一级分类聚合（parentId 逐级上溯至根；11.6 待确认采纳一级分类口径）。
     * reused 非空且 group=category 时直接复用已算好的 items，避免重复计算。
     */
    private List<InventorySummaryVO.SummaryItem> aggregateByRootCategory(
            List<Inventory> rows, Map<Long, Product> products,
            Map<Long, ProductCategory> categoryMap,
            List<InventorySummaryVO.SummaryItem> reused) {
        if (reused != null) {
            return reused;
        }
        // rootId -> sku 集合 / 数量（rootId 为 null 表示「未分类」）
        Map<Long, Set<Long>> skuByRoot = new LinkedHashMap<>();
        Map<Long, BigDecimal> qtyByRoot = new LinkedHashMap<>();
        Set<Long> rootIds = new HashSet<>();
        for (Inventory row : rows) {
            Product p = products.get(row.getProductId());
            Long rootId = p == null ? null : rootCategoryId(p.getCategoryId(), categoryMap);
            if (rootId != null) {
                rootIds.add(rootId);
            }
            skuByRoot.computeIfAbsent(rootId, k -> new HashSet<>()).add(row.getProductId());
            qtyByRoot.merge(rootId, row.getQty() == null ? BigDecimal.ZERO : row.getQty(), BigDecimal::add);
        }
        Map<Long, ProductCategory> roots = rootIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(rootIds).stream()
                .collect(Collectors.toMap(ProductCategory::getId, c -> c, (x, y) -> x));

        // 有分类的按 categoryId 升序，「未分类」（null）排最后
        List<Long> orderedKeys = skuByRoot.keySet().stream()
                .sorted(java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .toList();
        List<InventorySummaryVO.SummaryItem> items = new ArrayList<>(orderedKeys.size());
        for (Long rootId : orderedKeys) {
            InventorySummaryVO.SummaryItem item = new InventorySummaryVO.SummaryItem();
            ProductCategory c = rootId == null ? null : roots.get(rootId);
            item.setCategoryId(rootId);
            item.setCategoryName(c == null ? (rootId == null ? "未分类" : null) : c.getCategoryName());
            item.setSkuCount((long) skuByRoot.get(rootId).size());
            item.setQty(qtyByRoot.getOrDefault(rootId, BigDecimal.ZERO));
            items.add(item);
        }
        return items;
    }

    /** 按商品聚合（按 productId 升序，skuCount 恒为 1）。 */
    private List<InventorySummaryVO.SummaryItem> aggregateByProduct(
            List<Inventory> rows, Map<Long, Product> products) {
        Map<Long, BigDecimal> qtyByProduct = new TreeMap<>();
        for (Inventory row : rows) {
            if (row.getProductId() == null) {
                continue;
            }
            qtyByProduct.merge(row.getProductId(),
                    row.getQty() == null ? BigDecimal.ZERO : row.getQty(), BigDecimal::add);
        }
        List<InventorySummaryVO.SummaryItem> items = new ArrayList<>(qtyByProduct.size());
        for (Map.Entry<Long, BigDecimal> entry : qtyByProduct.entrySet()) {
            InventorySummaryVO.SummaryItem item = new InventorySummaryVO.SummaryItem();
            Product p = products.get(entry.getKey());
            item.setProductId(entry.getKey());
            item.setProductCode(p == null ? null : p.getProductCode());
            item.setProductName(p == null ? null : p.getProductName());
            item.setSkuCount(1L);
            item.setQty(entry.getValue());
            items.add(item);
        }
        return items;
    }

    /** 分类逐级上溯到一级分类 id；分类不存在或无分类返回 null。 */
    private Long rootCategoryId(Long categoryId, Map<Long, ProductCategory> categoryMap) {
        if (categoryId == null) {
            return null;
        }
        Long current = categoryId;
        Set<Long> visited = new HashSet<>(); // 防脏数据成环
        while (current != null && visited.add(current)) {
            ProductCategory c = categoryMap.get(current);
            if (c == null) {
                return current; // 上级已删：以当前可见节点为根
            }
            if (c.getParentId() == null || c.getParentId() == 0) {
                return current;
            }
            current = c.getParentId();
        }
        return current;
    }

    /** 分类子树（含自身）BFS 展开；分类不存在 40401。 */
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
            Long id = queue.poll();
            if (result.add(id)) {
                queue.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return result;
    }

    // ==================== 私有方法 ====================

    /** 库存行转出参（批量反查商品/货位/库区/仓库/批次/分类）。 */
    private List<InventoryItemVO> toItemVOs(List<Inventory> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> productIds = new HashSet<>();
        Set<Long> locationIds = new HashSet<>();
        Set<Long> batchIds = new HashSet<>();
        Set<Long> warehouseIds = new HashSet<>();
        for (Inventory row : rows) {
            productIds.add(row.getProductId());
            locationIds.add(row.getLocationId());
            batchIds.add(row.getBatchId());
            warehouseIds.add(row.getWarehouseId());
        }
        Map<Long, Product> products = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (x, y) -> x));
        Map<Long, Location> locations = locationMapper.selectBatchIds(locationIds).stream()
                .collect(Collectors.toMap(Location::getId, l -> l, (x, y) -> x));
        Map<Long, InventoryBatch> batches = batchMapperSelect(batchIds);
        Map<Long, Warehouse> warehouses = warehouseMapper.selectBatchIds(warehouseIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, w -> w, (x, y) -> x));
        Set<Long> areaIds = locations.values().stream().map(Location::getAreaId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, StorageArea> areas = areaIds.isEmpty() ? Map.of()
                : areaMapper.selectBatchIds(areaIds).stream()
                .collect(Collectors.toMap(StorageArea::getId, a -> a, (x, y) -> x));
        Set<Long> categoryIds = products.values().stream().map(Product::getCategoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProductCategory> categories = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(ProductCategory::getId, c -> c, (x, y) -> x));

        List<InventoryItemVO> result = new ArrayList<>(rows.size());
        for (Inventory row : rows) {
            InventoryItemVO vo = new InventoryItemVO();
            vo.setId(row.getId());
            vo.setWarehouseId(row.getWarehouseId());
            Warehouse w = warehouses.get(row.getWarehouseId());
            vo.setWarehouseName(w == null ? null : w.getWarehouseName());
            Location l = locations.get(row.getLocationId());
            vo.setLocationId(row.getLocationId());
            vo.setLocationCode(l == null ? null : l.getLocationCode());
            if (l != null && l.getAreaId() != null) {
                vo.setAreaId(l.getAreaId());
                StorageArea area = areas.get(l.getAreaId());
                vo.setAreaName(area == null ? null : area.getAreaName());
            }
            Product p = products.get(row.getProductId());
            vo.setProductId(row.getProductId());
            vo.setProductCode(p == null ? null : p.getProductCode());
            vo.setProductName(p == null ? null : p.getProductName());
            vo.setUnit(p == null ? null : p.getUnit());
            if (p != null && p.getCategoryId() != null) {
                vo.setCategoryId(p.getCategoryId());
                ProductCategory c = categories.get(p.getCategoryId());
                vo.setCategoryName(c == null ? null : c.getCategoryName());
            }
            InventoryBatch b = batches.get(row.getBatchId());
            vo.setBatchId(row.getBatchId());
            if (b != null) {
                vo.setBatchNo(b.getBatchNo());
                vo.setProductionDate(b.getProductionDate());
                vo.setExpiryDate(b.getExpiryDate());
                vo.setBatchStatus(b.getBatchStatus());
            }
            vo.setQty(row.getQty());
            BigDecimal lockQty = row.getLockQty() == null ? BigDecimal.ZERO : row.getLockQty();
            vo.setLockQty(lockQty);
            vo.setAvailableQty(row.getQty().subtract(lockQty));
            vo.setVersion(row.getVersion());
            vo.setUpdateTime(row.getUpdateTime());
            result.add(vo);
        }
        return result;
    }

    /** 流水行转出参（批量反查商品/批次/货位/仓库/操作人，枚举转中文标签）。 */
    private List<FlowItemVO> toFlowVOs(List<StockFlow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> productIds = new HashSet<>();
        Set<Long> batchIds = new HashSet<>();
        Set<Long> locationIds = new HashSet<>();
        Set<Long> warehouseIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (StockFlow row : rows) {
            productIds.add(row.getProductId());
            batchIds.add(row.getBatchId());
            locationIds.add(row.getLocationId());
            warehouseIds.add(row.getWarehouseId());
            userIds.add(row.getOperatorId());
        }
        batchIds.remove(null);
        locationIds.remove(null);
        userIds.remove(null);
        Map<Long, Product> products = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (x, y) -> x));
        Map<Long, InventoryBatch> batches = batchMapperSelect(batchIds);
        Map<Long, Location> locations = locationIds.isEmpty() ? Map.of()
                : locationMapper.selectBatchIds(locationIds).stream()
                .collect(Collectors.toMap(Location::getId, l -> l, (x, y) -> x));
        Map<Long, Warehouse> warehouses = warehouseMapper.selectBatchIds(warehouseIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, w -> w, (x, y) -> x));
        Map<Long, SysUser> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (x, y) -> x));

        List<FlowItemVO> result = new ArrayList<>(rows.size());
        for (StockFlow row : rows) {
            FlowItemVO vo = new FlowItemVO();
            vo.setId(row.getId());
            vo.setFlowNo(row.getFlowNo());
            vo.setWarehouseId(row.getWarehouseId());
            Warehouse w = warehouses.get(row.getWarehouseId());
            vo.setWarehouseName(w == null ? null : w.getWarehouseName());
            Product p = products.get(row.getProductId());
            vo.setProductId(row.getProductId());
            vo.setProductCode(p == null ? null : p.getProductCode());
            vo.setProductName(p == null ? null : p.getProductName());
            vo.setBatchId(row.getBatchId());
            InventoryBatch b = row.getBatchId() == null ? null : batches.get(row.getBatchId());
            vo.setBatchNo(b == null ? null : b.getBatchNo());
            vo.setLocationId(row.getLocationId());
            Location l = row.getLocationId() == null ? null : locations.get(row.getLocationId());
            vo.setLocationCode(l == null ? null : l.getLocationCode());
            vo.setDirection(row.getDirection());
            vo.setQty(row.getQty());
            vo.setBeforeQty(row.getBeforeQty());
            vo.setAfterQty(row.getAfterQty());
            vo.setFlowType(StockFlowType.toLabel(row.getFlowType()));
            vo.setRefType(refTypeLabel(row.getRefType()));
            vo.setRefId(row.getRefId());
            vo.setRefNo(row.getRefNo());
            vo.setOperatorId(row.getOperatorId());
            SysUser u = row.getOperatorId() == null ? null : users.get(row.getOperatorId());
            vo.setOperatorName(u == null ? null : u.getUsername());
            vo.setOperTime(row.getOperTime());
            vo.setCreateTime(row.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    private Map<Long, InventoryBatch> batchMapperSelect(Set<Long> batchIds) {
        Set<Long> ids = batchIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return batchMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(InventoryBatch::getId, b -> b, (x, y) -> x));
    }

    /** ref_type 存储值转中文单据类型标签（未识别原样返回）。 */
    private String refTypeLabel(String refType) {
        if (refType == null) {
            return null;
        }
        return switch (refType) {
            case "INBOUND" -> "入库单";
            case "OUTBOUND" -> "出库单";
            case "TRANSFER" -> "调拨单";
            case "STOCKTAKE" -> "盘点单";
            case "ADJUST" -> "调整单";
            case "INIT" -> "期初";
            default -> refType;
        };
    }

    /** direction 白名单校验（IN/OUT，空白返回 null，非法 40001）。 */
    private String normalizeDirection(String direction) {
        if (!StringUtils.hasText(direction)) {
            return null;
        }
        String trimmed = direction.trim().toUpperCase();
        if (!trimmed.equals("IN") && !trimmed.equals("OUT")) {
            throw new BizException(ResultCode.PARAM_INVALID, "direction 仅允许 IN / OUT");
        }
        return trimmed;
    }

    /** 解析 yyyy-MM-dd 日期串（非法 40001）；空白返回 null。 */
    private LocalDate parseDate(String date, String field) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ResultCode.PARAM_INVALID, field + " 格式须为 yyyy-MM-dd");
        }
    }
}
