/** 统一展示格式化工具（数量/金额/日期/空值），全部页面复用，禁止页面内自行实现。 */

type DateInput = string | number | Date | null | undefined

function toDate(value: DateInput): Date | null {
  if (value === null || value === undefined || value === '') return null
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value
  if (typeof value === 'number') return new Date(value)
  // 'yyyy-MM-dd HH:mm:ss' 兼容 iOS 的 new Date
  const normalized = value.replace(/-/g, '/').replace('T', ' ')
  const d = new Date(normalized)
  return Number.isNaN(d.getTime()) ? null : d
}

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n)
}

/** 纯日期 yyyy-MM-dd。 */
export function formatDate(value: DateInput): string {
  const d = toDate(value)
  if (!d) return ''
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 日期时间 yyyy-MM-dd HH:mm:ss。 */
export function formatDateTime(value: DateInput): string {
  const d = toDate(value)
  if (!d) return ''
  return `${formatDate(d)} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 当前日期 yyyy-MM-dd。 */
export function today(): string {
  return formatDate(new Date())
}

/** 相对今天偏移天数的日期 yyyy-MM-dd。 */
export function dayOffset(offset: number): string {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return formatDate(d)
}

/**
 * 数量展示：最多 3 位小数，尾随 0 裁剪、最短保留 1 位（10.500 → 10.5，10.000 → 10.0）。
 */
export function formatQty(value: number | null | undefined, keepZero = true): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  const fixed = Number(value).toFixed(3)
  let [int, dec] = fixed.split('.')
  dec = dec.replace(/(\d{2})0$/, '$1').replace(/(\d)0$/, '$1')
  if (!keepZero) dec = dec.replace(/0$/, '')
  return `${int}.${dec}`
}

/** 金额展示：2 位小数 + 千分位（1,234.56）。 */
export function formatAmount(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  const [int, dec] = Number(value)
    .toFixed(2)
    .split('.')
  const grouped = int.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return `${grouped}.${dec}`
}

/** 千分位整数/一般数值。 */
export function formatNumber(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '—'
  return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** 空值统一展示为 —（em dash）。 */
export function emptyDash(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'string' && value.trim() === '') return '—'
  return String(value)
}
