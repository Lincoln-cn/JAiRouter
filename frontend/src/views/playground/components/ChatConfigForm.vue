<template>
  <el-card header="对话配置">
    <el-form ref="formRef" :model="chatConfig" :rules="formRules" label-width="120px"
      @submit.prevent="handleSubmit">
      <!-- 实例选择 -->
      <el-form-item label="选择实例">
        <div style="display: flex; gap: 8px; align-items: center; width: 100%;">
          <el-select v-model="selectedInstanceId" placeholder="请选择对话服务实例" @change="handleInstanceChange"
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
            已选择实例：{{ selectedInstanceInfo.name }}
          </el-text>
        </div>
      </el-form-item>

      <!-- 请求头配置 -->
      <el-form-item v-if="selectedInstanceInfo" label="请求头配置">
        <div class="headers-config">
          <div class="headers-list">
            <div v-for="(header, index) in headersList" :key="index" class="header-item">
              <el-input v-model="header.key" placeholder="请求头名称" size="small" @input="handleHeaderChange"
                class="header-key" :disabled="header.fromInstance" />
              <el-input v-model="header.value" placeholder="请求头值" size="small" @input="handleHeaderChange"
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

      <!-- 高级参数 -->
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

      <!-- 操作按钮 -->
      <el-form-item>
        <div class="action-buttons">
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit" :disabled="!canSubmit"
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
          <el-button @click="handleReset" :disabled="submitLoading">
            <el-icon>
              <RefreshLeft />
            </el-icon>
            重置
          </el-button>
          <el-button v-if="submitLoading" type="danger" @click="handleCancel" size="default">
            <el-icon>
              <Close />
            </el-icon>
            取消
          </el-button>
        </div>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Delete, Loading, Promotion, RefreshLeft, Close, Check, DocumentCopy, Refresh } from '@element-plus/icons-vue'
import { usePlaygroundData } from '@/composables/usePlaygroundData'
import type {
  ChatRequestConfig,
  ChatMessage,
  GlobalConfig,
  PlaygroundRequest
} from '../types/playground'
import { DEFAULT_CONFIGS } from '../types/playground'

// Props
interface Props {
  globalConfig?: GlobalConfig
  submitLoading?: boolean
  loadingText?: string
}

const props = withDefaults(defineProps<Props>(), {
  globalConfig: () => ({ authorization: '', customHeaders: {} }),
  submitLoading: false,
  loadingText: '发送中...'
})

// Emits
const emit = defineEmits<{
  submit: [request: PlaygroundRequest]
  cancel: []
  reset: []
  'update:globalConfig': [config: GlobalConfig]
}>()

// 表单引用
const formRef = ref<FormInstance>()

// 状态管理
const activeCollapse = ref<string[]>([])
const stopWordsInput = ref('')

// 使用 Playground 数据
const {
  availableInstances,
  instancesLoading,
  selectedInstanceId,
  selectedInstanceInfo,
  onInstanceChange: handleInstanceChangeBase,
  fetchInstances
} = usePlaygroundData('chat')

// 请求头配置
interface HeaderItem {
  key: string
  value: string
  fromInstance: boolean
}

const headersList = ref<HeaderItem[]>([])
const currentHeaders = ref<Record<string, string>>({})

// 实例选择变化处理
const handleInstanceChange = (instanceId: string) => {
  handleInstanceChangeBase(instanceId)

  if (!instanceId) {
    chatConfig.model = ''
    headersList.value = []
    currentHeaders.value = {}
    return
  }

  const instance = availableInstances.value.find(inst => inst.instanceId === instanceId)
  if (instance) {
    chatConfig.model = instance.name || 'default-model'
    initializeHeaders(instance.headers || {})
    ElMessage.success(`已选择实例 "${instance.name}"`)
  }
}

// 初始化请求头
const initializeHeaders = (instanceHeaders: Record<string, string>) => {
  headersList.value = []
  currentHeaders.value = {}

  Object.entries(instanceHeaders).forEach(([key, value]) => {
    headersList.value.push({
      key,
      value,
      fromInstance: true
    })
    currentHeaders.value[key] = value
  })

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
    handleHeaderChange()
  }
}

// 请求头变化处理
const handleHeaderChange = () => {
  const newHeaders: Record<string, string> = {}
  const customHeaders: Record<string, string> = {}

  headersList.value.forEach(header => {
    if (header.key.trim() && header.value.trim()) {
      const key = header.key.trim()
      const value = header.value.trim()
      newHeaders[key] = value

      if (!header.fromInstance) {
        customHeaders[key] = value
      }
    }
  })

  currentHeaders.value = newHeaders

  const updatedGlobalConfig = {
    ...props.globalConfig,
    customHeaders: customHeaders
  }

  emit('update:globalConfig', updatedGlobalConfig)
}

// 对话配置
const chatConfig = reactive<ChatRequestConfig>({
  ...DEFAULT_CONFIGS.chat,
  model: '',
  messages: [],
  stream: true,
  maxTokens: 1000,
  temperature: 0.7,
  topP: 1.0,
  topK: undefined,
  frequencyPenalty: 0,
  presencePenalty: 0,
  stop: undefined,
  user: ''
})

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
const canSubmit = computed(() => {
  return selectedInstanceId.value &&
    selectedInstanceInfo.value &&
    chatConfig.messages.length > 0 &&
    chatConfig.messages.every(msg => msg.content.trim()) &&
    !props.submitLoading
})

// 更新停止词
const updateStopWords = () => {
  if (stopWordsInput.value.trim()) {
    const words = stopWordsInput.value.split(',').map(w => w.trim()).filter(w => w)
    chatConfig.stop = words.length === 1 ? words[0] : words
  } else {
    chatConfig.stop = undefined
  }
}

// 监听配置变化
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
const handleReset = async () => {
  try {
    await ElMessageBox.confirm('确定要重置所有配置吗？', '确认重置', {
      type: 'warning'
    })

    Object.assign(chatConfig, {
      ...DEFAULT_CONFIGS.chat,
      model: '',
      messages: [],
      stream: true,
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
    emit('reset')
    ElMessage.success('配置已重置')
  } catch {
    // 用户取消
  }
}

// 构建请求
const buildRequest = (): PlaygroundRequest => {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  }

  if (props.globalConfig?.authorization) {
    headers['Authorization'] = props.globalConfig.authorization
  }

  Object.assign(headers, currentHeaders.value)

  const requestBody: any = {
    model: chatConfig.model,
    messages: chatConfig.messages.map(msg => ({
      role: msg.role,
      content: msg.content,
      ...(msg.name && { name: msg.name })
    }))
  }

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
    endpoint: '/v1/chat/completions',
    method: 'POST',
    headers,
    body: requestBody
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    const valid = await formRef.value.validate()
    if (!valid) {
      ElMessage.error('请检查表单输入')
      return
    }

    if (!selectedInstanceInfo.value) {
      ElMessage.error('请先选择一个对话服务实例')
      return
    }

    if (!chatConfig.model) {
      ElMessage.error('模型名称不能为空')
      return
    }

    const request = buildRequest()
    emit('submit', request)
  } catch {
    // 验证失败
  }
}

// 取消请求
const handleCancel = () => {
  emit('cancel')
}

// 暴露方法供父组件调用
defineExpose({
  chatConfig,
  resetForm: () => {
    Object.assign(chatConfig, {
      ...DEFAULT_CONFIGS.chat,
      model: '',
      messages: [],
      stream: true,
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
  }
})
</script>

<style scoped>
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
</style>
