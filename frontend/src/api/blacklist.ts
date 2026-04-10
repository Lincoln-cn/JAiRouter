import request from '@/utils/request'
import type { RouterResponse } from '@/types'

// 黑名单类型枚举
export type BlacklistType = 'TOKEN' | 'IP' | 'DEVICE'

// 黑名单状态枚举
export type BlacklistStatus = 'ACTIVE' | 'EXPIRED' | 'REMOVED'

// 风险等级枚举
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

// 黑名单条目
export interface BlacklistEntry {
    id: number
    blacklistType: BlacklistType
    targetValue: string
    targetValueMasked: string
    userId?: string
    reason?: string
    riskLevel: RiskLevel
    addedBy: string
    addedAt: string
    expiresAt?: string
    permanent: boolean
    status: BlacklistStatus
    source: 'MANUAL' | 'AUTO' | 'SYSTEM'
    active: boolean
    expired: boolean
    remainingSeconds?: number
}

// 黑名单统计
export interface BlacklistStats {
    totalActive: number
    typeCounts: Record<BlacklistType, number>
    tokenCount: number
    ipCount: number
    deviceCount: number
    highRiskCount?: number
    criticalRiskCount?: number
    lastAddedAt?: string
    lastExpiredAt?: string
    lastCleanupAt?: string
}

// 添加黑名单请求
export interface AddBlacklistRequest {
    blacklistType: BlacklistType
    targetValue: string
    userId?: string
    reason?: string
    riskLevel?: RiskLevel
    expiresInSeconds?: number
    source?: 'MANUAL' | 'AUTO' | 'SYSTEM'
    metadata?: string
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
}

/**
 * 获取黑名单列表（分页）
 */
export function getBlacklistPage(params: {
    type?: BlacklistType
    status?: BlacklistStatus
    page?: number
    size?: number
}): Promise<RouterResponse<PagedResult<BlacklistEntry>>> {
    return request({
        url: '/api/security/blacklist/list',
        method: 'get',
        params
    })
}

/**
 * 获取黑名单统计信息
 */
export function getBlacklistStats(): Promise<RouterResponse<BlacklistStats>> {
    return request({
        url: '/api/security/blacklist/stats',
        method: 'get'
    })
}

/**
 * 获取黑名单条目详情
 */
export function getBlacklistEntry(id: number): Promise<RouterResponse<BlacklistEntry>> {
    return request({
        url: `/api/security/blacklist/${id}`,
        method: 'get'
    })
}

/**
 * 添加到黑名单
 */
export function addToBlacklist(data: AddBlacklistRequest): Promise<RouterResponse<BlacklistEntry>> {
    return request({
        url: '/api/security/blacklist/add',
        method: 'post',
        data
    })
}

/**
 * 批量添加黑名单
 */
export function batchAddToBlacklist(dataList: AddBlacklistRequest[]): Promise<RouterResponse<number>> {
    return request({
        url: '/api/security/blacklist/batch-add',
        method: 'post',
        data: dataList
    })
}

/**
 * 从黑名单移除
 */
export function removeFromBlacklist(id: number): Promise<RouterResponse<boolean>> {
    return request({
        url: `/api/security/blacklist/${id}`,
        method: 'delete'
    })
}

/**
 * 检查是否在黑名单中
 */
export function checkBlacklist(type: BlacklistType, value: string): Promise<RouterResponse<boolean>> {
    return request({
        url: '/api/security/blacklist/check',
        method: 'get',
        params: { type, value }
    })
}

/**
 * 手动触发清理过期条目
 */
export function cleanupExpiredBlacklist(): Promise<RouterResponse<number>> {
    return request({
        url: '/api/security/blacklist/cleanup',
        method: 'post'
    })
}