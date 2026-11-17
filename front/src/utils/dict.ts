import type { TagProps } from 'element-plus'
import type { OrderStatus } from '@/types/enum'

/**
 * 全局展示字典与 Tag 配色映射。
 * 约束（系统UI设计规范 §7）：任何页面不得自定义状态配色，统一经本模块取值。
 */

export type TagType = TagProps['type']

export interface DictOption {
  value: number | string
  label: string
  type?: TagType
}

/** 单据状态 0-5（全局唯一配色来源）。 */
export const ORDER_STATUS: DictOption[] = [
  { value: 0, label: '草稿', type: 'info' },
  { value: 1, label: '待审核', type: 'warning' },
  { value: 2, label: '已审核', type: 'primary' },
  { value: 3, label: '已完成', type: 'success' },
  { value: 4, label: '已驳回', type: 'danger' },
  { value: 5, label: '已取消', type: 'info' },
]

export function orderStatusInfo(status: OrderStatus | number | null | undefined): DictOption {
  return ORDER_STATUS.find((s) => s.value === status) ?? { value: status ?? '', label: '—', type: 'info' }
}

/** 启用/停用状态。 */
export const ENABLE_STATUS: DictOption[] = [
  { value: 1, label: '启用', type: 'success' },
  { value: 0, label: '停用', type: 'info' },
]

export function enableStatusInfo(status: number | null | undefined): DictOption {
  return ENABLE_STATUS.find((s) => s.value === status) ?? { value: status ?? '', label: '—', type: 'info' }
}

/** 货位状态（currentStatus 存储中文枚举：空闲/占用/锁定/禁用）。 */
export const LOCATION_STATUS: DictOption[] = [
  { value: '空闲', label: '空闲', type: 'info' },
  { value: '占用', label: '占用', type: 'primary' },
  { value: '锁定', label: '锁定', type: 'warning' },
  { value: '禁用', label: '禁用', type: 'danger' },
]

/** 货位类型（存储位/拣货位/暂存位/收货位）。 */
export const LOCATION_TYPE: DictOption[] = [
  { value: '存储位', label: '存储位' },
  { value: '拣货位', label: '拣货位' },
  { value: '暂存位', label: '暂存位' },
  { value: '收货位', label: '收货位' },
]

/** 预警级别（level 存储中文枚举：提示/警告/严重）。 */
export const ALERT_LEVEL: DictOption[] = [
  { value: '提示', label: '提示', type: 'info' },
  { value: '警告', label: '警告', type: 'warning' },
  { value: '严重', label: '严重', type: 'danger' },
]

/** 预警类型（LOW_STOCK/HIGH_STOCK/NEAR_EXPIRY/EXPIRED）。 */
export const ALERT_TYPE: DictOption[] = [
  { value: 'LOW_STOCK', label: '库存低于下限', type: 'info' },
  { value: 'HIGH_STOCK', label: '库存高于上限', type: 'warning' },
  { value: 'NEAR_EXPIRY', label: '临近保质期', type: 'warning' },
  { value: 'EXPIRED', label: '已过期', type: 'danger' },
]

export function alertTypeInfo(type: string | null | undefined): DictOption {
  return ALERT_TYPE.find((t) => t.value === type) ?? { value: type ?? '', label: '—', type: 'info' }
}

/** 调拨单目标仓确认状态（confirmStatus 存储中文枚举）。 */
export const TRANSFER_CONFIRM_STATUS: DictOption[] = [
  { value: '未确认', label: '未确认', type: 'info' },
  { value: '已确认', label: '已确认', type: 'success' },
]

/** 入库单类型（orderType 存储中文枚举：采购入库/调拨入库/退货入库/盘盈入库）。 */
export const INBOUND_TYPE: DictOption[] = [
  { value: '采购入库', label: '采购入库' },
  { value: '调拨入库', label: '调拨入库' },
  { value: '退货入库', label: '退货入库' },
  { value: '盘盈入库', label: '盘盈入库' },
]

/** 出库单类型（orderType 存储中文枚举：销售出库/调拨出库/退货出库/报损出库）。 */
export const OUTBOUND_TYPE: DictOption[] = [
  { value: '销售出库', label: '销售出库' },
  { value: '调拨出库', label: '调拨出库' },
  { value: '退货出库', label: '退货出库' },
  { value: '报损出库', label: '报损出库' },
]

/** 库存流水类型（flowType 存储中文枚举）。 */
export const FLOW_TYPE: DictOption[] = [
  { value: '入库', label: '入库' },
  { value: '出库', label: '出库' },
  { value: '调拨入', label: '调拨入' },
  { value: '调拨出', label: '调拨出' },
  { value: '盘盈', label: '盘盈' },
  { value: '盘亏', label: '盘亏' },
  { value: '调整', label: '调整' },
]

/** 库存流水方向。 */
export const FLOW_DIRECTION: DictOption[] = [
  { value: 'IN', label: '入库', type: 'success' },
  { value: 'OUT', label: '出库', type: 'danger' },
]

/** 按值取标签文案（找不到时返回 —）。 */
export function dictLabel(options: DictOption[], value: number | string | null | undefined): string {
  return options.find((o) => o.value === value)?.label ?? '—'
}
