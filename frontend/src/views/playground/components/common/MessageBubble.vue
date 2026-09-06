<template>
  <div :class="['message-bubble', `message-${role}`]">
    <div class="message-avatar">
      <el-avatar
        :size="36"
        :style="{ backgroundColor: avatarColor }"
      >
        <el-icon :size="20">
          <component :is="avatarIcon" />
        </el-icon>
      </el-avatar>
    </div>
    <div class="message-content">
      <div class="message-header">
        <span class="message-role">{{ roleLabel }}</span>
        <span v-if="timestamp" class="message-time">{{ formatTime(timestamp) }}</span>
      </div>
      <div class="message-body">
        <!-- 用户消息直接显示文本 -->
        <template v-if="role === 'user'">
          <div class="message-text">{{ content }}</div>
        </template>
        <!-- 助手消息使用 Markdown 渲染 -->
        <template v-else>
          <MarkdownRenderer :content="content" />
        </template>
        <!-- 加载指示器 -->
        <div v-if="loading" class="message-loading">
          <span class="loading-dot"></span>
          <span class="loading-dot"></span>
          <span class="loading-dot"></span>
        </div>
      </div>
      <!-- 操作按钮 -->
      <div v-if="showActions" class="message-actions">
        <el-button
          text
          size="small"
          @click="handleCopy"
        >
          <el-icon><DocumentCopy /></el-icon>
          {{ t('playgroundCommon.action.copy') }}
        </el-button>
        <el-button
          v-if="role === 'assistant'"
          text
          size="small"
          @click="handleRegenerate"
        >
          <el-icon><RefreshRight /></el-icon>
          {{ t('playgroundCommon.action.regenerate') }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { User, Monitor, DocumentCopy, RefreshRight } from '@element-plus/icons-vue'
import MarkdownRenderer from './MarkdownRenderer.vue'
import { ElMessage } from 'element-plus'

interface Props {
  role: 'user' | 'assistant' | 'system'
  content: string
  timestamp?: string
  loading?: boolean
  showActions?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  showActions: true
})

const emit = defineEmits<{
  copy: [content: string]
  regenerate: []
}>()

const { t } = useI18n()

const roleLabel = computed(() => {
  switch (props.role) {
    case 'user':
      return t('playgroundCommon.role.user')
    case 'assistant':
      return t('playgroundCommon.role.assistant')
    case 'system':
      return t('playgroundCommon.role.system')
    default:
      return props.role
  }
})

const avatarIcon = computed(() => {
  return props.role === 'user' ? User : Monitor
})

const avatarColor = computed(() => {
  return props.role === 'user' ? '#409eff' : '#67c23a'
})

const formatTime = (time: string) => {
  const date = new Date(time)
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
}

const handleCopy = async () => {
  try {
    await navigator.clipboard.writeText(props.content)
    ElMessage.success(t('playgroundCommon.message.copiedToClipboard'))
    emit('copy', props.content)
  } catch {
    ElMessage.error(t('playgroundCommon.message.copyFailed'))
  }
}

const handleRegenerate = () => {
  emit('regenerate')
}
</script>

<style scoped>
.message-bubble {
  display: flex;
  gap: 12px;
  padding: 16px 0;
}

.message-user {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.message-content {
  flex: 1;
  min-width: 0;
  max-width: 85%;
}

.message-user .message-content {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.message-user .message-header {
  flex-direction: row-reverse;
}

.message-role {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.message-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.message-body {
  padding: 12px 16px;
  border-radius: 12px;
  background-color: var(--el-fill-color-light);
  line-height: 1.6;
  word-wrap: break-word;
}

.message-user .message-body {
  background-color: var(--el-color-primary-light-9);
  border-bottom-right-radius: 4px;
}

.message-assistant .message-body {
  border-bottom-left-radius: 4px;
}

.message-text {
  white-space: pre-wrap;
}

.message-loading {
  display: inline-flex;
  gap: 4px;
  padding: 4px 0;
}

.loading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--el-color-primary);
  animation: bounce 1.4s infinite ease-in-out both;
}

.loading-dot:nth-child(1) {
  animation-delay: -0.32s;
}

.loading-dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes bounce {
  0%,
  80%,
  100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

.message-actions {
  display: flex;
  gap: 4px;
  margin-top: 8px;
  opacity: 0;
  transition: opacity 0.2s;
}

.message-bubble:hover .message-actions {
  opacity: 1;
}

.message-user .message-actions {
  flex-direction: row-reverse;
}
</style>