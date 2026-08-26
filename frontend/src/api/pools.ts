import request from '@/utils/request'
import type { RouterResponse } from '@/types'

// v2.8.9: 资源池
export interface PoolMember {
  instanceId: string
  weight: number
  modelName?: string
}

export interface PoolDefinition {
  poolName: string
  name?: string
  serviceType: string
  enabled: boolean
  strategy: string
  description?: string
  members: PoolMember[]
}

export const getPoolList = () => {
  return request.get<RouterResponse<PoolDefinition[]>>('/config/pools/list')
}

export const getPool = (poolName: string) => {
  return request.get<RouterResponse<PoolDefinition>>(`/config/pools/${poolName}`)
}

export const createPool = (data: PoolDefinition) => {
  return request.post<RouterResponse<PoolDefinition>>('/config/pools', data)
}

export const updatePool = (poolName: string, data: PoolDefinition) => {
  return request.put<RouterResponse<PoolDefinition>>(`/config/pools/${poolName}`, data)
}

export const deletePool = (poolName: string) => {
  return request.delete<RouterResponse<void>>(`/config/pools/${poolName}`)
}
