import request from '@/utils/request'
import type { RouterResponse } from '@/types'

// 审计事件类型枚举
export enum AuditEventType {
  JWT_TOKEN_ISSUED = 'JWT_TOKEN_ISSUED',
  JWT_TOKEN_REFRESHED = 'JWT_TOKEN_REFRESHED',
  JWT_TOKEN_REVOKED = 'JWT_TOKEN_REVOKED',
  JWT_TOKEN_VALIDATED = 'JWT_TOKEN_VALIDATED',
  JWT_TOKEN_EXPIRED = 'JWT_TOKEN_EXPIRED',
  API_KEY_CREATED = 'API_KEY_CREATED',
  API_KEY_USED = 'API_KEY_USED',
  API_KEY_REVOKED = 'API_KEY_REVOKED',
  API_KEY_EXPIRED = 'API_KEY_EXPIRED',
  API_KEY_UPDATED = 'API_KEY_UPDATED',
  SECURITY_ALERT = 'SECURITY_ALERT',
  SUSPICIOUS_ACTIVITY = 'SUSPICIOUS_ACTIVITY',
  AUTHENTICATION_FAILED = 'AUTHENTICATION_FAILED',
  AUTHORIZATION_FAILED = 'AUTHORIZATION_FAILED',
  SYSTEM_CLEANUP = 'SYSTEM_CLEANUP',
  SYSTEM_MAINTENANCE = 'SYSTEM_MAINTENANCE'
}

// 风险等级枚举
export enum RiskLevel {
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
  CRITICAL = 'CRITICAL'
}

// 审计事件类型（统一版本）
export interface AuditEvent {
    id: string
    type: AuditEventType | string
    userId?: string
    resourceId?: string
    action?: string
    details?: string
    ipAddress?: string
    userAgent?: string
    success: boolean
    timestamp: string
    metadata?: Record<string, any>
    eventCategory?: string
    riskLevel?: RiskLevel | string
    geoLocation?: string
    deviceInfo?: string
}

// 审计查询响应类型
export interface ExtendedAuditQueryResponse {
    events: AuditEvent[]
    page: number
    size: number
    totalElements: number
    totalPages: number
    startTime: string
    endTime: string
    eventCategory: string
    hasMore: boolean
}

// 审计查询参数
export interface AuditQueryParams {
    startTime?: string
    endTime?: string
    userId?: string
    resourceId?: string
    eventTypes?: AuditEventType[]
    ipAddress?: string
    success?: boolean
    eventCategory?: string
    riskLevel?: RiskLevel
    page?: number
    size?: number
    sortBy?: string
    sortDirection?: 'ASC' | 'DESC'
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
    alerts: SecurityAlert[]
    highRiskEventCount: number
    successRate: number
}

// 安全告警类型
export interface SecurityAlert {
    eventType: string
    message: string
    userId?: string
    ipAddress?: string
    timestamp: string
    severity: string
}

// 审计统计类型
export interface AuditStatistics {
    totalEvents: number
    successCount: number
    failureCount: number
    eventTypeStatistics: Record<string, number>
    categoryStatistics: Record<string, number>
    riskLevelStatistics: Record<string, number>
    topUsers: { userId: string; count: number }[]
    topIpAddresses: { ip: string; count: number }[]
}

// 默认响应
const defaultQueryResponse = (): ExtendedAuditQueryResponse => ({
    events: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    startTime: '',
    endTime: '',
    eventCategory: 'ALL',
    hasMore: false
})

const defaultSecurityReport = (): SecurityReport => ({
    reportPeriodStart: '',
    reportPeriodEnd: '',
    totalJwtOperations: 0,
    totalApiKeyOperations: 0,
    failedAuthentications: 0,
    suspiciousActivities: 0,
    operationsByType: {},
    operationsByUser: {},
    topIpAddresses: [],
    alerts: [],
    highRiskEventCount: 0,
    successRate: 0
})

// 查询JWT令牌审计事件
export const queryJwtTokenAuditEvents = async (params: AuditQueryParams): Promise<ExtendedAuditQueryResponse> => {
    try {
        const response = await request.get<RouterResponse<ExtendedAuditQueryResponse>>('/security/audit/extended/jwt-tokens', { params })
        return response.data.data || { ...defaultQueryResponse(), eventCategory: 'JWT_TOKEN' }
    } catch (error) {
        console.error('查询JWT令牌审计事件失败:', error)
        throw error
    }
}

// 查询API Key审计事件
export const queryApiKeyAuditEvents = async (params: AuditQueryParams): Promise<ExtendedAuditQueryResponse> => {
    try {
        const response = await request.get<RouterResponse<ExtendedAuditQueryResponse>>('/security/audit/extended/api-keys', { params })
        return response.data.data || { ...defaultQueryResponse(), eventCategory: 'API_KEY' }
    } catch (error) {
        console.error('查询API Key审计事件失败:', error)
        throw error
    }
}

// 查询安全事件
export const querySecurityEvents = async (params: AuditQueryParams): Promise<ExtendedAuditQueryResponse> => {
    try {
        const response = await request.get<RouterResponse<ExtendedAuditQueryResponse>>('/security/audit/extended/security-events', { params })
        return response.data.data || { ...defaultQueryResponse(), eventCategory: 'SECURITY' }
    } catch (error) {
        console.error('查询安全事件失败:', error)
        throw error
    }
}

// 复杂条件查询审计事件
export const queryAuditEventsAdvanced = async (query: any): Promise<ExtendedAuditQueryResponse> => {
    try {
        const response = await request.post<RouterResponse<ExtendedAuditQueryResponse>>('/security/audit/extended/query', query)
        return response.data.data || defaultQueryResponse()
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
        return response.data.data || defaultSecurityReport()
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
        return response.data.data || { ...defaultQueryResponse(), eventCategory: 'USER_SPECIFIC' }
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
        return response.data.data || { ...defaultQueryResponse(), eventCategory: 'IP_SPECIFIC' }
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

        const response = await request.get<RouterResponse<any>>('/security/audit/extended/statistics/extended', { params })
        return response.data.data || {}
    } catch (error) {
        console.error('获取扩展审计统计信息失败:', error)
        throw error
    }
}