import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { OrderActionBody, TransferDetail, TransferForm, TransferOrder } from '@/types/entities/inventory'

/** 调拨单分页查询参数（API-TRF-001 Query）。 */
export interface TransferQuery {
  pageNum?: number
  pageSize?: number
  status?: number
  srcWarehouseId?: number
  destWarehouseId?: number
  transferNo?: string
  keyword?: string
  startDate?: string
  endDate?: string
}

/** API-TRF-001 调拨单分页查询。 */
export function getTransfers(params?: TransferQuery): Promise<PageResult<TransferOrder>> {
  return request.get('/transfers', { params })
}

/** API-TRF-002 创建调拨单（草稿）。 */
export function createTransfer(data: TransferForm): Promise<{ id: number }> {
  return request.post('/transfers', data)
}

/** API-TRF-003 调拨单详情（主表 + 明细）。 */
export function getTransfer(id: number): Promise<TransferDetail> {
  return request.get(`/transfers/${id}`)
}

/** API-TRF-004 编辑调拨单（仅 0 草稿 / 4 已驳回）。 */
export function updateTransfer(id: number, data: TransferForm): Promise<{ id: number }> {
  return request.put(`/transfers/${id}`, data)
}

/** API-TRF-005 删除调拨单（仅草稿）。 */
export function deleteTransfer(id: number, version?: number): Promise<boolean> {
  return request.delete(`/transfers/${id}`, { params: version !== undefined ? { version } : undefined })
}

/** API-TRF-006 提交待审核（0 → 1）。 */
export function submitTransfer(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/transfers/${id}/submit`, data ?? {})
}

/** API-TRF-007 审核通过（1 → 2，不改库存）。 */
export function auditTransfer(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/transfers/${id}/audit`, data ?? {})
}

/** API-TRF-008 驳回（1 → 4，reason 必填）。 */
export function rejectTransfer(id: number, data: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/transfers/${id}/reject`, data)
}

/** API-TRF-009 过账（2 → 3，源仓减 + 目标仓增，同事务写两条流水）。 */
export function postTransfer(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/transfers/${id}/post`, data ?? {})
}

/** API-TRF-010 取消（0/1/4 → 5，终态）。 */
export function cancelTransfer(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/transfers/${id}/cancel`, data ?? {})
}

/** API-TRF-011 撤回至草稿（1/4 → 0）。 */
export function withdrawTransfer(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/transfers/${id}/withdraw`, data ?? {})
}
