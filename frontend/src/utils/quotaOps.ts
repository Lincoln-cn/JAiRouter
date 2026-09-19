/**
 * API Key 配额预设与剩余量展示工具（v3.2.1）
 */
export interface QuotaLimits {
  dailyRequestLimit: number
  dailyTokenLimit: number
  rateLimitPerMinute: number
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
    limits: { dailyRequestLimit: 0, dailyTokenLimit: 0, rateLimitPerMinute: 0, quotaAlertThreshold: 0.8 }
  },
  {
    id: 'dev',
    labelKey: 'apiKeys.quotaPresets.dev',
    limits: { dailyRequestLimit: 1000, dailyTokenLimit: 100000, rateLimitPerMinute: 60, quotaAlertThreshold: 0.8 }
  },
  {
    id: 'standard',
    labelKey: 'apiKeys.quotaPresets.standard',
    limits: { dailyRequestLimit: 10000, dailyTokenLimit: 2000000, rateLimitPerMinute: 300, quotaAlertThreshold: 0.8 }
  },
  {
    id: 'strict',
    labelKey: 'apiKeys.quotaPresets.strict',
    limits: { dailyRequestLimit: 200, dailyTokenLimit: 50000, rateLimitPerMinute: 20, quotaAlertThreshold: 0.7 }
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
