import request from '@/utils/request'
import type { RouterResponse } from '@/types'

/**
 * 响应缓存管理 API（v2.10.2）
 *
 * 对应后端 ResponseCacheController：
 * - DELETE /api/config/cache/response  失效响应缓存（query params: serviceType?, model?）
 *
 * 缓存状态来自配置属性，无独立 GET 端点——前端从 ResponseCacheProperties 推导展示。
 */

/** 缓存状态（前端契约，基于 ResponseCacheProperties + CacheStore） */
export interface CacheStatus {
  enabled: boolean
  ttlSeconds: number
  maxSize: number
  size: number | null
  skipStreaming: boolean
  onlyDeterministic: boolean
}

/** 失效结果 */
export interface InvalidateResult {
  executed: boolean
  serviceType: string | null
  model: string | null
}

/**
 * 获取缓存状态（优雅降级：404 时返回 null）
 *
 * 当前后端无 GET 端点，尝试 GET /api/config/cache/response，
 * 404 返回 null，前端展示 "—/未就绪"。
 */
export const getCacheStatus = async (): Promise<CacheStatus | null> => {
  try {
    const response = await request.get<RouterResponse<CacheStatus>>('/config/cache/response')
    return response.data.data ?? null
  } catch {
    return null
  }
}

/**
 * 失效响应缓存
 *
 * @param params.serviceType 服务类型（可选，如 chat / embedding / rerank）
 * @param params.model 模型名称（可选）
 * 无参数时清空全部缓存
 */
export const invalidateCache = async (params?: {
  serviceType?: string
  model?: string
}): Promise<InvalidateResult> => {
  const queryParams: Record<string, string> = {}
  if (params?.serviceType) queryParams.serviceType = params.serviceType
  if (params?.model) queryParams.model = params.model

  const response = await request.delete<RouterResponse<InvalidateResult>>(
    '/config/cache/response',
    { params: queryParams }
  )
  return response.data.data ?? { executed: false, serviceType: null, model: null }
}
