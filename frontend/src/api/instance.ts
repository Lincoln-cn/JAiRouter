import request from '@/utils/request'
import type { RouterResponse } from '@/types'

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

// 获取服务实例列表
export const getServiceInstances = (serviceType: string) => {
  return request.get<RouterResponse<any[]>>(`/config/instance/${serviceType}`)
}

// 获取单个实例详情
export const getServiceInstance = (serviceType: string, instanceId: string) => {
  return request.get<RouterResponse<any>>(`/config/instance/${serviceType}/${instanceId}`)
}

// 添加服务实例
export const addServiceInstance = (serviceType: string, instanceConfig: InstanceConfig) => {
  return request.post<RouterResponse<any>>(`/config/instance/${serviceType}`, instanceConfig)
}

// 更新服务实例
export const updateServiceInstance = (serviceType: string, instanceId: string, instanceConfig: InstanceConfig) => {
  return request.put<RouterResponse<any>>(`/config/instance/${serviceType}/${instanceId}`, instanceConfig)
}

// 删除服务实例
export const deleteServiceInstance = (serviceType: string, instanceId: string) => {
  return request.delete<RouterResponse<void>>(`/config/instance/${serviceType}/${instanceId}`)
}

// ==================== 限流器配置 API ====================

/**
 * 获取实例的限流器配置
 */
export const getRateLimitConfig = (serviceType: string, instanceId: string) => {
  return request.get<RouterResponse<RateLimitConfig>>(`/config/instance/${serviceType}/${instanceId}/rate-limit`)
}

/**
 * 保存实例的限流器配置
 */
export const saveRateLimitConfig = (serviceType: string, instanceId: string, config: RateLimitConfig) => {
  return request.put<RouterResponse<RateLimitConfig>>(`/config/instance/${serviceType}/${instanceId}/rate-limit`, config)
}

// ==================== 熔断器配置 API ====================

/**
 * 获取实例的熔断器配置
 */
export const getCircuitBreakerConfig = (serviceType: string, instanceId: string) => {
  return request.get<RouterResponse<CircuitBreakerConfig>>(`/config/instance/${serviceType}/${instanceId}/circuit-breaker`)
}

/**
 * 保存实例的熔断器配置
 */
export const saveCircuitBreakerConfig = (serviceType: string, instanceId: string, config: CircuitBreakerConfig) => {
  return request.put<RouterResponse<CircuitBreakerConfig>>(`/config/instance/${serviceType}/${instanceId}/circuit-breaker`, config)
}