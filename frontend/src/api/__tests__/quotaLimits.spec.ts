/**
 * 配额用量进度计算单测（v3.2.0 TDD）
 * 与 QuotaUsageMonitoring.vue 中 usagePercent / formatLimit 语义对齐
 */
import { describe, expect, it } from 'vitest'

export function formatLimit(value?: number | null, unlimitedLabel = '不限'): string {
  if (value == null || value === 0) return unlimitedLabel
  return Number(value).toLocaleString()
}

export function usagePercent(
  row: { window: string; requestCount: number; tokenCount: number; dimensions?: { apiKeyId?: string } },
  limits?: { dailyRequestLimit?: number; dailyTokenLimit?: number }
): number {
  if (!limits || row.window !== 'DAY') return 0
  const reqLimit = limits.dailyRequestLimit || 0
  const tokLimit = limits.dailyTokenLimit || 0
  const reqPct = reqLimit > 0 ? (row.requestCount / reqLimit) * 100 : 0
  const tokPct = tokLimit > 0 ? (row.tokenCount / tokLimit) * 100 : 0
  return Math.min(100, Math.round(Math.max(reqPct, tokPct)))
}

describe('quota limit formatting & progress', () => {
  it('0 或 null 表示不限制', () => {
    expect(formatLimit(0)).toBe('不限')
    expect(formatLimit(null)).toBe('不限')
    expect(formatLimit(undefined)).toBe('不限')
  })

  it('正数按本地化展示', () => {
    expect(formatLimit(100000)).toBe((100000).toLocaleString())
  })

  it('DAY 窗口取 request/token 进度较大者', () => {
    const pct = usagePercent(
      { window: 'DAY', requestCount: 40, tokenCount: 900, dimensions: { apiKeyId: 'k1' } },
      { dailyRequestLimit: 100, dailyTokenLimit: 1000 }
    )
    expect(pct).toBe(90)
  })

  it('限额为 0 时进度为 0（不限制）', () => {
    const pct = usagePercent(
      { window: 'DAY', requestCount: 999, tokenCount: 999, dimensions: { apiKeyId: 'k1' } },
      { dailyRequestLimit: 0, dailyTokenLimit: 0 }
    )
    expect(pct).toBe(0)
  })

  it('非 DAY 窗口不计算进度', () => {
    const pct = usagePercent(
      { window: 'MINUTE', requestCount: 50, tokenCount: 500, dimensions: { apiKeyId: 'k1' } },
      { dailyRequestLimit: 100, dailyTokenLimit: 1000 }
    )
    expect(pct).toBe(0)
  })

  it('进度封顶 100', () => {
    const pct = usagePercent(
      { window: 'DAY', requestCount: 200, tokenCount: 5000, dimensions: { apiKeyId: 'k1' } },
      { dailyRequestLimit: 100, dailyTokenLimit: 1000 }
    )
    expect(pct).toBe(100)
  })
})
