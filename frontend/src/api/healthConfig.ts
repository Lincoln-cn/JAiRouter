import request from '@/utils/request'

/**
 * 健康探针设置 API 封装
 *
 * 对应后端：
 * - GET  /api/config/health   获取健康探针开关快照
 * - PUT  /api/config/health   热改开关（两项均可热改，无需重启）
 *
 * 页面用途：解释「/actuator/health 与 /liveness、/readiness 分别适用于谁」，
 * 并把两个影响编排行为的开关暴露出来。
 */

/** 就绪探针开关 */
export interface HealthReadinessConfig {
  /** 就绪探针是否聚合 db/redis 依赖 */
  includeDependencies: boolean
  /** 当前取值的影响面说明（后端给，避免前端自己编文案） */
  impact: string
}

/** JWT Redis 故障判定开关 */
export interface HealthJwtRedisConfig {
  /** Redis 不可用时 jwtRedis 组件是否判 DOWN */
  failHealth: boolean
  /** 当前取值的影响面说明 */
  impact: string
}

/** 健康探针设置快照 */
export interface HealthConfig {
  readiness: HealthReadinessConfig
  jwtRedis: HealthJwtRedisConfig
  /** 可热改字段名（与后端一致，供页面展示） */
  hotEditableFields: string[]
  /** 需重启字段名（当前为空） */
  restartRequiredFields: string[]
}

/** 更新请求：只对非 undefined 字段生效 */
export interface HealthConfigPayload {
  readinessIncludeDependencies?: boolean
  jwtRedisFailHealth?: boolean
}

/** 获取健康探针设置快照 */
export function getHealthConfig(): Promise<HealthConfig> {
  return request.get('/config/health')
}

/** 更新健康探针设置（部分更新） */
export function updateHealthConfig(payload: HealthConfigPayload): Promise<HealthConfig> {
  return request.put('/config/health', payload)
}
