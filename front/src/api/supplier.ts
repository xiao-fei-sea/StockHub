import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { Supplier, SupplierForm } from '@/types/entity'

/** 供应商分页查询参数（API-SUP-001）。 */
export interface SupplierQuery {
  pageNum?: number
  pageSize?: number
  /** 匹配 supplierCode / supplierName / contactName */
  keyword?: string
  /** 0 停用、1 正常 */
  status?: number
}

/** API-SUP-001 供应商分页查询。 */
export function getSuppliers(params?: SupplierQuery): Promise<PageResult<Supplier>> {
  return request.get('/suppliers', { params })
}

/** API-SUP-002 新增供应商，返回新供应商 id。 */
export function createSupplier(data: SupplierForm): Promise<number> {
  return request.post('/suppliers', data)
}

/** API-SUP-003 修改供应商。 */
export function updateSupplier(id: number, data: SupplierForm): Promise<void> {
  return request.put(`/suppliers/${id}`, data)
}

/** API-SUP-004 删除供应商（被入库单/库存批次引用时后端返回 40901 拦截）。 */
export function deleteSupplier(id: number): Promise<void> {
  return request.delete(`/suppliers/${id}`)
}

/** API-SUP-005 供应商下拉选项。 */
export function getSupplierOptions(keyword?: string): Promise<Pick<Supplier, 'id' | 'supplierCode' | 'supplierName'>[]> {
  return request.get('/suppliers/options', { params: { keyword } })
}
