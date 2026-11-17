import request from '@/utils/request'
import type { RouterResponse } from '@/types'

/**
 * 登录保护（暴力破解频控）配置与解锁接口（issue #1176）。
 *
 * 后端把阈值存在 `security_login_guard_config` 单行表里：首次启动由 YAML 落库，
 * 之后以数据库为准。所以这里的 PUT 是「写库 + 立即热改 + 写审计」，不是纯内存覆盖。
 */

/** 四个阈值 + 最后修改信息。 */
export interface LoginGuardConfig {
  /** 同一来源 IP + 同一用户名的失败阈值 */
  maxFailuresPerPrincipal: number
  /** 同一来源 IP 跨用户名的失败阈值 */
  maxFailuresPerIp: number
  /** 失败计数窗口（分钟） */
  windowMinutes: number
  /** 达到阈值后的锁定时长（分钟） */
  lockMinutes: number
  /** 最后修改时间（首次落库前可能为 null） */
  updatedAt: string | null
  /** 最后修改人 */
  updatedBy: string | null
}

/** 一条仍处于锁定窗口内的主体。 */
export interface LockedPrincipal {
  /** 锁定维度：account（IP + 用户名）或 ip（仅 IP） */
  scope: 'account' | 'ip' | string
  /** 用户名（scope=ip 时为空串） */
  username: string
  ip: string
  lockedUntilEpochMs: number
  remainingSeconds: number
}

/** 查询当前生效配置。 */
export const getLoginGuardConfig = async (): Promise<LoginGuardConfig | null> => {
  const response = await request.get<RouterResponse<LoginGuardConfig>>('/security/login-guard')
  return response.data.data ?? null
}

/** 修改配置（四个阈值必须同时提供）。 */
export const updateLoginGuardConfig = async (
  payload: Pick<LoginGuardConfig, 'maxFailuresPerPrincipal' | 'maxFailuresPerIp' | 'windowMinutes' | 'lockMinutes'>
): Promise<LoginGuardConfig | null> => {
  const response = await request.put<RouterResponse<LoginGuardConfig>>('/security/login-guard', payload)
  return response.data.data ?? null
}

/** 当前被锁定的主体（只含仍在锁定窗口内的）。 */
export const getLockedPrincipals = async (): Promise<LockedPrincipal[]> => {
  const response = await request.get<RouterResponse<LockedPrincipal[]>>('/security/login-guard/locked')
  return response.data.data ?? []
}

/**
 * 解除登录锁定。
 *
 * 两者都给 ⇒ 只解该 (IP, 用户名)；只给 username ⇒ 解该用户名所有 IP；
 * 只给 ip ⇒ 解该 IP；都不给 ⇒ 全部解锁。
 */
export const unlockLoginGuard = async (payload: { username?: string; ip?: string }): Promise<number> => {
  const response = await request.post<RouterResponse<{ cleared: number }>>('/security/login-guard/unlock', payload)
  return response.data.data?.cleared ?? 0
}
