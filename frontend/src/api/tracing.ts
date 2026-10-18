import request from '@/utils/request'
import type {
    RouterResponse,
    LatencyAnalysis,
    ErrorAnalysis,
    ThroughputAnalysis,
    TimeRange
} from '@/types'
import type {
    TraceChainNode,
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
    limit?: number
}

export const getTraceChain = (traceId: string) => {
    return request.get<RouterResponse<TraceChainNode[]>>(`/tracing/query/trace/${traceId}`)
}

export const searchTraces = (params: TraceSearchParams) => {
    // 新格式 `{ traces, total, totalPages }`，旧格式裸数组 —— 页面两种都兼容
    return request.get<RouterResponse<TraceSearchResult | TraceChainNode[]>>('/tracing/query/search', { params })
}

export const getRecentTraces = (limit: number = 50) => {
    return request.get<RouterResponse<TraceChainNode[]>>('/tracing/query/recent', { params: { limit } })
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

export const getThroughputAnalysis = (timeRange?: TimeRange) => {
    return request.get<RouterResponse<ThroughputAnalysis>>('/tracing/query/performance/throughput', {
        params: timeRange
    })
}
