import request from '@/utils/request'
import type {
    RouterResponse,
    LatencyAnalysis,
    ErrorAnalysis,
    ThroughputAnalysis,
    TimeRange,
    TrendPoint
} from '@/types'
import type {
    TraceChainResponse,
    TraceSpan,
    TraceExportRequest,
    TraceSearchResult,
    TracingCleanupResult,
    TracingConfig,
    TracingHealth,
    TracingServiceListResult,
    TracingServiceStat,
    TracingStatistics,
    TracingStatus
} from '@/types/tracing'

// 追踪概览相关接口
export const getTracingOverview = () => {
    return request.get<RouterResponse<TracingStatistics>>('/tracing/query/statistics')
}

export const getTracingStatus = () => {
    return request.get<RouterResponse<TracingStatus>>('/tracing/actuator/status')
}

export const getTracingHealth = () => {
    return request.get<RouterResponse<TracingHealth>>('/tracing/actuator/health')
}

export const getTracingConfig = () => {
    return request.get<RouterResponse<TracingConfig>>('/tracing/actuator/config')
}

export const updateTracingConfig = (config: Partial<TracingConfig>) => {
    return request.put<RouterResponse<TracingConfig>>('/tracing/actuator/config', config)
}

export const getServiceStats = () => {
    // 两种形态都被页面兼容：裸数组（单个服务统计）或 `{ services: string[] }`
    return request.get<RouterResponse<TracingServiceStat[] | TracingServiceListResult>>('/tracing/query/services')
}

export const refreshTracingData = () => {
    return request.post<RouterResponse<void>>('/tracing/actuator/clear-cache')
}

export const refreshSamplingStrategy = () => {
    return request.post<RouterResponse<void>>('/tracing/actuator/sampling/refresh')
}

export const enableTracing = () => {
    return request.post<RouterResponse<void>>('/tracing/actuator/enable')
}

export const disableTracing = () => {
    return request.post<RouterResponse<void>>('/tracing/actuator/disable')
}

// 追踪查询相关接口
/** `GET /tracing/query/search` 的查询条件（页面实际下发的键） */
export interface TraceSearchParams {
    serviceName?: string
    traceId?: string
    minDuration?: number
    maxDuration?: number
    status?: string
    startTime?: string
    endTime?: string
    /** 只看错误链路；未选择时页面传 null */
    hasError?: boolean | null
    /** 页码（从 1 开始，页面直接传 currentPage） */
    page?: number
    size?: number
}

export const getTraceChain = (traceId: string) => {
    return request.get<RouterResponse<TraceChainResponse>>(`/tracing/query/trace/${traceId}`)
}

export const searchTraces = (params: TraceSearchParams) => {
    // 新格式 `{ traces, total, totalPages }`，旧格式裸数组 —— 页面两种都兼容
    return request.get<RouterResponse<TraceSearchResult | TraceSpan[]>>('/tracing/query/search', { params })
}

export interface ArchivedTraceRow {
  traceId: string
  serviceName?: string
  operationName?: string
  startTime: number
  duration?: number
  statusCode?: string
  errorCount?: number
  hasError: boolean
  archived: boolean
}

/**
 * 读取持久化的异常链路归档（#1155）。
 *
 * 只在用户显式勾选「包含已归档」时调用——默认搜索仍然只查内存，
 * 因为归档表可能很大，且其中的链路早已不在运行态。
 */
export const getArchivedRecentTraces = (limit = 50) =>
  request.get('/tracing/query/archived/recent', { params: { limit } })

export const getRecentTraces = (limit: number = 50) => {
    return request.get<RouterResponse<TraceSpan[]>>('/tracing/query/recent', { params: { limit } })
}

export const exportTraces = (exportRequest: TraceExportRequest) => {
    return request.post<RouterResponse<unknown>>('/tracing/query/export', exportRequest)
}

export const cleanupExpiredTraces = (retentionHours: number = 24) => {
    return request.post<RouterResponse<TracingCleanupResult>>('/tracing/query/cleanup', null, {
        params: { retentionHours }
    })
}

export const getLatencyAnalysis = (timeRange?: TimeRange) => {
    return request.get<RouterResponse<LatencyAnalysis>>('/tracing/query/performance/latency', {
        params: timeRange
    })
}

export const getErrorAnalysis = (timeRange?: TimeRange) => {
    return request.get<RouterResponse<ErrorAnalysis>>('/tracing/query/performance/errors', {
        params: timeRange
    })
}

/** 异常排行的一行（按 operation / 上游实例 / 客户端三个维度共用）。 */
export interface ErrorRankRow {
    name: string
    totalCount: number
    errorCount: number
    errorRate: number
}

/** 异常聚合结果：三个维度排行 + 错误时间线 + 筛选项。 */
export interface ErrorAnalysisAggregated {
    totalTraces: number
    errorTraces: number
    errorRate: number
    byOperation: ErrorRankRow[]
    byInstance: ErrorRankRow[]
    byClient: ErrorRankRow[]
    errorTrend: TrendPoint[]
    filterOptions: {
        operations: string[]
        instances: string[]
        clients: string[]
    }
}

export interface ErrorAnalysisParams {
    startTime?: string
    endTime?: string
    operation?: string
    instance?: string
    client?: string
}

export const getErrorAnalysisAggregated = (params?: ErrorAnalysisParams) => {
    return request.get<RouterResponse<ErrorAnalysisAggregated>>('/tracing/query/error-analysis', {
        params
    })
}

export const getThroughputAnalysis = (timeRange?: TimeRange) => {
    return request.get<RouterResponse<ThroughputAnalysis>>('/tracing/query/performance/throughput', {
        params: timeRange
    })
}
