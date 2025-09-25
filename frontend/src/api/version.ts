import request from '@/utils/request'
import type { RouterResponse } from '@/types'

// 定义版本配置类型
export interface VersionConfig {
  [key: string]: any
}

// 定义版本信息类型
export interface VersionInfo {
  version: number
  config: VersionConfig
  current: boolean
  operation?: string
  operationDetail?: string
  timestamp?: number
}

// 定义版本类型（用于前端展示）
export interface Version {
  version: number
  status: 'current' | 'history'
  config: VersionConfig
  operation?: string
  operationDetail?: string
  timestamp?: number
}

// 获取所有版本的详细信息
export const getAllVersionInfo = () => {
  return request.get<RouterResponse<VersionInfo[]>>('/config/version/info')
}

// 应用指定版本的配置
export const applyVersion = (version: number) => {
  return request.post<RouterResponse<void>>(`/config/version/apply/${version}`)
}

// 删除指定版本的配置
export const deleteConfigVersion = (version: number) => {
  return request.delete<RouterResponse<void>>(`/config/version/${version}`)
}