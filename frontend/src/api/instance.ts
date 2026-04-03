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
  headers?: Record<string, string>
  rateLimitEnabled?: boolean
  rateLimitAlgorithm?: string
  rateLimitCapacity?: number
  rateLimitRate?: number
  rateLimitScope?: string
  rateLimitClientIpEnable?: boolean
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

// 更新服务实例（使用扁平化格式）
export const updateServiceInstanceFlat = (serviceType: string, instanceId: string, instanceConfig: InstanceConfig) => {
  return request.put<RouterResponse<any>>(`/config/instance/${serviceType}/${instanceId}/flat`, instanceConfig)
}

// 删除服务实例
export const deleteServiceInstance = (serviceType: string, instanceId: string) => {
  return request.delete<RouterResponse<void>>(`/config/instance/${serviceType}/${instanceId}`)
}

// ==================== 已废弃的旧接口 ====================

/**
 * @deprecated 使用 updateServiceInstance 替代
 * 旧版更新实例接口
 */
export const updateServiceInstanceOld = (serviceType: string, instanceConfig: any, createNewVersion = false) => {
  return request.put<RouterResponse<void>>(`/config/instance/update/${serviceType}?createNewVersion=${createNewVersion}`, instanceConfig)
}

/**
 * @deprecated 使用 addServiceInstance 替代
 * 旧版添加实例接口
 */
export const addServiceInstanceOld = (serviceType: string, instanceConfig: any, createNewVersion = false) => {
  return request.post<RouterResponse<void>>(`/config/instance/add/${serviceType}?createNewVersion=${createNewVersion}`, instanceConfig)
}

/**
 * @deprecated 使用 deleteServiceInstance 替代
 * 旧版删除实例接口
 */
export const deleteServiceInstanceOld = (serviceType: string, instanceId: string, createNewVersion = false) => {
  return request.delete<RouterResponse<void>>(`/config/instance/del/${serviceType}?instanceId=${instanceId}&createNewVersion=${createNewVersion}`)
}
