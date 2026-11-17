import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { Warehouse, WarehouseForm, WarehouseOption } from '@/types/entity'

/** 仓库分页查询参数（API-WH-001）。 */
export interface WarehouseQuery {
  pageNum?: number
  pageSize?: number
  /** 匹配 warehouseCode / warehouseName / address */
  keyword?: string
  /** 0 停用、1 正常 */
  status?: number
}

/** 仓库下拉选项（API-WH-006，仅返回数据权限内仓库）。 */
export function getWarehouseOptions(): Promise<WarehouseOption[]> {
  return request.get('/warehouses/options')
}

/** API-WH-001 仓库分页查询。 */
export function getWarehouses(params?: WarehouseQuery): Promise<PageResult<Warehouse>> {
  return request.get('/warehouses', { params })
}

/** API-WH-002 新增仓库，返回新仓库 id。 */
export function createWarehouse(data: WarehouseForm): Promise<number> {
  return request.post('/warehouses', data)
}

/** API-WH-003 仓库详情。 */
export function getWarehouseDetail(id: number): Promise<Warehouse> {
  return request.get(`/warehouses/${id}`)
}

/** API-WH-004 修改仓库。 */
export function updateWarehouse(id: number, data: WarehouseForm): Promise<void> {
  return request.put(`/warehouses/${id}`, data)
}

/** API-WH-005 删除仓库（被库区/库存/单据引用时后端返回 40901 拦截）。 */
export function deleteWarehouse(id: number): Promise<void> {
  return request.delete(`/warehouses/${id}`)
}
