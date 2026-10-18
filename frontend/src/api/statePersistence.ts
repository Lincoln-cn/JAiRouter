import request from '@/utils/request'
import type { RouterResponse } from '@/types'

/**
 * 状态持久化管理 API
 *
 * 为什么单独建这个模块：`views/config/StatePersistenceManagement.vue` 原本用裸
 * `request.post(\`${apiBaseUrl}/...\`)` 直接调用（issue `#529`），带来三个问题：
 * 1. 响应全是 `any`（该文件在 lint 里的一条 `any` 来源）；
 * 2. URL 拼装分散在页面里，改前缀要改页面；
 * 3. **接口对账器扫不到**（它只认 `@/api/*` 的封装）。
 *
 * 本模块只做"路径 + 类型"，不改任何调用时机与语义。
 */

const BASE = '/state-persistence'

/** 持久层健康（`/tiers` 与 `/tiers/refresh` 返回的都是这个形状） */
export interface TierHealth {
  healthy: boolean
  message?: string
}

/** 单条持久化状态明细（字段与页面模板渲染的一致） */
export interface StateDetail {
  instanceId?: string
  serviceType?: string
  stateType: string
  state: string
  lastModified: string
  tier?: string
  dataSize?: string
}

/** 总览里的计数字段 */
export interface PersistenceStats {
  circuitBreakerCount: number
  loadBalancerCount: number
  rateLimiterCount: number
  pendingSync: number
}

/** 持久化总览 */
export interface PersistenceStatus {
  currentTier: string
  stats?: PersistenceStats
}

/** 恢复/同步类操作的返回体（后端只给一句 message） */
export interface OperationResult {
  message?: string
}

/** 读取持久化总览 */
export const getPersistenceStatus = () =>
  request.get<RouterResponse<PersistenceStatus>>(`${BASE}/status`)

/** 读取各持久层健康 */
export const getTierHealth = () => request.get<RouterResponse<Record<string, TierHealth>>>(`${BASE}/tiers`)

/** 重算各持久层健康 */
export const refreshTiers = () =>
  request.post<RouterResponse<Record<string, TierHealth>>>(`${BASE}/tiers/refresh`)

/** 读取状态明细列表 */
export const getStateDetails = () => request.get<RouterResponse<StateDetail[]>>(`${BASE}/details`)

/** 全量恢复 */
export const recoverAll = () => request.post<RouterResponse<OperationResult>>(`${BASE}/recovery/all`)

/** 单条恢复（按状态类型走不同路径） */
export const recoverSingle = (stateType: string, id: string) => {
  if (stateType === 'CIRCUIT_BREAKER') {
    return request.post<RouterResponse<OperationResult>>(`${BASE}/recovery/circuit-breaker/${id}`)
  }
  if (stateType === 'LOAD_BALANCER') {
    return request.post<RouterResponse<OperationResult>>(`${BASE}/recovery/load-balancer/${id}`)
  }
  return request.post<RouterResponse<OperationResult>>(`${BASE}/recovery/rate-limiter/${id}`)
}

/** 手动同步 */
export const syncStates = () => request.post<RouterResponse<OperationResult>>(`${BASE}/sync`)

/** 切换持久层分层 */
export const switchTier = (tier: string) =>
  request.post<RouterResponse<OperationResult>>(`${BASE}/tiers/switch/${tier}`)
