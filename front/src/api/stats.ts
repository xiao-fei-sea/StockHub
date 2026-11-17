import request from '@/utils/request'
import type {
  CategoryDistribution,
  DashboardSummary,
  StockAging,
  TopProducts,
  TrendData,
  WarehouseOccupancy,
} from '@/types/entity'

/** 数据统计统一查询参数（各接口按需组合，空值不传）。 */
export interface StatsQuery {
  warehouseId?: number
  days?: number
  limit?: number
  type?: 'inbound' | 'outbound' | 'total'
  startDate?: string
  endDate?: string
  groupBy?: 'warehouse' | 'area'
  level?: number
  agingDays?: string
  topN?: number
  minStockQty?: number
}

/** API-STS-001 首页看板汇总。 */
export function getDashboardSummary(params?: { warehouseId?: number }): Promise<DashboardSummary> {
  return request.get('/stats/dashboard', { params })
}

/** API-STS-002 出入库趋势。 */
export function getInOutTrend(params?: { days?: number; warehouseId?: number }): Promise<TrendData> {
  return request.get('/stats/inbound-outbound-trend', { params })
}

/** API-STS-003 库存分类分布。 */
export function getCategoryDistribution(
  params?: { warehouseId?: number; level?: number },
): Promise<CategoryDistribution> {
  return request.get('/stats/category-distribution', { params })
}

/** API-STS-004 仓库货位利用率。 */
export function getWarehouseOccupancy(
  params?: { warehouseId?: number; groupBy?: 'warehouse' | 'area' },
): Promise<WarehouseOccupancy> {
  return request.get('/stats/warehouse-occupancy', { params })
}

/** API-STS-005 出入库 Top 商品。 */
export function getTopProducts(params?: StatsQuery): Promise<TopProducts> {
  return request.get('/stats/top-products', { params })
}

/** API-STS-006 库存账龄/滞销。 */
export function getStockAging(params?: StatsQuery): Promise<StockAging> {
  return request.get('/stats/stock-aging', { params })
}
