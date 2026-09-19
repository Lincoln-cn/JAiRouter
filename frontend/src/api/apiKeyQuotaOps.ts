/**
 * API Key 配额便捷操作 API（v3.2.1）
 */
import request from '@/utils/request'
import type { RouterResponse } from '@/types'
import type { QuotaUsageDetail } from '@/types'

export interface QuotaLimitsPayload {
  dailyRequestLimit?: number
  dailyTokenLimit?: number
  rateLimitPerMinute?: number
  quotaAlertThreshold?: number
}

export const updateApiKeyQuota = async (
  keyId: string,
  payload: QuotaLimitsPayload
): Promise<QuotaUsageDetail> => {
  const response = await request.put<RouterResponse<QuotaUsageDetail>>(
    `/auth/api-keys/${keyId}/quota`,
    payload,
    { timeout: 8000 }
  )
  return response.data.data as QuotaUsageDetail
}

export const batchResetApiKeyQuota = async (
  keyIds: string[]
): Promise<{ requested: number; reset: number }> => {
  const response = await request.post<RouterResponse<{ requested: number; reset: number }>>(
    '/auth/api-keys/quota/batch-reset',
    { keyIds }
  )
  return response.data.data as { requested: number; reset: number }
}

export const resetAllApiKeyQuota = async (): Promise<void> => {
  await request.post<RouterResponse<void>>('/auth/api-keys/quota/reset-all')
}
