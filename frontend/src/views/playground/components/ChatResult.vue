<template>
  <el-card header="对话结果" class="result-card">
    <div class="chat-result">
      <!-- 错误状态 -->
      <div v-if="requestStatus && requestStatus.type === 'error'" class="error-state">
        <el-alert :title="requestStatus.message" type="error" show-icon :closable="false" />
      </div>

      <!-- 有响应内容时 -->
      <div v-else-if="chatResponse" class="response-content">
        <div class="response-info">
          <el-tag :type="loading ? 'warning' : 'success'">
            <el-icon>
              <Loading v-if="loading" class="is-loading" />
              <Check v-else />
            </el-icon>
            {{ loading ? '正在生成中...' : '对话生成成功' }}
          </el-tag>
          <span class="duration-info">
            耗时：{{ chatResponse.duration }}ms
          </span>
        </div>

        <div class="message-response">
          <div class="response-header">
            <span class="role-tag">Assistant</span>
            <el-button type="text" size="small" @click="copyResponse" :disabled="loading">
              <el-icon>
                <DocumentCopy />
              </el-icon>
              复制
            </el-button>
          </div>
          <div class="response-text" ref="responseTextRef">
            {{ getResponseContent() }}
            <span v-if="loading" class="typing-cursor">|</span>
          </div>
        </div>

        <div v-if="getUsageInfo() && !loading" class="usage-info">
          <el-descriptions :column="3" size="small" border>
            <el-descriptions-item label="提示令牌">
              {{ getUsageInfo().prompt_tokens }}
            </el-descriptions-item>
            <el-descriptions-item label="完成令牌">
              {{ getUsageInfo().completion_tokens }}
            </el-descriptions-item>
            <el-descriptions-item label="总令牌">
              {{ getUsageInfo().total_tokens }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <!-- 纯加载状态 -->
      <div v-else-if="loading" class="loading-state">
        <el-icon class="is-loading">
          <Loading />
        </el-icon>
        <p>{{ loadingText }}</p>
      </div>

      <!-- 空状态 -->
      <div v-else class="empty-state">
        <el-empty description="发送对话请求后，生成的回复将在此处显示" :image-size="80" />
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, Check, DocumentCopy } from '@element-plus/icons-vue'
import type { PlaygroundResponse } from '../types/playground'
import type { RequestStatus } from '@/composables/useChatRequest'

// Props
interface Props {
  chatResponse: PlaygroundResponse | null
  loading: boolean
  loadingText: string
  requestStatus: RequestStatus | null
}

const props = defineProps<Props>()

// 响应文本区域引用
const responseTextRef = ref<HTMLElement>()

// 获取响应内容
const getResponseContent = (): string => {
  if (!props.chatResponse?.data) return ''

  const data = props.chatResponse.data as any

  // 处理流式响应
  if (data.choices && data.choices[0]?.message) {
    return data.choices[0].message.content || ''
  }

  // 处理普通响应
  if (data.choices && data.choices[0]?.delta) {
    return data.choices[0].delta.content || ''
  }

  // 处理其他格式
  if (typeof data === 'string') {
    return data
  }

  if (data.content) {
    return data.content
  }

  return JSON.stringify(data, null, 2)
}

// 获取使用量信息
const getUsageInfo = () => {
  if (!props.chatResponse?.data) return null

  const data = props.chatResponse.data as any

  if (data.usage) {
    return data.usage
  }

  if (data.choices && data.choices[0]?.usage) {
    return data.choices[0].usage
  }

  return null
}

// 复制响应
const copyResponse = async () => {
  const content = getResponseContent()
  if (!content) {
    ElMessage.warning('没有可复制的内容')
    return
  }

  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败')
  }
}

// 计算属性
const isLoading = computed(() => props.loading)
</script>

<style scoped>
.result-card {
  height: 100%;
}

.chat-result {
  display: flex;
  flex-direction: column;
  min-height: 400px;
}

.error-state {
  margin-bottom: 16px;
}

.response-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.response-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.duration-info {
  color: #909399;
  font-size: 14px;
}

.message-response {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
}

.response-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.role-tag {
  padding: 4px 12px;
  background: #409eff;
  color: #fff;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 500;
}

.response-text {
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
  min-height: 100px;
}

.typing-cursor {
  animation: blink 1s infinite;
  color: #409eff;
}

@keyframes blink {
  0%, 50% {
    opacity: 1;
  }
  51%, 100% {
    opacity: 0;
  }
}

.usage-info {
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid #e4e7ed;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  min-height: 200px;
  color: #909399;
}

.loading-state .el-icon {
  font-size: 32px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}
</style>
