// Common type definitions for the admin interface

export interface ApiResponse<T = any> {
  success: boolean
  data?: T
  message?: string
  error?: string
}

export interface RouterResponse<T = any> {
  success: boolean
  message: string
  data?: T
  errorCode?: string
  timestamp: string
}

export interface SystemStatus {
  uptime: number
  version: string
  environment: string
}

export interface DashboardOverview {
  systemStatus: SystemStatus
  serviceStats: {
    totalServices: number
    activeServices: number
    failedServices: number
  }
  requestStats: {
    totalRequests: number
    requestsPerSecond: number
    averageResponseTime: number
    errorRate: number
  }
  resourceUsage: {
    cpuUsage: number
    memoryUsage: number
    diskUsage: number
  }
}

// Config Merge Management Types
export interface VersionFile {
  version: number
  filePath: string
}

export interface Statistics {
  totalVersionFiles?: number
  oldestVersion?: number | null
  newestVersion?: number | null
  previewAvailable?: boolean
}

export interface ServiceStatus {
  availableVersionFiles?: number
  configDirectory?: string
  serviceReady?: boolean
}

export interface MergePreviewData {
  mergedConfig?: Record<string, any>
  mergeStatistics?: {
    sourceVersionCount?: number
    totalServiceTypes?: number
    totalSourceInstances?: number
    mergedInstances?: number
    instanceReduction?: number
    mergedServiceTypes?: number
  }
}

export interface MergeResult {
  conflicts?: string[]
  warnings?: string[]
  success: boolean
  message: string
}

// API Key Management Types
export interface ApiKeyInfo {
  keyId: string
  description: string
  createdAt: string
  expiresAt: string
  enabled: boolean
  permissions: string[]
  expired?: boolean
}

export interface ApiKeyCreationResponse {
  keyId: string
  keyValue: string
  description: string
  createdAt: string
}

// API Key Management Request Types
export interface CreateApiKeyRequest {
  keyId?: string
  description: string
  permissions: string[]
  enabled?: boolean
  expiresAt?: string
}

export interface UpdateApiKeyRequest {
  description?: string
  permissions?: string[]
  enabled?: boolean
  expiresAt?: string
}

// Tracing Management Types
export interface TracingOverview {
  totalTraces: number
  errorTraces: number
  avgDuration: number
  samplingRate: number
  successfulTraces?: number
  totalSpans?: number
  maxDuration?: number
  minDuration?: number
}

export interface TracingStats {
  traceVolumeTrend: TimeSeriesData[]
  errorTrend: TimeSeriesData[]
  processing?: {
    queue_size: number
    processed_count: number
    is_running: boolean
    success_rate: number
    dropped_count: number
  }
  configInfo?: {
    serviceName: string
    exporterType: string
    globalSamplingRatio: number
    enabled: boolean
  }
  memory?: {
    cache_size: number
    pressure_level: string
    cache_hit_ratio: number
    heap_usage_ratio: number
  }
  timestamp?: string
}

export interface ServiceStats {
  name: string
  traces: number
  errors: number
  avgDuration: number
  p95Duration: number
  p99Duration: number
  errorRate: number
}

export interface TimeSeriesData {
  timestamp: string | number
  value: number
}

// Performance Analysis Types
export interface PerformanceStats {
  latencyDistribution: ServiceLatency[]
  latencyTrend: LatencyTrendData
  slowTraces: SlowTrace[]
}

export interface LatencyAnalysis {
  distribution: ServiceLatency[]
  trend: LatencyTrendData
  percentiles: PercentileData
}

export interface ErrorAnalysis {
  errorRateDistribution: ServiceErrorRate[]
  errorTrend: TimeSeriesData[]
  commonErrors: CommonError[]
}

export interface ThroughputAnalysis {
  requestDistribution: ServiceThroughput[]
  qpsTrend: TimeSeriesData[]
}

export interface ServiceLatency {
  service: string
  avgLatency: number
  p95Latency: number
  p99Latency: number
}

export interface LatencyTrendData {
  timestamps: string[]
  avgLatency: number[]
  p95Latency: number[]
  p99Latency: number[]
}

export interface PercentileData {
  p50: number
  p95: number
  p99: number
  p999: number
}

export interface ServiceErrorRate {
  service: string
  errorRate: number
  totalRequests: number
  errorCount: number
}

export interface ServiceThroughput {
  service: string
  requestsPerSecond: number
  totalRequests: number
}

export interface SlowTrace {
  traceId: string
  service: string
  operation: string
  duration: number
  startTime: string
}

export interface CommonError {
  errorType: string
  service: string
  operation: string
  count: number
  lastOccurrence: string
}

export interface TraceDetails {
  traceId: string
  spans: SpanInfo[]
  totalDuration: number
  services: string[]
}

export interface SpanInfo {
  spanId: string
  service: string
  operation: string
  duration: number
  startTime: string
  status: string
}

// Sampling Configuration Types
export interface SamplingConfig {
  globalRate: number
  adaptiveSampling: boolean
  alwaysSamplePaths: string[]
  neverSamplePaths: string[]
  serviceConfigs: ServiceSamplingConfig[]
}

export interface ServiceSamplingConfig {
  service: string
  rate: number
}

export interface SamplingStats {
  totalSamples: number
  droppedSamples: number
  samplingEfficiency: number
}

// Common Types
export interface TimeRange {
  startTime?: string
  endTime?: string
}