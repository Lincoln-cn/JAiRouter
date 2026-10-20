<template>
  <span class="time-text" :title="fullText">{{ displayText }}</span>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'

/**
 * 时间文本（设计 §4.5）：
 * - `relative`（默认）：刚刚 / N 分钟前 / N 小时前 / N 天前；超过 30 天回落绝对日期。
 *   单位文案由 `Intl.RelativeTimeFormat('zh-CN')` 产出。
 * - `absolute`：`Intl.DateTimeFormat('zh-CN')` 的日期时间。
 * - 空值（null / undefined / ''）渲染空串，避免出现 `Invalid Date`。
 * - `title` 恒为完整日期时间；挂载后每 30s 刷新一次，卸载时清理定时器。
 */
const props = withDefaults(
  defineProps<{
    value?: string | number | Date | null
    mode?: 'relative' | 'absolute'
    precision?: 'minute' | 'second'
  }>(),
  { value: null, mode: 'relative', precision: 'minute' }
)

const MINUTE = 60_000
const HOUR = 3_600_000
const DAY = 86_400_000
/** 超过 30 天转绝对日期（设计 §4.5）；`刚刚` 是设计明确要求的固定文案 */
const RELATIVE_LIMIT = 30 * DAY
const JUST_NOW = '刚刚'
const REFRESH_INTERVAL = 30_000

const relativeFormatter = new Intl.RelativeTimeFormat('zh-CN', { numeric: 'always' })

const formatAbsolute = (date: Date, withSeconds: boolean): string =>
  new Intl.DateTimeFormat('zh-CN', {
    dateStyle: 'medium',
    timeStyle: withSeconds ? 'medium' : 'short'
  }).format(date)

const fullFormatter = new Intl.DateTimeFormat('zh-CN', {
  dateStyle: 'medium',
  timeStyle: 'medium'
})

const parseValue = (value: string | number | Date | null | undefined): Date | null => {
  if (value === null || value === undefined || value === '') return null
  let date: Date
  if (value instanceof Date) {
    date = value
  } else if (typeof value === 'string' && /^-?\d+$/.test(value.trim())) {
    date = new Date(Number(value))
  } else {
    date = new Date(value)
  }
  return Number.isNaN(date.getTime()) ? null : date
}

const formatRelative = (
  date: Date,
  nowMs: number,
  precision: 'minute' | 'second'
): string => {
  const diff = date.getTime() - nowMs
  const abs = Math.abs(diff)

  if (abs > RELATIVE_LIMIT) {
    return formatAbsolute(date, true)
  }
  if (abs < MINUTE) {
    if (precision === 'second') {
      return relativeFormatter.format(Math.round(diff / 1000), 'second')
    }
    return JUST_NOW
  }
  if (abs < HOUR) {
    return relativeFormatter.format(Math.round(diff / MINUTE), 'minute')
  }
  if (abs < DAY) {
    return relativeFormatter.format(Math.round(diff / HOUR), 'hour')
  }
  return relativeFormatter.format(Math.round(diff / DAY), 'day')
}

const now = ref(Date.now())

const parsed = computed(() => parseValue(props.value))

const fullText = computed(() => (parsed.value ? fullFormatter.format(parsed.value) : ''))

const displayText = computed(() => {
  const date = parsed.value
  if (!date) return ''
  if (props.mode === 'absolute') {
    return formatAbsolute(date, props.precision === 'second')
  }
  return formatRelative(date, now.value, props.precision)
})

let timer: number | null = null

onMounted(() => {
  timer = window.setInterval(() => {
    now.value = Date.now()
  }, REFRESH_INTERVAL)
})

onUnmounted(() => {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
})
</script>

<style scoped>
.time-text {
  color: inherit;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
</style>
