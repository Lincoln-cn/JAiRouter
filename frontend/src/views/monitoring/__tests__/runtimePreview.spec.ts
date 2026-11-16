import { describe, expect, it } from 'vitest'
import {
  countErrorEntries,
  readBreakerState,
  readCacheEntries,
  readDegradationLevel
} from '../runtimePreview'

/**
 * 运行时摘要提取的边界测试（#1172）.
 *
 * 这些函数决定确认气泡里出不出现「将移除 N 条」这类数字，所以两类断言都要有：
 * 有值时必须取到正确的那一个（字段名有历史遗留的两种写法），
 * 没值时必须返回 null（页面据此退回通用文案，而不是显示 0 条）。
 */
describe('runtimePreview：确认文案的影响面摘要', () => {
  describe('readCacheEntries', () => {
    it('优先读 cacheSize', () => {
      expect(readCacheEntries({ cacheSize: 12, size: 5 })).toBe(12)
    })

    it('没有 cacheSize 时回落到历史字段 size', () => {
      expect(readCacheEntries({ size: 5 })).toBe(5)
    })

    it('0 条是有效读数，不能被当成缺失', () => {
      expect(readCacheEntries({ cacheSize: 0 })).toBe(0)
    })

    it('字段缺失返回 null', () => {
      expect(readCacheEntries(null)).toBeNull()
      expect(readCacheEntries({})).toBeNull()
    })

    it('非数字（字符串 / NaN）返回 null', () => {
      expect(readCacheEntries({ cacheSize: '12' })).toBeNull()
      expect(readCacheEntries({ cacheSize: Number.NaN })).toBeNull()
    })
  })

  describe('countErrorEntries', () => {
    it('统计 errors 的键数', () => {
      expect(countErrorEntries({ errors: { a: 1, b: 2 } })).toBe(2)
    })

    it('统计 errorCounts 的键数', () => {
      expect(countErrorEntries({ errorCounts: { a: 1 } })).toBe(1)
    })

    it('不把顶层元数据字段算成错误项', () => {
      expect(countErrorEntries({ totalErrors: 9, anotherField: 1 })).toBeNull()
    })

    it('嵌套值是数组或非对象时返回 null', () => {
      expect(countErrorEntries({ errors: [1, 2, 3] })).toBeNull()
      expect(countErrorEntries({ errors: 3 })).toBeNull()
    })
  })

  describe('readBreakerState', () => {
    it('读 state', () => {
      expect(readBreakerState({ state: 'CLOSED' })).toBe('CLOSED')
    })

    it('没有 state 时读 currentState', () => {
      expect(readBreakerState({ currentState: 'OPEN' })).toBe('OPEN')
    })

    it('空串与缺失都返回 null', () => {
      expect(readBreakerState({ state: '   ' })).toBeNull()
      expect(readBreakerState(null)).toBeNull()
    })
  })

  describe('readDegradationLevel', () => {
    it('读 level', () => {
      expect(readDegradationLevel({ level: 'PARTIAL' })).toBe('PARTIAL')
    })

    it('缺失时返回 null（页面退回通用文案）', () => {
      expect(readDegradationLevel({})).toBeNull()
    })
  })
})
