import request from '@/utils/request'
import type { RouterResponse } from '@/types'

// 定义账户类型
export interface JwtAccount {
  username: string
  password?: string
  roles: string[]
  enabled: boolean
}

// 创建账户请求类型
export interface CreateJwtAccountRequest {
  username: string
  password: string
  roles: string[]
  enabled: boolean
}

// JWT账户配置状态类型
export interface JwtAccountConfigStatus {
  hasPersistedConfig: boolean
  currentVersion: number
  totalVersions: number
}

// 获取账户列表
export const getJwtAccounts = async (): Promise<JwtAccount[]> => {
  try {
    const response = await request.get<RouterResponse<JwtAccount[]>>('/security/jwt/accounts')
    return response.data.data || []
  } catch (error) {
    console.error('获取账户列表失败:', error)
    throw error
  }
}

// 创建账户
export const createJwtAccount = async (account: CreateJwtAccountRequest): Promise<void> => {
  try {
    await request.post<RouterResponse<void>>('/security/jwt/accounts', account)
  } catch (error) {
    console.error('创建账户失败:', error)
    throw error
  }
}

// 更新账户
export const updateJwtAccount = async (username: string, account: JwtAccount): Promise<void> => {
  try {
    await request.put<RouterResponse<void>>(`/security/jwt/accounts/${username}`, account)
  } catch (error) {
    console.error('更新账户失败:', error)
    throw error
  }
}

// 删除账户
export const deleteJwtAccount = async (username: string): Promise<void> => {
  try {
    await request.delete<RouterResponse<void>>(`/security/jwt/accounts/${username}`)
  } catch (error) {
    console.error('删除账户失败:', error)
    throw error
  }
}

// 切换账户状态
export const toggleJwtAccountStatus = async (username: string, enabled: boolean): Promise<void> => {
  try {
    await request.patch<RouterResponse<void>>(`/security/jwt/accounts/${username}/status`, null, {
      params: { enabled }
    })
  } catch (error) {
    console.error('切换账户状态失败:', error)
    throw error
  }
}

// ==================== 版本管理接口 ====================

// 获取所有账户配置版本列表
export const getJwtAccountVersions = async (): Promise<number[]> => {
  try {
    const response = await request.get<RouterResponse<number[]>>('/security/jwt/accounts/versions')
    return response.data.data || []
  } catch (error) {
    console.error('获取账户配置版本列表失败:', error)
    throw error
  }
}

// 获取指定版本的账户配置
export const getJwtAccountVersionConfig = async (version: number): Promise<Record<string, any> | null> => {
  try {
    const response = await request.get<RouterResponse<Record<string, any>>>(`/security/jwt/accounts/versions/${version}`)
    return response.data.data || null
  } catch (error) {
    console.error(`获取账户配置版本 ${version} 失败:`, error)
    throw error
  }
}

// 应用指定版本的账户配置
export const applyJwtAccountVersion = async (version: number): Promise<void> => {
  try {
    await request.post<RouterResponse<void>>(`/security/jwt/accounts/versions/${version}/apply`)
  } catch (error) {
    console.error(`应用账户配置版本 ${version} 失败:`, error)
    throw error
  }
}

// 获取当前账户配置版本号
export const getCurrentJwtAccountVersion = async (): Promise<number> => {
  try {
    const response = await request.get<RouterResponse<number>>('/security/jwt/accounts/versions/current')
    return response.data.data || 0
  } catch (error) {
    console.error('获取当前账户配置版本号失败:', error)
    throw error
  }
}

// ==================== 配置管理接口 ====================

// 重置账户配置为默认值
export const resetJwtAccountsToDefault = async (): Promise<void> => {
  try {
    await request.post<RouterResponse<void>>('/security/jwt/accounts/reset')
  } catch (error) {
    console.error('重置账户配置为默认值失败:', error)
    throw error
  }
}

// 获取账户配置状态
export const getJwtAccountConfigStatus = async (): Promise<JwtAccountConfigStatus> => {
  try {
    const response = await request.get<RouterResponse<JwtAccountConfigStatus>>('/security/jwt/accounts/config/status')
    return response.data.data || { hasPersistedConfig: false, currentVersion: 0, totalVersions: 0 }
  } catch (error) {
    console.error('获取账户配置状态失败:', error)
    throw error
  }
}