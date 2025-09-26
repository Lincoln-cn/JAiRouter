import request from '@/utils/request'
import type {RouterResponse} from '@/types'

// 定义JWT令牌类型
export interface JwtToken {
    userId: string
    token: string
    issuedAt: string
    expiresAt: string
    status: 'active' | 'revoked'
}

// 定义令牌撤销请求类型
export interface TokenRevokeRequest {
    token: string
    userId?: string
    reason?: string
}

// 定义批量令牌撤销请求类型
export interface BatchTokenRevokeRequest {
    tokens: string[]
    reason?: string
}

// 定义令牌验证请求类型
export interface TokenValidationRequest {
    token: string
}

// 定义令牌验证响应类型
export interface TokenValidationResponse {
    valid: boolean
    userId?: string
    message: string
    timestamp: string
}

// 定义黑名单统计信息类型
export interface BlacklistStats {
    memoryBlacklistSize: number
    blacklistEnabled: boolean
    lastCleanupTime: number | string

    [key: string]: any
}

// 撤销JWT令牌
export const revokeToken = async (revokeRequest: TokenRevokeRequest): Promise<boolean> => {
    try {
        const response = await request.post<RouterResponse<any>>('/api/auth/jwt/revoke', revokeRequest)
        return response.data.success || false
    } catch (error) {
        console.error('撤销JWT令牌失败:', error)
        throw error
    }
}

// 批量撤销JWT令牌
export const revokeTokensBatch = async (batchRevokeRequest: BatchTokenRevokeRequest): Promise<boolean> => {
    try {
        const response = await request.post<RouterResponse<any>>('/api/auth/jwt/revoke/batch', batchRevokeRequest)
        return response.data.success || false
    } catch (error) {
        console.error('批量撤销JWT令牌失败:', error)
        throw error
    }
}

// 验证JWT令牌
export const validateToken = async (validationRequest: TokenValidationRequest): Promise<TokenValidationResponse> => {
    try {
        const response = await request.post<RouterResponse<TokenValidationResponse>>('/api/auth/jwt/validate', validationRequest)
        return response.data.data || {valid: false, message: '未知错误', timestamp: new Date().toISOString()}
    } catch (error) {
        console.error('验证JWT令牌失败:', error)
        throw error
    }
}

// 获取黑名单统计信息
export const getBlacklistStats = async (): Promise<BlacklistStats> => {
    try {
        const response = await request.get<RouterResponse<BlacklistStats>>('/api/auth/jwt/blacklist/stats')
        return response.data.data || {memoryBlacklistSize: 0, blacklistEnabled: false, lastCleanupTime: ''}
    } catch (error) {
        console.error('获取黑名单统计信息失败:', error)
        throw error
    }
}