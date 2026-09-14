import request from '@/utils/request'
import type { RouterResponse } from '@/types'

/**
 * 配额管理 API 封装
 *
 * 对应后端：
 * - GET  /api/config/quota                获取配额运行时配置
 * - PUT  /api/config/quota                热改配额运行时配置（仅 hot-editable 字段）
 * - GET  /api/monitoring/quota/status      获取配额运行态
 * - GET  /api/monitoring/quota/usage       查询配额多维用量
 */

export interface QuotaRetention {
  minute: string
  hour: string
  day: string
  month: string
}

export interface QuotaDistributed {
  enabled: boolean
  keyPrefix: string
  timeoutMs: number
  degradeToLocal: boolean
}

export interface QuotaConfig {
  enabled: boolean
  failOpen: boolean
  windows: string[]
  flushIntervalSeconds: number
  retention: QuotaRetention
  distributed: QuotaDistributed
  backendName: string
  hotEditableFields: string[]
  restartRequiredFields: string[]
}

/** 热改请求体（全部可空，null/省略 = 不改） */
export interface QuotaConfigPayload {
  enabled?: boolean | null
  failOpen?: boolean | null
  windows?: string[] | null
}

export interface RedisProbe {
  reachable: boolean
  status: string
  reason?: string
}

export interface CounterMetrics {
  degradationCount: number
}

export interface QuotaStatus {
  enabled: boolean
  backendName: string
  degraded: boolean
  degradedReason?: string
  failOpen: boolean
  windows: string[]
  distributed: QuotaDistributed
  redisProbe?: RedisProbe
  counterMetrics?: CounterMetrics
}

export interface QuotaUsageDimensions {
  tenantId: string
  apiKeyId: string
  userId: string
  serviceType: string
  model: string
}

export interface QuotaUsageItem {
  dimensions: QuotaUsageDimensions
  window: string
  windowStart: string
  requestCount: number
  tokenCount: number
}

export interface QuotaUsageQuery {
  tenantId?: string
  apiKeyId?: string
  userId?: string
  serviceType?: string
  model?: string
  window?: string
}

/**
 * 获取配额运行时配置
 * GET /api/config/quota → RouterResponse<QuotaConfig>
 */
export const getQuotaConfig = async (): Promise<QuotaConfig | null> => {
  try {
    const response = await request.get<RouterResponse<QuotaConfig>>('/config/quota')
    return response.data.data ?? null
  } catch {
    return null
  }
}

/**
 * 热改配额运行时配置
 * PUT /api/config/quota
 * 400 时抛出 AxiosError，调用方需从 error.response.data 提取后端 message。
 */
export const updateQuotaConfig = async (payload: QuotaConfigPayload): Promise<QuotaConfig> => {
  const response = await request.put<RouterResponse<QuotaConfig>>('/config/quota', payload)
  return response.data.data as QuotaConfig
}

/**
 * 获取配额运行态
 * GET /api/monitoring/quota/status → RouterResponse<QuotaStatus>
 */
export const getQuotaStatus = async (): Promise<QuotaStatus | null> => {
  try {
    const response = await request.get<RouterResponse<QuotaStatus>>('/monitoring/quota/status')
    return response.data.data ?? null
  } catch {
    return null
  }
}

/**
 * 查询配额多维用量
 * GET /api/monitoring/quota/usage?tenantId=&apiKeyId=&... → RouterResponse<QuotaUsageItem[]>
 * 配额未启用时返回 data=[] 且 message="配额账本未启用"（HTTP 200，非错误）。
 */
export const getQuotaUsage = async (query: QuotaUsageQuery): Promise<QuotaUsageItem[]> => {
  try {
    const params: Record<string, string> = {}
    if (query.tenantId) params.tenantId = query.tenantId
    if (query.apiKeyId) params.apiKeyId = query.apiKeyId
    if (query.userId) params.userId = query.userId
    if (query.serviceType) params.serviceType = query.serviceType
    if (query.model) params.model = query.model
    if (query.window) params.window = query.window

    const response = await request.get<RouterResponse<QuotaUsageItem[]>>(
      '/monitoring/quota/usage',
      { params }
    )
    return response.data.data ?? []
  } catch {
    return []
  }
}
