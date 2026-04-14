<template>
  <div class="message-list-container">
    <MessageBubble
      v-for="(message, index) in displayMessages"
      :key="index"
      :role="message.role"
      :content="message.content"
      :timestamp="message.timestamp"
      :loading="isLoading && index === displayMessages.length - 1 && message.role === 'assistant' && !message.content"
      :show-actions="message.role === 'assistant' && !isLoading"
      @copy="handleCopy"
      @regenerate="handleRegenerate"
    />

    <!-- 流式响应时显示实时内容 -->
    <MessageBubble
      v-if="isLoading && streamingContent"
      role="assistant"
      :content="streamingContent"
      :loading="false"
      :show-actions="false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import MessageBubble from '../common/MessageBubble.vue'
import type { ChatMessage } from '../../types/playground'

interface Props {
  messages: ChatMessage[]
  loading?: boolean
  streamingContent?: string
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  streamingContent: ''
})

const emit = defineEmits<{
  copy: [content: string]
  regenerate: []
}>()

// 显示的消息列表（过滤掉正在流式更新的空助手消息）
const displayMessages = computed(() => {
  if (props.loading && props.streamingContent) {
    // 流式响应时，过滤掉最后一个空的助手消息
    return props.messages.filter((msg, index) => {
      if (
        index === props.messages.length - 1 &&
        msg.role === 'assistant' &&
        !msg.content
      ) {
        return false
      }
      return true
    })
  }
  return props.messages
})

const isLoading = computed(() => props.loading)

const handleCopy = (content: string) => {
  emit('copy', content)
}

const handleRegenerate = () => {
  emit('regenerate')
}
</script>

<style scoped>
.message-list-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>