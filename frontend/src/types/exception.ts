
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

