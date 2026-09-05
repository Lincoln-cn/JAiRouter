import request from '@/utils/request'
import type { RouterResponse } from '@/types'

/**
 * 响应缓存管理 API
 *
 * 对应后端 ResponseCacheController：
 * - GET    /api/config/cache/response         获取缓存快照
 * - PUT    /api/config/cache/response/config  部分更新运行时配置
 * - DELETE /api/config/cache/response          失效响应缓存（query params: serviceType?, model?）
 */

/** 缓存快照（后端 RouterResponse.snapshot 映射） */
export interface CacheStatus {
  enabled: boolean
  ttlSeconds: number
  maxSize: number
  size: number | null
  skipStreaming: boolean
  onlyDeterministic: boolean
  hits: number
  misses: number
  /** 命中率 0~1 小数，无请求时为 null */
  hitRatio: number | null
}

/** 运行时配置更新载荷（Boolean/Long 包装类型，传 null 表示不改） */
export interface CacheConfigPayload {
  enabled?: boolean
  skipStreaming?: boolean
  onlyDeterministic?: boolean
  ttlSeconds?: number
}

/** 失效结果 */
export interface InvalidateResult {
  executed: boolean
  serviceType: string | null
  model: string | null
}

/**
 * 获取缓存快照
 *
 * GET /api/config/cache/response → RouterResponse<CacheStatus>
 * 404 / 异常时优雅降级返回 null。
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
 * 部分更新运行时配置
 *
 * PUT /api/config/cache/response/config
 * body: { enabled?, skipStreaming?, onlyDeterministic?, ttlSeconds? }
 * 未传字段保持不变；成功返回新 snapshot。
 */
export const updateCacheConfig = async (
  payload: CacheConfigPayload
): Promise<CacheStatus> => {
  const response = await request.put<RouterResponse<CacheStatus>>(
    '/config/cache/response/config',
    payload
  )
  return response.data.data as CacheStatus
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
