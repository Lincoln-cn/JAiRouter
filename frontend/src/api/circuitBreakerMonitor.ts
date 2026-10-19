import request from '@/utils/request'
import type { RouterResponse } from '@/types'

/**
 * 熔断器实例状态（运行时快照 + 实例元数据）
 *
 * 后端 `GET /api/v1/circuit-breaker-monitor/instances` 直接返回数组（不是
 * `RouterResponse` 包装），数据来源 = 运行时熔断器 map ⋈ 实例库表，
 * 因此 `failureThreshold` / `lastFailureTime` 等字段来自真实熔断器对象，
 * 不再是前端硬编码的 0。
 */
export interface CircuitBreakerInstance {
  instanceId: string
  instanceName: string | null
  serviceType: string | null
  baseUrl: string | null
  state: string
  failureCount: number
  successCount: number
  failureThreshold: number
  successThreshold: number
  timeoutMillis: number
  lastFailureTime: string | null
}

/** 单个实例重置结果：保留前后状态，便于前端展示「OPEN → CLOSED」 */
export interface CircuitBreakerResetResult {
  instanceId: string
  instanceName: string | null
  serviceType: string | null
  previousState: string
  currentState: string
  /** 是否成功删除了持久化状态（决定重启后会不会被恢复回 OPEN） */
  persistedStateDeleted: boolean
  triggerReason: string
}

/** 批量重置结果：`matched` = 命中范围的实例数，`reset` = 真正重置成功的明细 */
export interface CircuitBreakerBatchResetResult {
  matched: number
  reset: CircuitBreakerResetResult[]
}

/**
 * 批量重置范围
 *
 * - 传 `instanceIds` = 按选中项重置
 * - 传 `serviceType` / `states` = 按筛选条件重置（后端取交集）
 * 两者都不传 = 重置全部（管理页的「重置筛选结果」在无筛选时即为全部）
 */
export interface CircuitBreakerBatchResetRequest {
  instanceIds?: string[]
  serviceType?: string
  states?: string[]
  reason?: string
}

const BASE_URL = '/v1/circuit-breaker-monitor'

/** 查询所有熔断器实例状态（含未熔断的 CLOSED 实例） */
export const getCircuitBreakerInstances = () => {
  return request.get<CircuitBreakerInstance[]>(`${BASE_URL}/instances`)
}

/** 重置单个实例熔断器（状态机 + 持久化 + 历史/监控记录一并处理） */
export const resetCircuitBreakerInstance = (instanceId: string, reason?: string) => {
  return request.post<RouterResponse<CircuitBreakerResetResult>>(
    `${BASE_URL}/instances/${encodeURIComponent(instanceId)}/reset`,
    reason ? { reason } : {}
  )
}

/** 按范围批量重置熔断器 */
export const resetCircuitBreakerInstances = (payload: CircuitBreakerBatchResetRequest) => {
  return request.post<RouterResponse<CircuitBreakerBatchResetResult>>(
    `${BASE_URL}/instances/reset`,
    payload
  )
}
