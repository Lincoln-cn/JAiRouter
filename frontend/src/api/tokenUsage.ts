import type { TokenUsageStatistics, TokenUsageRecord } from '@/types/tokenUsage'
import request from '@/utils/request'

/**
 * 获取 Token 使用量统计信息
 */
export const getTokenUsageStatistics = async (
  startTime?: string,
  endTime?: string
): Promise<TokenUsageStatistics> => {
  const params: Record<string, any> = {}
  if (startTime) params.startTime = startTime
  if (endTime) params.endTime = endTime

  const response = await request.get('/token-usage/statistics', { params })
  return response.data.data
}

/**
 * 获取最近的使用记录
 */
export const getRecentUsage = async (limit: number = 20): Promise<TokenUsageRecord[]> => {
  const response = await request.get('/token-usage/recent', { params: { limit } })
  return response.data.data || []
}

/**
 * 获取指定模型的最近使用记录
 */
export const getRecentUsageByModel = async (
  modelName: string,
  limit: number = 20
): Promise<TokenUsageRecord[]> => {
  const response = await request.get(`/token-usage/recent/${modelName}`, {
    params: { limit }
  })
  return response.data.data || []
}

/**
 * 获取模型使用量排名
 */
export const getTopModels = async (
  startTime?: string,
  endTime?: string,
  limit: number = 10
): Promise<any[]> => {
  const params: Record<string, any> = { limit }
  if (startTime) params.startTime = startTime
  if (endTime) params.endTime = endTime

  const response = await request.get('/token-usage/top/models', { params })
  return response.data.data || []
}

/**
 * 获取服务类型使用量排名
 */
export const getTopServiceTypes = async (
  startTime?: string,
  endTime?: string,
  limit: number = 10
): Promise<any[]> => {
  const params: Record<string, any> = { limit }
  if (startTime) params.startTime = startTime
  if (endTime) params.endTime = endTime

  const response = await request.get('/token-usage/top/services', { params })
  return response.data.data || []
}

/**
 * 获取仪表盘数据
 */
export const getTokenUsageDashboard = async (
  startTime?: string,
  endTime?: string
): Promise<any> => {
  const params: Record<string, any> = {}
  if (startTime) params.startTime = startTime
  if (endTime) params.endTime = endTime

  const response = await request.get('/token-usage/dashboard', { params })
  return response.data.data
}

/**
 * 删除过期使用记录
 */
export const deleteOldUsageRecords = async (cutoffTime: string): Promise<number> => {
  const response = await request.delete('/token-usage/cleanup', {
    params: { cutoffTime }
  })
  return response.data.data?.deletedCount || 0
}
