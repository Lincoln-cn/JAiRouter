<template>
  <div :class="['message-bubble', `message-${role}`]">
    <div class="message-avatar">
      <el-avatar :size="36" class="message-avatar__badge">
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
  timestamp: '',
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
  gap: var(--ja-space-3);
  padding: var(--ja-space-4) 0;
}

/* 头像块：中性底 + 中性图标，不再是蓝/绿双色身份标识 */
.message-avatar__badge {
  background-color: var(--ja-surface-2);
  color: var(--ja-ink-2);
  border: 1px solid var(--ja-border);
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
  gap: var(--ja-space-2);
  margin-bottom: var(--ja-space-1);
}

.message-user .message-header {
  flex-direction: row-reverse;
}

.message-role {
  font-weight: 600;
  font-size: 14px;
  color: var(--ja-ink);
}

.message-time {
  font-size: 12px;
  color: var(--ja-ink-3);
}

/* 助手：paper 底 + 描边；用户：中性 surface-2 底 + ink 文字 */
.message-body {
  padding: var(--ja-space-3) var(--ja-space-4);
  border-radius: var(--ja-radius-lg);
  background-color: var(--ja-paper);
  border: 1px solid var(--ja-border);
  color: var(--ja-ink);
  line-height: 1.6;
  word-wrap: break-word;
}

.message-user .message-body {
  background-color: var(--ja-surface-2);
  color: var(--ja-ink);
  border-bottom-right-radius: var(--ja-radius-sm);
}

.message-assistant .message-body {
  border-bottom-left-radius: var(--ja-radius-sm);
}

.message-text {
  white-space: pre-wrap;
}

.message-loading {
  display: inline-flex;
  gap: var(--ja-space-1);
  padding: var(--ja-space-1) 0;
}

.loading-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--ja-ink-3);
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
  gap: var(--ja-space-1);
  margin-top: var(--ja-space-2);
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