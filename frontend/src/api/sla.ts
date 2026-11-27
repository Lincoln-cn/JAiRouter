import request from '@/utils/request'
import type { RouterResponse } from '@/types'

/**
 * SLA 接入账号与一次性令牌 API 封装（#1174）
 *
 * 对应后端：
 * - GET    /api/security/sla/accounts                      账号列表
 * - POST   /api/security/sla/accounts                      新建账号（绑角色模板）
 * - POST   /api/security/sla/accounts/{id}/bootstrap       签发一次性接入令牌
 * - POST   /api/security/sla/accounts/{id}/revoke          吊销该账号所有凭据（含已签发访问令牌）
 * - POST   /api/security/sla/accounts/{id}/enabled         启用/禁用账号
 * - DELETE /api/security/sla/accounts/{id}                 删除账号与凭据
 *
 * 注意：这里只管**管理端**。SLA 部署侧自己调
 * `POST /api/auth/sla/bootstrap`（匿名）与 `POST /api/auth/sla/refresh`（匿名），
 * 不走前端，也不需要管理员凭据。
 */

/** SLA 接入账号（列表行） */
export interface SlaAccount {
  accountId: string
  name: string
  roleTemplate: string
  enabled: boolean
  createdBy: string | null
  createdAt: string | null
  lastSeenAt: string | null
  /** 未吊销的刷新凭据数量 */
  activeRefreshTokens: number
  /** 已签发但未被消费的一次性令牌数量 */
  unusedBootstrapTokens: number
}

/** 一次性接入令牌签发结果 —— 明文只在这里出现一次 */
export interface SlaBootstrapIssue {
  bootstrapToken: string
  expiresAt: string
  expiresIn: number
}

export interface SlaCreatePayload {
  name: string
  roleTemplate: string
}

export interface SlaIssuePayload {
  ttlMinutes?: number
  allowedIp?: string
}

/** 分页容器（与后端 PagedResult 对齐） */
export interface SlaAccountPageResult {
  content: SlaAccount[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  hasNext: boolean
  hasPrevious: boolean
}

/** 全量汇总指标 —— 与列表一起返回，避免分页后按页求和算出错的「总数」 */
export interface SlaAccountSummary {
  accounts: number
  enabled: number
  refreshTokens: number
  pendingTokens: number
}

/** 列表响应：当前页 + 全量汇总 */
export interface SlaAccountPage {
  page: SlaAccountPageResult
  summary: SlaAccountSummary
}

/** 账号列表（分页 + 关键字） */
export async function getSlaAccounts(
  params: { page?: number; size?: number; keyword?: string } = {}
): Promise<SlaAccountPage> {
  const response = await request.get<RouterResponse<SlaAccountPage>>('/security/sla/accounts', {
    params: {
      page: params.page ?? 0,
      size: params.size ?? 20,
      keyword: params.keyword || undefined
    }
  })
  const data = response.data.data
  return {
    page: data?.page ?? {
      content: [],
      page: 0,
      size: params.size ?? 20,
      totalElements: 0,
      totalPages: 0,
      first: true,
      last: true,
      hasNext: false,
      hasPrevious: false
    },
    summary: data?.summary ?? { accounts: 0, enabled: 0, refreshTokens: 0, pendingTokens: 0 }
  }
}

/** 新建账号 */
export async function createSlaAccount(payload: SlaCreatePayload): Promise<SlaAccount> {
  const response = await request.post<RouterResponse<SlaAccount>>('/security/sla/accounts', payload)
  return response.data.data as SlaAccount
}

/** 签发一次性接入令牌 */
export async function issueSlaBootstrap(
  accountId: string,
  payload: SlaIssuePayload
): Promise<SlaBootstrapIssue> {
  const response = await request.post<RouterResponse<SlaBootstrapIssue>>(
    `/security/sla/accounts/${accountId}/bootstrap`,
    payload
  )
  return response.data.data as SlaBootstrapIssue
}

/** 启用或禁用账号；禁用会连带吊销凭据并停用登录主体 */
export async function setSlaAccountEnabled(
  accountId: string,
  enabled: boolean
): Promise<SlaAccount> {
  const response = await request.post<RouterResponse<SlaAccount>>(
    `/security/sla/accounts/${accountId}/enabled`,
    { enabled }
  )
  return response.data.data as SlaAccount
}

/** 吊销该账号所有凭据（含已签发的访问令牌） */
export async function revokeSlaCredentials(accountId: string): Promise<number> {
  const response = await request.post<RouterResponse<{ revoked: number }>>(
    `/security/sla/accounts/${accountId}/revoke`
  )
  return response.data.data?.revoked ?? 0
}

/** 删除账号（连带凭据与 jwt_accounts 里的镜像行） */
export async function deleteSlaAccount(accountId: string): Promise<void> {
  await request.delete<RouterResponse<null>>(`/security/sla/accounts/${accountId}`)
}
