import request from '@/utils/request'
import type { RouterResponse } from '@/types'

// 审计事件类型
export interface AuditEvent {
    id: string
    type: string
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

// 审计查询响应类型
export interface ExtendedAuditQueryResponse {
    events: AuditEvent[]
    page: number
    size: number
    totalElements: number
    startTime: string
    endTime: string
    eventCategory: string
}

// 审计查询参数
export interface AuditQueryParams {
    startTime?: string
    endTime?: string
    userId?: string
    tokenId?: string
    keyId?: string
    ipAddress?: string
    success?: boolean
    page?: number
    size?: number
}

// 安全报告类型
export interface SecurityReport {
    reportPeriodStart: string
    reportPeriodEnd: string
    totalJwtOperations: number
    totalApiKeyOperations: number
    failedAuthentications: number
    suspiciousActivities: number
    operationsByType: Record<string, number>
    operationsByUser: Record<string, number>
    topIpAddresses: string[]
    alerts: any[]
}

// 查询JWT令牌审计事件
export const queryJwtTokenAuditEvents = async (params: AuditQueryParams): Promise<ExtendedAuditQueryResponse> => {
    try {
        const response = await request.get<RouterResponse<ExtendedAuditQueryResponse>>('/security/audit/extended/jwt-tokens', { params })
        return response.data.data || {
            events: [],
            page: 0,
            size: 20,
            totalElements: 0,
            startTime: '',
            endTime: '',
            eventCategory: 'JWT_TOKEN'
        }
    } catch (error) {
        console.error('查询JWT令牌审计事件失败:', error)
        throw error
    }
}

// 查询API Key审计事件
export const queryApiKeyAuditEvents = async (params: AuditQueryParams): Promise<ExtendedAuditQueryResponse> => {
    try {
        const response = await request.get<RouterResponse<ExtendedAuditQueryResponse>>('/security/audit/extended/api-keys', { params })
        return response.data.data || {
            events: [],
            page: 0,
            size: 20,
            totalElements: 0,
            startTime: '',
            endTime: '',
            eventCategory: 'API_KEY'
        }
    } catch (error) {
        console.error('查询API Key审计事件失败:', error)
        throw error
    }
}

// 查询安全事件
export const querySecurityEvents = async (params: AuditQueryParams): Promise<ExtendedAuditQueryResponse> => {
    try {
        const response = await request.get<RouterResponse<ExtendedAuditQueryResponse>>('/security/audit/extended/security-events', { params })
        return response.data.data || {
            events: [],
            page: 0,
            size: 20,
            totalElements: 0,
            startTime: '',
            endTime: '',
            eventCategory: 'SECURITY'
        }
    } catch (error) {
        console.error('查询安全事件失败:', error)
        throw error
    }
}

// 复杂条件查询审计事件
export const queryAuditEventsAdvanced = async (query: any): Promise<ExtendedAuditQueryResponse> => {
    try {
        const response = await request.post<RouterResponse<ExtendedAuditQueryResponse>>('/security/audit/extended/query', query)
        return response.data.data || {
            events: [],
            page: 0,
            size: 20,
            totalElements: 0,
            startTime: '',
            endTime: '',
            eventCategory: 'ALL'
        }
    } catch (error) {
        console.error('复杂条件查询审计事件失败:', error)
        throw error
    }
}

// 生成安全报告
export const generateSecurityReport = async (startTime?: string, endTime?: string): Promise<SecurityReport> => {
    try {
        const params: any = {}
        if (startTime) params.startTime = startTime
        if (endTime) params.endTime = endTime

        const response = await request.get<RouterResponse<SecurityReport>>('/security/audit/extended/reports/security', { params })
        return response.data.data || {
            reportPeriodStart: '',
            reportPeriodEnd: '',
            totalJwtOperations: 0,
            totalApiKeyOperations: 0,
            failedAuthentications: 0,
            suspiciousActivities: 0,
            operationsByType: {},
            operationsByUser: {},
            topIpAddresses: [],
            alerts: []
        }
    } catch (error) {
        console.error('生成安全报告失败:', error)
        throw error
    }
}

// 获取用户审计事件
export const getUserAuditEvents = async (userId: string, startTime?: string, endTime?: string, limit: number = 50): Promise<ExtendedAuditQueryResponse> => {
    try {
        const params: any = { limit }
        if (startTime) params.startTime = startTime
        if (endTime) params.endTime = endTime

        const response = await request.get<RouterResponse<ExtendedAuditQueryResponse>>(`/security/audit/extended/users/${userId}/events`, { params })
        return response.data.data || {
            events: [],
            page: 0,
            size: 0,
            totalElements: 0,
            startTime: '',
            endTime: '',
            eventCategory: 'USER_SPECIFIC'
        }
    } catch (error) {
        console.error('获取用户审计事件失败:', error)
        throw error
    }
}

// 获取IP地址审计事件
export const getIpAuditEvents = async (ipAddress: string, startTime?: string, endTime?: string, limit: number = 50): Promise<ExtendedAuditQueryResponse> => {
    try {
        const params: any = { limit }
        if (startTime) params.startTime = startTime
        if (endTime) params.endTime = endTime

        const response = await request.get<RouterResponse<ExtendedAuditQueryResponse>>(`/security/audit/extended/ip-addresses/${ipAddress}/events`, { params })
        return response.data.data || {
            events: [],
            page: 0,
            size: 0,
            totalElements: 0,
            startTime: '',
            endTime: '',
            eventCategory: 'IP_SPECIFIC'
        }
    } catch (error) {
        console.error('获取IP地址审计事件失败:', error)
        throw error
    }
}

// 获取扩展审计统计信息
export const getExtendedAuditStatistics = async (startTime?: string, endTime?: string): Promise<any> => {
    try {
        const params: any = {}
        if (startTime) params.startTime = startTime
        if (endTime) params.endTime = endTime

        const response = await request.get<RouterResponse<any>>('security/audit/extended/statistics/extended', { params })
        return response.data.data || {}
    } catch (error) {
        console.error('获取扩展审计统计信息失败:', error)
        throw error
    }
}