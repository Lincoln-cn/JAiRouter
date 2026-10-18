/**
 * 通用类型定义
 * 用于减少 any 的使用，提高类型安全
 */

// 服务实例类型
export interface ServiceInstance {
  instanceId: string
  name: string
  type: string
  adapter: string
  baseUrl: string
  weight: number
  headers?: Record<string, string>
  health?: boolean
  metadata?: Record<string, unknown>
  /**
   * 后端返回的其余字段（`/config/instance/{type}` 会带运行时统计等）。
   * 前端只透传、不做业务判断，故用 unknown；加索引签名也让本类型与
   * `stores/playgroundCache.ts` 的同名类型结构兼容（#520）。
   */
  [key: string]: unknown
}

// 服务配置类型
export interface ServiceConfig {
  type: string
  adapter: string
  loadBalanceType: string
  rateLimitType: string
  instances: ServiceInstance[]
  metadata?: Record<string, unknown>
}

/**
 * 按服务类型分组的配置条目（`GET /config/type`、`GET /config/type/services/{type}` 的元素）。
 *
 * 除列出的这几段外的字段随服务类型不同（实例列表 / 限流…），前端只透传、不按键做业务判断，
 * 故用索引签名 `unknown` 而不是 `any`（#520）。列出的字段取自 `ServiceManagement.vue` 的实际读取。
 */
export interface ServiceConfigEntry {
  adapter?: string
  description?: string
  loadBalance?: {
    type?: string
    [key: string]: unknown
  }
  rateLimit?: ServiceRateLimitSection
  circuitBreaker?: ServiceCircuitBreakerSection
  [key: string]: unknown
}

/** 通用配置里的限流段（canonical 格式见 `@/api/service` 的 ServiceRateLimitConfig） */
export interface ServiceRateLimitSection {
  enabled?: boolean
  algorithm?: string
  capacity?: number
  rate?: number
  scope?: string
  key?: string
  clientIpEnable?: boolean
}

/** 通用配置里的熔断段 */
export interface ServiceCircuitBreakerSection {
  enabled?: boolean
  failureThreshold?: number
  timeout?: number
  successThreshold?: number
}

/**
 * `GET /config/type` 的载荷：按服务类型分组的 `services` 映射，
 * 外加一个顶层 `adapter`（某类型未声明自己的 adapter 时回落到它）（#520）。
 */
export interface ServiceConfigPayload {
  adapter?: string
  services?: Record<string, ServiceConfigEntry>
}


