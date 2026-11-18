import { computed, ref } from 'vue'
import { getCallHistoryStatistics } from '@/api/callHistory'
import type { CallHistoryStatistics } from '@/types/callHistory'

/**
 * 角色首页共用的「时间范围 + 统计加载」。
 *
 * 只做三件事：把预设区间换成 ISO 时间、拉一次 `/call-history/statistics`、暴露加载/错误态。
 * 服务端已经按调用者权限做数据范围过滤（持本人读码时只看本人的记录），
 * 所以这里不需要也不应该传任何 userId。
 */
export type StatsPreset = 'today' | '7d' | '30d' | '90d'

export const STATS_PRESETS: StatsPreset[] = ['today', '7d', '30d', '90d']

const PRESET_HOURS: Record<StatsPreset, number> = {
  today: 24,
  '7d': 24 * 7,
  '30d': 24 * 30,
  '90d': 24 * 90,
}

/** 后端 `@DateTimeFormat(iso = DATE_TIME)` 接受不带时区的本地时间。 */
const toLocalIso = (date: Date): string => {
  const pad = (value: number): string => String(value).padStart(2, '0')
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  )
}

export function useStatsRange(defaultPreset: StatsPreset = '7d') {
  const preset = ref<StatsPreset>(defaultPreset)
  const loading = ref(false)
  const stats = ref<CallHistoryStatistics | null>(null)
  const failed = ref(false)

  const range = computed(() => {
    const end = new Date()
    const start = new Date(end.getTime() - PRESET_HOURS[preset.value] * 3600 * 1000)
    return { startTime: toLocalIso(start), endTime: toLocalIso(end) }
  })

  /** 加载失败只置标志位，由页面显示一条安静的错误提示；不弹窗打断。 */
  const load = async (): Promise<void> => {
    loading.value = true
    failed.value = false
    try {
      stats.value = await getCallHistoryStatistics(range.value.startTime, range.value.endTime)
    } catch {
      failed.value = true
      stats.value = null
    } finally {
      loading.value = false
    }
  }

  return { preset, presets: STATS_PRESETS, range, loading, failed, stats, load }
}

/** 数字千分位；空值统一显示为 `—`，避免出现 `NaN`。 */
export const formatCount = (value?: number | null): string =>
  typeof value === 'number' && Number.isFinite(value) ? value.toLocaleString() : '—'

/** 百分比保留一位；空值显示为 `—`。 */
export const formatPercent = (value?: number | null): string =>
  typeof value === 'number' && Number.isFinite(value) ? `${value.toFixed(1)}%` : '—'

/** 毫秒取整；空值显示为 `—`。 */
export const formatMs = (value?: number | null): string =>
  typeof value === 'number' && Number.isFinite(value) ? `${Math.round(value)} ms` : '—'
