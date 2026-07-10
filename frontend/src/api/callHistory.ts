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
  const params: Record<string, any> = {
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
 * 根据 traceId 查询调用链路
 */
export const findByTraceId = async (traceId: string): Promise<ApiCallHistoryRecord[]> => {
  const response = await request.get(`/call-history/trace/${traceId}`)
  return response.data.data || []
}

/**
 * 获取最近的调用记录
 */
export const getRecentCalls = async (limit: number = 20): Promise<ApiCallHistoryRecord[]> => {
  const response = await request.get('/call-history/recent', { params: { limit } })
  return response.data.data || []
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
  const params: Record<string, any> = { limit }
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
  const params: Record<string, any> = {}
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
  const params: Record<string, any> = {}
  if (startTime) params.startTime = startTime
  if (endTime) params.endTime = endTime

  const response = await request.get('/call-history/dashboard', { params })
  return response.data.data
}

/**
 * 清理过期数据
 */
export const cleanupCallHistory = async (cutoffTime?: string): Promise<number> => {
  const params: Record<string, any> = {}
  if (cutoffTime) params.cutoffTime = cutoffTime

  const response = await request.delete('/call-history/cleanup', { params })
  return response.data.data?.deletedCount || 0
}

/**
 * 获取总记录数
 */
export const getCallHistoryCount = async (): Promise<number> => {
  const response = await request.get('/call-history/count')
  return response.data.data?.count || 0
}
