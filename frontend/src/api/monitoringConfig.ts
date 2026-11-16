import type { AxiosResponse } from 'axios'
import request from '@/utils/request'
import type { RouterResponse } from '@/types'

/**
 * 监控配置与运行时状态 API 封装
 *
 * 对应后端 `MonitoringController`（`/api/monitoring`）：
 * - GET  /config                          读取当前配置（enabled/prefix/collectionInterval/categories/sampling/performance）
 * - PUT  /config/enabled                  启停监控（热改）
 * - PUT  /config/prefix                   指标前缀（热改）
 * - PUT  /config/collection-interval      采集间隔（ISO-8601 Duration，如 PT10S）
 * - PUT  /config/categories               采集类别（system/business/infrastructure）
 * - PUT  /config/sampling                 采样率（6 项，热改）
 * - PUT  /config/performance              采集性能（异步/批大小/缓冲区，热改）
 * - GET  /config/snapshot                 配置快照（生效值）
 * - GET  /health、/errors/stats、/degradation/status、/cache/stats、
 *        /metrics-pipeline/circuit-breaker/stats、/circuit-breaker/stats
 * - POST /errors/reset、/degradation/{level,auto-mode,force-recovery}、/cache/clear、
 *        /metrics-pipeline/circuit-breaker/{force-open,force-close}、/circuit-breaker/{force-open,force-close}
 *
 * 这些写端点只改内存（`DynamicMonitoringConfigUpdater`），重启后回到配置文件，
 * 页面必须把「重启后回落」写清楚。
 */

/** 采样率（0~1，按指标类型分别配置） */
export interface MonitoringSampling {
  requestMetrics: number
  backendMetrics: number
  infrastructureMetrics: number
  traceMetrics: number
  traceProcessingMetrics: number
  traceAnalysisMetrics: number
}

/** 采集性能 */
export interface MonitoringPerformance {
  asyncProcessing: boolean
  batchSize: number
  bufferSize: number
}

/** 监控配置（GET /config 的响应体） */
export interface MonitoringConfig {
  enabled: boolean
  prefix: string
  /** ISO-8601 Duration 文本，例如 PT10S */
  collectionInterval: string
  enabledCategories: string[]
  customTags: Record<string, string>
  sampling: MonitoringSampling
  performance: MonitoringPerformance
}

/** 采样率热改请求：null / 省略 = 不改该项 */
export type MonitoringSamplingPayload = Partial<MonitoringSampling>

/** 采集性能热改请求：null / 省略 = 不改该项 */
export type MonitoringPerformancePayload = Partial<MonitoringPerformance>

/** 健康检查结果 */
export interface MonitoringHealth {
  status: string
  healthy: boolean
  details?: Record<string, unknown>
  issues?: string[]
}

/** 错误统计 */
export interface MonitoringErrorStats {
  [key: string]: unknown
}

/** 降级状态 */
export interface MonitoringDegradationStatus {
  [key: string]: unknown
}

/** 缓存统计 */
export interface MonitoringCacheStats {
  [key: string]: unknown
}

/** 熔断统计 */
export interface MonitoringCircuitBreakerStats {
  [key: string]: unknown
}

const unwrap = <T>(response: AxiosResponse<RouterResponse<T>>): T | null => response.data?.data ?? null

export const getMonitoringConfig = async (): Promise<MonitoringConfig | null> => {
  const response = await request.get<RouterResponse<MonitoringConfig>>('/monitoring/config')
  return unwrap(response)
}

export const updateMonitoringEnabled = async (enabled: boolean): Promise<void> => {
  await request.put<RouterResponse<unknown>>('/monitoring/config/enabled', { enabled })
}

export const updateMonitoringPrefix = async (prefix: string): Promise<void> => {
  await request.put<RouterResponse<unknown>>('/monitoring/config/prefix', { prefix })
}

export const updateMonitoringCollectionInterval = async (interval: string): Promise<void> => {
  await request.put<RouterResponse<unknown>>('/monitoring/config/collection-interval', { interval })
}

export const updateMonitoringCategories = async (categories: string[]): Promise<void> => {
  await request.put<RouterResponse<unknown>>('/monitoring/config/categories', { categories })
}

export const updateMonitoringSampling = async (payload: MonitoringSamplingPayload): Promise<void> => {
  await request.put<RouterResponse<unknown>>('/monitoring/config/sampling', payload)
}

export const updateMonitoringPerformance = async (
  payload: MonitoringPerformancePayload
): Promise<void> => {
  await request.put<RouterResponse<unknown>>('/monitoring/config/performance', payload)
}

export const getMonitoringConfigSnapshot = async (): Promise<MonitoringConfig | null> => {
  const response = await request.get<RouterResponse<MonitoringConfig>>('/monitoring/config/snapshot')
  return unwrap(response)
}

export const getMonitoringHealth = async (): Promise<MonitoringHealth | null> => {
  const response = await request.get<RouterResponse<MonitoringHealth>>('/monitoring/health')
  return unwrap(response)
}

export const getMonitoringErrorStats = async (): Promise<MonitoringErrorStats | null> => {
  const response = await request.get<RouterResponse<MonitoringErrorStats>>('/monitoring/errors/stats')
  return unwrap(response)
}

export const resetMonitoringErrors = async (component: string, operation: string): Promise<void> => {
  await request.post<RouterResponse<unknown>>('/monitoring/errors/reset', { component, operation })
}

export const getMonitoringDegradationStatus =
  async (): Promise<MonitoringDegradationStatus | null> => {
    const response = await request.get<RouterResponse<MonitoringDegradationStatus>>(
      '/monitoring/degradation/status'
    )
    return unwrap(response)
  }

export const setMonitoringDegradationLevel = async (level: string): Promise<void> => {
  await request.post<RouterResponse<unknown>>('/monitoring/degradation/level', { level })
}

export const setMonitoringDegradationAutoMode = async (enabled: boolean): Promise<void> => {
  await request.post<RouterResponse<unknown>>('/monitoring/degradation/auto-mode', { enabled })
}

export const forceMonitoringRecovery = async (): Promise<void> => {
  await request.post<RouterResponse<unknown>>('/monitoring/degradation/force-recovery')
}

export const getMonitoringCacheStats = async (): Promise<MonitoringCacheStats | null> => {
  const response = await request.get<RouterResponse<MonitoringCacheStats>>('/monitoring/cache/stats')
  return unwrap(response)
}

export const clearMonitoringCache = async (): Promise<void> => {
  await request.post<RouterResponse<unknown>>('/monitoring/cache/clear')
}

export const getMonitoringPipelineBreakerStats =
  async (): Promise<MonitoringCircuitBreakerStats | null> => {
    const response = await request.get<RouterResponse<MonitoringCircuitBreakerStats>>(
      '/monitoring/metrics-pipeline/circuit-breaker/stats'
    )
    return unwrap(response)
  }

export const forceOpenPipelineBreaker = async (): Promise<void> => {
  await request.post<RouterResponse<unknown>>('/monitoring/metrics-pipeline/circuit-breaker/force-open')
}

export const forceClosePipelineBreaker = async (): Promise<void> => {
  await request.post<RouterResponse<unknown>>(
    '/monitoring/metrics-pipeline/circuit-breaker/force-close'
  )
}

export const getMonitoringBreakerStats = async (): Promise<MonitoringCircuitBreakerStats | null> => {
  const response = await request.get<RouterResponse<MonitoringCircuitBreakerStats>>(
    '/monitoring/circuit-breaker/stats'
  )
  return unwrap(response)
}

export const forceOpenMonitoringBreaker = async (): Promise<void> => {
  await request.post<RouterResponse<unknown>>('/monitoring/circuit-breaker/force-open')
}

export const forceCloseMonitoringBreaker = async (): Promise<void> => {
  await request.post<RouterResponse<unknown>>('/monitoring/circuit-breaker/force-close')
}

/**
 * 把后端返回的 ISO-8601 Duration（如 `PT10S`、`PT1M30S`）转成秒。
 *
 * <p>后端 `GET /config` 的 `collectionInterval` 是 `Duration.toString()` 的结果，
 * 而写入端点用 `Duration.parse`，两边都是 ISO-8601；页面用秒展示更直观，
 * 因此在这里做一次解析/拼装（决策 D5：契约不变，前端转换）。
 */
export const parseDurationSeconds = (raw: string): number | null => {
  const match = /^PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?$/.exec(
    raw?.trim() ?? ''
  )
  if (!match || (match[1] === undefined && match[2] === undefined && match[3] === undefined)) {
    return null
  }
  const hours = match[1] ? Number(match[1]) : 0
  const minutes = match[2] ? Number(match[2]) : 0
  const seconds = match[3] ? Number(match[3]) : 0
  return hours * 3600 + minutes * 60 + seconds
}

/** 秒 → ISO-8601 Duration（`PT{n}S`），与后端 `Duration.parse` 对应。 */
export const formatDurationSeconds = (seconds: number): string => `PT${seconds}S`
