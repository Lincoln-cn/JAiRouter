import request from '@/utils/request'
import type { DashboardMetrics, DashboardOverview, RouterResponse, ServiceStatsPayload } from '@/types'
import type { ServiceConfigPayload, ServiceInstance } from '@/types/common'
import type { InstanceConfig } from './instance'

// 获取服务统计信息
export const getServiceStats = () => {
  return request.get<RouterResponse<ServiceStatsPayload>>('/models/stats')
}

// 获取服务类型列表
export const getServiceTypes = () => {
  return request.get<RouterResponse<string[]>>('/config/type/services')
}

// 获取指定服务的实例列表（#520：用具名的 ServiceInstance，不用 any[]）
export const getServiceInstances = (serviceType: string) => {
  return request.get<RouterResponse<ServiceInstance[]>>(`/config/instance/${serviceType}`)
}

// 添加服务实例
export const addServiceInstance = (serviceType: string, instanceConfig: InstanceConfig) => {
  return request.post<RouterResponse<void>>(`/config/instance/${serviceType}`, instanceConfig)
}

// 更新服务实例
export const updateServiceInstance = (serviceType: string, instanceId: string, instanceConfig: InstanceConfig) => {
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

// 获取所有服务配置信息（载荷形状见 `@/types/common` 的 ServiceConfigPayload，与 service.ts 共用）
export const getAllServiceConfig = () => {
  return request.get<RouterResponse<ServiceConfigPayload>>('/config/type')
}

/**
 * `GET /dashboard/metrics` 的载荷（#520）。
 *
 * 该端点返回一组按监控类别分组的数值（告警数、请求量、资源占用…），
 * 各字段的消费方（Dashboard.vue）目前用 `ref<any>` 接收（属 #526 的范围），
 * 所以这里**不抄一整份后端形状**，只如实声明为「键值未知的对象」，避免用 any 假装有约束。
 */
export type DashboardMetricsPayload = DashboardMetrics

// 获取仪表盘真实业务指标
export const getDashboardMetrics = () => {
  return request.get<RouterResponse<DashboardMetricsPayload>>('/dashboard/metrics')
}
