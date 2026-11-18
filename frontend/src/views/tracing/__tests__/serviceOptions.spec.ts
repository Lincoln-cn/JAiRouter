import { describe, expect, it } from 'vitest'
import { FALLBACK_SERVICE_OPTIONS, normalizeServiceOptions } from '../serviceOptions'

describe('normalizeServiceOptions', () => {
  // 这一组是回归用例：后端 /tracing/query/services 返回的服务名在 `name` 字段，
  // 前端曾按 `serviceName` 取 ⇒ 下拉变成两个空选项，点了也选不中。
  it('后端对象数组（name 字段）能取出服务名', () => {
    const data = [
      { name: 'console', requestCount: 76 },
      { name: 'gateway', requestCount: 3 }
    ]
    expect(normalizeServiceOptions(data)).toEqual(['console', 'gateway'])
  })

  it('同时兼容早期的 serviceName 字段', () => {
    expect(normalizeServiceOptions([{ serviceName: 'legacy-a' }])).toEqual(['legacy-a'])
  })

  it('name 与 serviceName 同时存在时以 serviceName 为准，且不会重复', () => {
    expect(normalizeServiceOptions([{ name: 'same', serviceName: 'same' }])).toEqual(['same'])
  })

  it('兼容 { services: string[] } 形态', () => {
    expect(normalizeServiceOptions({ services: ['b-service', 'a-service'] })).toEqual([
      'a-service',
      'b-service'
    ])
  })

  it('空串、空白、非字符串与不认识的结构都丢弃，不会产生空选项', () => {
    const data = [{ name: '' }, { name: '   ' }, { name: 42 }, null, 'ok-service', { foo: 'bar' }]
    expect(normalizeServiceOptions(data)).toEqual(['ok-service'])
  })

  it('全部无效时返回空数组（由调用方决定兜底）', () => {
    expect(normalizeServiceOptions([{ name: '' }, {}])).toEqual([])
    expect(normalizeServiceOptions(undefined)).toEqual([])
    expect(normalizeServiceOptions({ services: 'not-an-array' })).toEqual([])
  })

  it('兜底清单非空且无重复，避免下拉再次空白', () => {
    expect(FALLBACK_SERVICE_OPTIONS.length).toBeGreaterThan(0)
    expect(new Set(FALLBACK_SERVICE_OPTIONS).size).toBe(FALLBACK_SERVICE_OPTIONS.length)
  })
})
