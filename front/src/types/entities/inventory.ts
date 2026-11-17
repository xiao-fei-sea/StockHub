/**
 * 库存模块实体类型（库存查询/流水/汇总 API-INV-001~003、调拨 API-TRF、盘点 API-STK、预警规则 API-ALR）。
 * 说明：统一使用 type 别名（获得隐式索引签名，可直接传入 AppTable/ItemEditTable 的 Record<string, unknown>[]）。
 */

/* ---------------- 库存查询（API-INV-001 库存分页） ---------------- */

export type InventoryItem = {
  id: number
  warehouseId: number
  warehouseName: string
  areaId?: number | null
  areaName?: string | null
  locationId: number
  locationCode: string
  productId: number
  productCode: string
  productName: string
  categoryId?: number | null
  categoryName?: string | null
  unit?: string | null
  batchId: number
  batchNo: string
  productionDate?: string | null
  expiryDate?: string | null
  qty: number
  lockQty: number
  availableQty: number
  version?: number
  updateTime?: string | null
}

/** 库存多条件查询参数（API-INV-001 Query）。 */
export type InventoryQuery = {
  pageNum?: number
  pageSize?: number
  warehouseId?: number
  categoryId?: number
  productId?: number
  locationId?: number
  batchNo?: string
  keyword?: string
  onlyPositive?: boolean
}

/* ---------------- 库存流水（API-INV-002） ---------------- */

export type InventoryFlow = {
  id: number
  flowNo: string
  warehouseId: number
  warehouseName: string
  productId: number
  productCode: string
  productName: string
  batchId?: number | null
  batchNo?: string | null
  locationId?: number | null
  locationCode?: string | null
  direction: 'IN' | 'OUT'
  qty: number
  beforeQty: number
  afterQty: number
  flowType: string
  refType?: string | null
  refId?: number | null
  refNo?: string | null
  operatorId?: number | null
  operatorName?: string | null
  operTime?: string | null
  createTime?: string | null
}

/** 库存流水分页查询参数（API-INV-002 Query）。 */
export type InventoryFlowQuery = {
  pageNum?: number
  pageSize?: number
  warehouseId?: number
  productId?: number
  flowType?: string
  direction?: string
  refNo?: string
  startDate?: string
  endDate?: string
}

/* ---------------- 库存汇总（API-INV-003） ---------------- */

export type InventorySummaryGroup = {
  warehouseId?: number | null
  warehouseName?: string | null
  categoryId?: number | null
  categoryName?: string | null
  skuCount: number
  qty: number
}

export type InventorySummary = {
  groupType: string
  totalQty: number
  totalSku: number
  items: InventorySummaryGroup[]
  byCategory?: InventorySummaryGroup[] | null
}

/* ---------------- 商品分类树（API-CAT-001，库存查询/预警规则数据源） ---------------- */

/* 与 base.ts 保持同一声明，避免 entity.ts 桶文件重复导出歧义 */
export type { CategoryNode } from './base'

/* ---------------- 调拨单（API-TRF-001~011） ---------------- */

/** 调拨明细行（表单编辑态，lineNo 由前端维护）。 */
export type TransferItemForm = {
  lineNo: number
  productId?: number | null
  batchId?: number | null
  srcLocationId?: number | null
  destLocationId?: number | null
  planQty?: number | null
  actualQty?: number | null
  unit?: string
  remark?: string
}

/** 调拨明细行（服务端返回）。 */
export type TransferItem = {
  id?: number
  lineNo: number
  productId: number
  productCode?: string
  productName?: string
  batchId?: number | null
  batchNo?: string | null
  srcLocationId?: number | null
  srcLocationCode?: string | null
  destLocationId?: number | null
  destLocationCode?: string | null
  planQty: number
  actualQty?: number | null
  unit?: string | null
  remark?: string | null
}

/** 调拨单（列表/详情主表）。 */
export type TransferOrder = {
  id: number
  transferNo: string
  srcWarehouseId: number
  srcWarehouseName: string
  destWarehouseId: number
  destWarehouseName: string
  status: number
  statusDesc?: string | null
  confirmStatus?: string | null
  totalQty: number
  actualQty?: number | null
  remark?: string | null
  rejectReason?: string | null
  version: number
  createBy?: string | null
  createTime?: string | null
  auditorId?: number | null
  auditTime?: string | null
  posterId?: number | null
  postTime?: string | null
}

export type TransferDetail = TransferOrder & { items: TransferItem[] }

/** 调拨单创建/编辑请求体（API-TRF-002/004）。 */
export type TransferForm = {
  id?: number
  version?: number
  srcWarehouseId: number
  destWarehouseId: number
  remark?: string
  items: {
    productId: number
    batchId?: number | null
    srcLocationId?: number | null
    destLocationId?: number | null
    planQty: number
    actualQty?: number | null
    unit?: string
    remark?: string
  }[]
}

/** 单据状态动作请求体（submit/audit/post/cancel/withdraw 等，API-TRF/STK 通用）。 */
export type OrderActionBody = {
  remark?: string
  reason?: string
  version?: number
}

/* ---------------- 盘点单（API-STK-001~011） ---------------- */

/** 盘点明细行（服务端生成，录入实盘时编辑）。 */
export type StocktakeItem = {
  id: number
  lineNo: number
  locationId: number
  locationCode?: string | null
  productId: number
  productCode?: string
  productName?: string
  batchId?: number | null
  batchNo?: string | null
  bookQty: number
  actualQty?: number | null
  differQty?: number | null
  diffReason?: string | null
  status?: string | null
  unit?: string | null
}

/** 盘点单（列表/详情主表）。 */
export type StocktakeOrder = {
  id: number
  stocktakeNo: string
  warehouseId: number
  warehouseName: string
  areaId?: number | null
  areaName?: string | null
  scope: string
  status: number
  statusDesc?: string | null
  diffStatus?: string | null
  planDate?: string | null
  totalDifferQty?: number | null
  remark?: string | null
  rejectReason?: string | null
  version: number
  createBy?: string | null
  createTime?: string | null
  auditorId?: number | null
  auditTime?: string | null
  posterId?: number | null
  postTime?: string | null
  finishTime?: string | null
}

export type StocktakeDetail = StocktakeOrder & { items: StocktakeItem[] }

/** 盘点单创建请求体（API-STK-002，明细由服务端生成）。 */
export type StocktakeForm = {
  warehouseId: number
  scope: string
  areaId?: number | null
  planDate?: string
  remark?: string
  includeEmptyLocation?: boolean
}

/** 盘点实盘录入请求体（API-STK-004）。 */
export type StocktakeInputForm = {
  version?: number
  remark?: string
  items: {
    id: number
    actualQty: number
    diffReason?: string
  }[]
}

/* ---------------- 预警规则（API-ALR-001~005） ---------------- */

export type AlertRule = {
  id: number
  ruleName: string
  warehouseId?: number | null
  warehouseName?: string | null
  categoryId?: number | null
  categoryName?: string | null
  productId?: number | null
  productName?: string | null
  productCode?: string | null
  alertType: string
  alertTypeDesc?: string | null
  thresholdValue?: number | null
  minQty?: number | null
  maxQty?: number | null
  nearDays?: number | null
  status: number
  statusDesc?: string | null
  remark?: string | null
  createTime?: string | null
  createBy?: string | null
  updateTime?: string | null
}

/** 预警规则创建/编辑请求体（API-ALR-002/003）。 */
export type AlertRuleForm = {
  ruleName: string
  warehouseId?: number | null
  categoryId?: number | null
  productId?: number | null
  alertType: string
  thresholdValue?: number | null
  minQty?: number | null
  maxQty?: number | null
  nearDays?: number | null
  status: number
  remark?: string
}

/** 手动扫描结果（API-ALR-008 响应）。 */
export type AlertScanResult = {
  scanTime: string
  ruleCount: number
  newRecordCount: number
  detail?: { alertType: string; count: number }[]
}
