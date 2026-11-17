package com.smartwms.module.stats.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.smartwms.module.alert.mapper.StockAlertRecordMapper;
import com.smartwms.module.storage.mapper.LocationMapper;
import com.smartwms.module.product.mapper.ProductCategoryMapper;
import com.smartwms.module.product.mapper.ProductMapper;
import com.smartwms.module.storage.mapper.StorageAreaMapper;
import com.smartwms.module.warehouse.mapper.WarehouseMapper;
import com.smartwms.entity.Inventory;
import com.smartwms.entity.InventoryBatch;
import com.smartwms.entity.InboundOrder;
import com.smartwms.entity.Location;
import com.smartwms.entity.OutboundOrder;
import com.smartwms.entity.Product;
import com.smartwms.entity.ProductCategory;
import com.smartwms.entity.StockAlertRecord;
import com.smartwms.entity.StockFlow;
import com.smartwms.entity.StocktakeOrder;
import com.smartwms.entity.StorageArea;
import com.smartwms.entity.TransferOrder;
import com.smartwms.entity.Warehouse;
import com.smartwms.common.enums.ResultCode;
import com.smartwms.common.BizException;
import com.smartwms.module.inbound.mapper.InboundOrderMapper;
import com.smartwms.module.inventory.mapper.InventoryBatchMapper;
import com.smartwms.module.inventory.mapper.InventoryMapper;
import com.smartwms.module.inventory.mapper.StockFlowMapper;
import com.smartwms.module.outbound.mapper.OutboundOrderMapper;
import com.smartwms.framework.security.WarehouseDataPerm;
import com.smartwms.module.stats.dto.BatchFlowStatDTO;
import com.smartwms.module.stats.dto.CategoryDistributionQueryDTO;
import com.smartwms.module.stats.dto.DashboardQueryDTO;
import com.smartwms.module.stats.dto.OccupancyQueryDTO;
import com.smartwms.module.stats.dto.StockAgingQueryDTO;
import com.smartwms.module.stats.dto.TopProductsQueryDTO;
import com.smartwms.module.stats.dto.TrendQueryDTO;
import com.smartwms.module.stats.mapper.StatsMapper;
import com.smartwms.module.stats.service.StatsService;
import com.smartwms.module.stats.vo.CategoryDistributionVO;
import com.smartwms.module.stats.vo.DashboardVO;
import com.smartwms.module.stats.vo.StockAgingVO;
import com.smartwms.module.stats.vo.TopProductsVO;
import com.smartwms.module.stats.vo.TrendVO;
import com.smartwms.module.stats.vo.WarehouseOccupancyVO;
import com.smartwms.module.stocktake.mapper.StocktakeOrderMapper;
import com.smartwms.module.transfer.mapper.TransferOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据统计服务实现（API-STS-001~006，详见《15. 数据统计接口文档》）。
 * 口径总则（15.1）：库存取 inventory 当前值；出入库量按 stock_flow.oper_time 的全部 IN/OUT 流水
 * （含调拨/盘盈亏，15.6 待确认采纳文档默认）；单据数按 status=3 的 post_time；
 * 分类分布 level 参数支持任意层级（15.6 待确认采纳文档默认）、未分类商品归「未分类」分组置尾；
 * 滞销阈值复用账龄最大分段（15.6 待确认采纳文档默认）。
 * 首期无缓存，全部实时聚合；统一数据权限：warehouseId 指定优先（越权 40302），
 * 空则按授权集合过滤（SQL IN 天然排除 warehouse_id IS NULL 行）。
 */
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final WarehouseDataPerm dataPerm;
    private final InventoryMapper inventoryMapper;
    private final StockFlowMapper stockFlowMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final OutboundOrderMapper outboundOrderMapper;
    private final StocktakeOrderMapper stocktakeOrderMapper;
    private final TransferOrderMapper transferOrderMapper;
    private final LocationMapper locationMapper;
    private final StorageAreaMapper storageAreaMapper;
    private final WarehouseMapper warehouseMapper;
    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final StockAlertRecordMapper alertRecordMapper;
    private final InventoryBatchMapper batchMapper;
    private final StatsMapper statsMapper;

    // ==================== API-STS-001 首页看板汇总 ====================

    @Override
    public DashboardVO dashboard(DashboardQueryDTO dto) {
        Long wh = dto.getWarehouseId();
        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        dataPerm.checkWarehouse(wh);

        DashboardVO vo = new DashboardVO();
        vo.setStatDate(LocalDate.now().toString());
        vo.setWarehouseScope(resolveScopeName(wh));

        // 1) 当前库存概况（qty > 0 计入 SKU/批次/货位数）
        List<Inventory> inventories = inventoryMapper.selectList(
                scope(new LambdaQueryWrapper<Inventory>(), wh, allowed, Inventory::getWarehouseId));
        BigDecimal totalQty = BigDecimal.ZERO;
        Set<Long> skuSet = new HashSet<>();
        Set<Long> batchSet = new HashSet<>();
        Set<Long> locSet = new HashSet<>();
        for (Inventory inv : inventories) {
            if (inv.getQty() == null || inv.getQty().signum() <= 0) {
                continue;
            }
            totalQty = totalQty.add(inv.getQty());
            skuSet.add(inv.getProductId());
            if (inv.getBatchId() != null) {
                batchSet.add(inv.getBatchId());
            }
            if (inv.getLocationId() != null) {
                locSet.add(inv.getLocationId());
            }
        }
        DashboardVO.InventoryStats invStats = vo.getInventory();
        invStats.setTotalQty(totalQty);
        invStats.setSkuCount(skuSet.size());
        invStats.setBatchCount(batchSet.size());
        invStats.setLocationOccupiedCount(locSet.size());

        // 2) 今日出入库：量按流水 oper_time，单数按 status=3 的 post_time
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        BigDecimal inQty = BigDecimal.ZERO;
        BigDecimal outQty = BigDecimal.ZERO;
        List<StockFlow> todayFlows = stockFlowMapper.selectList(
                scope(new LambdaQueryWrapper<StockFlow>()
                                .ge(StockFlow::getOperTime, dayStart)
                                .lt(StockFlow::getOperTime, dayEnd),
                        wh, allowed, StockFlow::getWarehouseId));
        for (StockFlow f : todayFlows) {
            if (f.getQty() == null) {
                continue;
            }
            if ("IN".equals(f.getDirection())) {
                inQty = inQty.add(f.getQty());
            } else if ("OUT".equals(f.getDirection())) {
                outQty = outQty.add(f.getQty());
            }
        }
        DashboardVO.TodayStats todayStats = vo.getToday();
        todayStats.setInboundQty(inQty);
        todayStats.setOutboundQty(outQty);
        todayStats.setInboundOrderCount(postedInbound(dayStart, dayEnd, wh, allowed).size());
        todayStats.setOutboundOrderCount(postedOutbound(dayStart, dayEnd, wh, allowed).size());

        // 3) 待审核单据数（各单据表 status=1；调拨按源/目标任一仓口径）
        DashboardVO.PendingStats pending = vo.getPending();
        pending.setInboundPendingCount(inboundOrderMapper.selectCount(
                scope(new LambdaQueryWrapper<InboundOrder>().eq(InboundOrder::getStatus, 1),
                        wh, allowed, InboundOrder::getWarehouseId)).intValue());
        pending.setOutboundPendingCount(outboundOrderMapper.selectCount(
                scope(new LambdaQueryWrapper<OutboundOrder>().eq(OutboundOrder::getStatus, 1),
                        wh, allowed, OutboundOrder::getWarehouseId)).intValue());
        pending.setStocktakePendingCount(stocktakeOrderMapper.selectCount(
                scope(new LambdaQueryWrapper<StocktakeOrder>().eq(StocktakeOrder::getStatus, 1),
                        wh, allowed, StocktakeOrder::getWarehouseId)).intValue());
        pending.setTransferPendingCount(transferOrderMapper.selectCount(
                transferScope(new LambdaQueryWrapper<TransferOrder>().eq(TransferOrder::getStatus, 1), wh, allowed)).intValue());

        // 4) 预警概况（未处理 / 未处理且严重）
        vo.getAlert().setUnhandledCount(alertRecordMapper.selectCount(
                scope(new LambdaQueryWrapper<StockAlertRecord>().eq(StockAlertRecord::getStatus, 0),
                        wh, allowed, StockAlertRecord::getWarehouseId)).intValue());
        vo.getAlert().setSeriousCount(alertRecordMapper.selectCount(
                scope(new LambdaQueryWrapper<StockAlertRecord>()
                                .eq(StockAlertRecord::getStatus, 0)
                                .eq(StockAlertRecord::getLevel, 3),
                        wh, allowed, StockAlertRecord::getWarehouseId)).intValue());

        // 5) 仓库与货位概况（分母 = status=1 启用货位）
        DashboardVO.WarehouseStats ws = vo.getWarehouse();
        ws.setWarehouseCount(warehouseMapper.selectCount(
                scope(new LambdaQueryWrapper<Warehouse>(), wh, allowed, Warehouse::getId)).intValue());
        int total = 0;
        int used = 0;
        for (Location loc : locationMapper.selectList(
                scope(new LambdaQueryWrapper<Location>(), wh, allowed, Location::getWarehouseId))) {
            if (loc.getStatus() == null || loc.getStatus() != 1) {
                continue;
            }
            total++;
            if (loc.getCurrentStatus() != null && loc.getCurrentStatus() == 2) {
                used++;
            }
        }
        ws.setLocationTotal(total);
        ws.setLocationUsed(used);
        ws.setOccupancyRate(ratio(BigDecimal.valueOf(used), BigDecimal.valueOf(total), 2));
        return vo;
    }

    // ==================== API-STS-002 出入库趋势 ====================

    @Override
    public TrendVO trend(TrendQueryDTO dto) {
        int days = dto.getDays();
        Long wh = dto.getWarehouseId();
        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        dataPerm.checkWarehouse(wh);

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);
        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEnd = end.plusDays(1).atStartOfDay();

        // 当日出入库量（全部 IN/OUT 流水）
        Map<LocalDate, BigDecimal[]> qtyByDate = new HashMap<>();
        for (StockFlow f : stockFlowMapper.selectList(
                scope(new LambdaQueryWrapper<StockFlow>()
                                .ge(StockFlow::getOperTime, rangeStart)
                                .lt(StockFlow::getOperTime, rangeEnd),
                        wh, allowed, StockFlow::getWarehouseId))) {
            if (f.getOperTime() == null || f.getQty() == null) {
                continue;
            }
            BigDecimal[] arr = qtyByDate.computeIfAbsent(f.getOperTime().toLocalDate(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if ("IN".equals(f.getDirection())) {
                arr[0] = arr[0].add(f.getQty());
            } else if ("OUT".equals(f.getDirection())) {
                arr[1] = arr[1].add(f.getQty());
            }
        }

        // 当日已过账单据数
        Map<LocalDate, int[]> countByDate = new HashMap<>();
        for (InboundOrder o : postedInbound(rangeStart, rangeEnd, wh, allowed)) {
            if (o.getPostTime() != null) {
                countByDate.computeIfAbsent(o.getPostTime().toLocalDate(), k -> new int[2])[0]++;
            }
        }
        for (OutboundOrder o : postedOutbound(rangeStart, rangeEnd, wh, allowed)) {
            if (o.getPostTime() != null) {
                countByDate.computeIfAbsent(o.getPostTime().toLocalDate(), k -> new int[2])[1]++;
            }
        }

        // 序列补 0 + 区间合计
        List<TrendVO.TrendPoint> series = new ArrayList<>(days);
        BigDecimal inTotal = BigDecimal.ZERO;
        BigDecimal outTotal = BigDecimal.ZERO;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            BigDecimal[] q = qtyByDate.getOrDefault(d, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            int[] c = countByDate.getOrDefault(d, new int[2]);
            series.add(TrendVO.TrendPoint.builder()
                    .date(d.toString())
                    .inboundQty(q[0])
                    .outboundQty(q[1])
                    .inboundCount(c[0])
                    .outboundCount(c[1])
                    .build());
            inTotal = inTotal.add(q[0]);
            outTotal = outTotal.add(q[1]);
        }
        return TrendVO.builder()
                .days(days)
                .startDate(start.toString())
                .endDate(end.toString())
                .series(series)
                .summary(TrendVO.Summary.builder()
                        .inboundQtyTotal(inTotal)
                        .outboundQtyTotal(outTotal)
                        .build())
                .build();
    }

    // ==================== API-STS-003 库存分类分布 ====================

    @Override
    public CategoryDistributionVO categoryDistribution(CategoryDistributionQueryDTO dto) {
        int level = dto.getLevel();
        Long wh = dto.getWarehouseId();
        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        dataPerm.checkWarehouse(wh);

        List<ProductCategory> categories = categoryMapper.selectList(null);
        int maxLevel = categories.stream()
                .mapToInt(c -> c.getLevel() == null ? 0 : c.getLevel())
                .max().orElse(0);
        if (maxLevel > 0 && level > maxLevel) {
            throw new BizException(ResultCode.PARAM_INVALID, "level 超出分类最大层级 " + maxLevel);
        }
        Map<Long, ProductCategory> catById = categories.stream()
                .collect(Collectors.toMap(ProductCategory::getId, c -> c, (a, b) -> a));

        // 库存按商品汇总（qty > 0）
        Map<Long, BigDecimal> qtyByProduct = new HashMap<>();
        Set<Long> stockedProducts = new HashSet<>();
        for (Inventory inv : inventoryMapper.selectList(
                scope(new LambdaQueryWrapper<Inventory>(), wh, allowed, Inventory::getWarehouseId))) {
            if (inv.getQty() == null || inv.getQty().signum() <= 0 || inv.getProductId() == null) {
                continue;
            }
            qtyByProduct.merge(inv.getProductId(), inv.getQty(), BigDecimal::add);
            stockedProducts.add(inv.getProductId());
        }
        Map<Long, Product> productById = productMapper.selectList(null).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        // 按目标层级分组（categoryId 为空/分类已失效 → 未分类，key 为 null）
        Map<Long, BigDecimal> qtyByGroup = new HashMap<>();
        Map<Long, Set<Long>> skuByGroup = new HashMap<>();
        for (Long pid : stockedProducts) {
            Product p = productById.get(pid);
            Long groupId = null;
            if (p != null && p.getCategoryId() != null) {
                ProductCategory ancestor = resolveAncestor(p.getCategoryId(), level, catById);
                if (ancestor != null) {
                    groupId = ancestor.getId();
                }
            }
            qtyByGroup.merge(groupId, qtyByProduct.get(pid), BigDecimal::add);
            skuByGroup.computeIfAbsent(groupId, k -> new HashSet<>()).add(pid);
        }
        BigDecimal totalQty = qtyByGroup.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategoryDistributionVO.CategoryItem> items = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : qtyByGroup.entrySet()) {
            Long groupId = e.getKey();
            ProductCategory cat = groupId == null ? null : catById.get(groupId);
            items.add(CategoryDistributionVO.CategoryItem.builder()
                    .categoryId(groupId)
                    .categoryCode(cat == null ? null : cat.getCategoryCode())
                    .categoryName(cat == null ? "未分类" : cat.getCategoryName())
                    .skuCount(skuByGroup.get(groupId).size())
                    .qty(e.getValue())
                    .qtyRatio(ratio(e.getValue(), totalQty, 3))
                    .build());
        }
        // qty 降序，未分类置尾
        items.sort((a, b) -> {
            boolean ua = a.getCategoryId() == null;
            boolean ub = b.getCategoryId() == null;
            if (ua != ub) {
                return ua ? 1 : -1;
            }
            return b.getQty().compareTo(a.getQty());
        });
        return CategoryDistributionVO.builder()
                .groupLevel(level)
                .totalQty(totalQty)
                .totalSku(stockedProducts.size())
                .items(items)
                .build();
    }

    // ==================== API-STS-004 仓库货位利用率 ====================

    @Override
    public WarehouseOccupancyVO occupancy(OccupancyQueryDTO dto) {
        String groupBy = dto.getGroupBy();
        Long wh = dto.getWarehouseId();
        if ("area".equals(groupBy) && wh == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "groupBy=area 时须指定 warehouseId");
        }
        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        dataPerm.checkWarehouse(wh);

        List<Location> locations = locationMapper.selectList(
                scope(new LambdaQueryWrapper<Location>(), wh, allowed, Location::getWarehouseId));
        List<Inventory> inventories = inventoryMapper.selectList(
                scope(new LambdaQueryWrapper<Inventory>(), wh, allowed, Inventory::getWarehouseId));

        // 分组骨架：warehouse 维度取范围内全部仓库；area 维度取指定仓库全部库区
        Map<Long, OccAcc> accs = new LinkedHashMap<>();
        Map<Long, String> whNames = new HashMap<>();
        Map<Long, String> areaNames = new HashMap<>();
        if ("area".equals(groupBy)) {
            for (StorageArea area : storageAreaMapper.selectList(
                    new LambdaQueryWrapper<StorageArea>().eq(StorageArea::getWarehouseId, wh))) {
                OccAcc acc = new OccAcc();
                acc.warehouseId = wh;
                acc.areaId = area.getId();
                accs.put(area.getId(), acc);
                areaNames.put(area.getId(), area.getAreaName());
            }
        } else {
            for (Warehouse w : warehouseMapper.selectList(
                    scope(new LambdaQueryWrapper<Warehouse>(), wh, allowed, Warehouse::getId))) {
                accs.put(w.getId(), new OccAcc());
                whNames.put(w.getId(), w.getWarehouseName());
            }
        }

        // 货位计数（仅 status=1 启用货位计入分母与四态）
        Map<Long, Location> locById = new HashMap<>();
        for (Location loc : locations) {
            locById.put(loc.getId(), loc);
            if (loc.getStatus() == null || loc.getStatus() != 1) {
                continue;
            }
            Long key = "area".equals(groupBy) ? loc.getAreaId() : loc.getWarehouseId();
            if (key == null) {
                continue;
            }
            OccAcc acc = accs.computeIfAbsent(key, k -> {
                OccAcc a = new OccAcc();
                a.warehouseId = loc.getWarehouseId();
                a.areaId = loc.getAreaId();
                return a;
            });
            acc.total++;
            Integer cs = loc.getCurrentStatus();
            if (cs == null || cs == 1) {
                acc.idle++;
            } else if (cs == 2) {
                acc.used++;
            } else if (cs == 3) {
                acc.locked++;
            } else if (cs == 4) {
                acc.disabled++;
            }
        }

        // 库存按分组汇总（area 维度经货位归属库区）
        for (Inventory inv : inventories) {
            if (inv.getQty() == null || inv.getQty().signum() <= 0 || inv.getProductId() == null) {
                continue;
            }
            Long key;
            if ("area".equals(groupBy)) {
                Location loc = locById.get(inv.getLocationId());
                key = loc == null ? null : loc.getAreaId();
            } else {
                key = inv.getWarehouseId();
            }
            if (key == null) {
                continue;
            }
            OccAcc acc = accs.get(key);
            if (acc == null) {
                continue;
            }
            acc.skus.add(inv.getProductId());
            acc.qty = acc.qty.add(inv.getQty());
        }

        List<WarehouseOccupancyVO.OccItem> items = new ArrayList<>();
        int sumTotal = 0;
        int sumUsed = 0;
        int sumLocked = 0;
        int sumDisabled = 0;
        int sumIdle = 0;
        for (OccAcc acc : accs.values()) {
            sumTotal += acc.total;
            sumUsed += acc.used;
            sumLocked += acc.locked;
            sumDisabled += acc.disabled;
            sumIdle += acc.idle;
            items.add(WarehouseOccupancyVO.OccItem.builder()
                    .warehouseId(acc.warehouseId)
                    .warehouseName(whNames.get(acc.warehouseId))
                    .areaId(acc.areaId)
                    .areaName(acc.areaId == null ? null : areaNames.get(acc.areaId))
                    .locationTotal(acc.total)
                    .locationUsed(acc.used)
                    .locationIdle(acc.idle)
                    .locationLocked(acc.locked)
                    .locationDisabled(acc.disabled)
                    .occupancyRate(ratio(BigDecimal.valueOf(acc.used), BigDecimal.valueOf(acc.total), 2))
                    .skuCount(acc.skus.size())
                    .qty(acc.qty)
                    .build());
        }
        items.sort((a, b) -> {
            Long ka = a.getAreaId() != null ? a.getAreaId() : a.getWarehouseId();
            Long kb = b.getAreaId() != null ? b.getAreaId() : b.getWarehouseId();
            if (ka == null || kb == null) {
                return Boolean.compare(ka == null, kb == null);
            }
            return ka.compareTo(kb);
        });
        return WarehouseOccupancyVO.builder()
                .groupType(groupBy)
                .summary(WarehouseOccupancyVO.Summary.builder()
                        .locationTotal(sumTotal)
                        .locationUsed(sumUsed)
                        .locationLocked(sumLocked)
                        .locationDisabled(sumDisabled)
                        .locationIdle(sumIdle)
                        .occupancyRate(ratio(BigDecimal.valueOf(sumUsed), BigDecimal.valueOf(sumTotal), 2))
                        .build())
                .items(items)
                .build();
    }

    // ==================== API-STS-005 出入库 Top 商品 ====================

    @Override
    public TopProductsVO topProducts(TopProductsQueryDTO dto) {
        String type = dto.getType();
        int limit = dto.getLimit();
        Long wh = dto.getWarehouseId();
        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        dataPerm.checkWarehouse(wh);

        // 日期区间：显式 startDate/endDate 优先（须成对），否则按 days
        LocalDate start;
        LocalDate end;
        if (dto.getStartDate() != null || dto.getEndDate() != null) {
            if (dto.getStartDate() == null || dto.getEndDate() == null) {
                throw new BizException(ResultCode.PARAM_INVALID, "startDate 与 endDate 须成对传入");
            }
            try {
                start = LocalDate.parse(dto.getStartDate());
                end = LocalDate.parse(dto.getEndDate());
            } catch (DateTimeParseException e) {
                throw new BizException(ResultCode.PARAM_INVALID, "日期格式须为 yyyy-MM-dd");
            }
            if (start.isAfter(end)) {
                throw new BizException(ResultCode.PARAM_INVALID, "startDate 不能晚于 endDate");
            }
        } else {
            end = LocalDate.now();
            start = end.minusDays(dto.getDays() - 1L);
        }
        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEnd = end.plusDays(1).atStartOfDay();

        // 区间流水聚合（全部 IN/OUT 流水；orderCount 仅 INBOUND/OUTBOUND 单据，流水在过账时产生）
        Map<Long, BigDecimal> inQty = new HashMap<>();
        Map<Long, BigDecimal> outQty = new HashMap<>();
        Map<Long, Set<Long>> inOrders = new HashMap<>();
        Map<Long, Set<Long>> outOrders = new HashMap<>();
        for (StockFlow f : stockFlowMapper.selectList(
                scope(new LambdaQueryWrapper<StockFlow>()
                                .ge(StockFlow::getOperTime, rangeStart)
                                .lt(StockFlow::getOperTime, rangeEnd),
                        wh, allowed, StockFlow::getWarehouseId))) {
            if (f.getProductId() == null || f.getQty() == null) {
                continue;
            }
            if ("IN".equals(f.getDirection())) {
                inQty.merge(f.getProductId(), f.getQty(), BigDecimal::add);
                if (f.getRefId() != null && "INBOUND".equals(f.getRefType())) {
                    inOrders.computeIfAbsent(f.getProductId(), k -> new HashSet<>()).add(f.getRefId());
                }
            } else if ("OUT".equals(f.getDirection())) {
                outQty.merge(f.getProductId(), f.getQty(), BigDecimal::add);
                if (f.getRefId() != null && "OUTBOUND".equals(f.getRefType())) {
                    outOrders.computeIfAbsent(f.getProductId(), k -> new HashSet<>()).add(f.getRefId());
                }
            }
        }

        // 按 type 口径求排序量并降序取前 N
        Set<Long> candidateIds = new HashSet<>(inQty.keySet());
        candidateIds.addAll(outQty.keySet());
        Map<Long, BigDecimal> sortQty = new HashMap<>();
        for (Long pid : candidateIds) {
            BigDecimal i = inQty.getOrDefault(pid, BigDecimal.ZERO);
            BigDecimal o = outQty.getOrDefault(pid, BigDecimal.ZERO);
            BigDecimal q = switch (type) {
                case "outbound" -> o;
                case "total" -> i.add(o);
                default -> i;
            };
            if (q.signum() > 0) {
                sortQty.put(pid, q);
            }
        }
        List<Long> ranked = new ArrayList<>(sortQty.keySet());
        ranked.sort((a, b) -> {
            int c = sortQty.get(b).compareTo(sortQty.get(a));
            return c != 0 ? c : Long.compare(a, b);
        });
        if (ranked.size() > limit) {
            ranked = new ArrayList<>(ranked.subList(0, limit));
        }

        Map<Long, Product> products = ranked.isEmpty() ? Map.of()
                : productMapper.selectList(new LambdaQueryWrapper<Product>().in(Product::getId, ranked))
                        .stream().collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        Map<Long, ProductCategory> catById = categoryMapper.selectList(null).stream()
                .collect(Collectors.toMap(ProductCategory::getId, c -> c, (a, b) -> a));

        List<TopProductsVO.TopItem> items = new ArrayList<>();
        int rank = 1;
        for (Long pid : ranked) {
            Product p = products.get(pid);
            if (p == null) {
                continue;
            }
            ProductCategory cat = p.getCategoryId() == null ? null : catById.get(p.getCategoryId());
            int orderCount = switch (type) {
                case "inbound" -> inOrders.getOrDefault(pid, Set.of()).size();
                case "outbound" -> outOrders.getOrDefault(pid, Set.of()).size();
                default -> inOrders.getOrDefault(pid, Set.of()).size()
                        + outOrders.getOrDefault(pid, Set.of()).size();
            };
            items.add(TopProductsVO.TopItem.builder()
                    .rank(rank++)
                    .productId(pid)
                    .productCode(p.getProductCode())
                    .productName(p.getProductName())
                    .categoryName(cat == null ? null : cat.getCategoryName())
                    .qty(sortQty.get(pid))
                    .orderCount(orderCount)
                    .inboundQty(inQty.getOrDefault(pid, BigDecimal.ZERO))
                    .outboundQty(outQty.getOrDefault(pid, BigDecimal.ZERO))
                    .build());
        }
        return TopProductsVO.builder()
                .type(type)
                .startDate(start.toString())
                .endDate(end.toString())
                .items(items)
                .build();
    }

    // ==================== API-STS-006 库存账龄/滞销 ====================

    @Override
    public StockAgingVO stockAging(StockAgingQueryDTO dto) {
        Long wh = dto.getWarehouseId();
        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        dataPerm.checkWarehouse(wh);

        int[] segments = parseAgingDays(dto.getAgingDays());
        int topN = dto.getTopN();
        BigDecimal minStockQty = dto.getMinStockQty();
        LocalDateTime statTime = LocalDateTime.now();

        // 库存（qty > 0）按（仓库, 批次）聚合
        List<Inventory> inventories = inventoryMapper.selectList(
                scope(new LambdaQueryWrapper<Inventory>().gt(Inventory::getQty, 0),
                        wh, allowed, Inventory::getWarehouseId));
        Set<Long> batchIds = inventories.stream()
                .map(Inventory::getBatchId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, InventoryBatch> batchById = batchMapper.selectList(
                scope(new LambdaQueryWrapper<InventoryBatch>(), wh, allowed, InventoryBatch::getWarehouseId))
                .stream()
                .filter(b -> batchIds.contains(b.getId()))
                .collect(Collectors.toMap(InventoryBatch::getId, b -> b, (a, b) -> a));
        Map<String, BatchFlowStatDTO> flowByKey = statsMapper.selectBatchFlowTimes(wh, allowed)
                .stream()
                .collect(Collectors.toMap(
                        f -> f.getWarehouseId() + "|" + f.getBatchId(), f -> f, (a, b) -> a));

        Map<String, BatchAgg> batchAggs = new HashMap<>();
        for (Inventory inv : inventories) {
            if (inv.getQty() == null || inv.getProductId() == null) {
                continue;
            }
            BatchAgg agg = batchAggs.computeIfAbsent(
                    inv.getWarehouseId() + "|" + inv.getBatchId(), k -> new BatchAgg());
            agg.warehouseId = inv.getWarehouseId();
            agg.batchId = inv.getBatchId();
            agg.productId = inv.getProductId();
            agg.qty = agg.qty.add(inv.getQty());
            if (inv.getCreateTime() != null
                    && (agg.earliestCreate == null || inv.getCreateTime().isBefore(agg.earliestCreate))) {
                agg.earliestCreate = inv.getCreateTime();
            }
        }
        for (BatchAgg agg : batchAggs.values()) {
            BatchFlowStatDTO flow = agg.batchId == null
                    ? null : flowByKey.get(agg.warehouseId + "|" + agg.batchId);
            InventoryBatch batch = agg.batchId == null ? null : batchById.get(agg.batchId);
            // 账龄基准：最近一次 IN 流水 → 批次创建时间 → 库存行创建时间
            agg.lastIn = flow != null && flow.getLastInboundTime() != null
                    ? flow.getLastInboundTime()
                    : batch != null && batch.getCreateTime() != null
                            ? batch.getCreateTime()
                            : agg.earliestCreate;
            agg.lastOut = flow == null ? null : flow.getLastOutboundTime();
            agg.ageDays = agg.lastIn == null ? 0 : (int) Math.max(0, ChronoUnit.DAYS.between(agg.lastIn, statTime));
        }

        // 账龄分段（段边界 n 个 → n+1 个分段，末段无上限）
        int bucketCount = segments.length + 1;
        BigDecimal[] segQty = new BigDecimal[bucketCount];
        Set<Long>[] segSkus = new Set[bucketCount];
        for (int i = 0; i < bucketCount; i++) {
            segQty[i] = BigDecimal.ZERO;
            segSkus[i] = new HashSet<>();
        }
        for (BatchAgg agg : batchAggs.values()) {
            int idx = segmentIndex(agg.ageDays, segments);
            segQty[idx] = segQty[idx].add(agg.qty);
            if (agg.productId != null) {
                segSkus[idx].add(agg.productId);
            }
        }
        BigDecimal totalQty = java.util.Arrays.stream(segQty).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<StockAgingVO.AgingSegment> agingSegments = new ArrayList<>(bucketCount);
        for (int i = 0; i < bucketCount; i++) {
            int minDays = i == 0 ? 0 : segments[i - 1] + 1;
            Integer maxDays = i == segments.length ? null : segments[i];
            String label = maxDays == null ? minDays + " 天以上" : minDays + "-" + maxDays + " 天";
            agingSegments.add(StockAgingVO.AgingSegment.builder()
                    .segment(label)
                    .minDays(minDays)
                    .maxDays(maxDays)
                    .qty(segQty[i])
                    .skuCount(segSkus[i].size())
                    .qtyRatio(ratio(segQty[i], totalQty, 3))
                    .build());
        }

        // 滞销：按（仓库, 商品）汇总，判定 = qty ≥ minStockQty 且（无出库记录 或 距最近出库 > 最大分段）
        int maxSeg = segments[segments.length - 1];
        Map<String, SlowAgg> slowMap = new HashMap<>();
        for (BatchAgg agg : batchAggs.values()) {
            SlowAgg s = slowMap.computeIfAbsent(agg.warehouseId + "|" + agg.productId, k -> new SlowAgg());
            s.qty = s.qty.add(agg.qty);
            s.lastIn = maxTime(s.lastIn, agg.lastIn);
            s.lastOut = maxTime(s.lastOut, agg.lastOut);
        }
        List<SlowCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, SlowAgg> e : slowMap.entrySet()) {
            SlowAgg s = e.getValue();
            if (s.qty.signum() <= 0 || s.qty.compareTo(minStockQty) < 0 || s.lastIn == null) {
                continue;
            }
            Integer daysOut = s.lastOut == null ? null : (int) Math.max(0, ChronoUnit.DAYS.between(s.lastOut, statTime));
            if (daysOut != null && daysOut <= maxSeg) {
                continue;
            }
            String[] key = e.getKey().split("\\|", -1);
            SlowCandidate c = new SlowCandidate();
            c.warehouseId = Long.valueOf(key[0]);
            c.productId = Long.valueOf(key[1]);
            c.qty = s.qty;
            c.lastIn = s.lastIn;
            c.lastOut = s.lastOut;
            c.agingDays = (int) Math.max(0, ChronoUnit.DAYS.between(s.lastIn, statTime));
            c.daysOut = daysOut;
            candidates.add(c);
        }
        // 无出库记录（null）排最前，其余按距最近出库天数降序
        candidates.sort((a, b) -> {
            if (a.daysOut == null && b.daysOut == null) {
                return 0;
            }
            if (a.daysOut == null) {
                return -1;
            }
            if (b.daysOut == null) {
                return 1;
            }
            return b.daysOut.compareTo(a.daysOut);
        });
        if (candidates.size() > topN) {
            candidates = new ArrayList<>(candidates.subList(0, topN));
        }

        Set<Long> productIds = candidates.stream().map(c -> c.productId).collect(Collectors.toSet());
        Set<Long> warehouseIds = candidates.stream().map(c -> c.warehouseId).collect(Collectors.toSet());
        Map<Long, Product> products = productIds.isEmpty() ? Map.of()
                : productMapper.selectList(new LambdaQueryWrapper<Product>().in(Product::getId, productIds))
                        .stream().collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        Map<Long, String> warehouseNames = warehouseIds.isEmpty() ? Map.of()
                : warehouseMapper.selectList(new LambdaQueryWrapper<Warehouse>().in(Warehouse::getId, warehouseIds))
                        .stream().collect(Collectors.toMap(Warehouse::getId, Warehouse::getWarehouseName, (a, b) -> a));

        List<StockAgingVO.SlowMovingItem> slowMoving = new ArrayList<>(candidates.size());
        for (SlowCandidate c : candidates) {
            Product p = products.get(c.productId);
            slowMoving.add(StockAgingVO.SlowMovingItem.builder()
                    .productId(c.productId)
                    .productCode(p == null ? null : p.getProductCode())
                    .productName(p == null ? null : p.getProductName())
                    .warehouseId(c.warehouseId)
                    .warehouseName(warehouseNames.get(c.warehouseId))
                    .qty(c.qty)
                    .lastInboundTime(c.lastIn)
                    .agingDays(c.agingDays)
                    .lastOutboundTime(c.lastOut)
                    .daysSinceLastOutbound(c.daysOut)
                    .build());
        }
        return StockAgingVO.builder()
                .statTime(statTime)
                .agingSegments(agingSegments)
                .slowMoving(slowMoving)
                .build();
    }

    // ==================== 私有辅助 ====================

    /**
     * 叠加仓库数据权限过滤：warehouseId 指定优先；空则按授权集合
     * （SQL IN 天然排除 warehouse_id IS NULL 行）；空授权集 → 恒无结果。
     */
    private <T> LambdaQueryWrapper<T> scope(LambdaQueryWrapper<T> wrapper, Long warehouseId,
                                            Set<Long> allowed, SFunction<T, ?> warehouseColumn) {
        wrapper.eq(warehouseId != null, warehouseColumn, warehouseId);
        if (warehouseId == null && allowed != null) {
            if (allowed.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(warehouseColumn, allowed);
            }
        }
        return wrapper;
    }

    /** 调拨单数据权限（源/目标任一仓可见口径，与 13.7 查询可见性一致）。 */
    private LambdaQueryWrapper<TransferOrder> transferScope(LambdaQueryWrapper<TransferOrder> wrapper,
                                                            Long warehouseId, Set<Long> allowed) {
        if (warehouseId != null) {
            wrapper.and(w -> w.eq(TransferOrder::getSrcWarehouseId, warehouseId)
                    .or().eq(TransferOrder::getDestWarehouseId, warehouseId));
        } else if (allowed != null) {
            if (allowed.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.and(w -> w.in(TransferOrder::getSrcWarehouseId, allowed)
                        .or().in(TransferOrder::getDestWarehouseId, allowed));
            }
        }
        return wrapper;
    }

    /** 区间内已过账（status=3）入库单（单据数按 post_time 口径）。 */
    private List<InboundOrder> postedInbound(LocalDateTime start, LocalDateTime end,
                                             Long wh, Set<Long> allowed) {
        return inboundOrderMapper.selectList(
                scope(new LambdaQueryWrapper<InboundOrder>()
                                .eq(InboundOrder::getStatus, 3)
                                .ge(InboundOrder::getPostTime, start)
                                .lt(InboundOrder::getPostTime, end),
                        wh, allowed, InboundOrder::getWarehouseId));
    }

    /** 区间内已过账（status=3）出库单。 */
    private List<OutboundOrder> postedOutbound(LocalDateTime start, LocalDateTime end,
                                               Long wh, Set<Long> allowed) {
        return outboundOrderMapper.selectList(
                scope(new LambdaQueryWrapper<OutboundOrder>()
                                .eq(OutboundOrder::getStatus, 3)
                                .ge(OutboundOrder::getPostTime, start)
                                .lt(OutboundOrder::getPostTime, end),
                        wh, allowed, OutboundOrder::getWarehouseId));
    }

    /** 统计范围描述。 */
    private String resolveScopeName(Long warehouseId) {
        if (warehouseId == null) {
            return "数据权限内全部仓库";
        }
        Warehouse w = warehouseMapper.selectById(warehouseId);
        return w == null ? "未知仓库" : w.getWarehouseName();
    }

    /** 逐级上溯至目标层级；分类挂在更浅层级（或已失效）时取其自身所在分类。 */
    private ProductCategory resolveAncestor(Long categoryId, int level,
                                            Map<Long, ProductCategory> catById) {
        ProductCategory cat = catById.get(categoryId);
        while (cat != null && cat.getLevel() != null && cat.getLevel() > level) {
            Long pid = cat.getParentId();
            if (pid == null || pid == 0L) {
                break;
            }
            cat = catById.get(pid);
        }
        return cat;
    }

    /** 解析账龄分段参数：逗号分隔升序正整数、最多 6 段，非法返回 40001。 */
    private int[] parseAgingDays(String raw) {
        String[] parts = raw.split(",");
        if (parts.length == 0 || parts.length > 6) {
            throw new BizException(ResultCode.PARAM_INVALID, "agingDays 最多 6 段");
        }
        int[] arr = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                arr[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                throw new BizException(ResultCode.PARAM_INVALID, "agingDays 须为逗号分隔的正整数");
            }
            if (arr[i] <= 0) {
                throw new BizException(ResultCode.PARAM_INVALID, "agingDays 须为正整数");
            }
            if (i > 0 && arr[i] <= arr[i - 1]) {
                throw new BizException(ResultCode.PARAM_INVALID, "agingDays 须严格升序");
            }
        }
        return arr;
    }

    /** 账龄所在分段下标（段边界 n 个 → n+1 段，超出最大边界落末段）。 */
    private int segmentIndex(int ageDays, int[] segments) {
        for (int i = 0; i < segments.length; i++) {
            if (ageDays <= segments[i]) {
                return i;
            }
        }
        return segments.length;
    }

    /** 比率保留指定位数（分母为 0 时返回 0）。 */
    private static BigDecimal ratio(BigDecimal value, BigDecimal total, int scale) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        }
        return value.divide(total, scale, RoundingMode.HALF_UP);
    }

    private static LocalDateTime maxTime(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }

    /** 货位利用率分组累计结构。 */
    private static final class OccAcc {
        Long warehouseId;
        Long areaId;
        int total;
        int used;
        int idle;
        int locked;
        int disabled;
        final Set<Long> skus = new HashSet<>();
        BigDecimal qty = BigDecimal.ZERO;
    }

    /** 批次账龄聚合中间结构。 */
    private static final class BatchAgg {
        Long warehouseId;
        Long batchId;
        Long productId;
        BigDecimal qty = BigDecimal.ZERO;
        LocalDateTime earliestCreate;
        LocalDateTime lastIn;
        LocalDateTime lastOut;
        int ageDays;
    }

    /** 滞销（仓库, 商品）聚合中间结构。 */
    private static final class SlowAgg {
        BigDecimal qty = BigDecimal.ZERO;
        LocalDateTime lastIn;
        LocalDateTime lastOut;
    }

    /** 滞销候选项（商品/仓库名称在截断 topN 后批量回查）。 */
    private static final class SlowCandidate {
        Long productId;
        Long warehouseId;
        BigDecimal qty;
        LocalDateTime lastIn;
        LocalDateTime lastOut;
        int agingDays;
        Integer daysOut;
    }
}
