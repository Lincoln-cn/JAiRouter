import request from '@/utils/request'
import type { RouterResponse } from '@/types'

// 定义版本配置类型
export interface ConfigVersion {
  version: number
  config: Record<string, any>
  createTime?: string
  description?: string
}

// 定义版本信息类型
export interface VersionInfo {
  version: number
  config: Record<string, any>
  current: boolean
}

// 获取配置的所有版本列表
export const getConfigVersions = () => {
  return request.get<RouterResponse<number[]>>('/config/version')
}

// 获取当前配置版本
export const getCurrentVersion = () => {
  return request.get<RouterResponse<number>>('/config/version/current')
}

// 获取指定版本的配置详情
export const getConfigByVersion = (version: number) => {
  return request.get<RouterResponse<Record<string, any>>>(`/config/version/${version}`)
}

// 应用指定版本的配置
export const applyVersion = (version: number) => {
  return request.post<RouterResponse<void>>(`/config/version/apply/${version}`)
}

// 回滚到指定版本
export const rollbackVersion = (version: number) => {
  return request.post<RouterResponse<void>>(`/config/version/rollback/${version}`)
}

// 删除指定版本的配置
export const deleteConfigVersion = (version: number) => {
  return request.delete<RouterResponse<void>>(`/config/version/${version}`)
}

// 获取所有版本的详细信息（优化接口）
export const getAllVersionInfo = () => {
  return request.get<RouterResponse<VersionInfo[]>>('/config/version/info')
}

// 配置合并相关接口
// 扫描版本配置文件
export const scanVersionFiles = () => {
  return request.get<RouterResponse<Record<number, string>>>('/config/merge/scan')
}

// 获取合并预览
export const getMergePreview = () => {
  return request.get<RouterResponse<Record<string, any>>>('/config/merge/preview')
}

// 执行自动合并
export const performAutoMerge = () => {
  return request.post<RouterResponse<any>>('/config/merge/execute')
}

// 备份配置文件
export const backupConfigFiles = () => {
  return request.post<RouterResponse<any>>('/config/merge/backup')
}

// 清理配置文件
export const cleanupConfigFiles = (deleteOriginals: boolean = false) => {
  return request.delete<RouterResponse<any>>('/config/merge/cleanup', {
    params: { deleteOriginals }
  })
}

// 获取合并服务状态
export const getMergeServiceStatus = () => {
  return request.get<RouterResponse<Record<string, any>>>('/config/merge/status')
}

// 批量操作：备份 + 合并 + 清理
export const performBatchOperation = (deleteOriginals: boolean = false) => {
  return request.post<RouterResponse<Record<string, any>>>('/config/merge/batch', null, {
    params: { deleteOriginals }
  })
}

// 验证配置文件
export const validateConfigFiles = () => {
  return request.get<RouterResponse<Record<string, any>>>('/config/merge/validate')
}

// 获取配置文件统计信息
export const getConfigStatistics = () => {
  return request.get<RouterResponse<Record<string, any>>>('/config/merge/statistics')
}