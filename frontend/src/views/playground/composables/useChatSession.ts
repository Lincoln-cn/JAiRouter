import { ref, computed, watch } from 'vue'
import type { ChatMessage } from '../types/playground'

export interface ChatSession {
  id: string
  title: string
  messages: ChatMessage[]
  model: string
  createdAt: string
  updatedAt: string
}

const STORAGE_KEY = 'jairouter_chat_sessions'

export function useChatSession() {
  const sessions = ref<ChatSession[]>([])
  const activeSessionId = ref<string>('')
  const isInitialized = ref(false)

  // 当前活跃会话
  const activeSession = computed(() => {
    return sessions.value.find(s => s.id === activeSessionId.value) || null
  })

  // 当前会话的消息列表
  const messages = computed(() => {
    return activeSession.value?.messages || []
  })

  // 初始化：从 localStorage 加载
  const initialize = () => {
    if (isInitialized.value) return

    try {
      const stored = localStorage.getItem(STORAGE_KEY)
      if (stored) {
        sessions.value = JSON.parse(stored)
      }
    } catch {
      sessions.value = []
    }

    // 如果没有会话，创建一个新的
    if (sessions.value.length === 0) {
      createNewSession()
    } else {
      activeSessionId.value = sessions.value[0].id
    }

    isInitialized.value = true
  }

  // 保存到 localStorage
  const saveToStorage = () => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions.value))
    } catch {
      console.error('Failed to save chat sessions')
    }
  }

  // 创建新会话
  const createNewSession = (model: string = ''): ChatSession => {
    const session: ChatSession = {
      id: `session_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
      title: '新对话',
      messages: [],
      model,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }
    sessions.value.unshift(session)
    activeSessionId.value = session.id
    saveToStorage()
    return session
  }

  // 删除会话
  const deleteSession = (sessionId: string) => {
    const index = sessions.value.findIndex(s => s.id === sessionId)
    if (index !== -1) {
      sessions.value.splice(index, 1)

      // 如果删除的是当前活跃会话
      if (activeSessionId.value === sessionId) {
        if (sessions.value.length > 0) {
          activeSessionId.value = sessions.value[0].id
        } else {
          createNewSession()
        }
      }
      saveToStorage()
    }
  }

  // 切换会话
  const switchSession = (sessionId: string) => {
    if (sessions.value.find(s => s.id === sessionId)) {
      activeSessionId.value = sessionId
    }
  }

  // 添加消息
  const addMessage = (message: ChatMessage) => {
    if (!activeSession.value) return

    activeSession.value.messages.push(message)
    activeSession.value.updatedAt = new Date().toISOString()

    // 更新会话标题（使用第一条用户消息）
    if (activeSession.value.title === '新对话' && message.role === 'user') {
      activeSession.value.title = message.content.slice(0, 30) + (message.content.length > 30 ? '...' : '')
    }

    saveToStorage()
  }

  // 更新最后一条消息（用于流式更新）
  const updateLastMessage = (content: string) => {
    if (!activeSession.value || activeSession.value.messages.length === 0) return

    const lastMessage = activeSession.value.messages[activeSession.value.messages.length - 1]
    lastMessage.content = content
    activeSession.value.updatedAt = new Date().toISOString()
  }

  // 清空当前会话消息
  const clearMessages = () => {
    if (!activeSession.value) return

    activeSession.value.messages = []
    activeSession.value.title = '新对话'
    activeSession.value.updatedAt = new Date().toISOString()
    saveToStorage()
  }

  // 更新会话模型
  const updateModel = (model: string) => {
    if (!activeSession.value) return
    activeSession.value.model = model
    saveToStorage()
  }

  // 监听变化自动保存
  watch(
    () => sessions.value,
    () => {
      if (isInitialized.value) {
        saveToStorage()
      }
    },
    { deep: true }
  )

  return {
    sessions,
    activeSessionId,
    activeSession,
    messages,
    isInitialized,
    initialize,
    createNewSession,
    deleteSession,
    switchSession,
    addMessage,
    updateLastMessage,
    clearMessages,
    updateModel
  }
}