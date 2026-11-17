/** 预警类型下拉：alertType 存英文值，界面显示中文。 */
export const ALERT_TYPE_OPTIONS: { label: string; value: string }[] = [
  { label: '低于下限', value: 'LOW_STOCK' },
  { label: '高于上限', value: 'HIGH_STOCK' },
  { label: '临近保质期', value: 'NEAR_EXPIRY' },
  { label: '已过期', value: 'EXPIRED' },
]

/** 预警记录处理状态（存中文值）。 */
export const ALERT_HANDLE_STATUS_OPTIONS = [
  { label: '未处理', value: '未处理' },
  { label: '已处理', value: '已处理' },
]

export function alertTypeLabel(value?: string | null): string {
  return ALERT_TYPE_OPTIONS.find((o) => o.value === value)?.label ?? value ?? '—'
}

/** 规则阈值条件文案（随 alertType 变化）。 */
export function alertConditionText(rule: {
  alertType: string
  minQty?: number | null
  maxQty?: number | null
  nearDays?: number | null
}): string {
  switch (rule.alertType) {
    case 'LOW_STOCK':
      return rule.minQty != null ? `低于 ${rule.minQty} 触发` : '缺少下限'
    case 'HIGH_STOCK':
      return rule.maxQty != null ? `高于 ${rule.maxQty} 触发` : '缺少上限'
    case 'NEAR_EXPIRY':
      return rule.nearDays != null ? `到期前 ${rule.nearDays} 天触发` : '缺少天数'
    default:
      return '按到期判定'
  }
}
