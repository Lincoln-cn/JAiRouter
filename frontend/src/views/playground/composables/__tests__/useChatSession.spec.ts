/**
 * useChatSession 落盘：深层高频变更合并写入 localStorage，尾次调用保留最终内容
 *
 * 复现 issue #135 的持久化放大路径：旧实现每个 token 都会深层改写最后一条消息，
 * 触发 deep watch → saveToStorage（全量 JSON.stringify + setItem）。
 * 断言合并写入次数远小于变更次数，且最终落盘内容仍为完整文本。
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { useChatSession } from '@/views/playground/composables/useChatSession'

const STORAGE_KEY = 'jairouter_chat_sessions'

describe('useChatSession persistence', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
    localStorage.clear()
  })

  it('coalesces rapid deep mutations into far fewer writes and keeps the final content', async () => {
    const { sessions, initialize, addMessage } = useChatSession()
    initialize()
    addMessage({ role: 'user', content: 'hi' })
    addMessage({ role: 'assistant', content: '' })

    const setItemSpy = vi.spyOn(localStorage, 'setItem')
    setItemSpy.mockClear()

    const N = 50
    const last = () => sessions.value[0].messages[sessions.value[0].messages.length - 1]
    for (let i = 1; i <= N; i++) {
      // 模拟旧实现里每 token 深层改写最后一条消息
      last().content = 'x'.repeat(i)
      await nextTick()
    }
    vi.advanceTimersByTime(500)

    // 远少于 N 次（leading + trailing 合并）
    expect(setItemSpy.mock.calls.length).toBeGreaterThan(0)
    expect(setItemSpy.mock.calls.length).toBeLessThan(N / 2)

    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY)!)
    const msgs = stored[0].messages
    expect(msgs[msgs.length - 1].content).toBe('x'.repeat(N))
  })

  it('updateLastMessage persists the final content immediately (explicit save on complete)', () => {
    const { initialize, addMessage, updateLastMessage } = useChatSession()
    initialize()
    addMessage({ role: 'user', content: 'hi' })
    addMessage({ role: 'assistant', content: '' })

    const full = 'the final streamed answer'
    updateLastMessage(full)

    // 不依赖节流尾次调用，显式立即落盘
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY)!)
    const msgs = stored[0].messages
    expect(msgs[msgs.length - 1].content).toBe(full)
  })
})
