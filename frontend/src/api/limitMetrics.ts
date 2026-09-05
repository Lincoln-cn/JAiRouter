/**
 * 治理链路 API 封装 — Dashboard v2 治理指挥台专用
 *
 * 所有函数在失败时返回降级默认值，不抛异常，保证面板不白屏。
 * @since 2.10.1
 */
import request from '@/utils/request'
import type { RouterResponse } from '@/types'

/* ────────────────────────── 类型定义 ────────────────────────── */

/** 限流器摘要（/rate-limiter/summary 裸响应） */
export interface RateLimiterSummary {
  totalLimiters: number
  globalLimiters: number
  serviceLimiters: number
  instanceLimiters: number
  averageUsageRatio: number
  highUsageLimiters: number
}

/** 单条限流器指标（/rate-limiter/metrics 裸响应数组元素） */
export interface RateLimiterMetricItem {
  service: string
  scope: string
  identifier: string
  algorithm: string
  remainingCapacity: number
  usageRatio: number
  capacity: number
  rate: number
}

/** 熔断器实例状态（/config/instance/circuit-breaker/states 响应 data 元素） */
export interface CircuitBreakerState {
  instanceId: string
  instanceName: string
  serviceType: string
  state: string       // CLOSED | OPEN | HALF_OPEN
  failureCount: number
  successCount: number
}

/** 路由监控状态（/v1/routing-monitor/status 裸响应） */
export interface RoutingMonitorStatus {
  enabled: boolean
  paused: boolean
  sampleRate: number
  historySize: number
  totalSampledCount: number
  pausedServices: string[]
}

/** 单服务路由统计 */
export interface ServiceRoutingStats {
  strategy: string
  totalSelections: number
  requestsPerSecond: number
  instanceCounts: Record<string, number>
  modelInstanceCounts: Record<string, Record<string, number>>
  recentSelections: string[]
  clientInstanceMap: Record<string, string>
}

/* ────────────────────────── 降级默认值 ────────────────────────── */

const DEFAULT_RATE_LIMITER_SUMMARY: RateLimiterSummary = {
  totalLimiters: 0,
  globalLimiters: 0,
  serviceLimiters: 0,
  instanceLimiters: 0,
  averageUsageRatio: 0,
  highUsageLimiters: 0,
}

const DEFAULT_ROUTING_MONITOR_STATUS: RoutingMonitorStatus = {
  enabled: false,
  paused: true,
  sampleRate: 0,
  historySize: 0,
  totalSampledCount: 0,
  pausedServices: [],
}

/* ────────────────────────── 导出函数 ────────────────────────── */

/**
 * 获取限流器摘要 — /rate-limiter/summary
 * 后端直接返回 JSON（无 RouterResponse 包装）
 */
export async function getRateLimiterSummary(): Promise<RateLimiterSummary> {
  try {
    const res = await request.get('/rate-limiter/summary')
    return (res.data as RateLimiterSummary) ?? DEFAULT_RATE_LIMITER_SUMMARY
  } catch {
    return DEFAULT_RATE_LIMITER_SUMMARY
  }
}

/**
 * 获取限流器详细指标列表 — /rate-limiter/metrics
 * 后端直接返回 JSON 数组（无 RouterResponse 包装）
 */
export async function getRateLimiterMetrics(): Promise<RateLimiterMetricItem[]> {
  try {
    const res = await request.get('/rate-limiter/metrics')
    return (res.data as RateLimiterMetricItem[]) ?? []
  } catch {
    return []
  }
}

/**
 * 获取全部熔断器实例状态 — /config/instance/circuit-breaker/states
 * 后端返回 RouterResponse<CircuitBreakerState[]>
 */
export async function getCircuitBreakerStates(): Promise<CircuitBreakerState[]> {
  try {
    const res = await request.get<RouterResponse<CircuitBreakerState[]>>(
      '/config/instance/circuit-breaker/states'
    )
    if (res.data?.success && Array.isArray(res.data.data)) {
      return res.data.data
    }
    return []
  } catch {
    return []
  }
}

/**
 * 获取路由监控状态 — /v1/routing-monitor/status
 * 后端直接返回 JSON（无 RouterResponse 包装）
 */
export async function getRoutingMonitorStatus(): Promise<RoutingMonitorStatus> {
  try {
    const res = await request.get('/v1/routing-monitor/status')
    return (res.data as RoutingMonitorStatus) ?? DEFAULT_ROUTING_MONITOR_STATUS
  } catch {
    return DEFAULT_ROUTING_MONITOR_STATUS
  }
}

/**
 * 获取路由监控统计 — /v1/routing-monitor/stats
 * 后端直接返回 JSON（Record<serviceType, ServiceRoutingStats>）
 */
export async function getRoutingMonitorStats(): Promise<Record<string, ServiceRoutingStats>> {
  try {
    const res = await request.get('/v1/routing-monitor/stats')
    return (res.data as Record<string, ServiceRoutingStats>) ?? {}
  } catch {
    return {}
  }
}
