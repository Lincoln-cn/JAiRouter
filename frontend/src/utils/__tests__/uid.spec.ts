/**
 * 客户端唯一 ID：批量唯一性、前缀透传与输出形态
 */
import { describe, expect, it } from 'vitest'
import { uid } from '@/utils/uid'

describe('uid', () => {
  it('generates all-unique ids in a large batch', () => {
    const batch = 10000
    const ids = Array.from({ length: batch }, () => uid())
    expect(new Set(ids).size).toBe(batch)
  })

  it('honours the prefix', () => {
    const id = uid('session_')
    expect(id.startsWith('session_')).toBe(true)
    // 前缀之外仍是非空负载
    expect(id.length).toBeGreaterThan('session_'.length)
  })

  it('produces a sane shape', () => {
    // 含前缀与不含前缀两种形态都必须是可直接当 key 用的字符串
    const ids = [...Array.from({ length: 100 }, () => uid()), ...Array.from({ length: 100 }, () => uid('pfx_'))]
    for (const id of ids) {
      expect(typeof id).toBe('string')
      expect(id).not.toBe('')
      expect(id).not.toContain('NaN')
      expect(id).not.toContain('undefined')
    }
  })
})
