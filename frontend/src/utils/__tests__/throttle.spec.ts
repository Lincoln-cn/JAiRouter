/**
 * throttle：leading + trailing 合并调用，尾次调用保证最后一次参数不丢失
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { throttle } from '@/utils/throttle'

describe('throttle', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('invokes immediately on the leading edge', () => {
    const fn = vi.fn()
    const t = throttle(fn, 400)
    t('a')
    expect(fn).toHaveBeenCalledTimes(1)
    expect(fn).toHaveBeenCalledWith('a')
  })

  it('coalesces a burst into leading + one trailing call with the latest args', () => {
    const fn = vi.fn()
    const t = throttle(fn, 400)
    t('a')
    t('b')
    t('c')
    expect(fn).toHaveBeenCalledTimes(1)
    vi.advanceTimersByTime(400)
    expect(fn).toHaveBeenCalledTimes(2)
    expect(fn).toHaveBeenLastCalledWith('c')
  })

  it('always delivers the final call (trailing guarantee)', () => {
    const fn = vi.fn()
    const t = throttle(fn, 400)
    for (let i = 0; i < 50; i++) {
      t(String(i))
    }
    vi.advanceTimersByTime(400)
    expect(fn).toHaveBeenLastCalledWith('49')
    expect(fn.mock.calls.length).toBeLessThan(5)
  })

  it('flush forces the pending trailing call immediately', () => {
    const fn = vi.fn()
    const t = throttle(fn, 400)
    t('a')
    t('b')
    t.flush()
    expect(fn).toHaveBeenCalledTimes(2)
    expect(fn).toHaveBeenLastCalledWith('b')
  })

  it('cancel drops the pending trailing call', () => {
    const fn = vi.fn()
    const t = throttle(fn, 400)
    t('a')
    t('b')
    t.cancel()
    vi.advanceTimersByTime(400)
    expect(fn).toHaveBeenCalledTimes(1)
  })

  it('cancel resets the window so the next call is a leading call', () => {
    const fn = vi.fn()
    const t = throttle(fn, 400)
    t('a')
    t('b')
    t.cancel()
    t('c')
    expect(fn).toHaveBeenCalledTimes(2)
    expect(fn).toHaveBeenLastCalledWith('c')
  })
})
