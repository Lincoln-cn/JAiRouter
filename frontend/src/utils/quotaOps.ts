/**
 * API Key 配额预设与剩余量展示工具（v3.2.1）
 */
export interface QuotaLimits {
  dailyRequestLimit: number
  dailyTokenLimit: number
  rateLimitPerMinute: number
  /** 每小时请求上限（TA3，0 = 不限） */
  hourlyRequestLimit: number
  /** 每小时 Token 上限（TA3，0 = 不限） */
  hourlyTokenLimit: number
  /** 每月请求上限（TA3，0 = 不限） */
  monthlyRequestLimit: number
  /** 每月 Token 上限（TA3，0 = 不限） */
  monthlyTokenLimit: number
  quotaAlertThreshold: number
}

export interface QuotaPreset {
  id: string
  labelKey: string
  limits: QuotaLimits
}

export const QUOTA_PRESETS: QuotaPreset[] = [
  {
    id: 'unlimited',
    labelKey: 'apiKeys.quotaPresets.unlimited',
    limits: {
      dailyRequestLimit: 0,
      dailyTokenLimit: 0,
      rateLimitPerMinute: 0,
      hourlyRequestLimit: 0,
      hourlyTokenLimit: 0,
      monthlyRequestLimit: 0,
      monthlyTokenLimit: 0,
      quotaAlertThreshold: 0.8
    }
  },
  {
    id: 'dev',
    labelKey: 'apiKeys.quotaPresets.dev',
    limits: {
      dailyRequestLimit: 1000,
      dailyTokenLimit: 100000,
      rateLimitPerMinute: 60,
      hourlyRequestLimit: 100,
      hourlyTokenLimit: 10000,
      monthlyRequestLimit: 20000,
      monthlyTokenLimit: 2000000,
      quotaAlertThreshold: 0.8
    }
  },
  {
    id: 'standard',
    labelKey: 'apiKeys.quotaPresets.standard',
    limits: {
      dailyRequestLimit: 10000,
      dailyTokenLimit: 2000000,
      rateLimitPerMinute: 300,
      hourlyRequestLimit: 600,
      hourlyTokenLimit: 100000,
      monthlyRequestLimit: 200000,
      monthlyTokenLimit: 40000000,
      quotaAlertThreshold: 0.8
    }
  },
  {
    id: 'strict',
    labelKey: 'apiKeys.quotaPresets.strict',
    limits: {
      dailyRequestLimit: 200,
      dailyTokenLimit: 50000,
      rateLimitPerMinute: 20,
      hourlyRequestLimit: 20,
      hourlyTokenLimit: 5000,
      monthlyRequestLimit: 4000,
      monthlyTokenLimit: 1000000,
      quotaAlertThreshold: 0.7
    }
  }
]

/** 0 或负值展示为不限；-1 剩余量同样展示为不限 */
export function formatQuotaLimit(value?: number | null, unlimitedLabel = '不限'): string {
  if (value == null || value <= 0) return unlimitedLabel
  return Number(value).toLocaleString()
}

export function formatRemaining(
  remaining: number | null | undefined,
  limit: number | null | undefined,
  unlimitedLabel = '不限'
): string {
  if (limit == null || limit <= 0) return unlimitedLabel
  if (remaining == null || remaining < 0) return formatQuotaLimit(limit, unlimitedLabel)
  return `${Number(remaining).toLocaleString()} / ${Number(limit).toLocaleString()}`
}

/** 用量进度 0–100；不限制或无数据返回 0 */
export function quotaProgress(used: number | null | undefined, limit: number | null | undefined): number {
  if (!limit || limit <= 0) return 0
  const u = used ?? 0
  return Math.min(100, Math.round((u / limit) * 100))
}
