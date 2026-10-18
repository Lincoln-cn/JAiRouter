import request from '@/utils/request'
import type { RouterResponse } from '@/types'
import type { ServiceInstance } from '@/types/common'

// 定义实例配置接口
export interface InstanceConfig {
  instanceId?: string
  name: string
  baseUrl: string
  path?: string
  weight?: number
  status?: 'active' | 'inactive'
  adapter?: string
  headers?: Record<string, string>
  tags?: Record<string, string>  // v2.9.7: 实例标签(键值对)
}

// 限流器配置接口
export interface RateLimitConfig {
  id?: number
  instanceId?: number
  enabled: boolean
  algorithm: string
  capacity: number
  rate: number
  scope: string
  key?: string
  clientIpEnable: boolean
}

// 熔断器配置接口
export interface CircuitBreakerConfig {
  id?: number
  instanceId?: number
  enabled: boolean
  failureThreshold: number
  timeout: number
  successThreshold: number
}

// 获取服务实例列表（#520：用具名的 ServiceInstance，不用 any[]）
export const getServiceInstances = (serviceType: string) => {
  return request.get<RouterResponse<ServiceInstance[]>>(`/config/instance/${serviceType}`)
}

// 注：这里曾有一个 `getServiceInstance(serviceType, instanceId)` 调用
// `GET /config/instance/{serviceType}/{instanceId}`，但后端 ServiceTypeInstanceController
// 在该路径上**只声明了 PUT 与 DELETE**，没有 GET ⇒ 真调用会得到 405。
// 又因为它全仓零调用方（0 references），审计 #454 直接删除。
// 若将来确实需要「取单个实例」，要么后端补 GET，要么复用列表接口按 instanceId 过滤。

// 添加服务实例
export const addServiceInstance = (serviceType: string, instanceConfig: InstanceConfig) => {
  return request.post<RouterResponse<unknown>>(`/config/instance/${serviceType}`, instanceConfig)
}

// 更新服务实例
export const updateServiceInstance = (serviceType: string, instanceId: string, instanceConfig: InstanceConfig) => {
  return request.put<RouterResponse<unknown>>(`/config/instance/${serviceType}/${instanceId}`, instanceConfig)
}

// 删除服务实例
export const deleteServiceInstance = (serviceType: string, instanceId: string) => {
  return request.delete<RouterResponse<void>>(`/config/instance/${serviceType}/${instanceId}`)
}

// ==================== 限流器配置 API ====================

/**
 * 保存实例的限流器配置
 */
export const saveRateLimitConfig = (serviceType: string, instanceId: string, config: RateLimitConfig) => {
  return request.put<RouterResponse<RateLimitConfig>>(`/config/instance/${serviceType}/${instanceId}/rate-limit`, config)
}

// ==================== 熔断器配置 API ====================

/**
 * 保存实例的熔断器配置
 */
export const saveCircuitBreakerConfig = (serviceType: string, instanceId: string, config: CircuitBreakerConfig) => {
  return request.put<RouterResponse<CircuitBreakerConfig>>(`/config/instance/${serviceType}/${instanceId}/circuit-breaker`, config)
}

// ==================== 熔断器重置 API ====================

/**
 * 重置指定实例的熔断器状态（请求体方式）
 * @param instanceId 实例标识（Map key，可以是数字 ID 或复合键）
 */
export const resetCircuitBreakerById = (instanceId: string) => {
  return request.post<RouterResponse<null>>('/config/instance/circuit-breaker/reset', { instanceId })
}

/**
 * 清除所有实例的熔断器状态
 */
export const clearAllCircuitBreakers = () => {
  return request.post<RouterResponse<null>>('/config/instance/circuit-breaker/clear-all')
}