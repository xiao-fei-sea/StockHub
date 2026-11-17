import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { OrderActionBody } from '@/types/entities/inventory'
import type { OutboundDetail, OutboundForm, OutboundOrder } from '@/types/entities/order'

/** 出库单分页查询参数（API-OUT-001 Query）。 */
export interface OutboundQuery {
  pageNum?: number
  pageSize?: number
  status?: number
  warehouseId?: number
  outboundNo?: string
  keyword?: string
  orderType?: string
  startDate?: string
  endDate?: string
}

/** API-OUT-001 出库单分页查询。 */
export function getOutbounds(params?: OutboundQuery): Promise<PageResult<OutboundOrder>> {
  return request.get('/outbounds', { params })
}

/** API-OUT-002 创建出库单（草稿，单号由服务端生成）。 */
export function createOutbound(data: OutboundForm): Promise<{ id: number; outboundNo?: string; status?: number; version?: number }> {
  return request.post('/outbounds', data)
}

/** API-OUT-003 出库单详情（主表 + 明细，含过账回填的批次/货位）。 */
export function getOutbound(id: number): Promise<OutboundDetail> {
  return request.get(`/outbounds/${id}`)
}

/** API-OUT-004 编辑出库单（仅 0 草稿 / 4 已驳回，明细整体替换）。 */
export function updateOutbound(id: number, data: OutboundForm): Promise<{ id: number; status?: number; version?: number }> {
  return request.put(`/outbounds/${id}`, data)
}

/** API-OUT-005 删除出库单（仅草稿，物理删除）。 */
export function deleteOutbound(id: number, version?: number): Promise<boolean> {
  return request.delete(`/outbounds/${id}`, { params: version !== undefined ? { version } : undefined })
}

/** API-OUT-006 提交待审核（0 → 1，服务端做可用库存预检，不足返回 40902）。 */
export function submitOutbound(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/outbounds/${id}/submit`, data ?? {})
}

/** API-OUT-007 审核通过（1 → 2，不改库存）。 */
export function auditOutbound(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/outbounds/${id}/audit`, data ?? {})
}

/** API-OUT-008 驳回（1 → 4，reason 必填）。 */
export function rejectOutbound(id: number, data: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/outbounds/${id}/reject`, data)
}

/** API-OUT-009 过账（2 → 3，FIFO 分配批次并扣减库存；不足返回 40902，由 request 层统一提示）。 */
export function postOutbound(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/outbounds/${id}/post`, data ?? {})
}

/** API-OUT-010 取消（0/1/4 → 5，终态）。 */
export function cancelOutbound(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/outbounds/${id}/cancel`, data ?? {})
}

/** API-OUT-011 撤回至草稿（1/4 → 0）。 */
export function withdrawOutbound(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/outbounds/${id}/withdraw`, data ?? {})
}

/* ---------------- 数据源 ---------------- */

/** 用户下拉选项（API-USR-009，出库单拣货人数据源）。 */
export interface UserOption {
  id: number
  username: string
  realName?: string | null
}

/** GET /api/users/options（keyword 可模糊匹配用户名/姓名）。 */
export function getUserOptions(keyword?: string): Promise<UserOption[]> {
  return request.get('/users/options', { params: keyword ? { keyword } : undefined })
}
