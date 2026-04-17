import request from '@/utils/request'
import type { RouterResponse } from '@/types'
import type {
  ExceptionEvent,
  ExceptionStatistics,
  ExceptionQueryParams,
  ExceptionQueryResponse,
  ExceptionDashboardData,
  ExceptionDeleteResult
} from '@/types/exception'

/**
 * 异常管理 API 接口封装
 * @since 1.9.3
 */

/**
 * 查询异常事件列表
 * @param params 查询参数
 */
export const queryExceptionEvents = async (params: ExceptionQueryParams): Promise<ExceptionQueryResponse> => {
  try {
    const response = await request.get<RouterResponse<ExceptionQueryResponse>>('/api/exceptions', { params })
    return response.data.data || {
      content: [],
      page: params.page || 0,
      size: params.size || 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      hasNext: false,
      hasPrevious: false
    }
  } catch (error) {
    console.error('查询异常事件列表失败:', error)
    throw error
  }
}

/**
 * 根据 ID 查询异常事件详情
 * @param eventId 事件 ID
 */
export const getExceptionEventById = async (eventId: string): Promise<ExceptionEvent | null> => {
  try {
    const response = await request.get<RouterResponse<ExceptionEvent>>(`/api/exceptions/${eventId}`)
    if (!response.data.success || response.data.errorCode === '404') {
      return null
    }
    return response.data.data || null
  } catch (error) {
    console.error('查询异常事件详情失败:', error)
    throw error
  }
}

/**
 * 获取异常统计信息
 * @param startTime 开始时间
 * @param endTime 结束时间
 */
export const getExceptionStatistics = async (
  startTime?: string,
  endTime?: string
): Promise<ExceptionStatistics> => {
  try {
    const params: any = {}
    if (startTime) params.startTime = startTime
    if (endTime) params.endTime = endTime

    const response = await request.get<RouterResponse<ExceptionStatistics>>('/api/exceptions/statistics', { params })
    return response.data.data || {
      startTime: startTime || '',
      endTime: endTime || '',
      totalEvents: 0,
      totalTypes: 0,
      eventsByType: {},
      eventsByCategory: {},
      eventsByOperation: {},
      eventsByHttpStatus: {},
      topClientIps: [],
      hourlyDistribution: []
    }
  } catch (error) {
    console.error('获取异常统计信息失败:', error)
    throw error
  }
}

/**
 * 获取最近的异常事件
 * @param limit 最大数量
 */
export const getRecentExceptionEvents = async (limit: number = 10): Promise<ExceptionEvent[]> => {
  try {
    const response = await request.get<RouterResponse<ExceptionEvent[]>>('/api/exceptions/recent', {
      params: { limit }
    })
    return response.data.data || []
  } catch (error) {
    console.error('获取最近的异常事件失败:', error)
    throw error
  }
}

/**
 * 获取指定类型的最近异常事件
 * @param exceptionType 异常类型
 * @param limit 最大数量
 */
export const getRecentExceptionEventsByType = async (
  exceptionType: string,
  limit: number = 10
): Promise<ExceptionEvent[]> => {
  try {
    const response = await request.get<RouterResponse<ExceptionEvent[]>>(`/api/exceptions/recent/${exceptionType}`, {
      params: { limit }
    })
    return response.data.data || []
  } catch (error) {
    console.error('获取指定类型的最近异常事件失败:', error)
    throw error
  }
}

/**
 * 删除过期异常事件
 * @param cutoffTime 截止时间
 * @param aggregatedOnly 是否仅删除已聚合的事件
 */
export const deleteOldExceptionEvents = async (
  cutoffTime: string,
  aggregatedOnly: boolean = false
): Promise<ExceptionDeleteResult> => {
  try {
    const response = await request.delete<RouterResponse<ExceptionDeleteResult>>('/api/exceptions/cleanup', {
      params: { cutoffTime, aggregatedOnly }
    })
    return response.data.data || {
      deletedCount: 0,
      cutoffTime,
      aggregatedOnly
    }
  } catch (error) {
    console.error('删除过期异常事件失败:', error)
    throw error
  }
}

/**
 * 获取异常管理仪表盘数据
 * @param startTime 开始时间
 * @param endTime 结束时间
 */
export const getExceptionDashboardData = async (
  startTime?: string,
  endTime?: string
): Promise<ExceptionDashboardData> => {
  try {
    const params: any = {}
    if (startTime) params.startTime = startTime
    if (endTime) params.endTime = endTime

    const response = await request.get<RouterResponse<ExceptionDashboardData>>('/api/exceptions/dashboard', { params })
    return response.data.data || {
      statistics: {
        startTime: startTime || '',
        endTime: endTime || '',
        totalEvents: 0,
        totalTypes: 0,
        eventsByType: {},
        eventsByCategory: {},
        eventsByOperation: {},
        eventsByHttpStatus: {},
        topClientIps: [],
        hourlyDistribution: []
      },
      recentEvents: [],
      timeRange: {
        startTime: startTime || '',
        endTime: endTime || ''
      }
    }
  } catch (error) {
    console.error('获取异常管理仪表盘数据失败:', error)
    throw error
  }
}
