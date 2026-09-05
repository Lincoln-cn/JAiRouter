import request from '@/utils/request'
import type { RouterResponse } from '@/types'

/**
 * 慢查询分析 API（v2.10.2）
 *
 * 对应后端 SlowQueryAnalysisController（/api/monitoring/slow-queries）：
 * - GET  /stats              全量操作慢查询统计（Map<string, SlowQueryStats>）
 * - GET  /stats/{operation}  特定操作统计
 * - GET  /count              总慢查询数
 * - GET  /hotspots           性能热点（limit 参数，默认 10）
 * - GET  /trends             慢查询趋势
 * - GET  /alerts/stats       告警统计
 * - GET  /alerts/status      告警系统状态
 * - DELETE /stats            重置统计
 * - DELETE /alerts/stats     重置告警统计
 */

/** 单操作慢查询统计 */
export interface SlowQueryStats {
  count: number
  totalDuration: number
  maxDuration: number
  minDuration: number
  averageDuration: number
}

/** 操作统计（性能热点用） */
export interface OperationStats {
  callCount: number
  totalDuration: number
  maxDuration: number
  minDuration: number
  averageDuration: number
}

/** 性能热点 */
export interface PerformanceHotspot {
  operationName: string
  stats: OperationStats
  totalDuration: number
}

/** 告警统计 */
export interface SlowQueryAlertStats {
  totalAlertsTriggered: number
  totalAlertsSuppressed: number
  activeAlertKeys: number
  activeOperations: string[]
  alertTriggerRate?: number
  alertSuppressionRate?: number
  averageAlertsPerOperation?: number
}

/** 告警系统状态 */
export interface AlertSystemStatus {
  alertServiceEnabled: boolean
  alertStats?: SlowQueryAlertStats
  alertTriggerRate?: number
  alertSuppressionRate?: number
  averageAlertsPerOperation?: number
  message?: string
}

/** 慢查询趋势 */
export interface SlowQueryTrends {
  slowOperations: Record<string, OperationStats>
  totalSlowQueries: number
}

/** 获取全量操作慢查询统计 */
export const getSlowQueryStats = async (): Promise<Record<string, SlowQueryStats>> => {
  const response = await request.get<Record<string, SlowQueryStats>>('/monitoring/slow-queries/stats')
  return response.data ?? {}
}

/** 获取特定操作慢查询统计 */
export const getSlowQueryStatsByOperation = async (
  operationName: string
): Promise<SlowQueryStats> => {
  const response = await request.get<SlowQueryStats>(
    `/monitoring/slow-queries/stats/${encodeURIComponent(operationName)}`
  )
  return response.data ?? { count: 0, totalDuration: 0, maxDuration: 0, minDuration: 0, averageDuration: 0 }
}

/** 获取慢查询总数 */
export const getSlowQueryCount = async (): Promise<number> => {
  const response = await request.get<RouterResponse<number>>('/monitoring/slow-queries/count')
  return (response.data as unknown as number) ?? 0
}

/** 获取性能热点 */
export const getPerformanceHotspots = async (limit: number = 10): Promise<PerformanceHotspot[]> => {
  const response = await request.get<PerformanceHotspot[]>('/monitoring/slow-queries/hotspots', {
    params: { limit }
  })
  return response.data ?? []
}

/** 获取慢查询趋势 */
export const getSlowQueryTrends = async (): Promise<SlowQueryTrends> => {
  const response = await request.get<SlowQueryTrends>('/monitoring/slow-queries/trends')
  return response.data ?? { slowOperations: {}, totalSlowQueries: 0 }
}

/** 获取告警统计 */
export const getAlertStats = async (): Promise<SlowQueryAlertStats> => {
  const response = await request.get<SlowQueryAlertStats>('/monitoring/slow-queries/alerts/stats')
  return response.data ?? {
    totalAlertsTriggered: 0,
    totalAlertsSuppressed: 0,
    activeAlertKeys: 0,
    activeOperations: []
  }
}

/** 获取告警系统状态 */
export const getAlertSystemStatus = async (): Promise<AlertSystemStatus> => {
  const response = await request.get<AlertSystemStatus>('/monitoring/slow-queries/alerts/status')
  return response.data ?? { alertServiceEnabled: false }
}

/** 重置慢查询统计 */
export const resetSlowQueryStats = async (): Promise<void> => {
  await request.delete('/monitoring/slow-queries/stats')
}

/** 重置告警统计 */
export const resetAlertStats = async (): Promise<void> => {
  await request.delete('/monitoring/slow-queries/alerts/stats')
}
