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

// API Key Management Types
export interface ApiKeyVO {
  keyId: string
  description: string
  permissions: string[]
  enabled: boolean
  expired: boolean
  createdAt: string
  createdBy?: string          // 创建者用户名
  creatorIpAddress?: string   // 创建者 IP 地址
  expiresAt: string
  lastUsedAt?: string
  totalRequests: number
  successfulRequests: number
  failedRequests: number
  remainingDays?: number      // -1 表示已过期，null 表示永不过期
  allowedIpAddresses?: string[]  // IP 白名单
  dailyRequestLimit?: number     // 每日请求限制
  rotationPeriodDays?: number    // 密钥轮换周期（天数）
  lastRotatedAt?: string         // 上次轮换时间
  needsRotation?: boolean        // 是否需要轮换
}

export interface ApiKeyCreationVO {
  keyId: string
  keyValue: string  // 仅在创建时返回，请妥善保存
  description: string
  permissions: string[]
  enabled: boolean
  createdAt: string
  expiresAt?: string
  lastRotatedAt?: string    // 上次轮换时间
  warning: string  // 警告信息
}

export interface ApiKeyListVO {
  items: ApiKeyVO[]
  total: number
  enabledCount: number
  disabledCount: number
  expiredCount: number
  summary: {
    todayTotalRequests: number
    todaySuccessfulRequests: number
    todayFailedRequests: number
  }
}

// API Key Management Request Types
export interface ApiKeyCreateRequest {
  keyId?: string
  description?: string
  permissions?: string[]
  enabled?: boolean
  expiresAt?: string
  allowedIpAddresses?: string[]
  dailyRequestLimit?: number
  rotationPeriodDays?: number   // 密钥轮换周期（天数）
}

export interface ApiKeyUpdateRequest {
  description?: string
  permissions?: string[]
  enabled?: boolean
  expiresAt?: string
  allowedIpAddresses?: string[]
  dailyRequestLimit?: number
  rotationPeriodDays?: number   // 密钥轮换周期（天数）
}

// 批量导入/导出类型
export interface ApiKeyBatchExportVO {
  exportTime: string
  total: number
  keys: ApiKeyExportedKey[]
}

export interface ApiKeyExportedKey {
  keyId: string
  description: string
  permissions: string[]
  enabled: boolean
  createdAt: string
  createdBy?: string
  expiresAt?: string
  allowedIpAddresses?: string[]
  dailyRequestLimit?: number
  rotationPeriodDays?: number
  lastRotatedAt?: string
}

export interface ApiKeyBatchImportRequest {
  keys: ApiKeyImportItem[]
  mode?: 'MERGE' | 'REPLACE'  // MERGE: 合并保留现有, REPLACE: 替换删除现有
}

export interface ApiKeyImportItem {
  keyId?: string
  description?: string
  permissions?: string[]
  enabled?: boolean
  expiresAt?: string
  allowedIpAddresses?: string[]
  dailyRequestLimit?: number
  rotationPeriodDays?: number
}

export interface ApiKeyBatchImportResult {
  totalAttempted: number
  successCount: number
  failureCount: number
  importedKeys: ApiKeyCreationVO[]
  errors: ImportError[]
}

export interface ImportError {
  keyId?: string
  reason: string
}

// 兼容旧类型
export type ApiKeyInfo = ApiKeyVO
export type ApiKeyCreationResponse = ApiKeyCreationVO
export type CreateApiKeyRequest = ApiKeyCreateRequest
export type UpdateApiKeyRequest = ApiKeyUpdateRequest

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