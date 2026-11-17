/** 单据状态机 0-5（业务文档：草稿→待审核→已审核→已完成；驳回/取消为终态）。 */
export type OrderStatus = 0 | 1 | 2 | 3 | 4 | 5

export const OrderStatusMap: Record<OrderStatus, string> = {
  0: '草稿',
  1: '待审核',
  2: '已审核',
  3: '已完成',
  4: '已驳回',
  5: '已取消',
}

/** 菜单类型：C 目录 / M 菜单 / B 按钮。 */
export type MenuType = 'C' | 'M' | 'B'

/** 预警级别（存储中文枚举）。 */
export type AlertLevel = '提示' | '警告' | '严重'

/** 预警类型。 */
export type AlertType = 'LOW_STOCK' | 'HIGH_STOCK' | 'NEAR_EXPIRY' | 'EXPIRED'

/** 货位状态（存储中文枚举）。 */
export type LocationStatus = '空闲' | '占用' | '锁定' | '禁用'
