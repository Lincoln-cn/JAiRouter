import request from '@/utils/request'
import type { DashboardOverview, RouterResponse } from '@/types'

// 获取Dashboard概览数据
export const getDashboardOverview = () => {
  return request.get<RouterResponse<DashboardOverview>>('/monitoring/overview')
}

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
  return request.get<RouterResponse<any[]>>(`/config/instance/type/${serviceType}`)
}

// 获取系统健康状态
export const getSystemHealth = () => {
  return request.get<any>('/actuator/health')
}

// 获取监控概览
export const getMonitoringOverview = () => {
  return request.get<RouterResponse<any>>('/monitoring/overview')
}

// 获取所有服务配置信息
export const getAllServiceConfig = () => {
  return request.get<RouterResponse<any>>('/config/type')
}