/**
 * API 调用历史类型定义
 * @since 2.7.8
 */

// API 调用历史记录
export interface ApiCallHistoryRecord {
  id: number
  traceId: string
  requestId: string
  requestMethod: string
  requestPath: string
  requestBodySummary?: string
  contentType?: string
  serviceType: string
  modelName: string
  provider?: string
  instanceName?: string
  instanceUrl?: string
  httpStatusCode?: number
  responseBodySummary?: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
  responseTimeMs?: number
  isSuccess: boolean
  errorCode?: string
  errorMessage?: string
  apiKeyId?: string
  userId?: string
  clientIp?: string
  userAgent?: string
  rateLimited: boolean
  circuitBroken: boolean
  createdAt: string
  requestDate: string
  requestHour: number
}

// API 调用历史查询参数
export interface CallHistoryQuery {
  startTime?: string
  endTime?: string
  modelName?: string
  serviceType?: string
  apiKeyId?: string
  isSuccess?: boolean
  httpStatusCode?: number
  page?: number
  size?: number
  sortField?: string
  sortDirection?: string
}

// 分页响应
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

// API 调用历史统计
export interface CallHistoryStatistics {
  startTime: string
  endTime: string
  totalRequests: number
  successfulRequests: number
  failedRequests: number
  successRate: number
  totalTokens: number
  avgResponseTimeMs: number
  avgTokensPerRequest: number
  byModel: ModelCallStats[]
  byServiceType: ServiceTypeCallStats[]
  byDay: DailyCallStats[]
  byHour: HourlyCallStats[]
  byStatusCode: StatusCodeStats[]
  byErrorCode: ErrorCodeStats[]
}

// 模型调用统计
export interface ModelCallStats {
  modelName: string
  requestCount: number
  totalTokens: number
  avgResponseTimeMs: number
  successCount: number
  successRate: number
}

// 服务类型调用统计
export interface ServiceTypeCallStats {
  serviceType: string
  requestCount: number
  totalTokens: number
  avgResponseTimeMs: number
}

// 日调用统计
export interface DailyCallStats {
  date: string
  requestCount: number
  totalTokens: number
}

// 小时调用统计
export interface HourlyCallStats {
  hour: number
  requestCount: number
  label: string
}

// HTTP 状态码统计
export interface StatusCodeStats {
  statusCode: number
  count: number
}

// 错误码统计
export interface ErrorCodeStats {
  errorCode: string
  count: number
}

// 记录器状态
export interface RecorderStats {
  bufferSize: number
  totalRecords: number
  droppedRecords: number
}

// 仪表盘数据
export interface CallHistoryDashboard {
  statistics: CallHistoryStatistics
  recentCalls: ApiCallHistoryRecord[]
  recorderStats: RecorderStats
}
