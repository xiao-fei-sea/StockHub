import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { Customer, CustomerForm } from '@/types/entity'

/** 客户分页查询参数（API-CUS-001）。 */
export interface CustomerQuery {
  pageNum?: number
  pageSize?: number
  /** 匹配 customerCode / customerName / contactName */
  keyword?: string
  /** 0 停用、1 正常 */
  status?: number
}

/** API-CUS-001 客户分页查询。 */
export function getCustomers(params?: CustomerQuery): Promise<PageResult<Customer>> {
  return request.get('/customers', { params })
}

/** API-CUS-002 新增客户，返回新客户 id。 */
export function createCustomer(data: CustomerForm): Promise<number> {
  return request.post('/customers', data)
}

/** API-CUS-003 修改客户。 */
export function updateCustomer(id: number, data: CustomerForm): Promise<void> {
  return request.put(`/customers/${id}`, data)
}

/** API-CUS-004 删除客户（被出库单引用时后端返回 40901 拦截）。 */
export function deleteCustomer(id: number): Promise<void> {
  return request.delete(`/customers/${id}`)
}

/** API-CUS-005 客户下拉选项。 */
export function getCustomerOptions(keyword?: string): Promise<Pick<Customer, 'id' | 'customerCode' | 'customerName'>[]> {
  return request.get('/customers/options', { params: { keyword } })
}
