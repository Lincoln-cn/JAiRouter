/**
 * Token 使用量统计类型定义
 */

// Token 使用量记录
export interface TokenUsageRecord {
  id: number
  traceId?: string
  serviceType: string
  modelName: string
  provider?: string
  instanceName?: string
  instanceUrl?: string
  promptTokens: number
  completionTokens: number
  totalTokens: number
  apiKeyId?: string
  userId?: string
  clientIp?: string
  isSuccess?: boolean
  errorCode?: string
  errorMessage?: string
  responseTimeMs?: number
  occurredAt: string
  createdAt?: string
}

// Token 使用量统计
export interface TokenUsageStatistics {
  startTime: string
  endTime: string
  totalRequests: number
  successfulRequests: number
  failedRequests: number
  totalTokens: number
  totalPromptTokens: number
  totalCompletionTokens: number
  avgResponseTimeMs: number
  successRate: number
  byModel: ModelTokenStats[]
  byServiceType: ServiceTypeStats[]
  byProvider: ProviderStats[]
  byDay: DailyStats[]
  byWeek: WeeklyStats[]
  byMonth: MonthlyStats[]
  byHour: HourlyStats[]
  byApiKey?: ApiKeyStats[]
  byUser?: UserStats[]
}

// 模型 Token 统计
export interface ModelTokenStats {
  modelName: string
  totalTokens: number
  promptTokens: number
  completionTokens: number
  requestCount: number
  avgTokensPerRequest: number
}

// 服务类型统计
export interface ServiceTypeStats {
  serviceType: string
  totalTokens: number
  promptTokens: number
  completionTokens: number
  requestCount: number
}

// 提供商统计
export interface ProviderStats {
  provider: string
  totalTokens: number
  requestCount: number
}

// 日统计
export interface DailyStats {
  date: string
  totalTokens: number
  promptTokens: number
  completionTokens: number
  requestCount: number
}

// 周统计
export interface WeeklyStats {
  year: number
  week: number
  totalTokens: number
  promptTokens: number
  completionTokens: number
  requestCount: number
  weekLabel: string
}

// 月统计
export interface MonthlyStats {
  year: number
  month: number
  totalTokens: number
  promptTokens: number
  completionTokens: number
  requestCount: number
  monthLabel: string
}

// 小时统计
export interface HourlyStats {
  hour: number
  totalTokens: number
  requestCount: number
  hourLabel: string
}

// API Key 统计
export interface ApiKeyStats {
  apiKeyId: string
  totalTokens: number
  requestCount: number
}

// 用户统计
export interface UserStats {
  userId: string
  totalTokens: number
  requestCount: number
}
