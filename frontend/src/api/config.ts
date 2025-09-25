import request from '@/utils/request'
import type { RouterResponse, Statistics, ServiceStatus, MergePreviewData, MergeResult } from '@/types'

// 配置合并相关接口
// 扫描版本配置文件
export const scanVersionFiles = () => {
  return request.get<RouterResponse<Record<number, string>>>('/config/merge/scan')
}

// 获取合并预览
export const getMergePreview = () => {
  return request.get<RouterResponse<MergePreviewData>>('/config/merge/preview')
}

// 执行自动合并
export const performAutoMerge = () => {
  return request.post<RouterResponse<MergeResult>>('/config/merge/execute')
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
  return request.get<RouterResponse<ServiceStatus>>('/config/merge/status')
}

// 批量操作：备份 + 合并 + 清理
export const performBatchOperation = (deleteOriginals: boolean = false) => {
  return request.post<RouterResponse<MergeResult>>('/config/merge/batch', null, {
    params: { deleteOriginals }
  })
}

// 验证配置文件
export const validateConfigFiles = () => {
  return request.get<RouterResponse<Record<string, any>>>('/config/merge/validate')
}

// 获取配置文件统计信息
export const getConfigStatistics = () => {
  return request.get<RouterResponse<Statistics>>('/config/merge/statistics')
}

// 预览指定版本的配置文件内容
export const previewConfigFile = (version: number) => {
  return request.get<RouterResponse<Record<string, any>>>(`/config/merge/preview/${version}`)
}

// 删除指定版本的配置文件
export const deleteConfigFile = (version: number) => {
  return request.delete<RouterResponse<any>>(`/config/version/${version}`)
}

// 版本管理系统集成接口
// 从版本管理系统扫描版本文件
export const scanVersionFilesFromVersionManager = () => {
    return request.get<RouterResponse<Record<number, string>>>('/config/merge/scan/version-manager')
}

// 获取基于版本管理系统的合并预览
export const getMergePreviewFromVersionManager = () => {
    return request.get<RouterResponse<MergePreviewData>>('/config/merge/preview/version-manager')
}

// 执行基于版本管理系统的自动合并
export const performAutoMergeWithVersionManager = () => {
    return request.post<RouterResponse<MergeResult>>('/config/merge/execute/version-manager')
}

// 执行原子性配置合并操作
export const performAtomicMergeWithVersionManager = () => {
    return request.post<RouterResponse<MergeResult>>('/config/merge/execute/atomic')
}