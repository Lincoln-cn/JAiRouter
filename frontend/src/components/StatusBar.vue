<template>
  <div v-if="visible" class="status-bar" role="group" :aria-label="t('statusBar.label')">
    <button
      v-if="openBreakers !== null"
      type="button"
      class="status-bar__pill status-bar__pill--breakers"
      :class="{ 'is-danger': openBreakers > 0 }"
      :aria-label="t('statusBar.openBreakersAria', { count: openBreakers })"
      :title="t('statusBar.openBreakersAria', { count: openBreakers })"
      @click="go('/circuit-breakers/monitoring')"
    >
      <el-icon class="status-bar__icon" aria-hidden="true"><SwitchButton /></el-icon>
      <span v-if="!isNarrow" class="status-bar__label">{{ t('statusBar.openBreakers') }}</span>
      <span class="status-bar__value">{{ openBreakers }}</span>
    </button>

    <button
      v-if="exceptions24h !== null"
      type="button"
      class="status-bar__pill status-bar__pill--exceptions"
      :class="{ 'is-warning': exceptions24h > 0 }"
      :aria-label="t('statusBar.exceptions24hAria', { count: exceptions24h })"
      :title="t('statusBar.exceptions24hAria', { count: exceptions24h })"
      @click="go('/exceptions/list')"
    >
      <el-icon class="status-bar__icon" aria-hidden="true"><Warning /></el-icon>
      <span v-if="!isNarrow" class="status-bar__label">{{ t('statusBar.exceptions24h') }}</span>
      <span class="status-bar__value">{{ exceptions24h }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
/**
 * 顶部状态条：开放熔断数 + 24h 异常数。
 *
 * - 轮询 60s；`document.hidden` 时停表并 abort 在途请求，恢复可见立即刷新一次。
 * - 任一接口失败只把对应胶囊置 null（等于隐藏）；两个都是 null 时整个容器不渲染。
 * - 组件卸载清定时器、解绑 visibilitychange、abort 请求，并用 disposed 拦住迟到响应。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { SwitchButton, Warning } from '@element-plus/icons-vue'
import request from '@/utils/request'
import type { RouterResponse } from '@/types'
import { useViewport } from '@/composables/useViewport'

const POLL_INTERVAL_MS = 60_000
const DAY_MS = 24 * 60 * 60 * 1000

interface CircuitBreakerInstanceView {
  state?: string
}

interface ExceptionStatisticsView {
  totalEvents?: number
}

const { t } = useI18n()
const router = useRouter()
const { isNarrow } = useViewport()

const openBreakers = ref<number | null>(null)
const exceptions24h = ref<number | null>(null)

const visible = computed(() => openBreakers.value !== null || exceptions24h.value !== null)

let timer: ReturnType<typeof setInterval> | undefined
let controller: AbortController | undefined
let disposed = false

const debug = (message: string, error: unknown): void => {
  // 状态条是辅助信息：失败整条隐藏即可，不弹错也不进错误日志
  console.debug(`[status-bar] ${message}`, error)
}

/** 后端 statistics 接收 LocalDateTime（ISO date-time，不带时区），因此按本地时间格式化 */
const toLocalIso = (date: Date): string => {
  const pad = (value: number): string => String(value).padStart(2, '0')
  return [
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`,
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  ].join('T')
}

const loadOpenBreakers = async (signal: AbortSignal): Promise<void> => {
  try {
    const response = await request.get<CircuitBreakerInstanceView[]>(
      '/v1/circuit-breaker-monitor/instances',
      { signal }
    )
    if (disposed || signal.aborted) {
      return
    }
    // 该端点直接返回数组（见 CircuitBreakerMonitorController#getInstances）
    const list = Array.isArray(response.data) ? response.data : []
    openBreakers.value = list.filter(
      item => (item?.state ?? '').toUpperCase() === 'OPEN'
    ).length
  } catch (error) {
    if (disposed || signal.aborted) {
      return
    }
    debug('circuit breaker instances failed', error)
    openBreakers.value = null
  }
}

const loadExceptions = async (signal: AbortSignal): Promise<void> => {
  try {
    const end = new Date()
    const start = new Date(end.getTime() - DAY_MS)
    const response = await request.get<RouterResponse<ExceptionStatisticsView>>(
      '/exceptions/statistics',
      { params: { startTime: toLocalIso(start), endTime: toLocalIso(end) }, signal }
    )
    if (disposed || signal.aborted) {
      return
    }
    const total = response.data?.data?.totalEvents
    exceptions24h.value = typeof total === 'number' ? total : null
  } catch (error) {
    if (disposed || signal.aborted) {
      return
    }
    debug('exception statistics failed', error)
    exceptions24h.value = null
  }
}

/** 与 request 拦截器同源的登录态判断：没有 token 时不发请求，避免把 401 变成登录跳转 */
const hasSessionToken = (): boolean => {
  try {
    return Boolean(window.localStorage.getItem('admin_token'))
  } catch {
    return false
  }
}

const load = async (): Promise<void> => {
  controller?.abort()
  if (!hasSessionToken()) {
    openBreakers.value = null
    exceptions24h.value = null
    return
  }
  const current = new AbortController()
  controller = current
  await Promise.allSettled([loadOpenBreakers(current.signal), loadExceptions(current.signal)])
}

const stopTimer = (): void => {
  if (timer) {
    clearInterval(timer)
    timer = undefined
  }
}

const startTimer = (): void => {
  stopTimer()
  timer = setInterval(() => {
    if (!document.hidden) {
      void load()
    }
  }, POLL_INTERVAL_MS)
}

const onVisibilityChange = (): void => {
  if (document.hidden) {
    stopTimer()
    controller?.abort()
    return
  }
  startTimer()
  void load()
}

const go = (path: string): void => {
  void router.push(path)
}

onMounted(() => {
  void load()
  startTimer()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onBeforeUnmount(() => {
  disposed = true
  stopTimer()
  controller?.abort()
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<style scoped>
.status-bar {
  display: flex;
  gap: var(--ja-space-2);
  align-items: center;
}

.status-bar__pill {
  display: inline-flex;
  gap: var(--ja-space-1);
  align-items: center;
  padding: 2px var(--ja-space-2);
  font: inherit;
  font-size: 12px;
  line-height: 20px;
  color: var(--ja-ink-2);
  white-space: nowrap;
  cursor: pointer;
  background: var(--ja-surface-2);
  border: 1px solid var(--ja-border);
  border-radius: 999px;
}

.status-bar__pill:hover {
  background: var(--ja-surface);
}

.status-bar__icon {
  font-size: 13px;
}

.status-bar__value {
  font-variant-numeric: tabular-nums;
}

.status-bar__pill.is-danger .status-bar__value,
.status-bar__pill.is-danger .status-bar__icon {
  color: var(--ja-danger);
}

.status-bar__pill.is-warning .status-bar__value,
.status-bar__pill.is-warning .status-bar__icon {
  color: var(--ja-warning);
}

@media (max-width: 767px) {
  .status-bar__pill {
    padding: 2px 6px;
  }
}
</style>
