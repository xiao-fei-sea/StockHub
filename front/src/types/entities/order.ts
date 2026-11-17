/**
 * 入库单 / 出库单实体类型（API-INB-001~011、API-OUT-001~011）。
 * 说明：统一使用 type 别名（获得隐式索引签名，可直接传入 AppTable/ItemEditTable 的 Record<string, unknown>[]）。
 */

/* ---------------- 入库单（API-INB） ---------------- */

/** 入库明细行（服务端返回，详情/列表复用）。 */
export type InboundItem = {
  id?: number
  lineNo: number
  productId: number
  productCode?: string
  productName?: string
  locationId: number
  locationCode?: string
  batchNo?: string | null
  productionDate?: string | null
  expiryDate?: string | null
  planQty: number
  actualQty?: number | null
  unit?: string | null
  remark?: string | null
}

/** 入库单（列表/详情主表）。 */
export type InboundOrder = {
  id: number
  inboundNo: string
  /** 采购入库 / 调拨入库 / 退货入库 / 盘盈入库 */
  orderType: string
  supplierId?: number | null
  supplierName?: string | null
  sourceOrderNo?: string | null
  expectDate?: string | null
  status: number
  statusDesc?: string | null
  warehouseId: number
  warehouseName: string
  totalQty: number
  actualQty?: number | null
  remark?: string | null
  rejectReason?: string | null
  version: number
  createBy?: string | null
  createTime?: string | null
  auditorId?: number | null
  auditorName?: string | null
  auditTime?: string | null
  posterId?: number | null
  posterName?: string | null
  postTime?: string | null
}

export type InboundDetail = InboundOrder & { items: InboundItem[] }

/** 入库单创建/编辑明细载荷（API-INB-002/004 items[] 元素）。 */
export type InboundItemPayload = {
  productId: number
  locationId: number
  batchNo?: string
  productionDate?: string
  expiryDate?: string
  planQty: number
  actualQty?: number
  unit?: string
  remark?: string
}

/** 入库单创建/编辑请求体（API-INB-002/004）。 */
export type InboundForm = {
  id?: number
  version?: number
  orderType: string
  warehouseId: number
  supplierId?: number | null
  sourceOrderNo?: string
  expectDate?: string
  remark?: string
  items: InboundItemPayload[]
}

/* ---------------- 出库单（API-OUT） ---------------- */

/** 出库明细行（服务端返回；batchId/locationId 过账时按 FIFO 回填）。 */
export type OutboundItem = {
  id?: number
  lineNo: number
  productId: number
  productCode?: string
  productName?: string
  batchId?: number | null
  batchNo?: string | null
  locationId?: number | null
  locationCode?: string | null
  planQty: number
  actualQty?: number | null
  unit?: string | null
  remark?: string | null
}

/** 出库单（列表/详情主表）。 */
export type OutboundOrder = {
  id: number
  outboundNo: string
  /** 销售出库 / 调拨出库 / 退货出库 / 报损出库 */
  orderType: string
  customerId?: number | null
  customerName?: string | null
  sourceOrderNo?: string | null
  expectDate?: string | null
  status: number
  statusDesc?: string | null
  warehouseId: number
  warehouseName: string
  totalQty: number
  actualQty?: number | null
  /** 拣货人 */
  pickUserId?: number | null
  pickUserName?: string | null
  remark?: string | null
  rejectReason?: string | null
  version: number
  createBy?: string | null
  createTime?: string | null
  auditorId?: number | null
  auditorName?: string | null
  auditTime?: string | null
  posterId?: number | null
  posterName?: string | null
  postTime?: string | null
}

export type OutboundDetail = OutboundOrder & { items: OutboundItem[] }

/** 出库单创建/编辑明细载荷（API-OUT-002/004 items[] 元素）。 */
export type OutboundItemPayload = {
  productId: number
  batchNo?: string
  locationId?: number
  planQty: number
  actualQty?: number
  unit?: string
  remark?: string
}

/** 出库单创建/编辑请求体（API-OUT-002/004）。 */
export type OutboundForm = {
  id?: number
  version?: number
  orderType: string
  warehouseId: number
  customerId?: number | null
  sourceOrderNo?: string
  expectDate?: string
  pickUserId?: number | null
  remark?: string
  items: OutboundItemPayload[]
}
