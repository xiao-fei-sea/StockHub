import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { AreaOption } from '@/types/entity'
import type {
  OrderActionBody,
  StocktakeDetail,
  StocktakeForm,
  StocktakeInputForm,
  StocktakeOrder,
} from '@/types/entities/inventory'

/** 盘点单分页查询参数（API-STK-001 Query）。 */
export interface StocktakeQuery {
  pageNum?: number
  pageSize?: number
  status?: number
  warehouseId?: number
  areaId?: number
  stocktakeNo?: string
  keyword?: string
  diffStatus?: string
  startDate?: string
  endDate?: string
}

/** API-STK-001 盘点单分页查询。 */
export function getStocktakes(params?: StocktakeQuery): Promise<PageResult<StocktakeOrder>> {
  return request.get('/stocktakes', { params })
}

/** API-STK-002 创建盘点单（服务端按盘点范围自动生成账面明细）。 */
export function createStocktake(
  data: StocktakeForm,
): Promise<{ id: number; stocktakeNo: string; itemCount?: number }> {
  return request.post('/stocktakes', data)
}

/** API-STK-003 盘点单详情（主表 + 含账面数/实盘数/差异数明细）。 */
export function getStocktake(id: number): Promise<StocktakeDetail> {
  return request.get(`/stocktakes/${id}`)
}

/** API-STK-004 录入/更新实盘数（按明细 id 匹配，仅 0 草稿 / 4 已驳回）。 */
export function updateStocktake(id: number, data: StocktakeInputForm): Promise<{ status: number }> {
  return request.put(`/stocktakes/${id}`, data)
}

/** API-STK-005 删除盘点单（仅草稿）。 */
export function deleteStocktake(id: number, version?: number): Promise<boolean> {
  return request.delete(`/stocktakes/${id}`, { params: version !== undefined ? { version } : undefined })
}

/** API-STK-006 提交待审核（0 → 1，要求实盘数录入完整）。 */
export function submitStocktake(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/stocktakes/${id}/submit`, data ?? {})
}

/** API-STK-007 审核通过（1 → 2，盘点差异授权，不改库存）。 */
export function auditStocktake(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/stocktakes/${id}/audit`, data ?? {})
}

/** API-STK-008 驳回（1 → 4，reason 必填）。 */
export function rejectStocktake(id: number, data: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/stocktakes/${id}/reject`, data)
}

/** API-STK-009 过账（2 → 3，按差异盘盈增/盘亏减并写流水）。 */
export function postStocktake(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/stocktakes/${id}/post`, data ?? {})
}

/** API-STK-010 取消（0/1/4 → 5，终态）。 */
export function cancelStocktake(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/stocktakes/${id}/cancel`, data ?? {})
}

/** API-STK-011 撤回至草稿（1/4 → 0）。 */
export function withdrawStocktake(id: number, data?: OrderActionBody): Promise<{ status: number }> {
  return request.put(`/stocktakes/${id}/withdraw`, data ?? {})
}

/** 库区下拉选项（GET /api/areas/options?warehouseId=，盘点「按库区」范围数据源）。 */
export function getAreaOptions(params: { warehouseId: number }): Promise<AreaOption[]> {
  return request.get('/areas/options', { params })
}
