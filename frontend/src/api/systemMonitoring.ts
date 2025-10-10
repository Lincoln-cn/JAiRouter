import request from '@/utils/request'
import type { RouterResponse } from '@/types'

// 系统统计数据类型
export interface SystemStats {
    activeTokens: number
    blacklistSize: number
    totalCleanedTokens: number
}

// 系统健康状态类型
export interface SystemHealth {
    overall: 'healthy' | 'unhealthy'
    redis: {
        connected: boolean
        responseTime: number
        memoryUsage: number
        keyCount: number
    }
    memory: {
        status: 'healthy' | 'warning' | 'critical'
        usagePercentage: number
        tokenCount: number
        blacklistCount: number
    }
}

// 清理统计类型
export interface CleanupStats {
    lastCleanupTokens: number
    lastCleanupBlacklist: number
    lastCleanupTime: string
    totalCleanedTokens: number
    totalCleanedBlacklist: number
}

// 系统配置类型
export interface SystemConfig {
    jwt: {
        persistenceEnabled: boolean
        primaryStorage: string
        fallbackStorage: string
        cleanupSchedule: string
        retentionDays: number
    }
    blacklist: {
        enabled: boolean
        primaryStorage: string
        maxMemorySize: number
        cleanupInterval: number
    }
}

// 审计事件类型
export interface AuditEvent {
    id: string
    type: 'JWT_TOKEN' | 'API_KEY' | 'SECURITY'
    userId: string
    resourceId: string
    action: string
    details: string
    ipAddress: string
    userAgent: string
    success: boolean
    timestamp: string
    metadata: Record<string, any>
}

// 审计事件查询参数
export interface AuditEventQuery {
    type?: string
    userId?: string
    startTime?: string
    endTime?: string
    page?: number
    size?: number
}

// 获取系统统计数据
export const getSystemStats = async (): Promise<SystemStats> => {
    try {
        const response = await request.get<RouterResponse<SystemStats>>('/api/monitoring/system/stats')
        return response.data.data || {
            activeTokens: 0,
            blacklistSize: 0,
            totalCleanedTokens: 0
        }
    } catch (error) {
        console.error('获取系统统计数据失败:', error)
        throw error
    }
}

// 获取系统健康状态
export const getSystemHealth = async (): Promise<SystemHealth> => {
    try {
        const response = await request.get<RouterResponse<SystemHealth>>('/api/monitoring/system/health')
        return response.data.data || {
            overall: 'healthy',
            redis: {
                connected: true,
                responseTime: 0,
                memoryUsage: 0,
                keyCount: 0
            },
            memory: {
                status: 'healthy',
                usagePercentage: 0,
                tokenCount: 0,
                blacklistCount: 0
            }
        }
    } catch (error) {
        console.error('获取系统健康状态失败:', error)
        throw error
    }
}

// 获取清理统计数据
export const getCleanupStats = async (): Promise<CleanupStats> => {
    try {
        const response = await request.get<RouterResponse<CleanupStats>>('/api/monitoring/system/cleanup-stats')
        return response.data.data || {
            lastCleanupTokens: 0,
            lastCleanupBlacklist: 0,
            lastCleanupTime: '',
            totalCleanedTokens: 0,
            totalCleanedBlacklist: 0
        }
    } catch (error) {
        console.error('获取清理统计数据失败:', error)
        throw error
    }
}

// 获取系统配置状态
export const getSystemConfig = async (): Promise<SystemConfig> => {
    try {
        const response = await request.get<RouterResponse<SystemConfig>>('/api/monitoring/system/config')
        return response.data.data || {
            jwt: {
                persistenceEnabled: false,
                primaryStorage: 'memory',
                fallbackStorage: 'memory',
                cleanupSchedule: '',
                retentionDays: 30
            },
            blacklist: {
                enabled: false,
                primaryStorage: 'memory',
                maxMemorySize: 10000,
                cleanupInterval: 3600
            }
        }
    } catch (error) {
        console.error('获取系统配置状态失败:', error)
        throw error
    }
}

// 获取最近审计事件
export const getRecentAuditEvents = async (query?: AuditEventQuery): Promise<AuditEvent[]> => {
    try {
        const params = {
            type: query?.type,
            userId: query?.userId,
            startTime: query?.startTime,
            endTime: query?.endTime,
            page: query?.page || 0,
            size: query?.size || 10
        }

        const response = await request.get<RouterResponse<AuditEvent[]>>('/api/monitoring/audit/recent', { params })
        return response.data.data || []
    } catch (error) {
        console.error('获取最近审计事件失败:', error)
        throw error
    }
}

// 触发手动清理
export const triggerManualCleanup = async (): Promise<{ cleanedTokens: number, cleanedBlacklist: number }> => {
    try {
        const response = await request.post<RouterResponse<{ cleanedTokens: number, cleanedBlacklist: number }>>('/api/monitoring/system/cleanup')
        return response.data.data || { cleanedTokens: 0, cleanedBlacklist: 0 }
    } catch (error) {
        console.error('触发手动清理失败:', error)
        throw error
    }
}