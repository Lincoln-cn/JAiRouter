<template>
  <div class="chat-container">
    <!-- 顶部工具栏 -->
    <div class="chat-toolbar">
      <div class="toolbar-left">
        <el-select
          v-model="selectedModel"
          :placeholder="'选择模型'"
          :loading="instancesLoading"
          filterable
          class="model-select"
        >
          <template #prefix>
            <el-icon><Cpu /></el-icon>
          </template>
          <el-option
            v-for="inst in availableInstances"
            :key="inst.instanceId"
            :label="inst.name"
            :value="inst.name"
            :disabled="inst.healthStatus === 'UNHEALTHY'"
          >
            <div class="instance-option">
              <span
                class="health-indicator"
                :class="{
                  'health-healthy': inst.healthStatus === 'HEALTHY',
                  'health-unhealthy': inst.healthStatus === 'UNHEALTHY',
                  'health-unknown': inst.healthStatus === 'UNKNOWN' || !inst.healthStatus
                }"
              >
                ●
              </span>
              <span class="instance-name">{{ inst.name }}</span>
              <span
                v-if="inst.healthStatus === 'UNHEALTHY'"
                class="health-status-text"
              >
                (离线)
              </span>
              <span
                v-else-if="inst.healthStatus === 'UNKNOWN' || !inst.healthStatus"
                class="health-status-text unknown"
              >
                (未知)
              </span>
            </div>
          </el-option>
        </el-select>
        <el-button
          text
          @click="showConfig = !showConfig"
        >
          <el-icon><Setting /></el-icon>
          {{ showConfig ? '隐藏配置' : '参数配置' }}
        </el-button>
      </div>
      <div class="toolbar-right">
        <el-button
          text
          @click="handleNewChat"
        >
          <el-icon><Plus /></el-icon>
          新对话
        </el-button>
        <el-button
          text
          @click="handleClear"
        >
          <el-icon><Delete /></el-icon>
          清空
        </el-button>
      </div>
    </div>

    <!-- 配置面板 -->
    <Transition name="slide-down">
      <div
        v-if="showConfig"
        class="config-panel"
      >
        <ChatConfigPanel
          v-model="chatConfig"
          :disabled="isLoading"
        />
      </div>
    </Transition>

    <!-- 消息列表 -->
    <div
      ref="messageListRef"
      class="message-list"
    >
      <!-- 空状态 -->
      <div
        v-if="messages.length === 0 && !isLoading"
        class="empty-state"
      >
        <el-icon
          :size="64"
          color="#909399"
        >
          <ChatDotRound />
        </el-icon>
        <div class="empty-title">开始新的对话</div>
        <div class="empty-subtitle">输入消息，与 AI 开始交流</div>
        <div class="suggestions">
          <el-button
            v-for="suggestion in suggestions"
            :key="suggestion"
            round
            size="small"
            @click="handleSuggestionClick(suggestion)"
          >
            {{ suggestion }}
          </el-button>
        </div>
      </div>

      <!-- 消息列表 -->
      <MessageList
        :messages="messages"
        :loading="isLoading"
        :streaming-content="streamingContent"
        @copy="handleCopyMessage"
        @regenerate="handleRegenerateMessage"
      />
    </div>

    <!-- 输入区域 -->
    <div class="input-area">
      <MessageInput
        ref="inputRef"
        :disabled="!selectedModel"
        :loading="isLoading"
        :placeholder="inputPlaceholder"
        @submit="handleSendMessage"
        @cancel="handleCancelRequest"
      />
      <div class="input-tips">
        <span>按 Enter 发送，Shift + Enter 换行</span>
        <span
          v-if="!selectedModel"
          class="tip-warning"
        >请先选择模型</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { Setting, Plus, Delete, ChatDotRound, Cpu } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import MessageList from './MessageList.vue'
import MessageInput from './MessageInput.vue'
import ChatConfigPanel from './ChatConfigPanel.vue'
import { useChatSession } from '../../composables/useChatSession'
import { useStreaming } from '../../composables/useStreaming'
import { usePlaygroundData } from '@/composables/usePlaygroundData'
import { sendServiceRequest } from '@/api/playground'
import type { ChatMessage, ChatRequestConfig } from '../../types/playground'
import { parseErrorMessage, getErrorSuggestion } from '../../utils/errorHandler'

// Refs
const messageListRef = ref<HTMLElement>()
const inputRef = ref<InstanceType<typeof MessageInput>>()

// 状态
const showConfig = ref(false)
const selectedModel = ref('')
const isLoading = ref(false)
const streamingContent = ref('')

// 配置
const chatConfig = ref<Partial<ChatRequestConfig>>({
  stream: true,
  temperature: 0.7,
  maxTokens: 4096,
  topP: 1,
  frequencyPenalty: 0,
  presencePenalty: 0
})

// 会话管理
const {
  messages,
  initialize,
  createNewSession,
  addMessage,
  updateLastMessage,
  clearMessages,
  updateModel
} = useChatSession()

// 数据获取
const { availableInstances, instancesLoading, initializeData, refreshData } =
  usePlaygroundData('chat')

// 流式处理
const { isStreaming, cancelStream, createStreamRequest, handleStreamResponse } =
  useStreaming()

// 输入提示
const inputPlaceholder = computed(() => {
  if (!selectedModel.value) return '请先选择模型...'
  if (isLoading.value) return '正在生成回复...'
  return '输入消息...'
})

// 快捷建议
const suggestions = [
  '你好，请介绍一下你自己',
  '帮我写一段代码',
  '解释一下什么是机器学习'
]

// 初始化
onMounted(() => {
  initialize()
  initializeData()
})

// 监听消息变化，自动滚动到底部
watch(
  messages,
  () => {
    nextTick(() => {
      scrollToBottom()
    })
  },
  { deep: true }
)

watch(streamingContent, () => {
  nextTick(() => {
    scrollToBottom()
  })
})

// 滚动到底部
const scrollToBottom = () => {
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}

// 处理建议点击
const handleSuggestionClick = (suggestion: string) => {
  inputRef.value?.setContent(suggestion)
}

// 发送消息
const handleSendMessage = async (content: string) => {
  if (!content.trim() || !selectedModel.value || isLoading.value) return

  // 添加用户消息
  const userMessage: ChatMessage = {
    role: 'user',
    content: content.trim()
  }
  addMessage(userMessage)

  // 清空输入
  inputRef.value?.clear()

  // 准备请求
  isLoading.value = true
  streamingContent.value = ''

  // 添加空的助手消息（用于流式更新）
  const assistantMessage: ChatMessage = {
    role: 'assistant',
    content: ''
  }
  addMessage(assistantMessage)

  try {
    const config: ChatRequestConfig = {
      model: selectedModel.value,
      messages: messages.value.slice(0, -1).map((m) => ({
        role: m.role,
        content: m.content
      })),
      stream: chatConfig.value.stream ?? true,
      temperature: chatConfig.value.temperature ?? 0.7,
      maxTokens: chatConfig.value.maxTokens ?? 4096,
      topP: chatConfig.value.topP ?? 1,
      frequencyPenalty: chatConfig.value.frequencyPenalty ?? 0,
      presencePenalty: chatConfig.value.presencePenalty ?? 0
    }

    const headers: Record<string, string> = {}
    const token = localStorage.getItem('admin_token')
    if (token) {
      headers['Jairouter_Token'] = token
    }

    if (config.stream) {
      // 流式请求
      const baseURL = import.meta.env.VITE_API_BASE_URL || ''
      const url = `${baseURL}/v1/chat/completions`

      const response = await createStreamRequest(url, config, headers)

      const fullContent = await handleStreamResponse(response, {
        onChunk: (chunk) => {
          streamingContent.value += chunk
          updateLastMessage(streamingContent.value)
        },
        onComplete: (content) => {
          updateLastMessage(content)
          streamingContent.value = ''
        }
      })
    } else {
      // 非流式请求
      const response = await sendServiceRequest('chat', config, headers)
      const assistantContent =
        response.data?.choices?.[0]?.message?.content || ''

      updateLastMessage(assistantContent)
    }
  } catch (error: any) {
    const errorMessage = parseErrorMessage(error, '对话请求')
    const suggestion = getErrorSuggestion(error)

    updateLastMessage(`❌ 错误: ${errorMessage}`)

    if (suggestion) {
      ElMessage({
        type: 'error',
        message: `${errorMessage}\n\n💡 建议: ${suggestion}`,
        duration: 6000,
        showClose: true
      })
    } else {
      ElMessage.error(errorMessage)
    }
  } finally {
    isLoading.value = false
    isStreaming.value = false
  }
}

// 取消请求
const handleCancelRequest = () => {
  if (isStreaming.value) {
    cancelStream()
    isLoading.value = false
    ElMessage.info('已取消请求')
  }
}

// 新对话
const handleNewChat = () => {
  createNewSession(selectedModel.value)
  ElMessage.success('已创建新对话')
}

// 清空当前对话
const handleClear = () => {
  clearMessages()
  ElMessage.success('已清空对话')
}

// 复制消息
const handleCopyMessage = (content: string) => {
  // MessageBubble 已经处理了复制，这里可以添加额外的逻辑（如果需要）
}

// 重新生成 - 重新发送最后一个用户消息
const handleRegenerateMessage = async () => {
  if (isLoading.value) return

  // 找到最后一个用户消息
  const lastUserMessage = [...messages.value].reverse().find(m => m.role === 'user')
  if (!lastUserMessage) {
    ElMessage.warning('没有可以重新生成的消息')
    return
  }

  // 移除最后的助手消息
  if (messages.value.length > 0 && messages.value[messages.value.length - 1].role === 'assistant') {
    messages.value.pop()
  }

  // 准备请求
  isLoading.value = true
  streamingContent.value = ''

  // 添加空的助手消息（用于流式更新）
  const assistantMessage: ChatMessage = {
    role: 'assistant',
    content: ''
  }
  addMessage(assistantMessage)

  try {
    const config: ChatRequestConfig = {
      model: selectedModel.value,
      messages: messages.value.slice(0, -1).map((m) => ({
        role: m.role,
        content: m.content
      })),
      stream: chatConfig.value.stream ?? true,
      temperature: chatConfig.value.temperature ?? 0.7,
      maxTokens: chatConfig.value.maxTokens ?? 4096,
      topP: chatConfig.value.topP ?? 1,
      frequencyPenalty: chatConfig.value.frequencyPenalty ?? 0,
      presencePenalty: chatConfig.value.presencePenalty ?? 0
    }

    const headers: Record<string, string> = {}
    const token = localStorage.getItem('admin_token')
    if (token) {
      headers['Jairouter_Token'] = token
    }

    if (config.stream) {
      // 流式请求
      const baseURL = import.meta.env.VITE_API_BASE_URL || ''
      const url = `${baseURL}/v1/chat/completions`

      const response = await createStreamRequest(url, config, headers)

      const fullContent = await handleStreamResponse(response, {
        onChunk: (chunk) => {
          streamingContent.value += chunk
          updateLastMessage(streamingContent.value)
        },
        onComplete: (content) => {
          updateLastMessage(content)
          streamingContent.value = ''
        }
      })
    } else {
      // 鞞流式请求
      const response = await sendServiceRequest('chat', config, headers)
      const assistantContent =
        response.data?.choices?.[0]?.message?.content || ''

      updateLastMessage(assistantContent)
    }
  } catch (error: any) {
    const errorMessage = parseErrorMessage(error, '对话请求')
    const suggestion = getErrorSuggestion(error)

    updateLastMessage(`❌ 错误: ${errorMessage}`)

    if (suggestion) {
      ElMessage({
        type: 'error',
        message: `${errorMessage}\n\n💡 建议: ${suggestion}`,
        duration: 6000,
        showClose: true
      })
    } else {
      ElMessage.error(errorMessage)
    }
  } finally {
    isLoading.value = false
    isStreaming.value = false
  }
}

// 暴露方法
defineExpose({
  refreshData,
  handleNewChat,
  handleClear
})
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  background-color: #f5f5f5;
  border-radius: 4px;
}

.chat-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  border-radius: 4px 4px 0 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.model-select {
  width: 240px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.config-panel {
  padding: 12px 16px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
}

.slide-down-enter-from,
.slide-down-leave-to {
  transform: translateY(-10px);
  opacity: 0;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  min-height: 0;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}

.empty-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin-top: 16px;
  margin-bottom: 8px;
}

.empty-subtitle {
  font-size: 14px;
  color: #909399;
  margin-bottom: 24px;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-width: 600px;
}

.input-area {
  padding: 12px 16px;
  background-color: #fff;
  border-top: 1px solid #e4e7ed;
  border-radius: 0 0 4px 4px;
}

.input-tips {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.tip-warning {
  color: #f56c6c;
}

/* 健康状态指示器样式 */
.instance-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.health-indicator {
  font-size: 12px;
}

.health-healthy {
  color: #67c23a;
}

.health-unhealthy {
  color: #f56c6c;
}

.health-unknown {
  color: #909399;
}

.instance-name {
  flex: 1;
}

.health-status-text {
  font-size: 12px;
  color: #f56c6c;
}

.health-status-text.unknown {
  color: #909399;
}
</style>