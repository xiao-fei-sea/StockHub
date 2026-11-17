import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { OrderActionBody } from '@/types/entities/inventory'
import type { InboundDetail, InboundForm, InboundOrder } from '@/types/entities/order'

/** 入库单分页查询参数（API-INB-001 Query）。 */
export interface InboundQuery {
  pageNum?: number
  pageSize?: number
  status?: number
  warehouseId?: number
  inboundNo?: string
  keyword?: string
  orderType?: string
  startDate?: string
  endDate?: string
}

/** API-INB-001 入库单分页查询。 */
export function getInbounds(params?: InboundQuery): Promise<PageResult<InboundOrder>> {
  return request.get('/inbounds', { params })
}

/** API-INB-002 创建入库单（草稿，单号由服务端生成）。 */
export function createInbound(data: InboundForm): Promise<{ id: number; inboundNo?: string; status?: number; version?: number }> {
  return request.post('/inbounds', data)
}

/** API-INB-003 入库单详情（主表 + 明细）。 */
export function getInbound(id: number): Promise<InboundDetail> {
  return request.get(`/inbounds/${id}`)
}

/** API-INB-004 编辑入库单（仅 0 草稿 / 4 已驳回，明细整体替换）。 */
export function updateInbound(id: number, data: InboundForm): Promise<{ id: number; status?: number; version?: number }> {
  return request.put(`/inbounds/${id}`, data)
}

/** API-INB-005 删除入库单（仅草稿，物理删除）。 */
export function deleteInbound(id: number, version?: number): Promise<boolean> {
  return request.delete(`/inbounds/${id}`, { params: version !== undefined ? { version } : undefined })
}

/** API-INB-006 提交待审核（0 → 1）。 */
export function submitInbound(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/inbounds/${id}/submit`, data ?? {})
}

/** API-INB-007 审核通过（1 → 2，不改库存）。 */
export function auditInbound(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/inbounds/${id}/audit`, data ?? {})
}

/** API-INB-008 驳回（1 → 4，reason 必填）。 */
export function rejectInbound(id: number, data: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/inbounds/${id}/reject`, data)
}

/** API-INB-009 过账（2 → 3，库存增加并写入库流水）。 */
export function postInbound(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/inbounds/${id}/post`, data ?? {})
}

/** API-INB-010 取消（0/1/4 → 5，终态）。 */
export function cancelInbound(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/inbounds/${id}/cancel`, data ?? {})
}

/** API-INB-011 撤回至草稿（1/4 → 0）。 */
export function withdrawInbound(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/inbounds/${id}/withdraw`, data ?? {})
}
