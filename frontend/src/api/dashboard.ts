import request from '@/utils/request'
import type {DashboardOverview, RouterResponse} from '@/types'

// 获取服务统计信息
export const getServiceStats = () => {
  return request.get<RouterResponse<Object>>('/models/stats')
}

// 获取服务类型列表
export const getServiceTypes = () => {
  return request.get<RouterResponse<string[]>>('/config/type/services')
}

// 获取指定服务的实例列表
export const getServiceInstances = (serviceType: string) => {
  return request.get<RouterResponse<any[]>>(`/config/instance/${serviceType}`)
}

// 添加服务实例
export const addServiceInstance = (serviceType: string, instanceConfig: any) => {
  return request.post<RouterResponse<void>>(`/config/instance/${serviceType}`, instanceConfig)
}

// 更新服务实例
export const updateServiceInstance = (serviceType: string, instanceId: string, instanceConfig: any) => {
  return request.put<RouterResponse<void>>(`/config/instance/${serviceType}/${instanceId}`, instanceConfig)
}

// 删除服务实例
export const deleteServiceInstance = (serviceType: string, instanceId: string) => {
    return request.delete<RouterResponse<void>>(`/config/instance/${serviceType}/${instanceId}`)
}

// 获取监控概览
export const getMonitoringOverview = () => {
  return request.get<RouterResponse<DashboardOverview>>('/monitoring/overview')
}

// 获取所有服务配置信息
export const getAllServiceConfig = () => {
  return request.get<RouterResponse<any>>('/config/type')
}