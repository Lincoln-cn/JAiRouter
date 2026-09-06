import { i18n } from '@/i18n'
import type { AppLocale } from '@/utils/locale'

/**
 * 日期 / 数字格式化工具（v2.10.3 双语版）
 *
 * 各页面原先自写 padStart 拼接或 toLocaleString('zh-CN')，此处统一为应用语言感知：
 * - zh-CN：保持原样（2026-09-06 12:30:45），页面零回归
 * - en-US：月份缩写 + 24 小时制（Sep 6, 2026 12:30:45），数字千分位按 en-US
 *
 * 迁移调用点时把原函数替换为 formatDateTime / formatDate / formatNumber 即可。
 */
function currentLocale(): AppLocale {
  return i18n.global.locale.value === 'en-US' ? 'en-US' : 'zh-CN'
}

function toDate(value: number | string | Date | null | undefined): Date | null {
  if (value === null || value === undefined || value === '') return null
  const date = value instanceof Date ? value : new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

const pad = (n: number) => String(n).padStart(2, '0')

const EN_MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/**
 * 日期时间：zh `YYYY-MM-DD HH:mm:ss`；en `MMM D, YYYY HH:mm:ss`
 * 非法/空值原样返回输入（保持各页既有降级行为），options.dateOnly/timeOnly 只输出对应片段。
 */
export function formatDateTime(
  value: number | string | Date | null | undefined,
  options: { dateOnly?: boolean; timeOnly?: boolean } = {}
): string {
  const date = toDate(value)
  if (!date) return value === null || value === undefined ? '' : String(value)

  const locale = currentLocale()
  const datePart =
    locale === 'zh-CN'
      ? `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
      : `${EN_MONTHS[date.getMonth()]} ${date.getDate()}, ${date.getFullYear()}`
  const timePart = `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`

  if (options.dateOnly) return datePart
  if (options.timeOnly) return timePart
  return `${datePart} ${timePart}`
}

/** 日期（不含时间），如 zh `2026-09-06` / en `Sep 6, 2026` */
export function formatDate(value: number | string | Date | null | undefined): string {
  return formatDateTime(value, { dateOnly: true })
}

/** 时间（不含日期），如 `12:30:45` */
export function formatTime(value: number | string | Date | null | undefined): string {
  return formatDateTime(value, { timeOnly: true })
}

/**
 * 千分位数字（locale 感知，最多保留 digits 位小数，默认 2）
 * 仅对有限数值生效；其它输入原样返回。
 */
export function formatNumber(value: number | string | null | undefined, digits = 2): string {
  if (value === null || value === undefined || value === '') return ''
  const num = typeof value === 'number' ? value : Number(value)
  if (!Number.isFinite(num)) return String(value)
  return new Intl.NumberFormat(currentLocale() === 'zh-CN' ? 'zh-CN' : 'en-US', {
    minimumFractionDigits: 0,
    maximumFractionDigits: digits
  }).format(num)
}

/** 相对时长（秒 → 可读文本）：en/zh 单位本地化，如 `1.5s`、`2m 3s`、`1h 2m` */
export function formatDuration(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || !Number.isFinite(seconds)) return ''
  if (seconds < 1) return `${Math.round(seconds * 1000)} ms`
  const s = Math.floor(seconds)
  if (s < 60) return `${s}s`
  const m = Math.floor(s / 60)
  const rest = s % 60
  if (m < 60) return rest ? `${m}m ${rest}s` : `${m}m`
  const h = Math.floor(m / 60)
  const rm = m % 60
  return rm ? `${h}h ${rm}m` : `${h}h`
}
