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


