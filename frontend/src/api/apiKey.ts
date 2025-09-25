import request from '@/utils/request'
import type { RouterResponse } from '@/types'

// 定义API密钥类型
export interface ApiKeyInfo {
  keyId: string
  description: string
  createdAt: string
  expiresAt: string
  enabled: boolean
  permissions: string[]
  expired?: boolean
}

// 创建API密钥请求类型
export interface CreateApiKeyRequest {
  keyId?: string
  description: string
  permissions: string[]
  enabled?: boolean
  expiresAt?: string
}

// 更新API密钥请求类型
export interface UpdateApiKeyRequest {
  description?: string
  permissions?: string[]
  enabled?: boolean
  expiresAt?: string
}

// 创建API密钥响应类型
export interface ApiKeyCreationResponse {
  keyId: string
  keyValue: string
  description: string
  createdAt: string
}

// 获取所有API密钥
export const getApiKeys = async (): Promise<ApiKeyInfo[]> => {
  try {
    const response = await request.get<RouterResponse<ApiKeyInfo[]>>('/auth/api-keys')
    return response.data.data || []
  } catch (error) {
    console.error('获取API密钥列表失败:', error)
    throw error
  }
}

// 根据ID获取API密钥
export const getApiKeyById = async (keyId: string): Promise<ApiKeyInfo> => {
  try {
    const response = await request.get<RouterResponse<ApiKeyInfo>>(`/auth/api-keys/${keyId}`)
    return response.data.data!
  } catch (error) {
    console.error(`获取API密钥 ${keyId} 失败:`, error)
    throw error
  }
}

// 创建API密钥
export const createApiKey = async (apiKey: CreateApiKeyRequest): Promise<ApiKeyCreationResponse> => {
  try {
    const response = await request.post<RouterResponse<ApiKeyCreationResponse>>('/auth/api-keys', apiKey)
    return response.data.data!
  } catch (error) {
    console.error('创建API密钥失败:', error)
    throw error
  }
}

// 更新API密钥
export const updateApiKey = async (keyId: string, apiKey: UpdateApiKeyRequest): Promise<ApiKeyInfo> => {
  try {
    const response = await request.put<RouterResponse<ApiKeyInfo>>(`/auth/api-keys/${keyId}`, apiKey)
    return response.data.data!
  } catch (error) {
    console.error(`更新API密钥 ${keyId} 失败:`, error)
    throw error
  }
}

// 删除API密钥
export const deleteApiKey = async (keyId: string): Promise<void> => {
  try {
    await request.delete<RouterResponse<void>>(`/auth/api-keys/${keyId}`)
  } catch (error) {
    console.error(`删除API密钥 ${keyId} 失败:`, error)
    throw error
  }
}

// 禁用API密钥
export const disableApiKey = async (keyId: string): Promise<ApiKeyInfo> => {
  try {
    const response = await request.patch<RouterResponse<ApiKeyInfo>>(`/auth/api-keys/${keyId}/disable`)
    return response.data.data!
  } catch (error) {
    console.error(`禁用API密钥 ${keyId} 失败:`, error)
    throw error
  }
}

// 启用API密钥
export const enableApiKey = async (keyId: string): Promise<ApiKeyInfo> => {
  try {
    const response = await request.patch<RouterResponse<ApiKeyInfo>>(`/auth/api-keys/${keyId}/enable`)
    return response.data.data!
  } catch (error) {
    console.error(`启用API密钥 ${keyId} 失败:`, error)
    throw error
  }
}