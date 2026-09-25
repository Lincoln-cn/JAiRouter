/**
 * Issue #131：ChatContainer 卸载时必须中止进行中的流。
 *
 * 此前文件没有 onBeforeUnmount/onUnmounted，也从不 abort 在途的
 * AbortController，卸载后 while(true) reader.read() 循环仍继续消费并改写 ref。
 *
 * 本用例：挂载后立即卸载，断言 useStreaming 暴露的 cancelStream 被调用。
 */
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

const { cancelStream } = vi.hoisted(() => ({ cancelStream: vi.fn() }))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn()
  }
}))

vi.mock('@element-plus/icons-vue', () => ({
  Setting: { template: '<i />' },
  Plus: { template: '<i />' },
  Delete: { template: '<i />' },
  ChatDotRound: { template: '<i />' },
  Cpu: { template: '<i />' }
}))

vi.mock('@/views/playground/composables/useStreaming', async () => {
  const { ref } = await import('vue')
  return {
    useStreaming: () => ({
      isStreaming: ref(false),
      streamingContent: ref(''),
      cancelStream,
      createStreamRequest: vi.fn(),
      handleStreamResponse: vi.fn()
    })
  }
})

vi.mock('@/views/playground/composables/useChatSession', async () => {
  const { ref, computed } = await import('vue')
  return {
    useChatSession: () => {
      const messages = ref<any[]>([])
      return {
        messages: computed(() => messages.value),
        initialize: vi.fn(),
        createNewSession: vi.fn(),
        addMessage: vi.fn(),
        updateLastMessage: vi.fn(),
        clearMessages: vi.fn(),
        updateModel: vi.fn()
      }
    }
  }
})

vi.mock('@/composables/usePlaygroundData', async () => {
  const { ref } = await import('vue')
  return {
    usePlaygroundData: () => ({
      availableInstances: ref([]),
      instancesLoading: ref(false),
      initializeData: vi.fn(),
      refreshData: vi.fn()
    })
  }
})

vi.mock('@/composables/useRoutePreselect', () => ({
  useRoutePreselect: () => ({ requestedServiceType: () => undefined }),
  preselectInstanceName: () => undefined
}))

vi.mock('@/api/playground', () => ({
  sendServiceRequest: vi.fn()
}))

vi.mock('@/views/playground/utils/errorHandler', () => ({
  parseErrorMessage: vi.fn((e: unknown) => String(e)),
  getErrorSuggestion: vi.fn(() => null)
}))

import ChatContainer from '@/views/playground/components/chat/ChatContainer.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'zh-CN',
  missingWarn: false,
  fallbackWarn: false,
  messages: { 'zh-CN': {} }
})

const stubs = {
  'el-select': true,
  'el-option': true,
  'el-button': true,
  'el-icon': true,
  MessageList: true,
  MessageInput: true,
  ChatConfigPanel: true
}

describe('ChatContainer 卸载中止流（issue #131）', () => {
  beforeEach(() => {
    cancelStream.mockClear()
  })

  it('unmount 时调用 cancelStream 中止在途请求', () => {
    const wrapper = mount(ChatContainer, { global: { plugins: [i18n], stubs } })
    expect(cancelStream).not.toHaveBeenCalled()

    wrapper.unmount()

    expect(cancelStream).toHaveBeenCalledTimes(1)
  })
})
