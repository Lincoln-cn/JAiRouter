import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { useViewport } from '@/composables/useViewport'
import type { ViewportState } from '@/composables/useViewport'

type ChangeHandler = (event: MediaQueryListEvent) => void

interface FakeMediaQueryList {
  matches: boolean
  media: string
  onchange: null
  addEventListener: ReturnType<typeof vi.fn>
  removeEventListener: ReturnType<typeof vi.fn>
  addListener: ReturnType<typeof vi.fn>
  removeListener: ReturnType<typeof vi.fn>
  dispatchEvent: ReturnType<typeof vi.fn>
  trigger: (matches: boolean) => void
}

function createMediaQueryList(initial: boolean): FakeMediaQueryList {
  const handlers = new Set<ChangeHandler>()

  const query: FakeMediaQueryList = {
    matches: initial,
    media: '',
    onchange: null,
    addEventListener: vi.fn((type: string, handler: ChangeHandler) => {
      if (type === 'change') handlers.add(handler)
    }),
    removeEventListener: vi.fn((type: string, handler: ChangeHandler) => {
      if (type === 'change') handlers.delete(handler)
    }),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(() => true),
    trigger(next: boolean) {
      query.matches = next
      handlers.forEach((handler) => handler({ matches: next } as MediaQueryListEvent))
    }
  }

  return query
}

function mountViewport(): { wrapper: ReturnType<typeof mount>; state: ViewportState } {
  let state: ViewportState | undefined

  const wrapper = mount(
    defineComponent({
      setup() {
        state = useViewport()
        return () => h('div')
      }
    })
  )

  if (!state) {
    throw new Error('useViewport 未返回状态')
  }

  return { wrapper, state }
}

let originalMatchMedia: typeof window.matchMedia

function mockMatchMedia(narrow: FakeMediaQueryList, compact: FakeMediaQueryList): void {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: vi.fn((query: string) => {
      // 两条查询是互斥的；按 max-width:767 区分，避免依赖调用顺序
      return (query.includes('767') ? narrow : compact) as unknown as MediaQueryList
    })
  })
}

beforeEach(() => {
  originalMatchMedia = window.matchMedia
})

afterEach(() => {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: originalMatchMedia
  })
})

describe('useViewport', () => {
  it('matchMedia 存在时读取初值，并在 change 时同步更新', () => {
    const narrow = createMediaQueryList(false)
    const compact = createMediaQueryList(false)
    mockMatchMedia(narrow, compact)

    const { state } = mountViewport()

    expect(state.isNarrow.value).toBe(false)
    expect(state.isCompact.value).toBe(false)

    narrow.trigger(true)
    expect(state.isNarrow.value).toBe(true)
    expect(state.isCompact.value).toBe(false)

    narrow.trigger(false)
    compact.trigger(true)
    expect(state.isNarrow.value).toBe(false)
    expect(state.isCompact.value).toBe(true)
  })

  it('compact 与 narrow 互斥（临界值同时命中时以 narrow 为准）', () => {
    const narrow = createMediaQueryList(true)
    const compact = createMediaQueryList(true)
    mockMatchMedia(narrow, compact)

    const { state } = mountViewport()

    expect(state.isNarrow.value).toBe(true)
    expect(state.isCompact.value).toBe(false)
  })

  it('组件卸载时移除两个媒体查询的 change 监听', () => {
    const narrow = createMediaQueryList(false)
    const compact = createMediaQueryList(false)
    mockMatchMedia(narrow, compact)

    const { wrapper, state } = mountViewport()

    const narrowHandler = narrow.addEventListener.mock.calls[0]?.[1] as ChangeHandler
    const compactHandler = compact.addEventListener.mock.calls[0]?.[1] as ChangeHandler
    expect(narrowHandler).toBeTypeOf('function')
    expect(compactHandler).toBeTypeOf('function')

    wrapper.unmount()

    expect(narrow.removeEventListener).toHaveBeenCalledWith('change', narrowHandler)
    expect(compact.removeEventListener).toHaveBeenCalledWith('change', compactHandler)

    // 卸载后即使再触发 change，也不应继续写入已废弃的状态
    narrow.trigger(true)
    expect(state.isNarrow.value).toBe(false)
  })

  it('window.matchMedia 缺失时返回保守默认（宽屏，两者均为 false）', () => {
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      writable: true,
      value: undefined
    })

    const { state } = mountViewport()

    expect(state.isNarrow.value).toBe(false)
    expect(state.isCompact.value).toBe(false)
  })
})
