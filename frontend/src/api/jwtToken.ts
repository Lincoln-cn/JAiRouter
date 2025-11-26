import request from '@/utils/request'
import type {RouterResponse} from '@/types'

// 定义JWT令牌信息类型（基于设计文档的JwtTokenInfo）
export interface JwtTokenInfo {
    id: string              // 令牌ID (UUID)
    userId: string          // 用户ID
    tokenHash: string       // 令牌哈希值 (SHA-256)
    issuedAt: string        // 颁发时间
    expiresAt: string       // 过期时间
    status: 'ACTIVE' | 'REVOKED' | 'EXPIRED'  // 令牌状态
    deviceInfo?: string     // 设备信息
    ipAddress?: string      // IP地址
    revokeReason?: string   // 撤销原因
    revokedAt?: string      // 撤销时间
    revokedBy?: string      // 撤销者
    createdAt: string       // 创建时间
    updatedAt: string       // 更新时间
}

// 保持向后兼容的JWT令牌类型
export interface JwtToken {
    userId: string
    token: string
    issuedAt: string
    expiresAt: string
    status: 'active' | 'revoked'
}

// 分页结果类型
export interface PagedResult<T> {
    content: T[]
    totalElements: number
    totalPages: number
    size: number
    page: number
    first: boolean
    last: boolean
    hasNext: boolean
    hasPrevious: boolean
}

// 清理结果类型
export interface CleanupResult {
    cleanedTokens: number
    cleanedBlacklistEntries: number
    executionTime: number
    timestamp: string
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
        const response = await request.post<RouterResponse<any>>('/auth/jwt/revoke', revokeRequest)
        return response.data.success || false
    } catch (error) {
        console.error('撤销JWT令牌失败:', error)
        throw error
    }
}

// 批量撤销JWT令牌
export const revokeTokensBatch = async (batchRevokeRequest: BatchTokenRevokeRequest): Promise<boolean> => {
    try {
        const response = await request.post<RouterResponse<any>>('/auth/jwt/revoke/batch', batchRevokeRequest)
        return response.data.success || false
    } catch (error) {
        console.error('批量撤销JWT令牌失败:', error)
        throw error
    }
}

// 验证JWT令牌
export const validateToken = async (validationRequest: TokenValidationRequest): Promise<TokenValidationResponse> => {
    try {
        const response = await request.post<RouterResponse<TokenValidationResponse>>('/auth/jwt/validate', validationRequest)
        return response.data.data || {valid: false, message: '未知错误', timestamp: new Date().toISOString()}
    } catch (error) {
        console.error('验证JWT令牌失败:', error)
        throw error
    }
}

// 获取黑名单统计信息
export const getBlacklistStats = async (): Promise<BlacklistStats> => {
    try {
        const response = await request.get<RouterResponse<BlacklistStats>>('/auth/jwt/blacklist/stats')
        return response.data.data || {memoryBlacklistSize: 0, blacklistEnabled: false, lastCleanupTime: ''}
    } catch (error) {
        console.error('获取黑名单统计信息失败:', error)
        throw error
    }
}

// 获取令牌列表（新增的持久化API）
export const getTokens = async (
    page: number = 0, 
    size: number = 20, 
    userId?: string, 
    status?: string
): Promise<PagedResult<JwtTokenInfo>> => {
    try {
        const params: any = { page, size }
        if (userId) params.userId = userId
        if (status) params.status = status
        
        const response = await request.get<RouterResponse<PagedResult<JwtTokenInfo>>>('/auth/jwt/tokens', { params })
        return response.data.data || {
            content: [],
            totalElements: 0,
            totalPages: 0,
            size: 0,
            page: 0,
            first: true,
            last: true,
            hasNext: false,
            hasPrevious: false
        }
    } catch (error) {
        console.error('获取令牌列表失败:', error)
        throw error
    }
}

// 获取令牌详情（新增的持久化API）
export const getTokenDetails = async (tokenId: string): Promise<JwtTokenInfo> => {
    try {
        const response = await request.get<RouterResponse<JwtTokenInfo>>(`/auth/jwt/tokens/${tokenId}`)
        if (!response.data.data) {
            throw new Error('令牌不存在')
        }
        return response.data.data
    } catch (error) {
        console.error('获取令牌详情失败:', error)
        throw error
    }
}

// 清理过期令牌（新增的持久化API）
export const cleanupExpiredTokens = async (): Promise<CleanupResult> => {
    try {
        const response = await request.post<RouterResponse<CleanupResult>>('/auth/jwt/cleanup')
        return response.data.data || {
            cleanedTokens: 0,
            cleanedBlacklistEntries: 0,
            executionTime: 0,
            timestamp: new Date().toISOString()
        }
    } catch (error) {
        console.error('清理过期令牌失败:', error)
        throw error
    }
}