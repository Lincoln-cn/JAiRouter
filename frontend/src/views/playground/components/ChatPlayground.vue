<template>
  <div class="chat-playground">
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card header="对话配置">
          <el-form ref="formRef" :model="chatConfig" :rules="formRules" label-width="120px"
            @submit.prevent="sendRequest">
            <!-- 实例选择 -->
            <el-form-item label="选择实例">
              <div style="display: flex; gap: 8px; align-items: center; width: 100%;">
                <el-select v-model="selectedInstanceId" placeholder="请选择对话服务实例" @change="onInstanceChange"
                  style="flex: 1" :loading="instancesLoading" clearable
                  :class="{ 'instance-required': !selectedInstanceId }">
                  <el-option v-for="instance in availableInstances" :key="instance.instanceId" :label="instance.name"
                    :value="instance.instanceId">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                      <span>{{ instance.name }}</span>
                      <el-tag v-if="instance.headers && Object.keys(instance.headers).length > 0" type="success"
                        size="small">
                        {{ Object.keys(instance.headers).length }} 个请求头
                      </el-tag>
                    </div>
                  </el-option>
                  <template #empty>
                    <div style="padding: 10px; text-align: center; color: #999;">
                      {{ instancesLoading ? '加载中...' : '暂无可用实例，请先在实例管理中添加对话服务实例' }}
                    </div>
                  </template>
                </el-select>
                <el-button type="info" size="small" @click="() => fetchInstances(true)" :loading="instancesLoading"
                  title="刷新实例列表">
                  <el-icon>
                    <Refresh />
                  </el-icon>
                </el-button>
              </div>
              <div v-if="!selectedInstanceId" class="instance-hint">
                <el-text type="danger" size="small">请选择一个对话服务实例</el-text>
              </div>
              <div v-else-if="selectedInstanceInfo" class="instance-selected">
                <el-text type="success" size="small">
                  <el-icon>
                    <Check />
                  </el-icon>
                  已选择实例: {{ selectedInstanceInfo.name }}
                </el-text>
              </div>
            </el-form-item>

            <!-- 请求头配置 -->
            <el-form-item v-if="selectedInstanceInfo" label="请求头配置">
              <div class="headers-config">
                <div class="headers-list">
                  <div v-for="(header, index) in headersList" :key="index" class="header-item">
                    <el-input v-model="header.key" placeholder="请求头名称" size="small" @input="onHeaderChange"
                      class="header-key" :disabled="header.fromInstance" />
                    <el-input v-model="header.value" placeholder="请求头值" size="small" @input="onHeaderChange"
                      class="header-value"
                      :type="header.key.toLowerCase().includes('authorization') || header.key.toLowerCase().includes('key') ? 'password' : 'text'"
                      :show-password="header.key.toLowerCase().includes('authorization') || header.key.toLowerCase().includes('key')" />
                    <el-tag v-if="header.fromInstance" type="success" size="small" class="instance-tag">
                      实例
                    </el-tag>
                    <el-button v-else type="danger" size="small" @click="removeHeader(index)" class="remove-btn">
                      <el-icon>
                        <Close />
                      </el-icon>
                    </el-button>
                  </div>
                </div>
                <el-button type="primary" size="small" @click="addHeader" class="add-header-btn">
                  <el-icon>
                    <Plus />
                  </el-icon>
                  添加请求头
                </el-button>
              </div>
            </el-form-item>

            <!-- 消息列表 -->
            <el-form-item label="消息列表" prop="messages">
              <div class="messages-container">
                <div v-for="(message, index) in chatConfig.messages" :key="index" class="message-item">
                  <div class="message-header">
                    <el-select v-model="message.role" placeholder="角色" style="width: 120px">
                      <el-option label="系统" value="system" />
                      <el-option label="用户" value="user" />
                      <el-option label="助手" value="assistant" />
                    </el-select>
                    <el-button type="danger" size="small" :icon="Delete" circle @click="removeMessage(index)" />
                  </div>
                  <el-input v-model="message.content" type="textarea" :rows="3" placeholder="请输入消息内容"
                    class="message-content" />
                  <el-input v-if="message.role !== 'system'" v-model="message.name" placeholder="名称（可选）" size="small"
                    class="message-name" />
                </div>
                <el-button type="primary" size="small" :icon="Plus" @click="addMessage">
                  添加消息
                </el-button>
              </div>
            </el-form-item>

            <!-- 对话参数配置面板 -->
            <el-collapse v-model="activeCollapse" class="config-collapse">
              <el-collapse-item title="高级参数" name="advanced">
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="流式响应">
                      <el-switch v-model="chatConfig.stream" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="最大令牌数">
                      <el-input-number v-model="chatConfig.maxTokens" :min="1" :max="32000" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="温度">
                      <el-slider v-model="chatConfig.temperature" :min="0" :max="2" :step="0.1" show-input
                        :show-input-controls="false" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="Top P">
                      <el-slider v-model="chatConfig.topP" :min="0" :max="1" :step="0.1" show-input
                        :show-input-controls="false" />
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="Top K">
                      <el-input-number v-model="chatConfig.topK" :min="1" :max="100" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="用户标识">
                      <el-input v-model="chatConfig.user" placeholder="用户标识（可选）" />
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="频率惩罚">
                      <el-slider v-model="chatConfig.frequencyPenalty" :min="-2" :max="2" :step="0.1" show-input
                        :show-input-controls="false" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="存在惩罚">
                      <el-slider v-model="chatConfig.presencePenalty" :min="-2" :max="2" :step="0.1" show-input
                        :show-input-controls="false" />
                    </el-form-item>
                  </el-col>
                </el-row>

                <el-form-item label="停止词">
                  <el-input v-model="stopWordsInput" placeholder="多个停止词用逗号分隔" @blur="updateStopWords" />
                </el-form-item>
              </el-collapse-item>
            </el-collapse>

            <!-- 发送按钮 -->
            <el-form-item>
              <div class="action-buttons">
                <el-button type="primary" :loading="loading" @click="sendRequest" :disabled="!canSendRequest"
                  size="default" class="send-button">
                  <template #loading>
                    <div class="loading-content">
                      <el-icon class="is-loading">
                        <Loading />
                      </el-icon>
                      <span>{{ loadingText }}</span>
                    </div>
                  </template>
                  <template #default>
                    <el-icon>
                      <Promotion />
                    </el-icon>
                    <span>发送请求</span>
                  </template>
                </el-button>
                <el-button @click="resetForm" :disabled="loading">
                  <el-icon>
                    <RefreshLeft />
                  </el-icon>
                  重置
                </el-button>
                <el-button v-if="loading" type="danger" @click="cancelRequest" size="default">
                  <el-icon>
                    <Close />
                  </el-icon>
                  取消
                </el-button>
              </div>

              <!-- 请求状态指示器 -->
              <div v-if="requestStatus" class="request-status">
                <el-alert :title="requestStatus.message" :type="requestStatus.type" :closable="false" show-icon
                  class="status-alert">
                  <template v-if="requestStatus.type === 'info' && loading" #default>
                    <div class="progress-info">
                      <el-progress :percentage="requestProgress" :show-text="false" :stroke-width="4"
                        class="request-progress" />
                      <span class="progress-text">{{ requestStatus.message }}</span>
                    </div>
                  </template>
                </el-alert>
              </div>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card header="对话结果" class="result-card">
          <div class="chat-result">
            <!-- 错误状态 -->
            <div v-if="requestStatus && requestStatus.type === 'error'" class="error-state">
              <el-alert :title="requestStatus.message" type="error" show-icon :closable="false" />
            </div>

            <!-- 有响应内容时显示（包括流式响应） -->
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
                  耗时: {{ chatResponse.duration }}ms
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

            <!-- 纯加载状态（没有响应内容时） -->
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
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Delete, Loading, Promotion, RefreshLeft, Close, Check, DocumentCopy, Refresh } from '@element-plus/icons-vue'
import { sendUniversalRequest, sendUniversalStreamRequest } from '@/api/universal'
import { usePlaygroundData } from '@/composables/usePlaygroundData'
import type {
  ChatRequestConfig,
  ChatMessage,
  GlobalConfig,
  PlaygroundResponse,
  PlaygroundRequest
} from '../types/playground'
import { DEFAULT_CONFIGS, SERVICE_ENDPOINTS } from '../types/playground'

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
const formRef = ref<FormInstance>()

// 状态管理
const loading = ref(false)
const activeCollapse = ref<string[]>([])
const stopWordsInput = ref('')
const loadingText = ref('发送中...')
const requestProgress = ref(0)
const requestStatus = ref<{
  type: 'success' | 'warning' | 'info' | 'error'
  message: string
} | null>(null)

// 请求取消控制器
let abortController: AbortController | null = null

// 使用优化后的数据管理
const {
  availableInstances,
  availableModels,
  instancesLoading,
  modelsLoading,
  selectedInstanceId,
  selectedInstanceInfo,
  hasInstances,
  hasModels,
  onInstanceChange: handleInstanceChange,
  initializeData,
  refreshData,
  fetchInstances,
  fetchModels
} = usePlaygroundData('chat')

// 添加调试监听
watch(availableInstances, (newInstances) => {
  console.log('[ChatPlayground] availableInstances 更新:', newInstances.length, newInstances)
}, { immediate: true })

watch(instancesLoading, (loading) => {
  console.log('[ChatPlayground] instancesLoading 更新:', loading)
}, { immediate: true })

// 请求头配置状态
interface HeaderItem {
  key: string
  value: string
  fromInstance: boolean
}

const headersList = ref<HeaderItem[]>([])
const currentHeaders = ref<Record<string, string>>({})

// 实例选择变化处理（扩展 composable 的功能）
const onInstanceChange = (instanceId: string) => {
  // 调用 composable 的处理函数
  handleInstanceChange(instanceId)

  // 处理组件特有的逻辑
  console.log('实例选择变化:', { instanceId, currentSelected: selectedInstanceId.value })

  if (!instanceId) {
    console.log('清空实例选择')
    chatConfig.model = ''
    headersList.value = []
    currentHeaders.value = {}
    return
  }

  const instance = availableInstances.value.find(inst => inst.instanceId === instanceId)
  if (instance) {
    console.log('找到实例:', instance.name)

    // 使用实例名称作为默认模型
    chatConfig.model = instance.name || 'default-model'

    // 初始化请求头列表
    initializeHeaders(instance.headers || {})

    ElMessage.success(`已选择实例 "${instance.name}"`)
  } else {
    console.log('未找到实例:', instanceId)
  }
}

// 初始化请求头列表
const initializeHeaders = (instanceHeaders: Record<string, string>) => {
  headersList.value = []
  currentHeaders.value = {}

  // 添加实例的请求头（标记为来自实例，不可删除）
  Object.entries(instanceHeaders).forEach(([key, value]) => {
    headersList.value.push({
      key,
      value,
      fromInstance: true
    })
    currentHeaders.value[key] = value
  })

  // 添加全局配置中的自定义请求头
  Object.entries(props.globalConfig.customHeaders || {}).forEach(([key, value]) => {
    if (!currentHeaders.value[key]) {
      headersList.value.push({
        key,
        value,
        fromInstance: false
      })
      currentHeaders.value[key] = value
    }
  })
}

// 添加请求头
const addHeader = () => {
  headersList.value.push({
    key: '',
    value: '',
    fromInstance: false
  })
}

// 删除请求头
const removeHeader = (index: number) => {
  const header = headersList.value[index]
  if (!header.fromInstance) {
    headersList.value.splice(index, 1)
    onHeaderChange()
  }
}

// 请求头变化处理
const onHeaderChange = () => {
  console.log('请求头变化处理开始', {
    selectedInstanceId: selectedInstanceId.value,
    selectedInstanceInfo: !!selectedInstanceInfo.value
  })

  // 重新构建headers对象
  const newHeaders: Record<string, string> = {}
  const customHeaders: Record<string, string> = {}

  headersList.value.forEach(header => {
    if (header.key.trim() && header.value.trim()) {
      const key = header.key.trim()
      const value = header.value.trim()
      newHeaders[key] = value

      // 只有非实例来源的请求头才加入自定义请求头
      if (!header.fromInstance) {
        customHeaders[key] = value
      }
    }
  })

  currentHeaders.value = newHeaders

  // 更新全局配置（只更新自定义请求头，不影响实例请求头）
  const updatedGlobalConfig = {
    ...props.globalConfig,
    customHeaders: customHeaders
  }

  console.log('请求头变化处理完成', {
    selectedInstanceId: selectedInstanceId.value,
    selectedInstanceInfo: !!selectedInstanceInfo.value,
    newHeadersCount: Object.keys(newHeaders).length,
    customHeadersCount: Object.keys(customHeaders).length
  })

  emit('update:globalConfig', updatedGlobalConfig)
}

// 对话配置
const chatConfig = reactive<ChatRequestConfig>({
  ...DEFAULT_CONFIGS.chat,
  model: '',
  messages: [],
  stream: true, // 默认开启流式响应
  maxTokens: 1000,
  temperature: 0.7,
  topP: 1.0,
  topK: undefined,
  frequencyPenalty: 0,
  presencePenalty: 0,
  stop: undefined,
  user: ''
})

// 当前请求信息
const currentRequest = ref<PlaygroundRequest | null>(null)

// 对话响应结果
const chatResponse = ref<PlaygroundResponse | null>(null)

// 响应文本区域引用
const responseTextRef = ref<HTMLElement>()

// 表单验证规则
const formRules: FormRules = {
  messages: [
    {
      required: true,
      validator: (_rule, value, callback) => {
        if (!value || value.length === 0) {
          callback(new Error('至少需要添加一条消息'))
        } else if (value.some((msg: ChatMessage) => !msg.content.trim())) {
          callback(new Error('消息内容不能为空'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 计算属性
const canSendRequest = computed(() => {
  return selectedInstanceId.value &&
    selectedInstanceInfo.value &&
    chatConfig.messages.length > 0 &&
    chatConfig.messages.every(msg => msg.content.trim()) &&
    !loading.value
})

// 实例一致性由 composable 自动处理，无需手动维护

// 监听停止词输入变化
const updateStopWords = () => {
  if (stopWordsInput.value.trim()) {
    const words = stopWordsInput.value.split(',').map(w => w.trim()).filter(w => w)
    chatConfig.stop = words.length === 1 ? words[0] : words
  } else {
    chatConfig.stop = undefined
  }
}

// 监听配置变化，更新停止词显示
watch(() => chatConfig.stop, (newStop) => {
  if (Array.isArray(newStop)) {
    stopWordsInput.value = newStop.join(', ')
  } else if (typeof newStop === 'string') {
    stopWordsInput.value = newStop
  } else {
    stopWordsInput.value = ''
  }
}, { immediate: true })

// 添加消息
const addMessage = () => {
  chatConfig.messages.push({
    role: 'user',
    content: '',
    name: undefined
  })
}

// 删除消息
const removeMessage = (index: number) => {
  if (chatConfig.messages.length > 1) {
    chatConfig.messages.splice(index, 1)
  } else {
    ElMessage.warning('至少需要保留一条消息')
  }
}

// 重置表单
const resetForm = async () => {
  try {
    await ElMessageBox.confirm('确定要重置所有配置吗？', '确认重置', {
      type: 'warning'
    })

    Object.assign(chatConfig, {
      ...DEFAULT_CONFIGS.chat,
      model: '',
      messages: [],
      stream: true, // 默认开启流式响应
      maxTokens: 1000,
      temperature: 0.7,
      topP: 1.0,
      topK: undefined,
      frequencyPenalty: 0,
      presencePenalty: 0,
      stop: undefined,
      user: ''
    })

    stopWordsInput.value = ''
    currentRequest.value = null

    ElMessage.success('配置已重置')
  } catch {
    // 用户取消重置
  }
}

// 构建请求
const buildRequest = (): PlaygroundRequest => {
  const endpoint = SERVICE_ENDPOINTS.chat

  // 构建请求头
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  }

  // 添加授权头
  if (props.globalConfig?.authorization) {
    headers['Authorization'] = props.globalConfig.authorization
  }

  // 添加当前配置的请求头
  Object.assign(headers, currentHeaders.value)

  console.log('构建请求头:', {
    globalAuth: !!props.globalConfig?.authorization,
    customHeaders: Object.keys(currentHeaders.value),
    finalHeaders: Object.keys(headers)
  })

  // 构建请求体，过滤掉空值
  const requestBody: any = {
    model: chatConfig.model,
    messages: chatConfig.messages.map(msg => ({
      role: msg.role,
      content: msg.content,
      ...(msg.name && { name: msg.name })
    }))
  }

  // 添加可选参数
  if (chatConfig.stream !== undefined) requestBody.stream = chatConfig.stream
  if (chatConfig.maxTokens) requestBody.max_tokens = chatConfig.maxTokens
  if (chatConfig.temperature !== undefined) requestBody.temperature = chatConfig.temperature
  if (chatConfig.topP !== undefined) requestBody.top_p = chatConfig.topP
  if (chatConfig.topK !== undefined) requestBody.top_k = chatConfig.topK
  if (chatConfig.frequencyPenalty !== undefined) requestBody.frequency_penalty = chatConfig.frequencyPenalty
  if (chatConfig.presencePenalty !== undefined) requestBody.presence_penalty = chatConfig.presencePenalty
  if (chatConfig.stop !== undefined) requestBody.stop = chatConfig.stop
  if (chatConfig.user) requestBody.user = chatConfig.user

  return {
    endpoint: endpoint.path,
    method: endpoint.method,
    headers,
    body: requestBody
  }
}

// 取消请求
const cancelRequest = () => {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  loading.value = false
  requestProgress.value = 0
  requestStatus.value = {
    type: 'warning',
    message: '请求已取消'
  }
  emitResponse(null, false, '请求已取消')

  // 3秒后清除状态
  setTimeout(() => {
    requestStatus.value = null
  }, 3000)
}

// 更新请求状态
const updateRequestStatus = (type: 'success' | 'warning' | 'info' | 'error', message: string) => {
  requestStatus.value = { type, message }

  // 成功和错误状态3秒后自动清除
  if (type === 'success' || type === 'error') {
    setTimeout(() => {
      requestStatus.value = null
    }, 3000)
  }
}

// 发送请求
const sendRequest = async () => {
  if (!formRef.value) return

  try {
    // 表单验证
    const valid = await formRef.value.validate()
    if (!valid) {
      updateRequestStatus('error', '请检查表单输入')
      return
    }

    // 实例状态一致性由 composable 自动维护

    // 验证实例选择
    if (!selectedInstanceInfo.value) {
      updateRequestStatus('error', '请先选择一个对话服务实例')
      ElMessage.error('请先选择一个对话服务实例')
      return
    }

    // 验证模型配置
    if (!chatConfig.model) {
      updateRequestStatus('error', '模型名称不能为空')
      ElMessage.error('模型名称不能为空')
      return
    }

    // 验证模型是否存在（如果有可用模型列表）
    if (availableModels.value.length > 0 && !availableModels.value.includes(chatConfig.model)) {
      updateRequestStatus('error', `模型 "${chatConfig.model}" 在实例管理中不存在或未启用，请先在实例管理中添加对应的chat服务实例`)
      ElMessage.error(`模型 "${chatConfig.model}" 不可用，请检查实例管理配置`)
      return
    }

    // 创建新的取消控制器
    abortController = new AbortController()

    loading.value = true
    requestProgress.value = 0
    loadingText.value = '准备发送请求...'
    updateRequestStatus('info', '正在验证配置...')
    emitResponse(null, true, null)

    // 模拟进度更新
    const progressInterval = setInterval(() => {
      if (requestProgress.value < 90) {
        requestProgress.value += Math.random() * 10
        if (requestProgress.value < 30) {
          loadingText.value = '建立连接中...'
          updateRequestStatus('info', '正在连接服务器...')
        } else if (requestProgress.value < 60) {
          loadingText.value = '发送请求中...'
          updateRequestStatus('info', '正在发送请求数据...')
        } else {
          loadingText.value = '等待响应中...'
          updateRequestStatus('info', '正在等待服务器响应...')
        }
      }
    }, 200)

    // 构建请求
    const request = buildRequest()
    console.log('构建的请求:', {
      endpoint: request.endpoint,
      method: request.method,
      headers: Object.keys(request.headers),
      bodySize: JSON.stringify(request.body).length
    })
    emitRequest(request, JSON.stringify(request.body).length)

    if (chatConfig.stream) {
      // 处理流式响应
      await handleStreamRequest(request)
    } else {
      // 处理普通响应
      await handleNormalRequest(request)
    }

    clearInterval(progressInterval)
    requestProgress.value = 100

  } catch (error) {
    console.error('发送请求失败:', error)

    // 更好的错误处理
    let errorMessage = '发送请求失败'

    if (error instanceof Error) {
      errorMessage = error.message
    } else if (typeof error === 'object' && error !== null) {
      // 处理API响应错误
      const errorObj = error as any
      if ('message' in errorObj && typeof errorObj.message === 'string') {
        errorMessage = errorObj.message
      } else if ('statusText' in errorObj && typeof errorObj.statusText === 'string') {
        errorMessage = `${errorObj.status || 'Unknown'}: ${errorObj.statusText}`
      } else if ('data' in errorObj && errorObj.data && typeof errorObj.data === 'object') {
        // 处理嵌套的错误信息
        const dataObj = errorObj.data as any
        if (dataObj.message) {
          errorMessage = dataObj.message
        } else if (dataObj.error) {
          errorMessage = dataObj.error
        }
      }
    } else if (typeof error === 'string') {
      errorMessage = error
    }

    if (errorMessage.includes('aborted')) {
      updateRequestStatus('warning', '请求已取消')
    } else {
      updateRequestStatus('error', `请求失败: ${errorMessage}`)
      ElMessage.error(errorMessage)
    }

    emitResponse(null, false, errorMessage)
  } finally {
    loading.value = false
    requestProgress.value = 0
    abortController = null
  }
}

// 处理普通请求
const handleNormalRequest = async (request: PlaygroundRequest) => {
  try {
    updateRequestStatus('info', '正在处理请求...')

    const response = await sendUniversalRequest({
      endpoint: request.endpoint,
      method: request.method,
      headers: request.headers,
      body: request.body
    })

    // 构建PlaygroundResponse
    const playgroundResponse: PlaygroundResponse = {
      status: response.status,
      statusText: response.statusText,
      headers: response.headers,
      data: response.data,
      duration: response.duration,
      timestamp: response.timestamp
    }

    emitResponse(playgroundResponse, false, null)

    if (response.status >= 200 && response.status < 300) {
      updateRequestStatus('success', `请求成功 (${response.duration}ms)`)
      ElMessage.success({
        message: '请求发送成功',
        duration: 2000,
        showClose: true
      })
    } else {
      updateRequestStatus('warning', `请求完成，状态码: ${response.status}`)
      ElMessage.warning({
        message: `请求完成，状态码: ${response.status}`,
        duration: 3000,
        showClose: true
      })
    }

  } catch (error: any) {
    console.error('API请求失败:', error)

    // 如果是UniversalApiResponse错误，直接使用
    if (error && typeof error === 'object' && error.status !== undefined) {
      const playgroundResponse: PlaygroundResponse = {
        status: error.status,
        statusText: error.statusText || 'Unknown Error',
        headers: error.headers || {},
        data: error.data || { error: 'Unknown error occurred' },
        duration: error.duration || 0,
        timestamp: error.timestamp || new Date().toISOString()
      }
      emitResponse(playgroundResponse, false, null)

      // 构建更详细的错误信息
      let errorDetail = `${error.status} ${error.statusText || 'Unknown Error'}`
      if (error.data && typeof error.data === 'object') {
        const dataObj = error.data as any
        if (dataObj.message) {
          errorDetail += `: ${dataObj.message}`
        } else if (dataObj.error) {
          errorDetail += `: ${dataObj.error}`
        }
      }

      updateRequestStatus('error', `请求失败: ${errorDetail}`)
    } else {
      // 其他错误
      const errorMessage = error?.message || error?.toString() || '请求失败'
      updateRequestStatus('error', `网络错误: ${errorMessage}`)
      emitResponse(null, false, errorMessage)
    }
  }
}

// 处理流式请求
const handleStreamRequest = async (request: PlaygroundRequest) => {
  let streamResponse: PlaygroundResponse | null = null
  let streamContent = ''
  let messageCount = 0
  const startTime = Date.now()

  try {
    updateRequestStatus('info', '正在建立流式连接...')

    console.log('流式请求headers:', request.headers)

    await sendUniversalStreamRequest(
      {
        endpoint: request.endpoint,
        method: request.method,
        headers: request.headers,
        body: request.body
      },
      // onMessage
      (data: any) => {
        try {
          messageCount++

          console.log('收到流式数据:', data)

          // 处理流式数据
          if (data.choices && data.choices[0] && data.choices[0].delta) {
            const delta = data.choices[0].delta
            if (delta.content) {
              streamContent += delta.content
              console.log('累积内容长度:', streamContent.length, '新增内容:', delta.content)
            }

            // 更新流式状态（减少频繁更新）
            if (messageCount % 5 === 0 || delta.content) {
              updateRequestStatus('info', `正在接收流式数据... (${messageCount} 条消息, ${streamContent.length} 字符)`)
            }

            // 构建当前的响应数据
            const currentData = {
              ...data,
              choices: [{
                ...data.choices[0],
                message: {
                  role: 'assistant',
                  content: streamContent
                }
              }]
            }

            streamResponse = {
              status: 200,
              statusText: 'OK',
              headers: { 'content-type': 'text/event-stream' },
              data: currentData,
              duration: Date.now() - startTime,
              timestamp: new Date().toISOString()
            }

            // 立即更新界面显示
            emitResponse(streamResponse, true, null)
          } else {
            // 处理其他类型的流式数据
            console.log('收到非标准格式的流式数据:', data)
          }
        } catch (parseError) {
          console.warn('解析流式数据失败:', parseError, '原始数据:', data)
          updateRequestStatus('warning', '部分流式数据解析失败')
        }
      },
      // onError
      (error: any) => {
        console.error('流式请求错误:', error)
        updateRequestStatus('error', `流式请求错误: ${error.message || '未知错误'}`)
        emitResponse(null, false, error.message || '流式请求失败')
        ElMessage.error({
          message: `流式请求失败: ${error.message || '未知错误'}`,
          duration: 4000,
          showClose: true
        })
      },
      // onComplete
      () => {
        if (streamResponse) {
          updateRequestStatus('success', `流式请求完成 (接收 ${messageCount} 条消息)`)
          emitResponse(streamResponse, false, null)
          ElMessage.success({
            message: `流式请求完成，共接收 ${messageCount} 条消息`,
            duration: 3000,
            showClose: true
          })
        } else {
          updateRequestStatus('warning', '流式请求完成但未收到有效数据')
        }
      }
    )

  } catch (error: any) {
    console.error('流式请求失败:', error)

    // 更好的流式请求错误处理
    let errorMessage = '流式请求失败'

    if (error instanceof Error) {
      errorMessage = error.message
    } else if (typeof error === 'object' && error !== null) {
      const errorObj = error as any
      if (errorObj.message) {
        errorMessage = errorObj.message
      } else if (errorObj.statusText) {
        errorMessage = `${errorObj.status || 'Unknown'}: ${errorObj.statusText}`
      }
    } else if (typeof error === 'string') {
      errorMessage = error
    }

    updateRequestStatus('error', `流式请求失败: ${errorMessage}`)
    emitResponse(null, false, errorMessage)
  }
}

// 键盘快捷键支持
const handleKeyboardShortcuts = (event: Event) => {
  const customEvent = event as CustomEvent
  switch (customEvent.type) {
    case 'playground-send-request':
      if (canSendRequest.value) {
        sendRequest()
      }
      break
    case 'playground-reset-form':
      resetForm()
      break
    case 'playground-cancel-request':
      if (loading.value) {
        cancelRequest()
      }
      break
  }
}

// 生命周期
onMounted(async () => {
  // 监听键盘快捷键事件
  document.addEventListener('playground-send-request', handleKeyboardShortcuts)
  document.addEventListener('playground-reset-form', handleKeyboardShortcuts)
  document.addEventListener('playground-cancel-request', handleKeyboardShortcuts)
  document.addEventListener('playground-refresh-models', handleGlobalRefresh)

  // 等待下一个 tick 确保组件完全挂载
  await nextTick()

  // 初始化数据（使用缓存，静默加载）
  console.log('[ChatPlayground] 开始初始化数据...')
  await initializeData()
  console.log('[ChatPlayground] 数据初始化完成')
})

// 处理全局刷新事件
const handleGlobalRefresh = () => {
  refreshData()
}

onUnmounted(() => {
  // 清理事件监听器
  document.removeEventListener('playground-send-request', handleKeyboardShortcuts)
  document.removeEventListener('playground-reset-form', handleKeyboardShortcuts)
  document.removeEventListener('playground-cancel-request', handleKeyboardShortcuts)
  document.removeEventListener('playground-refresh-models', handleGlobalRefresh)
})

// 获取实际的数据对象（处理包装格式）
const getActualData = () => {
  if (!chatResponse.value) return null

  let data = chatResponse.value.data

  // 检查是否是包装的响应格式 (有success, message, data字段)
  if (data.success !== undefined && data.data) {
    data = data.data
  }

  return data
}

// 获取响应内容
const getResponseContent = () => {
  const data = getActualData()
  console.log('getResponseContent - data:', data)

  if (!data) return ''

  if (data.choices && data.choices[0]) {
    const content = data.choices[0].message?.content || data.choices[0].delta?.content || ''
    console.log('getResponseContent - 提取的内容:', content?.length, '字符')
    return content
  }

  return ''
}

// 获取使用信息
const getUsageInfo = () => {
  const data = getActualData()
  return data?.usage || null
}

// 复制响应内容
const copyResponse = async () => {
  const content = getResponseContent()
  if (!content) return

  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success('响应内容已复制到剪贴板')
  } catch (error) {
    ElMessage.error('复制失败')
  }
}



// 重写emit调用，同时更新本地状态
const emitResponse = (response: PlaygroundResponse | null, loading = false, error: string | null = null) => {
  console.log('emitResponse 被调用:', {
    hasResponse: !!response,
    loading: loading,
    error: error,
    responseData: response?.data
  })

  chatResponse.value = response

  // 调试流式更新
  if (response && loading) {
    const content = response.data?.choices?.[0]?.message?.content
    if (content) {
      console.log('更新界面内容，当前长度:', content.length, '最新内容:', content.slice(-20))

      // 自动滚动到底部，显示最新内容
      nextTick(() => {
        if (responseTextRef.value) {
          responseTextRef.value.scrollTop = responseTextRef.value.scrollHeight
        }
      })
    }
  }

  console.log('chatResponse.value 已更新:', !!chatResponse.value)

  emit('response', response, loading, error)
}

// 重写emit调用，同时更新本地状态
const emitRequest = (request: PlaygroundRequest | null, size = 0) => {
  currentRequest.value = request
  emit('request', request, size)
}

// 初始化默认消息
if (chatConfig.messages.length === 0) {
  addMessage()
}
</script>

<style scoped>
.chat-playground {
  padding: 20px;
  min-height: 600px;
  overflow: visible;
}

.model-select-container {
  display: flex;
  gap: 8px;
  align-items: center;
}

.model-select-container .el-select {
  flex: 1;
}

.refresh-btn {
  flex-shrink: 0;
}

.messages-container {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 15px;
  background-color: var(--el-bg-color-page);
  width: 100%;
}

/* 让请求头配置占满宽度 */
.headers-config {
  width: 100%;
}

.headers-list {
  width: 100%;
}

.header-item {
  width: 100%;
}

/* 让表单项占满宽度 */
.el-form-item__content {
  width: 100% !important;
}

/* 让选择器和输入框占满宽度 */
.el-select,
.el-input,
.el-textarea {
  width: 100% !important;
}

.message-item {
  margin-bottom: 15px;
  padding: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background-color: var(--el-bg-color);
}

.message-item:last-of-type {
  margin-bottom: 10px;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.message-content {
  margin-bottom: 8px;
}

.message-name {
  max-width: 200px;
}

.config-collapse {
  margin: 20px 0;
}

.config-collapse :deep(.el-collapse-item__header) {
  font-weight: 500;
}

.config-collapse :deep(.el-collapse-item__content) {
  padding-top: 10px;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .chat-playground :deep(.el-col) {
    margin-bottom: 20px;
  }
}

@media (max-width: 768px) {
  .chat-playground {
    padding: 10px;
  }

  .message-header {
    flex-direction: column;
    gap: 8px;
    align-items: stretch;
  }

  .message-header .el-select {
    width: 100% !important;
  }
}

/* 深色主题适配 */
@media (prefers-color-scheme: dark) {
  .messages-container {
    background-color: var(--el-bg-color-overlay);
  }

  .message-item {
    background-color: var(--el-bg-color-page);
  }
}

/* 滑块样式优化 */
.config-collapse :deep(.el-slider) {
  margin: 0 10px;
}

.config-collapse :deep(.el-slider__input) {
  width: 80px;
}

/* 表单项间距优化 */
.config-collapse .el-form-item {
  margin-bottom: 15px;
}

.config-collapse .el-row .el-form-item {
  margin-bottom: 10px;
}

/* 增强的按钮和状态样式 */
.action-buttons {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.send-button {
  min-width: 120px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.send-button:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}

.loading-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.loading-content .el-icon {
  font-size: 16px;
}

/* 请求状态样式 */
.request-status {
  margin-top: 16px;
  animation: slideInDown 0.3s ease-out;
}

.status-alert {
  border-radius: 8px;
  border: none;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.progress-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.request-progress {
  width: 100%;
}

.request-progress :deep(.el-progress-bar__outer) {
  background-color: var(--el-color-primary-light-8);
  border-radius: 4px;
}

.request-progress :deep(.el-progress-bar__inner) {
  background: linear-gradient(90deg, var(--el-color-primary), var(--el-color-primary-light-3));
  border-radius: 4px;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 13px;
  color: var(--el-text-color-regular);
  text-align: center;
}

/* 状态动画 */
@keyframes slideInDown {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 加载状态的脉冲效果 */
.send-button.is-loading {
  position: relative;
  overflow: hidden;
}

.send-button.is-loading::after {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  animation: shimmer 1.5s infinite;
}

@keyframes shimmer {
  0% {
    left: -100%;
  }

  100% {
    left: 100%;
  }
}

/* 响应式状态样式 */
@media (max-width: 768px) {
  .action-buttons {
    flex-direction: column;
    align-items: stretch;
  }

  .send-button {
    min-width: auto;
    width: 100%;
  }

  .request-status {
    margin-top: 12px;
  }

  .progress-info {
    gap: 6px;
  }
}

/* 深色主题状态样式 */
@media (prefers-color-scheme: dark) {
  .status-alert {
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  }

  .request-progress :deep(.el-progress-bar__outer) {
    background-color: var(--el-color-primary-light-3);
  }

  .send-button.is-loading::after {
    background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.1), transparent);
  }
}

/* 高对比度模式 */
@media (prefers-contrast: high) {
  .status-alert {
    border: 2px solid var(--el-text-color-primary);
  }

  .send-button {
    border: 2px solid var(--el-color-primary);
  }
}

/* 对话结果样式 */
.result-card {
  height: 100%;
}

.chat-result {
  min-height: 400px;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  color: var(--el-color-primary);
}

.loading-state .el-icon {
  font-size: 32px;
  margin-bottom: 16px;
}

.error-state {
  padding: 20px;
}

.response-content {
  padding: 16px;
}

.response-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.duration-info {
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.message-response {
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  overflow: hidden;
}

.response-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background-color: var(--el-bg-color-page);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.role-tag {
  font-size: 12px;
  font-weight: 500;
  color: var(--el-color-success);
}

.response-text {
  padding: 16px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  max-height: 400px;
  overflow-y: auto;
  background-color: var(--el-bg-color-page);
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);
}

/* 滚动条样式 */
.response-text::-webkit-scrollbar {
  width: 8px;
}

.response-text::-webkit-scrollbar-track {
  background: var(--el-bg-color-page);
  border-radius: 4px;
}

.response-text::-webkit-scrollbar-thumb {
  background: var(--el-border-color-dark);
  border-radius: 4px;
  transition: background 0.3s ease;
}

.response-text::-webkit-scrollbar-thumb:hover {
  background: var(--el-color-primary-light-5);
}

.typing-cursor {
  animation: blink 1s infinite;
  color: var(--el-color-primary);
  font-weight: bold;
}

@keyframes blink {

  0%,
  50% {
    opacity: 1;
  }

  51%,
  100% {
    opacity: 0;
  }
}

.usage-info {
  margin-top: 16px;
}

.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
}

/* 减少动画模式 */
@media (prefers-reduced-motion: reduce) {

  .send-button,
  .status-alert,
  .request-progress :deep(.el-progress-bar__inner) {
    transition: none;
    animation: none;
  }

  .send-button:not(:disabled):hover {
    transform: none;
  }
}

/* 请求头配置样式 */
.headers-config {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 15px;
  background-color: var(--el-bg-color-page);
}

.headers-list {
  max-height: 300px;
  overflow-y: auto;
  margin-bottom: 10px;
}

.header-item {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
  align-items: center;
}

.header-key {
  flex: 1;
  min-width: 120px;
}

.header-value {
  flex: 2;
}

.instance-tag {
  flex-shrink: 0;
}

.remove-btn {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  padding: 0;
}

.add-header-btn {
  width: 100%;
}

/* 实例选择样式 */
.instance-required :deep(.el-input__wrapper) {
  border-color: var(--el-color-danger) !important;
  box-shadow: 0 0 0 1px var(--el-color-danger) inset !important;
}

.instance-required :deep(.el-input__wrapper):hover {
  border-color: var(--el-color-danger) !important;
}

.instance-hint {
  margin-top: 4px;
  padding-left: 4px;
}

.instance-selected {
  margin-top: 4px;
  padding-left: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 深色主题适配 */
@media (prefers-color-scheme: dark) {
  .response-header {
    background-color: var(--el-bg-color-overlay);
  }

  .message-response {
    border-color: var(--el-border-color);
  }

  .headers-preview {
    background-color: var(--el-bg-color-overlay);
    border-color: var(--el-border-color);
  }

  .header-preview-item {
    background-color: var(--el-bg-color);
    border-color: var(--el-border-color);
  }
}
</style>