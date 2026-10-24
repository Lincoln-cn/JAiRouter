/**
 * PII / 脱敏配置管理 API
 * 契约与后端 /api/config/sanitization 对齐（v3.2.0 TDD）
 */
import { beforeEach, describe, expect, it, vi } from 'vitest'

const get = vi.fn()
const put = vi.fn()
const post = vi.fn()

vi.mock('@/utils/request', () => ({
  default: {
    get: (...args: unknown[]) => get(...(args as [])),
    put: (...args: unknown[]) => put(...(args as [])),
    post: (...args: unknown[]) => post(...(args as []))
  }
}))

import {
  getSanitizationConfig,
  getSanitizationRules,
  testSanitization,
  updateSanitizationConfig
} from '@/api/sanitization'

describe('sanitization API client', () => {
  beforeEach(() => {
    get.mockReset()
    put.mockReset()
    post.mockReset()
  })

  it('GET /config/sanitization 解析 RouterResponse.data', async () => {
    const payload = {
      response: {
        enabled: false,
        piiPatterns: [],
        sensitiveWords: [],
        maskingChar: '*',
        logSanitization: false,
        failOnError: true,
        preserveJsonStructure: true
      },
      ruleCount: 3
    }
    get.mockResolvedValue({ data: { data: payload } })

    const cfg = await getSanitizationConfig()
    expect(get).toHaveBeenCalledWith('/config/sanitization')
    expect(cfg?.ruleCount).toBe(3)
    expect(cfg?.response.failOnError).toBe(true)
  })

  it('PUT /config/sanitization 只提交 response 部分更新', async () => {
    put.mockResolvedValue({
      data: {
        data: {
          response: { enabled: false },
          ruleCount: 1
        }
      }
    })

    await updateSanitizationConfig({
      response: { enabled: false, preserveJsonStructure: true }
    })

    expect(put).toHaveBeenCalledWith('/config/sanitization', {
      response: { enabled: false, preserveJsonStructure: true }
    })
  })

  it('GET /config/sanitization/rules 返回规则数组', async () => {
    get.mockResolvedValue({
      data: {
        data: [
          {
            ruleId: 'response-pii-pattern-1',
            type: 'PII_PATTERN',
            pattern: '\\d{11}',
            enabled: true
          }
        ]
      }
    })

    const rules = await getSanitizationRules()
    expect(get).toHaveBeenCalledWith('/config/sanitization/rules')
    expect(rules).toHaveLength(1)
    expect(rules[0].ruleId).toBe('response-pii-pattern-1')
  })

  it('POST /config/sanitization/test 提交样例并返回 before/after', async () => {
    post.mockResolvedValue({
      data: {
        data: {
          before: 'phone 13800138000',
          after: 'phone ***********',
          matchedRuleIds: ['response-pii-pattern-1'],
          contentType: 'text/plain'
        }
      }
    })

    const result = await testSanitization('phone 13800138000')
    expect(post).toHaveBeenCalledWith('/config/sanitization/test', {
      sample: 'phone 13800138000',
      contentType: 'text/plain'
    })
    expect(result.after).toContain('***')
    expect(result.matchedRuleIds).toContain('response-pii-pattern-1')
  })
})
