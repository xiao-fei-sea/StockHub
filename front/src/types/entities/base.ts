/** 基础资料公共选项类型（下拉选择器与基础模块共享）。 */

export interface WarehouseOption {
  id: number
  warehouseCode: string
  warehouseName: string
}

export interface ProductOption {
  id: number
  productCode: string
  productName: string
  unit: string
  spec?: string
  /** 是否批次管理：1 是 / 0 否 */
  batchFlag: number
  /** 是否保质期管理：1 是 / 0 否 */
  shelfLifeFlag: number
}

export interface LocationOption {
  id: number
  locationCode: string
  locationName: string
  locationType: string
  currentStatus: string
}

export interface AreaOption {
  id: number
  areaCode: string
  areaName: string
  /** 库区类型：存储区/拣货区/暂存区（/api/areas/options 返回） */
  areaType?: string
}

/** 货架下拉/级联选项（/api/racks 分页行）。 */
export interface RackOption {
  id: number
  areaId: number
  rackCode: string
  rackName: string
  floorCount?: number
  columnCount?: number
  status?: number
}

/* ---------------- 实体（与接口文档字段逐字对应，camelCase） ---------------- */

/** 仓库（warehouse 表）。 */
export interface Warehouse {
  id: number
  warehouseCode: string
  warehouseName: string
  address?: string | null
  contactName?: string | null
  contactPhone?: string | null
  areaSize?: number | null
  status: number
  sort?: number
  remark?: string | null
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
}

/** 仓库表单（新增/编辑请求体）。 */
export interface WarehouseForm {
  warehouseCode: string
  warehouseName: string
  address?: string
  contactName?: string
  contactPhone?: string
  areaSize?: number | null
  status: number
  sort?: number
  remark?: string
}

/** 库区（storage_area 表）。 */
export interface Area {
  id: number
  warehouseId: number
  warehouseName?: string
  areaCode: string
  areaName: string
  /** 存储区/拣货区/暂存区 */
  areaType: string
  status: number
  sort?: number
  remark?: string | null
  createTime?: string
  updateTime?: string
}

/** 库区表单。 */
export interface AreaForm {
  warehouseId: number
  areaCode: string
  areaName: string
  areaType: string
  status: number
  sort?: number
  remark?: string
}

/** 货架（rack 表）。 */
export interface Rack {
  id: number
  warehouseId: number
  areaId: number
  areaName?: string
  rackCode: string
  rackName: string
  floorCount: number
  columnCount: number
  status: number
  sort?: number
  remark?: string | null
  createTime?: string
  updateTime?: string
}

/** 货架表单。 */
export interface RackForm {
  warehouseId: number
  areaId: number
  rackCode: string
  rackName: string
  floorCount: number
  columnCount: number
  status: number
  sort?: number
  remark?: string
}

/** 货位（location 表）。 */
export interface Location {
  id: number
  warehouseId: number
  areaId: number
  rackId: number
  areaName?: string
  rackName?: string
  locationCode: string
  locationName: string
  /** 存储位/拣货位/暂存位/收货位 */
  locationType: string
  capacityVolume?: number | null
  /** 空闲/占用/锁定/禁用 */
  currentStatus: string
  status: number
  sort?: number
  remark?: string | null
  createTime?: string
  updateTime?: string
}

/** 货位表单。 */
export interface LocationForm {
  warehouseId: number
  areaId: number
  rackId: number
  locationCode: string
  locationName: string
  locationType: string
  capacityVolume?: number | null
  currentStatus: string
  status: number
  sort?: number
  remark?: string
}

/** 批量生成货位请求体（POST /api/locations/batch）。 */
export interface LocationBatchForm {
  warehouseId: number
  areaId: number
  rackId: number
  prefix?: string
  startFloor?: number
  startColumn?: number
  floorCount?: number
  columnCount?: number
  locationType: string
  capacityVolume?: number | null
  skipExists?: boolean
}

/** 批量生成货位结果。 */
export interface LocationBatchResult {
  totalCount: number
  createdCount: number
  skippedCount: number
  firstLocationCode: string
  lastLocationCode: string
}

/** 商品分类（product_category 表，树形 children 嵌套）。 */
export interface CategoryNode {
  id: number
  parentId: number
  categoryCode: string
  categoryName: string
  level: number
  sort?: number
  status: number
  remark?: string | null
  children?: CategoryNode[]
}

/** 商品分类表单。 */
export interface CategoryForm {
  parentId: number
  categoryCode: string
  categoryName: string
  sort?: number
  status?: number
  remark?: string
}

/** 商品（product 表）。 */
export interface Product {
  id: number
  productCode: string
  productName: string
  categoryId: number
  categoryName?: string | null
  brand?: string | null
  unit: string
  spec?: string | null
  weight?: number | null
  volume?: number | null
  /** 0 否 / 1 是 */
  shelfLifeFlag: number
  shelfLifeDays?: number | null
  /** 0 否 / 1 是（默认 1） */
  batchFlag: number
  minStock?: number | null
  maxStock?: number | null
  price?: number | null
  imageUrl?: string | null
  status: number
  remark?: string | null
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
}

/** 商品表单（新增/编辑请求体）。 */
export interface ProductForm {
  productCode: string
  productName: string
  categoryId: number | null
  brand?: string
  unit: string
  spec?: string
  weight?: number | null
  volume?: number | null
  shelfLifeFlag: number
  shelfLifeDays?: number | null
  batchFlag: number
  minStock?: number | null
  maxStock?: number | null
  price?: number | null
  imageUrl?: string
  status: number
  remark?: string
}

/** 供应商（supplier 表）。 */
export interface Supplier {
  id: number
  supplierCode: string
  supplierName: string
  contactName?: string | null
  contactPhone?: string | null
  email?: string | null
  address?: string | null
  bankInfo?: string | null
  status: number
  remark?: string | null
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
}

/** 供应商表单。 */
export interface SupplierForm {
  supplierCode: string
  supplierName: string
  contactName?: string
  contactPhone?: string
  email?: string
  address?: string
  bankInfo?: string
  status: number
  remark?: string
}

/** 客户（customer 表，无 bankInfo）。 */
export interface Customer {
  id: number
  customerCode: string
  customerName: string
  contactName?: string | null
  contactPhone?: string | null
  email?: string | null
  address?: string | null
  status: number
  remark?: string | null
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
}

/** 客户表单。 */
export interface CustomerForm {
  customerCode: string
  customerName: string
  contactName?: string
  contactPhone?: string
  email?: string
  address?: string
  status: number
  remark?: string
}
