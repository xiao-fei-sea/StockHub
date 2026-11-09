package com.smartwms.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartwms.alert.dto.AlertHandleDTO;
import com.smartwms.alert.dto.AlertRecordPageQueryDTO;
import com.smartwms.alert.dto.AlertScanDTO;
import com.smartwms.alert.mapper.StockAlertRecordMapper;
import com.smartwms.alert.mapper.StockAlertRuleMapper;
import com.smartwms.alert.service.AlertService;
import com.smartwms.alert.vo.AlertHandleVO;
import com.smartwms.alert.vo.AlertRecordVO;
import com.smartwms.alert.vo.AlertScanVO;
import com.smartwms.base.mapper.ProductCategoryMapper;
import com.smartwms.base.mapper.ProductMapper;
import com.smartwms.base.mapper.WarehouseMapper;
import com.smartwms.entity.Inventory;
import com.smartwms.entity.InventoryBatch;
import com.smartwms.entity.Product;
import com.smartwms.entity.ProductCategory;
import com.smartwms.entity.StockAlertRecord;
import com.smartwms.entity.StockAlertRule;
import com.smartwms.entity.SysUser;
import com.smartwms.entity.Warehouse;
import com.smartwms.enums.AlertLevel;
import com.smartwms.enums.AlertType;
import com.smartwms.enums.HandleStatus;
import com.smartwms.enums.ResultCode;
import com.smartwms.exception.BizException;
import com.smartwms.inventory.mapper.InventoryBatchMapper;
import com.smartwms.inventory.mapper.InventoryMapper;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 预警记录服务实现（API-ALR-006~008）。预警为提示性能力：
 * 扫描只新增 stock_alert_record，不修改库存、不拦截出入库业务（基线第 8 节）。
 *
 * 14.6 待确认项采纳口径：
 * 1. LOW_STOCK 阈值以规则值为准（minQty 优先，缺省 thresholdValue，不回退商品 min_stock）；
 * 2. 扫描去重 = 按日去重：同规则+仓库+商品+批次 当日已存在「未处理」记录不重复生成，
 *    「已处理」后当日可再次提醒；
 * 3. 级别判定 = current_value 与阈值偏差比例 ≤30% 提示、≤60% 警告、>60% 严重，
 *    EXPIRED（已过期）固定「严重」；
 * 4. 扫描并发 = 单体进程内串行（synchronized），跨实例由按日去重兜底（不抛 40905）。
 *
 * 扫描口径补充（文档 14.4-008 规则 2 的实现细化）：
 * - LOW/HIGH：按 (仓库, 商品) 汇总 inventory.qty；限定商品/分类范围时无库存行按 0 评估
 *   （缺货即低库存），全量维度（不限商品）基于现有库存行汇总，避免全量笛卡尔积；
 * - NEAR_EXPIRY/EXPIRED：遍历 qty>0 且 batch_id 非空的库存行，
 *   仅 shelf_life_flag=1 商品，current_value=距过期天数（EXPIRED 为负数=已过期天数）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final StockAlertRecordMapper recordMapper;
    private final StockAlertRuleMapper ruleMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryBatchMapper batchMapper;
    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final WarehouseMapper warehouseMapper;
    private final SysUserMapper userMapper;
    private final WarehouseDataPerm dataPerm;

    // ==================== ALR-006 记录分页 ====================

    @Override
    public PageResult<AlertRecordVO> page(AlertRecordPageQueryDTO dto) {
        int pageNum = dto.getPageNum() == null ? 1 : dto.getPageNum();
        int pageSize = dto.getPageSize() == null ? 20 : Math.min(dto.getPageSize(), 100);
        Integer alertType = AlertType.toCode(dto.getAlertType());   // 英文标签，非法 40001
        Integer status = HandleStatus.toCode(dto.getStatus());       // 中文标签，非法 40001
        Integer level = AlertLevel.toCode(dto.getLevel());           // 中文标签，非法 40001
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

        LambdaQueryWrapper<StockAlertRecord> wrapper = new LambdaQueryWrapper<StockAlertRecord>()
                .eq(dto.getWarehouseId() != null, StockAlertRecord::getWarehouseId, dto.getWarehouseId())
                .eq(alertType != null, StockAlertRecord::getAlertType, alertType)
                .eq(status != null, StockAlertRecord::getStatus, status)
                .eq(level != null, StockAlertRecord::getLevel, level)
                .eq(dto.getProductId() != null, StockAlertRecord::getProductId, dto.getProductId())
                .ge(start != null, StockAlertRecord::getCreateTime,
                        start == null ? null : start.atStartOfDay())
                .lt(end != null, StockAlertRecord::getCreateTime,
                        end == null ? null : end.plusDays(1).atStartOfDay())
                // 记录恒有仓库，按数据权限过滤（不限定角色 allowed=null 不过滤）
                .in(allowed != null, StockAlertRecord::getWarehouseId, allowed)
                .orderByDesc(StockAlertRecord::getCreateTime)
                .orderByDesc(StockAlertRecord::getId);

        Page<StockAlertRecord> page = recordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, toVOList(page.getRecords()));
    }

    // ==================== ALR-007 处理预警 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertHandleVO handle(Long id, AlertHandleDTO dto) {
        StockAlertRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BizException(ResultCode.ORDER_NOT_EXIST, "预警记录不存在");
        }
        dataPerm.checkWarehouse(record.getWarehouseId()); // 越权 40302
        if (record.getStatus() != null && record.getStatus() == 1) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "预警记录已是「已处理」状态");
        }

        Long userId = currentUserId();
        String remark = StringUtils.hasText(dto.getHandleRemark()) ? dto.getHandleRemark().trim() : null;
        // 幂等核心：status=0 条件更新（记录表无 version 列），影响 0 行 = 重复处理 → 40901
        int updated = recordMapper.handleIfUnprocessed(id, remark, userId, LocalDateTime.now());
        if (updated == 0) {
            throw new BizException(ResultCode.ORDER_STATUS_INVALID, "预警记录已被处理，不可重复处理");
        }
        record = recordMapper.selectById(id); // 回读完整实体（handle_time/update_time 由 DB 生成）
        log.info("处理预警成功 id={}, handleUser={}", id, userId);

        AlertHandleVO vo = new AlertHandleVO();
        vo.setId(record.getId());
        vo.setStatus(HandleStatus.toLabel(record.getStatus()));
        vo.setHandleUser(record.getHandleUser());
        vo.setHandleUserName(username(record.getHandleUser()));
        vo.setHandleTime(record.getHandleTime());
        vo.setHandleRemark(record.getHandleRemark());
        return vo;
    }

    // ==================== ALR-008 手动触发扫描 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertScanVO scan(AlertScanDTO dto) {
        dataPerm.checkWarehouse(dto.getWarehouseId()); // 越权 40302（空 = 数据权限内全部）
        Set<Long> allowed = dataPerm.allowedWarehouseIds();
        LocalDateTime scanTime = LocalDateTime.now();
        LocalDate today = scanTime.toLocalDate();
        Long userId = currentUserId();

        // 14.6 待确认采纳「串行排队」：单体进程内串行执行，防同实例并发重复生成（跨实例由按日去重兜底）
        synchronized (this) {
            // 规则集：仅启用；可选 ruleIds 限定；受限用户仅见权限内仓库规则
            // （warehouse_id 为空的全部仓库规则仅不受限角色可达，SQL IN 天然排除 NULL 行）
            LambdaQueryWrapper<StockAlertRule> ruleWrapper = new LambdaQueryWrapper<StockAlertRule>()
                    .eq(StockAlertRule::getStatus, 1);
            if (dto.getRuleIds() != null && !dto.getRuleIds().isEmpty()) {
                ruleWrapper.in(StockAlertRule::getId, dto.getRuleIds());
            }
            if (allowed != null) {
                ruleWrapper.in(StockAlertRule::getWarehouseId, allowed);
            }
            List<StockAlertRule> rules = ruleMapper.selectList(ruleWrapper);
            if (rules.isEmpty()) {
                log.info("预警扫描：无可扫描规则 warehouseId={}, ruleCount=0", dto.getWarehouseId());
                return AlertScanVO.builder().scanTime(scanTime).ruleCount(0)
                        .newRecordCount(0).detail(List.of()).build();
            }

            // 库存快照（指定仓库或数据权限内全部库存行）
            LambdaQueryWrapper<Inventory> invWrapper = new LambdaQueryWrapper<Inventory>()
                    .eq(dto.getWarehouseId() != null, Inventory::getWarehouseId, dto.getWarehouseId())
                    .in(dto.getWarehouseId() == null && allowed != null,
                            Inventory::getWarehouseId, allowed);
            List<Inventory> inventories = inventoryMapper.selectList(invWrapper);

            // (仓库,商品) 库存汇总（LOW/HIGH 扫描用）
            Map<WhProd, BigDecimal> qtyByWhProd = inventories.stream().collect(Collectors.groupingBy(
                    inv -> new WhProd(inv.getWarehouseId(), inv.getProductId()),
                    Collectors.mapping(Inventory::getQty,
                            Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));

            // 保质期类扫描反查：批次（qty>0 且有批次）与商品（shelf_life_flag）
            Set<Long> batchIds = inventories.stream()
                    .filter(inv -> inv.getQty().signum() > 0 && inv.getBatchId() != null)
                    .map(Inventory::getBatchId).collect(Collectors.toSet());
            Map<Long, InventoryBatch> batchMap = batchIds.isEmpty() ? Map.of()
                    : batchMapper.selectBatchIds(batchIds).stream()
                    .collect(Collectors.toMap(InventoryBatch::getId, b -> b, (x, y) -> x));
            Set<Long> productIds = inventories.stream()
                    .map(Inventory::getProductId).collect(Collectors.toSet());
            Map<Long, Product> productMap = productIds.isEmpty() ? Map.of()
                    : productMapper.selectBatchIds(productIds).stream()
                    .collect(Collectors.toMap(Product::getId, p -> p, (x, y) -> x));

            // 全部仓库清单（warehouse_id 为空的规则按全部仓库扫描，仅不受限角色可达）
            List<Long> allWarehouseIds = warehouseMapper.selectList(null).stream()
                    .map(Warehouse::getId).toList();

            Map<String, Integer> countByType = new LinkedHashMap<>();
            int newRecordCount = 0;
            for (StockAlertRule rule : rules) {
                int generated = scanOneRule(rule, dto, allWarehouseIds, qtyByWhProd,
                        inventories, batchMap, productMap, today, userId);
                if (generated > 0) {
                    newRecordCount += generated;
                    countByType.merge(AlertType.toLabel(rule.getAlertType()), generated, Integer::sum);
                }
            }
            List<AlertScanVO.ScanDetail> detail = countByType.entrySet().stream()
                    .map(e -> AlertScanVO.ScanDetail.builder()
                            .alertType(e.getKey()).count(e.getValue()).build())
                    .toList();
            log.info("预警扫描完成 ruleCount={}, newRecordCount={}, detail={}",
                    rules.size(), newRecordCount, countByType);
            return AlertScanVO.builder()
                    .scanTime(scanTime)
                    .ruleCount(rules.size())
                    .newRecordCount(newRecordCount)
                    .detail(detail)
                    .build();
        }
    }

    // ---------- 扫描子步骤 ----------

    /** 单规则扫描：先算目标仓库与商品范围，再按日去重后分类型生成记录；返回生成数。 */
    private int scanOneRule(StockAlertRule rule, AlertScanDTO dto, List<Long> allWarehouseIds,
                            Map<WhProd, BigDecimal> qtyByWhProd, List<Inventory> inventories,
                            Map<Long, InventoryBatch> batchMap, Map<Long, Product> productMap,
                            LocalDate today, Long userId) {
        List<Long> targetWhs = resolveWarehouses(rule, dto, allWarehouseIds);
        if (targetWhs.isEmpty()) {
            return 0;
        }
        Set<Long> prodScope = productScope(rule); // null = 不限商品

        // 按日去重键集：同规则+仓库+商品+批次 当日已存在「未处理」记录不重复生成（14.6 采纳）
        Set<String> existing = recordMapper.selectList(new LambdaQueryWrapper<StockAlertRecord>()
                        .eq(StockAlertRecord::getRuleId, rule.getId())
                        .eq(StockAlertRecord::getStatus, 0)
                        .ge(StockAlertRecord::getCreateTime, today.atStartOfDay())).stream()
                .map(r -> dedupKey(r.getWarehouseId(), r.getProductId(), r.getBatchId()))
                .collect(Collectors.toCollection(HashSet::new));

        int count = 0;
        switch (rule.getAlertType() == null ? -1 : rule.getAlertType()) {
            case 1 -> count = scanLowStock(rule, targetWhs, prodScope, qtyByWhProd,
                    existing, today, userId);
            case 2 -> count = scanHighStock(rule, targetWhs, prodScope, qtyByWhProd,
                    existing, today, userId);
            case 3 -> count = scanNearExpiry(rule, targetWhs, prodScope, inventories,
                    batchMap, productMap, existing, today, userId);
            case 4 -> count = scanExpired(rule, targetWhs, prodScope, inventories,
                    batchMap, productMap, existing, today, userId);
            default -> log.warn("预警规则 alertType 非法，跳过 ruleId={}, alertType={}",
                    rule.getId(), rule.getAlertType());
        }
        return count;
    }

    /**
     * 低于下限：阈值 = minQty 优先（14.6 采纳规则值为准），Σ inventory.qty &lt; 阈值生成。
     * 限定商品/分类范围时无库存行按 0 评估（缺货即低库存）；全量维度基于现有库存行。
     */
    private int scanLowStock(StockAlertRule rule, List<Long> targetWhs, Set<Long> prodScope,
                             Map<WhProd, BigDecimal> qtyByWhProd, Set<String> existing,
                             LocalDate today, Long userId) {
        BigDecimal threshold = rule.getMinQty() != null ? rule.getMinQty() : rule.getThresholdValue();
        if (threshold == null) {
            return 0; // 规则缺阈值（创建已校验，兜底跳过）
        }
        int count = 0;
        for (Long wh : targetWhs) {
            for (Map.Entry<Long, BigDecimal> e
                    : candidateSums(wh, prodScope, qtyByWhProd).entrySet()) {
                if (e.getValue().compareTo(threshold) < 0
                        && insertIfAbsent(rule, wh, e.getKey(), null, e.getValue(), threshold,
                        levelLow(e.getValue(), threshold), existing, today, userId)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** 高于上限：阈值 = maxQty 优先，Σ inventory.qty > 阈值生成。 */
    private int scanHighStock(StockAlertRule rule, List<Long> targetWhs, Set<Long> prodScope,
                              Map<WhProd, BigDecimal> qtyByWhProd, Set<String> existing,
                              LocalDate today, Long userId) {
        BigDecimal threshold = rule.getMaxQty() != null ? rule.getMaxQty() : rule.getThresholdValue();
        if (threshold == null) {
            return 0;
        }
        int count = 0;
        for (Long wh : targetWhs) {
            for (Map.Entry<Long, BigDecimal> e
                    : candidateSums(wh, prodScope, qtyByWhProd).entrySet()) {
                if (e.getValue().compareTo(threshold) > 0
                        && insertIfAbsent(rule, wh, e.getKey(), null, e.getValue(), threshold,
                        levelHigh(e.getValue(), threshold), existing, today, userId)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** 临近保质期：shelf_life_flag=1 且 0 &lt; (expiry_date - 当前) ≤ near_days 且库存 > 0 的批次。 */
    private int scanNearExpiry(StockAlertRule rule, List<Long> targetWhs, Set<Long> prodScope,
                               List<Inventory> inventories, Map<Long, InventoryBatch> batchMap,
                               Map<Long, Product> productMap, Set<String> existing,
                               LocalDate today, Long userId) {
        Integer nearDays = rule.getNearDays();
        if (nearDays == null || nearDays < 1) {
            return 0;
        }
        BigDecimal threshold = BigDecimal.valueOf(nearDays);
        int count = 0;
        for (Inventory inv : inventories) {
            if (inv.getQty().signum() <= 0 || inv.getBatchId() == null
                    || !targetWhs.contains(inv.getWarehouseId())
                    || (prodScope != null && !prodScope.contains(inv.getProductId()))) {
                continue;
            }
            InventoryBatch batch = batchMap.get(inv.getBatchId());
            Product product = productMap.get(inv.getProductId());
            if (batch == null || batch.getExpiryDate() == null || product == null
                    || product.getShelfLifeFlag() == null || product.getShelfLifeFlag() != 1) {
                continue;
            }
            long daysLeft = ChronoUnit.DAYS.between(today, batch.getExpiryDate());
            if (daysLeft > 0 && daysLeft <= nearDays) {
                BigDecimal current = BigDecimal.valueOf(daysLeft);
                if (insertIfAbsent(rule, inv.getWarehouseId(), inv.getProductId(), inv.getBatchId(),
                        current, threshold, levelNear(daysLeft, nearDays),
                        existing, today, userId)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** 已过期：expiry_date &lt; 当前日期且库存 > 0 的批次；current_value 为负数（已过期天数）。 */
    private int scanExpired(StockAlertRule rule, List<Long> targetWhs, Set<Long> prodScope,
                            List<Inventory> inventories, Map<Long, InventoryBatch> batchMap,
                            Map<Long, Product> productMap, Set<String> existing,
                            LocalDate today, Long userId) {
        int count = 0;
        for (Inventory inv : inventories) {
            if (inv.getQty().signum() <= 0 || inv.getBatchId() == null
                    || !targetWhs.contains(inv.getWarehouseId())
                    || (prodScope != null && !prodScope.contains(inv.getProductId()))) {
                continue;
            }
            InventoryBatch batch = batchMap.get(inv.getBatchId());
            if (batch == null || batch.getExpiryDate() == null
                    || !batch.getExpiryDate().isBefore(today)) {
                continue;
            }
            long daysLeft = ChronoUnit.DAYS.between(today, batch.getExpiryDate()); // <0
            BigDecimal current = BigDecimal.valueOf(daysLeft);
            // EXPIRED 固定「严重」（14.6 偏差公式的特例：已过期即最严重级）
            if (insertIfAbsent(rule, inv.getWarehouseId(), inv.getProductId(), inv.getBatchId(),
                    current, ZERO, AlertLevel.SEVERE.getCode(), existing, today, userId)) {
                count++;
            }
        }
        return count;
    }

    /** 规则的目标仓库：dto 限定优先（须落在规则范围内），次之规则仓库，空 = 全部仓库。 */
    private List<Long> resolveWarehouses(StockAlertRule rule, AlertScanDTO dto,
                                         List<Long> allWarehouseIds) {
        if (dto.getWarehouseId() != null) {
            if (rule.getWarehouseId() == null || rule.getWarehouseId().equals(dto.getWarehouseId())) {
                return List.of(dto.getWarehouseId());
            }
            return List.of(); // 规则仓库与指定扫描仓库不同 → 跳过该规则
        }
        if (rule.getWarehouseId() != null) {
            return List.of(rule.getWarehouseId());
        }
        return allWarehouseIds;
    }

    /** 规则的商品范围：productId 单品 / categoryId 分类子树（含子分类）/ null 不限。 */
    private Set<Long> productScope(StockAlertRule rule) {
        if (rule.getProductId() != null) {
            return Set.of(rule.getProductId());
        }
        if (rule.getCategoryId() == null) {
            return null;
        }
        Set<Long> categoryIds = categorySubtree(rule.getCategoryId());
        if (categoryIds.isEmpty()) {
            return Set.of();
        }
        return productMapper.selectList(new LambdaQueryWrapper<Product>()
                        .select(Product::getId)
                        .in(Product::getCategoryId, categoryIds)).stream()
                .map(Product::getId).collect(Collectors.toSet());
    }

    /** 分类子树（含自身）BFS 展开（product_category 无 ancestors 列）；分类不存在返回空集（扫描不中断）。 */
    private Set<Long> categorySubtree(Long rootId) {
        List<ProductCategory> all = categoryMapper.selectList(null);
        if (all.stream().noneMatch(c -> rootId.equals(c.getId()))) {
            log.warn("预警规则引用的分类已不存在，按空商品范围扫描 categoryId={}", rootId);
            return Set.of();
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

    /** 单仓库候选（商品 → 库存汇总）：限定范围按范围展开（无库存行按 0），不限范围按现有行。 */
    private Map<Long, BigDecimal> candidateSums(Long warehouseId, Set<Long> prodScope,
                                                Map<WhProd, BigDecimal> qtyByWhProd) {
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        if (prodScope == null) {
            qtyByWhProd.forEach((key, qty) -> {
                if (warehouseId.equals(key.warehouseId())) {
                    result.put(key.productId(), qty);
                }
            });
        } else {
            for (Long productId : prodScope) {
                result.put(productId,
                        qtyByWhProd.getOrDefault(new WhProd(warehouseId, productId), ZERO));
            }
        }
        return result;
    }

    /** 按日去重键：仓库+商品+批次（同规则维度外层已限定）。 */
    private String dedupKey(Long warehouseId, Long productId, Long batchId) {
        return warehouseId + "|" + productId + "|" + (batchId == null ? "-" : batchId);
    }

    /**
     * 生成预警记录（去重核心：existing 集合不含该键才插入，插入后立即登记防同扫描内重复）。
     * 记录 status 初始未处理、阈值为规则快照。
     */
    private boolean insertIfAbsent(StockAlertRule rule, Long warehouseId, Long productId,
                                   Long batchId, BigDecimal currentValue, BigDecimal threshold,
                                   Integer level, Set<String> existing, LocalDate today,
                                   Long userId) {
        if (!existing.add(dedupKey(warehouseId, productId, batchId))) {
            return false;
        }
        StockAlertRecord record = new StockAlertRecord();
        record.setRuleId(rule.getId());
        record.setAlertType(rule.getAlertType());
        record.setWarehouseId(warehouseId);
        record.setProductId(productId);
        record.setBatchId(batchId);
        record.setCurrentValue(currentValue);
        record.setThresholdValue(threshold);
        record.setLevel(level);
        record.setStatus(0);
        record.setCreateBy(userId);
        recordMapper.insert(record);
        return true;
    }

    // ---------- 级别判定（14.6 待确认采纳：偏差 ≤30% 提示、≤60% 警告、>60% 严重） ----------

    private Integer levelLow(BigDecimal current, BigDecimal threshold) {
        if (threshold.signum() <= 0) {
            return AlertLevel.SEVERE.getCode();
        }
        return levelByDeviation(threshold.subtract(current)
                .divide(threshold, 6, RoundingMode.HALF_UP));
    }

    private Integer levelHigh(BigDecimal current, BigDecimal threshold) {
        if (threshold.signum() <= 0) {
            return AlertLevel.SEVERE.getCode();
        }
        return levelByDeviation(current.subtract(threshold)
                .divide(threshold, 6, RoundingMode.HALF_UP));
    }

    private Integer levelNear(long daysLeft, int nearDays) {
        BigDecimal threshold = BigDecimal.valueOf(nearDays);
        return levelByDeviation(threshold.subtract(BigDecimal.valueOf(daysLeft))
                .divide(threshold, 6, RoundingMode.HALF_UP));
    }

    private Integer levelByDeviation(BigDecimal deviation) {
        if (deviation.compareTo(new BigDecimal("0.3")) <= 0) {
            return AlertLevel.TIP.getCode();
        }
        if (deviation.compareTo(new BigDecimal("0.6")) <= 0) {
            return AlertLevel.WARNING.getCode();
        }
        return AlertLevel.SEVERE.getCode();
    }

    // ==================== 私有方法 ====================

    /** 记录批量转出参（反查规则名/仓库名/商品/处理人）。 */
    private List<AlertRecordVO> toVOList(List<StockAlertRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Set<Long> ruleIds = records.stream().map(StockAlertRecord::getRuleId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> warehouseIds = records.stream().map(StockAlertRecord::getWarehouseId)
                .collect(Collectors.toSet());
        Set<Long> productIds = records.stream().map(StockAlertRecord::getProductId)
                .collect(Collectors.toSet());
        Set<Long> userIds = records.stream().map(StockAlertRecord::getHandleUser)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> ruleNames = ruleIds.isEmpty() ? Map.of()
                : ruleMapper.selectBatchIds(ruleIds).stream()
                .collect(Collectors.toMap(StockAlertRule::getId, StockAlertRule::getRuleName, (x, y) -> x));
        Map<Long, String> warehouseNames = warehouseMapper.selectBatchIds(warehouseIds).stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getWarehouseName, (x, y) -> x));
        Map<Long, Product> products = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (x, y) -> x));
        Map<Long, String> usernames = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getUsername, (x, y) -> x));

        List<AlertRecordVO> result = new ArrayList<>(records.size());
        for (StockAlertRecord r : records) {
            AlertRecordVO vo = new AlertRecordVO();
            vo.setId(r.getId());
            vo.setRuleId(r.getRuleId());
            vo.setRuleName(ruleNames.get(r.getRuleId()));
            vo.setAlertType(AlertType.toLabel(r.getAlertType()));
            vo.setAlertTypeDesc(AlertType.toDesc(r.getAlertType()));
            vo.setWarehouseId(r.getWarehouseId());
            vo.setWarehouseName(warehouseNames.get(r.getWarehouseId()));
            vo.setProductId(r.getProductId());
            Product product = products.get(r.getProductId());
            vo.setProductCode(product == null ? null : product.getProductCode());
            vo.setProductName(product == null ? null : product.getProductName());
            vo.setBatchId(r.getBatchId());
            vo.setCurrentValue(r.getCurrentValue());
            vo.setThresholdValue(r.getThresholdValue());
            vo.setLevel(AlertLevel.toLabel(r.getLevel()));
            vo.setStatus(HandleStatus.toLabel(r.getStatus()));
            vo.setHandleRemark(r.getHandleRemark());
            vo.setHandleUser(r.getHandleUser());
            vo.setHandleUserName(usernames.get(r.getHandleUser()));
            vo.setHandleTime(r.getHandleTime());
            vo.setCreateTime(r.getCreateTime());
            result.add(vo);
        }
        return result;
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

    /** 用户名反查（已删除用户置 null）。 */
    private String username(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser user = userMapper.selectById(userId);
        return user == null ? null : user.getUsername();
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser.getId();
        }
        return null;
    }

    /** (仓库, 商品) 汇总键。 */
    private record WhProd(Long warehouseId, Long productId) {
    }
}
