import type {
  CallHistoryQuery,
  CallHistoryStatistics,
  ApiCallHistoryRecord,
  PageResponse,
  CallHistoryDashboard
} from '@/types/callHistory'
import request from '@/utils/request'

/**
 * 分页查询调用历史
 */
export const queryCallHistory = async (
  query: CallHistoryQuery = {}
): Promise<PageResponse<ApiCallHistoryRecord>> => {
  const params: Record<string, unknown> = {
    page: query.page || 0,
    size: query.size || 20
  }
  if (query.modelName) params.modelName = query.modelName
  if (query.serviceType) params.serviceType = query.serviceType
  if (query.apiKeyId) params.apiKeyId = query.apiKeyId
  if (query.isSuccess !== undefined && query.isSuccess !== null) params.isSuccess = query.isSuccess
  if (query.httpStatusCode) params.httpStatusCode = query.httpStatusCode
  if (query.startTime) params.startTime = query.startTime
  if (query.endTime) params.endTime = query.endTime
  if (query.sortField) params.sortField = query.sortField
  if (query.sortDirection) params.sortDirection = query.sortDirection

  const response = await request.get('/call-history', { params })
  return response.data.data
}

/**
 * 查询慢调用
 */
export const getSlowCalls = async (
  threshold?: number,
  startTime?: string,
  endTime?: string,
  limit: number = 50
): Promise<ApiCallHistoryRecord[]> => {
  const params: Record<string, unknown> = { limit }
  if (threshold) params.threshold = threshold
  if (startTime) params.startTime = startTime
  if (endTime) params.endTime = endTime

  const response = await request.get('/call-history/slow', { params })
  return response.data.data || []
}

/**
 * 获取统计信息
 */
export const getCallHistoryStatistics = async (
  startTime?: string,
  endTime?: string
): Promise<CallHistoryStatistics> => {
  const params: Record<string, unknown> = {}
  if (startTime) params.startTime = startTime
  if (endTime) params.endTime = endTime

  const response = await request.get('/call-history/statistics', { params })
  return response.data.data
}

/**
 * 获取仪表盘数据
 */
export const getCallHistoryDashboard = async (
  startTime?: string,
  endTime?: string
): Promise<CallHistoryDashboard> => {
  const params: Record<string, unknown> = {}
  if (startTime) params.startTime = startTime
  if (endTime) params.endTime = endTime

  const response = await request.get('/call-history/dashboard', { params })
  return response.data.data
}

// ---- v2.9.2 记录治理 (record governance) ----

export interface CallHistoryConfig {
  recordLevel: string   // "METADATA_ONLY" | "SUMMARY" | "FULL"
  maxContentLength?: number
  retentionDays?: number
  enabled?: boolean
  maxRecords?: number
  requestBodySummaryEnabled?: boolean
  responseBodySummaryEnabled?: boolean
  slowCallThresholdMs?: number
  /** 配置里请求的密钥来源（env / auto） */
  encryptionKeySource?: string
  /** 实现实际请求的密钥来源（与配置值同源，便于对照） */
  encryptionKeySourceRequested?: string
  /** 实现实际生效的密钥来源：请求 env 但环境变量缺失时会回退为 auto */
  encryptionKeySourceEffective?: string
  /** 请求来源是否被真正采纳（false 表示已回退） */
  encryptionKeySourceHonored?: boolean
  /** env 模式下环境变量 JAIR_CALL_HISTORY_KEY 是否可用 */
  environmentKeyPresent?: boolean
  encryptionAlgorithm?: string
  /** 密钥指纹（SHA-256 前 8 位），用于核对多副本是否同一把密钥 */
  encryptionKeyFingerprint?: string | null
  /** 当前级别是否把内容加密落库（仅 FULL 为 true） */
  contentStoredEncrypted?: boolean
}

/**
 * 获取调用历史记录配置
 */
export const getCallHistoryConfig = async (): Promise<CallHistoryConfig> => {
  const response = await request.get('/config/call-history')
  return response.data.data
}

/**
 * 更新调用历史记录配置
 */
export const updateCallHistoryConfig = async (
  payload: { recordLevel: string }
): Promise<CallHistoryConfig> => {
  const response = await request.put('/config/call-history', payload)
  return response.data.data
}
