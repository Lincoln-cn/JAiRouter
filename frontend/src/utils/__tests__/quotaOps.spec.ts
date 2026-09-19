import { describe, expect, it } from 'vitest'
import {
  QUOTA_PRESETS,
  formatQuotaLimit,
  formatRemaining,
  quotaProgress
} from '@/utils/quotaOps'

describe('quotaOps presets & formatters', () => {
  it('presets include unlimited and three constrained profiles', () => {
    expect(QUOTA_PRESETS.map(p => p.id)).toEqual(['unlimited', 'dev', 'standard', 'strict'])
    const unlimited = QUOTA_PRESETS.find(p => p.id === 'unlimited')!
    expect(unlimited.limits.dailyRequestLimit).toBe(0)
    expect(unlimited.limits.dailyTokenLimit).toBe(0)
  })

  it('formatQuotaLimit treats 0 as unlimited', () => {
    expect(formatQuotaLimit(0)).toBe('不限')
    expect(formatQuotaLimit(null)).toBe('不限')
    expect(formatQuotaLimit(1000)).toBe((1000).toLocaleString())
  })

  it('formatRemaining shows used/limit or unlimited', () => {
    expect(formatRemaining(70, 100)).toContain('70')
    expect(formatRemaining(-1, 0)).toBe('不限')
    expect(formatRemaining(null, 100)).toContain('100')
  })

  it('quotaProgress caps at 100 and is 0 when unlimited', () => {
    expect(quotaProgress(30, 100)).toBe(30)
    expect(quotaProgress(200, 100)).toBe(100)
    expect(quotaProgress(50, 0)).toBe(0)
  })
})
