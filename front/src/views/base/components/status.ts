/**
 * 基础资料页状态展示辅助：
 * StatusTag 的配色档位取自全局 ORDER_STATUS（0/5=info、1=warning、2=primary、3=success、4=danger），
 * 这里仅借用档位，文案始终来自 dict.ts 的 ENABLE_STATUS / LOCATION_STATUS 字典。
 */

/** 启停状态（0/1）→ StatusTag 展示用档位（3=success 启用、0=info 停用）。 */
export function enableTagStatus(status?: number | null): number {
  return status === 1 ? 3 : 0
}

/** 货位状态（中文枚举）→ StatusTag 展示用档位。 */
export const LOCATION_TAG_STATUS: Record<string, number> = {
  空闲: 0,
  占用: 2,
  锁定: 1,
  禁用: 4,
}

/** 货位状态档位取值（找不到时回退 info）。 */
export function locationTagStatus(status?: string | null): number {
  return status ? (LOCATION_TAG_STATUS[status] ?? 0) : 0
}
