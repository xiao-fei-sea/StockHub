package com.smartwms.stats.service;

import com.smartwms.stats.dto.CategoryDistributionQueryDTO;
import com.smartwms.stats.dto.DashboardQueryDTO;
import com.smartwms.stats.dto.OccupancyQueryDTO;
import com.smartwms.stats.dto.StockAgingQueryDTO;
import com.smartwms.stats.dto.TopProductsQueryDTO;
import com.smartwms.stats.dto.TrendQueryDTO;
import com.smartwms.stats.vo.CategoryDistributionVO;
import com.smartwms.stats.vo.DashboardVO;
import com.smartwms.stats.vo.StockAgingVO;
import com.smartwms.stats.vo.TopProductsVO;
import com.smartwms.stats.vo.TrendVO;
import com.smartwms.stats.vo.WarehouseOccupancyVO;

/**
 * 数据统计服务（API-STS-001~006，详见《15. 数据统计接口文档》）。
 * 全部为只读实时聚合，统一叠加仓库数据权限（越权 40302）。
 */
public interface StatsService {

    /** API-STS-001 首页看板汇总。 */
    DashboardVO dashboard(DashboardQueryDTO dto);

    /** API-STS-002 出入库趋势（近 N 天按日序列）。 */
    TrendVO trend(TrendQueryDTO dto);

    /** API-STS-003 库存分类分布（按 level 分组）。 */
    CategoryDistributionVO categoryDistribution(CategoryDistributionQueryDTO dto);

    /** API-STS-004 仓库货位利用率（warehouse / area 分组）。 */
    WarehouseOccupancyVO occupancy(OccupancyQueryDTO dto);

    /** API-STS-005 出入库 Top 商品。 */
    TopProductsVO topProducts(TopProductsQueryDTO dto);

    /** API-STS-006 库存账龄/滞销分析。 */
    StockAgingVO stockAging(StockAgingQueryDTO dto);
}
