import request from '@/utils/request'
import type { RouterResponse } from '@/types'
import type { ServiceConfigEntry, ServiceConfigPayload } from '@/types/common'
import type { AdapterInfo } from './adapter'

// 定义服务类型
// 单个服务类型的整份配置。字段随服务类型不同（实例列表 / 负载均衡 / 限流…），
// 前端只透传、不按键做业务判断，故索引签名用 unknown 而不是 any（#520）。
export interface ServiceConfig {
  [key: string]: unknown
}

export interface ServiceType {
  type: string
  config: ServiceConfig
}

/**
 * 服务级限流配置（canonical 格式，持久化 + 热生效）。
 * 形状取自 `ServiceManagement.vue` 组装 `rateLimitPayload` 的方式：
 * 关闭时只下发 `{ enabled: false }`，故除 `enabled` 外均为可选（#520）。
 */
export interface ServiceRateLimitConfig {
  enabled: boolean
  algorithm?: string
  capacity?: number
  rate?: number
  scope?: string
  key?: string
  /** 是否按客户端 IP 单独计数 */
  clientIpEnable?: boolean
}

// 获取所有服务类型
export const getServiceTypes = () => {
  return request.get<RouterResponse<string[]>>('/config/type/services')
}

// 获取所有服务类型及其配置（优化接口）
export const getAllServicesWithConfig = () => {
  return request.get<RouterResponse<Record<string, ServiceConfigEntry>>>('/config/type/services/batch')
}

// 获取所有配置（与 dashboard 的 getAllServiceConfig 同一端点、同一载荷）
export const getAllConfigurations = () => {
  return request.get<RouterResponse<ServiceConfigPayload>>('/config/type')
}

// 获取指定服务的配置
export const getServiceConfig = (serviceType: string) => {
  return request.get<RouterResponse<ServiceConfigEntry>>(`/config/type/services/${serviceType}`)
}

// 创建新服务
export const createService = (serviceType: string, serviceConfig: ServiceConfigEntry) => {
  return request.post<RouterResponse<void>>(`/config/type/services/${serviceType}`, serviceConfig)
}

// 更新服务配置
export const updateServiceConfig = (serviceType: string, serviceConfig: ServiceConfigEntry) => {
  return request.put<RouterResponse<ServiceConfigEntry>>(`/config/type/services/${serviceType}`, serviceConfig)
}

// 删除服务
export const deleteService = (serviceType: string) => {
  return request.delete<RouterResponse<void>>(`/config/type/services/${serviceType}`)
}

// 获取所有适配器
export const getAdapters = () => {
  return request.get<RouterResponse<AdapterInfo[]>>('/config/adapter')
}

// v2.8.8: 获取服务级限流配置(canonical 格式,持久化)
export const getServiceRateLimit = (serviceType: string) => {
  return request.get<RouterResponse<ServiceRateLimitConfig>>(`/services/${serviceType}/ratelimit`)
}

// v2.8.8: 更新服务级限流配置(canonical 格式,持久化 + 热生效)
export const updateServiceRateLimit = (serviceType: string, rateLimit: ServiceRateLimitConfig) => {
  return request.put<RouterResponse<ServiceRateLimitConfig>>(`/services/${serviceType}/ratelimit`, rateLimit)
}
