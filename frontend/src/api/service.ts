import request from '@/utils/request'
import type { RouterResponse } from '@/types'

// 定义服务类型
export interface ServiceConfig {
  [key: string]: any
}

export interface ServiceType {
  type: string
  config: ServiceConfig
}

// 获取所有服务类型
export const getServiceTypes = () => {
  return request.get<RouterResponse<string[]>>('/config/type/services')
}

// 获取所有服务类型及其配置（优化接口）
export const getAllServicesWithConfig = () => {
  return request.get<RouterResponse<Record<string, any>>>('/config/type/services/batch')
}

// 获取所有配置
export const getAllConfigurations = () => {
  return request.get<RouterResponse<any>>('/config/type')
}

// 获取指定服务的配置
export const getServiceConfig = (serviceType: string) => {
  return request.get<RouterResponse<any>>(`/config/type/services/${serviceType}`)
}

// 创建新服务
export const createService = (serviceType: string, serviceConfig: any) => {
  return request.post<RouterResponse<void>>(`/config/type/services/${serviceType}`, serviceConfig)
}

// 更新服务配置
export const updateServiceConfig = (serviceType: string, serviceConfig: any) => {
  return request.put<RouterResponse<any>>(`/config/type/services/${serviceType}`, serviceConfig)
}

// 删除服务
export const deleteService = (serviceType: string) => {
  return request.delete<RouterResponse<void>>(`/config/type/services/${serviceType}`)
}

// 获取指定服务的所有可用模型
export const getAvailableModels = (serviceType: string) => {
  return request.get<RouterResponse<string[]>>(`/config/type/${serviceType}/models`)
}

// 重置配置为默认值
export const resetToDefaultConfig = () => {
  return request.post<RouterResponse<void>>('/config/type/reset')
}

// 获取所有适配器
export const getAdapters = () => {
  return request.get<RouterResponse<any[]>>('/config/adapter')
}

// v2.8.8: 获取服务级限流配置(canonical 格式,持久化)
export const getServiceRateLimit = (serviceType: string) => {
  return request.get<RouterResponse<Record<string, any>>>(`/services/${serviceType}/ratelimit`)
}

// v2.8.8: 更新服务级限流配置(canonical 格式,持久化 + 热生效)
export const updateServiceRateLimit = (serviceType: string, rateLimit: any) => {
  return request.put<RouterResponse<Record<string, any>>>(`/services/${serviceType}/ratelimit`, rateLimit)
}