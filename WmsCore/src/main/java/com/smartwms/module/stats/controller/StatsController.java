package com.smartwms.module.stats.controller;

import com.smartwms.module.stats.dto.CategoryDistributionQueryDTO;
import com.smartwms.module.stats.dto.DashboardQueryDTO;
import com.smartwms.module.stats.dto.OccupancyQueryDTO;
import com.smartwms.module.stats.dto.StockAgingQueryDTO;
import com.smartwms.module.stats.dto.TopProductsQueryDTO;
import com.smartwms.module.stats.dto.TrendQueryDTO;
import com.smartwms.module.stats.service.StatsService;
import com.smartwms.module.stats.vo.CategoryDistributionVO;
import com.smartwms.module.stats.vo.DashboardVO;
import com.smartwms.module.stats.vo.StockAgingVO;
import com.smartwms.module.stats.vo.TopProductsVO;
import com.smartwms.module.stats.vo.TrendVO;
import com.smartwms.module.stats.vo.WarehouseOccupancyVO;
import com.smartwms.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据统计接口 API-STS-001~006（详见《15. 数据统计接口文档》）。
 * 全部 GET 只读、不分页（data 直接为统计对象）、权限统一 sys:stats:list（40301）；
 * 首期无缓存实时聚合，参数越界返回 40001，仓库越权返回 40302。
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** STS-001 首页看板汇总（库存/今日出入库/待审单据/预警/仓库概况）。 */
    @GetMapping("/dashboard")
    @PreAuthorize("@perm.has('sys:stats:list')")
    public Result<DashboardVO> dashboard(@Valid DashboardQueryDTO dto) {
        return Result.success(statsService.dashboard(dto));
    }

    /** STS-002 出入库趋势（近 N 天按日序列，无数据日补 0）。 */
    @GetMapping("/inbound-outbound-trend")
    @PreAuthorize("@perm.has('sys:stats:list')")
    public Result<TrendVO> trend(@Valid TrendQueryDTO dto) {
        return Result.success(statsService.trend(dto));
    }

    /** STS-003 库存分类分布（按 level 分组，未分类商品归「未分类」置尾）。 */
    @GetMapping("/category-distribution")
    @PreAuthorize("@perm.has('sys:stats:list')")
    public Result<CategoryDistributionVO> categoryDistribution(@Valid CategoryDistributionQueryDTO dto) {
        return Result.success(statsService.categoryDistribution(dto));
    }

    /** STS-004 仓库货位利用率（warehouse/area 分组，分母为启用货位数）。 */
    @GetMapping("/warehouse-occupancy")
    @PreAuthorize("@perm.has('sys:stats:list')")
    public Result<WarehouseOccupancyVO> occupancy(@Valid OccupancyQueryDTO dto) {
        return Result.success(statsService.occupancy(dto));
    }

    /** STS-005 出入库 Top 商品（区间流水聚合，按 type 口径降序取前 N）。 */
    @GetMapping("/top-products")
    @PreAuthorize("@perm.has('sys:stats:list')")
    public Result<TopProductsVO> topProducts(@Valid TopProductsQueryDTO dto) {
        return Result.success(statsService.topProducts(dto));
    }

    /** STS-006 库存账龄/滞销（批次账龄分段 + 滞销清单）。 */
    @GetMapping("/stock-aging")
    @PreAuthorize("@perm.has('sys:stats:list')")
    public Result<StockAgingVO> stockAging(@Valid StockAgingQueryDTO dto) {
        return Result.success(statsService.stockAging(dto));
    }
}
