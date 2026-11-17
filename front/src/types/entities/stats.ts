/** 数据统计模块实体类型（对应《15. 数据统计接口文档》API-STS-001~006）。 */

/** API-STS-001 首页看板汇总 data。 */
export interface DashboardSummary {
  statDate: string
  warehouseScope: string
  inventory: {
    totalQty: number
    skuCount: number
    batchCount: number
    locationOccupiedCount: number
  }
  today: {
    inboundOrderCount: number
    inboundQty: number
    outboundOrderCount: number
    outboundQty: number
  }
  pending: {
    inboundPendingCount: number
    outboundPendingCount: number
    stocktakePendingCount: number
    transferPendingCount: number
  }
  alert: {
    unhandledCount: number
    seriousCount: number
  }
  warehouse: {
    warehouseCount: number
    locationTotal: number
    locationUsed: number
    occupancyRate: number
  }
}

/** API-STS-002 出入库趋势 data。 */
export interface TrendData {
  days: number
  startDate: string
  endDate: string
  series: {
    date: string
    inboundQty: number
    outboundQty: number
    inboundCount: number
    outboundCount: number
  }[]
  summary: {
    inboundQtyTotal: number
    outboundQtyTotal: number
  }
}

/** API-STS-003 库存分类分布 data。 */
export interface CategoryDistribution {
  groupLevel: number
  totalQty: number
  totalSku: number
  items: {
    categoryId: number | null
    categoryCode: string | null
    categoryName: string
    skuCount: number
    qty: number
    qtyRatio: number
  }[]
}

/** API-STS-004 仓库货位利用率 data。 */
export interface WarehouseOccupancy {
  groupType: string
  summary: {
    locationTotal: number
    locationUsed: number
    locationLocked: number
    locationDisabled: number
    locationIdle: number
    occupancyRate: number
  }
  items: {
    warehouseId: number
    warehouseName: string
    locationTotal: number
    locationUsed: number
    locationIdle: number
    locationLocked: number
    locationDisabled: number
    occupancyRate: number
    skuCount: number
    qty: number
  }[]
}

/** API-STS-005 出入库 Top 商品 data。 */
export interface TopProducts {
  type: 'inbound' | 'outbound' | 'total'
  startDate: string
  endDate: string
  items: {
    rank: number
    productId: number
    productCode: string
    productName: string
    categoryName: string
    qty: number
    orderCount: number
    inboundQty: number
    outboundQty: number
  }[]
}

/** API-STS-006 库存账龄/滞销 data。 */
export interface StockAging {
  statTime: string
  agingSegments: {
    segment: string
    minDays: number
    maxDays: number | null
    qty: number
    skuCount: number
    qtyRatio: number
  }[]
  slowMoving: {
    productId: number
    productCode: string
    productName: string
    warehouseId: number
    warehouseName: string
    qty: number
    lastInboundTime: string
    agingDays: number
    lastOutboundTime: string | null
    daysSinceLastOutbound: number | null
  }[]
}
