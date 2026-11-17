import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { AlertRule, AlertRuleForm, AlertScanResult } from '@/types/entities/inventory'

/** 预警记录（API-ALR-006）。 */
export interface AlertRecord {
  id: number
  ruleId: number | null
  ruleName: string | null
  alertType: string
  alertTypeDesc: string
  warehouseId: number
  warehouseName: string
  productId: number
  productCode: string
  productName: string
  batchId: number | null
  currentValue: number
  thresholdValue: number
  level: string
  status: string
  handleRemark: string | null
  handleUser: string | null
  handleTime: string | null
  createTime: string
}

/** API-ALR-006 预警记录分页查询。 */
export function getAlerts(params?: {
  pageNum?: number
  pageSize?: number
  alertType?: string
  status?: string
  warehouseId?: number
  level?: string
  productId?: number
  startDate?: string
  endDate?: string
}): Promise<PageResult<AlertRecord>> {
  return request.get('/alerts', { params })
}

/** API-ALR-007 处理预警（置为已处理）。 */
export function handleAlert(id: number, data?: { handleRemark?: string }): Promise<void> {
  return request.put(`/alerts/${id}/handle`, data ?? {})
}

/* ---------------- 预警规则（API-ALR-001~005） ---------------- */

/** 预警规则分页查询参数（API-ALR-001 Query）。 */
export interface AlertRuleQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  warehouseId?: number
  alertType?: string
  status?: number
}

/** API-ALR-001 预警规则分页查询。 */
export function getAlertRules(params?: AlertRuleQuery): Promise<PageResult<AlertRule>> {
  return request.get('/alert-rules', { params })
}

/** API-ALR-005 预警规则详情。 */
export function getAlertRule(id: number): Promise<AlertRule> {
  return request.get(`/alert-rules/${id}`)
}

/** API-ALR-002 创建预警规则。 */
export function createAlertRule(data: AlertRuleForm): Promise<{ id: number }> {
  return request.post('/alert-rules', data)
}

/** API-ALR-003 编辑预警规则（字段校验同创建）。 */
export function updateAlertRule(id: number, data: AlertRuleForm): Promise<{ id: number }> {
  return request.put(`/alert-rules/${id}`, data)
}

/** API-ALR-004 删除预警规则（逻辑删除，已生成记录保留）。 */
export function deleteAlertRule(id: number): Promise<boolean> {
  return request.delete(`/alert-rules/${id}`)
}

/** API-ALR-008 手动触发预警扫描（立即按启用规则生成预警记录）。 */
export function scanAlerts(data?: { warehouseId?: number; ruleIds?: number[] }): Promise<AlertScanResult> {
  return request.post('/alerts/scan', data ?? {})
}
