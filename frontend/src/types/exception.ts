import type { RouterResponse } from '@/types'

/**
 * 异常事件类型定义
 * @since 1.9.3
 */

// 异常事件 DTO
export interface ExceptionEvent {
  eventId: string
  exceptionType: string
  exceptionMessage: string
  sanitizedMessage: string
  operation: string
  errorCode: string
  errorCategory: string
  httpStatus: string
  traceId: string
  clientIp: string
  serviceName: string
  serviceType: string
  modelName: string
  provider: string
  instanceName: string
  responseTimeMs: number
  occurrenceCount: number
  firstOccurrence: string
  lastOccurrence: string
  occurredAt: string
  isAggregated: boolean
}

// 异常统计数据 DTO
export interface ExceptionStatistics {
  startTime: string
  endTime: string
  totalEvents: number
  totalTypes: number
  eventsByType: Record<string, number>
  eventsByCategory: Record<string, number>
  eventsByOperation: Record<string, number>
  eventsByHttpStatus: Record<string, number>
  topClientIps: { ip: string; count: number }[]
  hourlyDistribution: { hour: string; count: number }[]
}

// 异常查询参数
export interface ExceptionQueryParams {
  startTime?: string
  endTime?: string
  exceptionType?: string
  operation?: string
  errorCode?: string
  errorCategory?: string
  traceId?: string
  clientIp?: string
  serviceType?: string
  modelName?: string
  aggregatedOnly?: boolean
  page?: number
  size?: number
  sortBy?: string
  sortDirection?: 'asc' | 'desc'
}

// 分页结果类型
export interface PagedResult<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  hasNext: boolean
  hasPrevious: boolean
}

// 异常查询响应类型
export interface ExceptionQueryResponse {
  content: ExceptionEvent[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  hasNext: boolean
  hasPrevious: boolean
}

// 仪表盘数据类型
export interface ExceptionDashboardData {
  statistics: ExceptionStatistics
  recentEvents: ExceptionEvent[]
  timeRange: {
    startTime: string
    endTime: string
  }
}

// 删除结果类型
export interface ExceptionDeleteResult {
  deletedCount: number
  cutoffTime: string
  aggregatedOnly: boolean
}

// 错误分类枚举
export enum ErrorCategory {
  CLIENT_ERROR = 'CLIENT_ERROR',
  SERVER_ERROR = 'SERVER_ERROR',
  NETWORK_ERROR = 'NETWORK_ERROR',
  TIMEOUT_ERROR = 'TIMEOUT_ERROR',
  VALIDATION_ERROR = 'VALIDATION_ERROR',
  SECURITY_ERROR = 'SECURITY_ERROR',
  SYSTEM_ERROR = 'SYSTEM_ERROR',
  UNKNOWN = 'UNKNOWN'
}

// 错误代码枚举（常见）
export enum ErrorCode {
  // 客户端错误 (4xx)
  BAD_REQUEST = '400',
  UNAUTHORIZED = '401',
  FORBIDDEN = '403',
  NOT_FOUND = '404',
  METHOD_NOT_ALLOWED = '405',
  CONFLICT = '409',
  TOO_MANY_REQUESTS = '429',
  
  // 服务端错误 (5xx)
  INTERNAL_SERVER_ERROR = '500',
  BAD_GATEWAY = '502',
  SERVICE_UNAVAILABLE = '503',
  GATEWAY_TIMEOUT = '504'
}

// 默认查询响应
export const defaultExceptionQueryResponse = (): ExceptionQueryResponse => ({
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
  hasNext: false,
  hasPrevious: false
})

// 默认统计数据
export const defaultExceptionStatistics = (): ExceptionStatistics => ({
  startTime: '',
  endTime: '',
  totalEvents: 0,
  totalTypes: 0,
  eventsByType: {},
  eventsByCategory: {},
  eventsByOperation: {},
  eventsByHttpStatus: {},
  topClientIps: [],
  hourlyDistribution: []
})

// 默认仪表盘数据
export const defaultExceptionDashboardData = (): ExceptionDashboardData => ({
  statistics: defaultExceptionStatistics(),
  recentEvents: [],
  timeRange: {
    startTime: '',
    endTime: ''
  }
})
