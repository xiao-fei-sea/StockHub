import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { OperLog } from '@/types/entity'

/** 操作日志分页查询参数（API-OPL-001，全部可选）。 */
export interface LogQuery {
  pageNum?: number
  pageSize?: number
  /** 业务模块名称，模糊匹配 */
  module?: string
  /** 操作标题，模糊匹配 */
  title?: string
  /** 操作用户名，模糊匹配 */
  username?: string
  /** 执行结果：0 失败 / 1 成功 */
  status?: number
  /** HTTP 方法：GET / POST / PUT / DELETE */
  requestMethod?: string
  /** 关键字（匹配标题、请求路径、用户名） */
  keyword?: string
  /** 操作开始日期 yyyy-MM-dd（含 00:00:00） */
  startDate?: string
  /** 操作结束日期 yyyy-MM-dd（含 23:59:59） */
  endDate?: string
}

/** API-OPL-001 操作日志分页查询（只读）。 */
export function getLogs(params?: LogQuery): Promise<PageResult<OperLog>> {
  return request.get('/logs', { params })
}

/** API-OPL-002 操作日志详情（含完整脱敏参数、响应结果与错误摘要）。 */
export function getLogDetail(id: number): Promise<OperLog> {
  return request.get(`/logs/${id}`)
}
