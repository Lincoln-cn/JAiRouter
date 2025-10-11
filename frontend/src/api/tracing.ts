import request from '@/utils/request'
import type {
    RouterResponse,
    TracingOverview,
    TracingStats,
    ServiceStats,
    PerformanceStats,
    LatencyAnalysis,
    ErrorAnalysis,
    ThroughputAnalysis,
    TraceDetails,
    SamplingConfig,
    SamplingStats,
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

export const getOperations = (serviceName?: string) => {
    const params = serviceName ? { serviceName } : {}
    return request.get<RouterResponse<any>>('/tracing/query/operations', { params })
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

export const getQueryServiceHealth = () => {
    return request.get<RouterResponse<any>>('/tracing/query/health')
}

// 性能分析相关接口
export const getPerformanceStats = (timeRange?: TimeRange) => {
    return request.get<RouterResponse<PerformanceStats>>('/tracing/performance/stats', {
        params: timeRange
    })
}

export const getProcessingStats = () => {
    return request.get<RouterResponse<any>>('/tracing/performance/processing-stats')
}

export const getMemoryStats = () => {
    return request.get<RouterResponse<any>>('/tracing/performance/memory-stats')
}

export const getPerformanceHealth = () => {
    return request.get<RouterResponse<any>>('/tracing/performance/health')
}

export const detectBottlenecks = () => {
    return request.get<RouterResponse<any>>('/tracing/performance/bottlenecks')
}

export const getOptimizationSuggestions = () => {
    return request.get<RouterResponse<any>>('/tracing/performance/suggestions')
}

export const generatePerformanceReport = () => {
    return request.get<RouterResponse<any>>('/tracing/performance/report')
}

export const triggerOptimization = () => {
    return request.post<RouterResponse<any>>('/tracing/performance/optimize')
}

export const performTuning = (tuningActions: string[]) => {
    return request.post<RouterResponse<any>>('/tracing/performance/tuning', tuningActions)
}

export const triggerGarbageCollection = () => {
    return request.post<RouterResponse<any>>('/tracing/performance/memory/gc')
}

export const performMemoryCheck = () => {
    return request.post<RouterResponse<any>>('/tracing/performance/memory/check')
}

export const flushProcessingBuffer = () => {
    return request.post<RouterResponse<any>>('/tracing/performance/processing/flush')
}

export const getDashboardMetrics = () => {
    return request.get<RouterResponse<any>>('/tracing/performance/metrics/dashboard')
}

export const getActiveAlerts = () => {
    return request.get<RouterResponse<any>>('/tracing/performance/alerts/active')
}

export const getLatencyAnalysis = (timeRange?: TimeRange) => {
    return request.get<RouterResponse<LatencyAnalysis>>('/tracing/performance/latency', {
        params: timeRange
    })
}

export const getErrorAnalysis = (timeRange?: TimeRange) => {
    return request.get<RouterResponse<ErrorAnalysis>>('/tracing/performance/errors', {
        params: timeRange
    })
}

export const getThroughputAnalysis = (timeRange?: TimeRange) => {
    return request.get<RouterResponse<ThroughputAnalysis>>('/tracing/performance/throughput', {
        params: timeRange
    })
}

export const getTraceDetails = (traceId: string) => {
    return request.get<RouterResponse<TraceDetails>>(`/tracing/query/trace/${traceId}`)
}

// 采样配置相关接口
export const getSamplingConfig = () => {
    return request.get<RouterResponse<SamplingConfig>>('/config/tracing/sampling')
}

export const updateSamplingConfig = (config: any, createNewVersion: boolean = true) => {
    return request.put<RouterResponse<void>>('/config/tracing/sampling', config, {
        params: { createNewVersion }
    })
}

export const resetSamplingConfig = (createNewVersion: boolean = true) => {
    return request.post<RouterResponse<void>>('/config/tracing/sampling/reset', null, {
        params: { createNewVersion }
    })
}

export const getSamplingStats = () => {
    return request.get<RouterResponse<SamplingStats>>('/tracing/actuator/stats')
}