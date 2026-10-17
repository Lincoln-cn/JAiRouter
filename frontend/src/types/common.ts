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

