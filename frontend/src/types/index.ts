// Common type definitions for the admin interface

// 默认类型参数用 `unknown` 而不是 `any`：调用方必须显式给出载荷类型或自行收窄，
// 避免「看起来有类型、实际什么都能进」（#521）。
export interface ApiResponse<T = unknown> {
  success: boolean
  data?: T
  message?: string
  error?: string
}

export interface RouterResponse<T = unknown> {
  success: boolean
  message: string
  data?: T
  errorCode?: string
  timestamp: string
}

/**
 * `GET /api/monitoring/overview` 的真实载荷（后端 `MonitoringController#getMonitoringOverview`）。
 *
 * issue #297：此前的版本声明了 `systemStatus` / `serviceStats` / `requestStats` / `resourceUsage`，
 * 而该接口**从不返回这些键** —— 类型因此对真实字段毫无约束（页面当时用 `ref<any>` 接收，也就没人发现）。
 * 现在按接口实际 put 的 9 个键重写，字段类型逐个对过后端 getter：
 * `enabledCategories` = `Set<String>`、`degradationStatus.level` = 枚举名、其余标量见下。
 */
/**
 * `GET /dashboard/metrics` 的载荷（#526）。
 *
 * 字段取自 `views/Dashboard.vue` 模板的**实际读取**（jvm/http/security/audit/system 五组），
 * 不照抄后端 DTO：页面没读的键不声明。
 */
export interface DashboardMetrics {
  jvm?: {
    heapUsagePercent?: number
    heapUsedMB?: number
    heapMaxMB?: number
    threadCount?: number
    peakThreadCount?: number
  }
  http?: {
    totalRequests?: number
    avgResponseTimeMs?: number
  }
  security?: {
    authSuccesses?: number
    authAttempts?: number
    authFailures?: number
    activeUsers?: number
    cacheHits?: number
    cacheMisses?: number
    cacheSize?: number
  }
  audit?: {
    totalEvents?: number
    successEvents?: number
    failureEvents?: number
  }
  system?: {
    systemLoadAverage?: number
    availableProcessors?: number
    processCpuUsage?: number
    uptimeSeconds?: number
  }
}

/** `GET /models/stats` 的载荷（#526）：首页统计卡片读的 4 个数 */
export interface ServiceStatsPayload {
  totalServices?: number
  totalInstances?: number
  totalModels?: number
  userCount?: number
}

export interface DashboardOverview {
  /** 监控总开关（`MonitoringProperties#isEnabled`） */
  enabled: boolean
  /** 指标前缀 */
  prefix: string
  /** 已启用的监控类别 */
  enabledCategories: string[]
  /** 健康检查结果（`MonitoringHealthChecker#performHealthCheck`） */
  healthy: boolean
  errorStats: {
    activeErrorComponents: number
    degradedComponents: number
    totalErrors: number
  }
  degradationStatus: {
    /** 降级级别枚举名 */
    level: string
    samplingRate: number
    autoModeEnabled: boolean
  }
  cacheStats: {
    usageRatio: number
    activeRetries: number
  }
  circuitBreakerStats: {
    /** 熔断器状态名 */
    state: string
    failureRate: number
  }
  /**
   * 近 24 小时安全告警数（#291）。
   * 由本接口返回；**alerts 关闭时不返回该键**，故为可选。
   */
  alertsLast24Hours?: number
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
  dailyTokenLimit?: number       // 每日 Token 使用上限
  rateLimitPerMinute?: number    // 每分钟请求速率限制
  quotaAlertThreshold?: number   // 配额告警阈值 (0.0-1.0)
  todayRequestCount?: number     // 今日请求次数
  todayTokenUsage?: number       // 今日 Token 使用量
  quotaAlertTriggered?: boolean  // 是否触发配额告警
  rotationPeriodDays?: number    // 密钥轮换周期（天数）
  lastRotatedAt?: string         // 上次轮换时间
  needsRotation?: boolean        // 是否需要轮换
}

export interface ApiKeyCreationVO {
  keyId: string
  keyValue: string  // 仅在创建时返回，请妥善保存
  description: string
  createdBy?: string  // 创建者用户名
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
  dailyTokenLimit?: number       // 每日 Token 使用上限
  rateLimitPerMinute?: number    // 每分钟请求速率限制
  quotaAlertThreshold?: number   // 配额告警阈值 (0.0-1.0)
  rotationPeriodDays?: number   // 密钥轮换周期（天数）
}

export interface ApiKeyUpdateRequest {
  description?: string
  permissions?: string[]
  enabled?: boolean
  expiresAt?: string
  allowedIpAddresses?: string[]
  dailyRequestLimit?: number
  dailyTokenLimit?: number       // 每日 Token 使用上限
  rateLimitPerMinute?: number    // 每分钟请求速率限制
  quotaAlertThreshold?: number   // 配额告警阈值 (0.0-1.0)
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

// API Key 配额管理类型
export interface QuotaUsageDetail {
  keyId: string
  description: string
  dailyRequestLimit: number
  dailyTokenLimit: number
  rateLimitPerMinute: number
  quotaAlertThreshold: number
  todayRequestCount: number
  todayTokenUsage: number
  currentRatePerMinute: number
  totalRequests: number
  dailyRequestUsagePercent: number  // -1 表示无限制
  dailyTokenUsagePercent: number    // -1 表示无限制
  alertTriggered: boolean
  /** 剩余请求数；限额 0 时为 -1（v3.2.1） */
  remainingRequests?: number
  /** 剩余 Token 数；限额 0 时为 -1（v3.2.1） */
  remainingTokens?: number
}

export interface QuotaAlertInfo {
  keyId: string
  description: string
  alertType: string        // REQUEST_QUOTA | TOKEN_QUOTA | GENERAL
  dailyRequestUsagePercent: number
  dailyTokenUsagePercent: number
  message: string
}

export interface TimeSeriesData {
  timestamp: string | number
  value: number
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

export interface CommonError {
  errorType: string
  service: string
  operation: string
  count: number
  lastOccurrence: string
}

// Common Types
export interface TimeRange {
  startTime?: string
  endTime?: string
}