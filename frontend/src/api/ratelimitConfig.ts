import request from '@/utils/request'
import type { RouterResponse } from '@/types'

/**
 * 限流运行时配置 API 封装
 *
 * 对应后端：
 * - GET  /api/config/ratelimit        获取限流运行时配置快照
 * - PUT  /api/config/ratelimit        热改限流运行时配置（仅 hot-editable 字段）
 *
 * 两层限流：
 * - apiKey  —— L1，API Key 级每分钟上限（阈值取自每个 Key 的 rateLimitPerMinute）
 * - endUser —— L2，终端用户级每分钟上限（阈值取自本配置，可按需热开）
 */

/** L1：API Key 级每分钟限流 */
export interface ApiKeyMinuteConfig {
  /** 是否启用（关闭即等于关闭整条 L1 判定） */
  enabled: boolean
  /** 本地计数器容量（需重启生效） */
  localCacheSize: number
}

/** L2：终端用户级限流 */
export interface EndUserConfig {
  /** 是否启用 */
  enabled: boolean
  /** 每个终端用户每分钟请求上限 */
  requestsPerMinute: number
  /** 是否允许从请求头 X-End-User-Id 读取标识 */
  headerEnabled: boolean
  /** 标识缺失时的处理方式 */
  missingIdentity: 'SKIP' | 'REJECT'
  /** 本地计数器容量（需重启生效） */
  localCacheSize: number
  /** 标识摘要长度（需重启生效） */
  fingerprintLength: number
}

/** 分布式计数运行态（装配期决定，页面只读） */
export interface RatelimitDistributed {
  enabled: boolean
  timeoutMs: number
  apiKeyBackend: string
  endUserBackend: string
}

export interface RatelimitConfig {
  apiKey: ApiKeyMinuteConfig
  endUser: EndUserConfig
  distributed: RatelimitDistributed
  hotEditableFields: string[]
  restartRequiredFields: string[]
}

/** 热改请求体（全部可空，null/省略 = 不改） */
export interface RatelimitConfigPayload {
  apiKeyEnabled?: boolean | null
  endUserEnabled?: boolean | null
  endUserRequestsPerMinute?: number | null
  endUserHeaderEnabled?: boolean | null
  endUserMissingIdentity?: 'SKIP' | 'REJECT' | null
}

/**
 * 获取限流运行时配置快照
 * GET /api/config/ratelimit → RouterResponse<RatelimitConfig>
 */
export const getRatelimitConfig = async (): Promise<RatelimitConfig | null> => {
  try {
    const response = await request.get<RouterResponse<RatelimitConfig>>('/config/ratelimit')
    return response.data.data ?? null
  } catch {
    return null
  }
}

/**
 * 热改限流运行时配置
 * PUT /api/config/ratelimit
 * 400 时抛出 AxiosError，调用方需从 error.response.data 提取后端 message。
 */
export const updateRatelimitConfig = async (
  payload: RatelimitConfigPayload
): Promise<RatelimitConfig> => {
  const response = await request.put<RouterResponse<RatelimitConfig>>('/config/ratelimit', payload)
  return response.data.data as RatelimitConfig
}
