<template>
  <div class="chat-playground">
    <el-row :gutter="20">
      <el-col :span="12">
        <!-- 配置表单组件 -->
        <ChatConfigForm
          ref="formRef"
          :global-config="globalConfig"
          :submit-loading="loading"
          :loading-text="loadingText"
          @submit="handleRequest"
          @cancel="cancelRequest"
          @reset="resetState"
          @update:global-config="handleUpdateGlobalConfig"
        />
      </el-col>

      <el-col :span="12">
        <!-- 结果展示组件 -->
        <ChatResult
          :chat-response="chatResponse"
          :loading="loading"
          :loading-text="loadingText"
          :request-status="requestStatus"
        />
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import ChatConfigForm from './ChatConfigForm.vue'
import ChatResult from './ChatResult.vue'
import { useChatRequest } from '@/composables/useChatRequest'
import type { GlobalConfig, PlaygroundRequest, PlaygroundResponse } from '../types/playground'
import { usePlaygroundData } from '@/composables/usePlaygroundData'

// Props
interface Props {
  globalConfig?: GlobalConfig
}

const props = withDefaults(defineProps<Props>(), {
  globalConfig: () => ({ authorization: '', customHeaders: {} })
})

// Emits
const emit = defineEmits<{
  response: [response: PlaygroundResponse | null, loading?: boolean, error?: string | null]
  request: [request: PlaygroundRequest | null, size?: number]
  'update:globalConfig': [config: GlobalConfig]
}>()

// 表单引用
const formRef = ref<InstanceType<typeof ChatConfigForm>>()

// 使用请求处理 composable
const {
  loading,
  loadingText,
  requestProgress,
  requestStatus,
  updateRequestStatus,
  sendRequest,
  cancelRequest,
  resetState
} = useChatRequest()

// 使用 Playground 数据
const {
  initializeData,
  refreshData,
  availableInstances,
  instancesLoading
} = usePlaygroundData('chat')

// 响应结果
const chatResponse = ref<PlaygroundResponse | null>(null)

// 监听全局配置变化
watch(() => props.globalConfig, (newConfig) => {
  console.log('[ChatPlayground] globalConfig 更新:', newConfig)
}, { deep: true })

// 监听实例列表变化
watch(availableInstances, (newInstances) => {
  console.log('[ChatPlayground] availableInstances 更新:', newInstances.length, newInstances)
}, { immediate: true })

// 处理请求
const handleRequest = async (request: PlaygroundRequest) => {
  console.log('[ChatPlayground] 收到请求:', {
    endpoint: request.endpoint,
    method: request.method,
    headers: Object.keys(request.headers),
    bodySize: JSON.stringify(request.body).length
  })

  // 发送请求
  emitRequest(request, JSON.stringify(request.body).length)

  const isStream = request.body?.stream === true

  const response = await sendRequest(request, isStream)

  if (response) {
    chatResponse.value = response
    emitResponse(response, false, null)
  }
}

// 更新全局配置
const handleUpdateGlobalConfig = (config: GlobalConfig) => {
  emit('update:globalConfig', config)
}

// 发送请求事件
const emitRequest = (request: PlaygroundRequest, size: number) => {
  emit('request', request, size)
}

// 发送响应事件
const emitResponse = (
  response: PlaygroundResponse | null,
  loading: boolean,
  error: string | null
) => {
  emit('response', response, loading, error)
}

// 初始化数据
initializeData()

// 暴露刷新方法
defineExpose({
  refreshData
})
</script>

<style scoped>
.chat-playground {
  padding: 20px;
}

.instance-required {
  border-color: #f56c6c;
}

.instance-hint {
  margin-top: 4px;
}

.instance-selected {
  margin-top: 4px;
}

.headers-config {
  width: 100%;
}

.headers-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 8px;
}

.header-item {
  display: flex;
  gap: 8px;
  align-items: center;
}

.header-key {
  flex: 1;
}

.header-value {
  flex: 2;
}

.instance-tag {
  min-width: 40px;
}

.remove-btn {
  padding: 4px 8px;
}

.add-header-btn {
  width: 100%;
}

.messages-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.message-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.message-content {
  width: 100%;
}

.message-name {
  width: 100%;
}

.config-collapse {
  margin-top: 16px;
}

.action-buttons {
  display: flex;
  gap: 8px;
  width: 100%;
}

.send-button {
  min-width: 120px;
}

.loading-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.request-status {
  margin-top: 16px;
}

.status-alert {
  width: 100%;
}

.progress-info {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.request-progress {
  flex: 1;
}

.progress-text {
  min-width: 120px;
}
</style>
