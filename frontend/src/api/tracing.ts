import request from '@/utils/request'
import type {
    RouterResponse,
    LatencyAnalysis,
    ErrorAnalysis,
    ThroughputAnalysis,
    TraceDetails,
    TimeRange
} from '@/types'

// 追踪概览相关接口
export const getTracingOverview = () => {
    return request.get<RouterResponse<any>>('/tracing/query/statistics')
}

export const getTracingStats = () => {
    return request.get<RouterResponse<any>>('/tracing/actuator/stats')
}

export const getTracingStatus = () => {
    return request.get<RouterResponse<any>>('/tracing/actuator/status')
}

export const getTracingHealth = () => {
    return request.get<RouterResponse<any>>('/tracing/actuator/health')
}

export const getTracingConfig = () => {
    return request.get<RouterResponse<any>>('/tracing/actuator/config')
}

export const updateTracingConfig = (config: any) => {
    return request.put<RouterResponse<any>>('/tracing/actuator/config', config)
}

export const getServiceStats = () => {
    return request.get<RouterResponse<any>>('/tracing/query/services')
}

export const refreshTracingData = () => {
    return request.post<RouterResponse<void>>('/tracing/actuator/clear-cache')
}

export const refreshSamplingStrategy = () => {
    return request.post<RouterResponse<any>>('/tracing/actuator/sampling/refresh')
}

export const enableTracing = () => {
    return request.post<RouterResponse<any>>('/tracing/actuator/enable')
}

export const disableTracing = () => {
    return request.post<RouterResponse<any>>('/tracing/actuator/disable')
}

// 追踪查询相关接口
export const getTraceChain = (traceId: string) => {
    return request.get<RouterResponse<any>>(`/tracing/query/trace/${traceId}`)
}

export const searchTraces = (params: any) => {
    return request.get<RouterResponse<any>>('/tracing/query/search', { params })
}

export const getRecentTraces = (limit: number = 50) => {
    return request.get<RouterResponse<any>>('/tracing/query/recent', { params: { limit } })
}

export const exportTraces = (exportRequest: any) => {
    return request.post<RouterResponse<any>>('/tracing/query/export', exportRequest)
}

export const cleanupExpiredTraces = (retentionHours: number = 24) => {
    return request.post<RouterResponse<any>>('/tracing/query/cleanup', null, {
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

export const getTraceDetails = (traceId: string) => {
    return request.get<RouterResponse<TraceDetails>>(`/tracing/query/trace/${traceId}`)
}