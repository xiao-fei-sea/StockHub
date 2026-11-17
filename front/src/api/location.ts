import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type {
  Area,
  AreaForm,
  AreaOption,
  Location,
  LocationBatchForm,
  LocationBatchResult,
  LocationForm,
  LocationOption,
  Rack,
  RackForm,
} from '@/types/entity'

/* ---------------- 库区（areas，API-AREA-001~005） ---------------- */

/** 库区分页查询参数（API-AREA-001）。 */
export interface AreaQuery {
  pageNum?: number
  pageSize?: number
  warehouseId?: number
  /** 匹配 areaCode / areaName */
  keyword?: string
  /** 存储区/拣货区/暂存区 */
  areaType?: string
  status?: number
}

/** API-AREA-001 库区分页查询。 */
export function getAreas(params?: AreaQuery): Promise<PageResult<Area>> {
  return request.get('/areas', { params })
}

/** API-AREA-002 新增库区，返回新库区 id。 */
export function createArea(data: AreaForm): Promise<number> {
  return request.post('/areas', data)
}

/** API-AREA-003 修改库区。 */
export function updateArea(id: number, data: AreaForm): Promise<void> {
  return request.put(`/areas/${id}`, data)
}

/** API-AREA-004 删除库区（存在下级货架时后端返回 40901 拦截）。 */
export function deleteArea(id: number): Promise<void> {
  return request.delete(`/areas/${id}`)
}

/** API-AREA-005 库区下拉选项（warehouseId 必填）。 */
export function getAreaOptions(warehouseId: number, keyword?: string): Promise<AreaOption[]> {
  return request.get('/areas/options', { params: { warehouseId, keyword } })
}

/* ---------------- 货架（racks，API-RACK-001~004） ---------------- */

/** 货架分页查询参数（API-RACK-001）。 */
export interface RackQuery {
  pageNum?: number
  pageSize?: number
  warehouseId?: number
  areaId?: number
  /** 匹配 rackCode / rackName */
  keyword?: string
  status?: number
}

/** API-RACK-001 货架分页查询。 */
export function getRacks(params?: RackQuery): Promise<PageResult<Rack>> {
  return request.get('/racks', { params })
}

/** API-RACK-002 新增货架，返回新货架 id。 */
export function createRack(data: RackForm): Promise<number> {
  return request.post('/racks', data)
}

/** API-RACK-003 修改货架。 */
export function updateRack(id: number, data: RackForm): Promise<void> {
  return request.put(`/racks/${id}`, data)
}

/** API-RACK-004 删除货架（存在下级货位时后端返回 40901 拦截）。 */
export function deleteRack(id: number): Promise<void> {
  return request.delete(`/racks/${id}`)
}

/* ---------------- 货位（locations，API-LOC-001~006） ---------------- */

/** 货位分页查询参数（API-LOC-001，层级参数须自洽）。 */
export interface LocationQuery {
  pageNum?: number
  pageSize?: number
  warehouseId?: number
  areaId?: number
  rackId?: number
  /** 启用状态：0 停用、1 正常 */
  status?: number
  /** 货位状态：空闲/占用/锁定/禁用 */
  currentStatus?: string
  /** 存储位/拣货位/暂存位/收货位 */
  locationType?: string
  /** 匹配 locationCode / locationName */
  keyword?: string
}

/** API-LOC-001 货位分页查询。 */
export function getLocations(params?: LocationQuery): Promise<PageResult<Location>> {
  return request.get('/locations', { params })
}

/** API-LOC-002 新增货位，返回新货位 id。 */
export function createLocation(data: LocationForm): Promise<number> {
  return request.post('/locations', data)
}

/** API-LOC-003 修改货位。 */
export function updateLocation(id: number, data: LocationForm): Promise<void> {
  return request.put(`/locations/${id}`, data)
}

/** API-LOC-004 删除货位（存在库存/被单据引用时后端返回 40901 拦截）。 */
export function deleteLocation(id: number): Promise<void> {
  return request.delete(`/locations/${id}`)
}

/** API-LOC-006 批量生成货位（按货架层数×列数）。 */
export function batchGenerateLocations(data: LocationBatchForm): Promise<LocationBatchResult> {
  return request.post('/locations/batch', data)
}

/** 货位下拉选项（API-LOC-005）。 */
export function getLocationOptions(params?: {
  warehouseId?: number
  areaId?: number
  rackId?: number
  locationType?: string
  currentStatus?: string
  keyword?: string
}): Promise<LocationOption[]> {
  return request.get('/locations/options', { params })
}
