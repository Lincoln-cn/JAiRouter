<template>
  <div class="embedding-playground">
    <el-row :gutter="20" class="playground-row">
      <el-col :span="12">
        <el-card header="嵌入配置" class="config-card">
          <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px"
            @submit.prevent="sendRequest">
            <!-- 实例选择 -->
            <el-form-item label="选择实例" prop="selectedInstanceId" :rules="formRules.selectedInstanceId">
              <div style="display: flex; gap: 8px; align-items: center; width: 100%;">
                <el-select 
                  v-model="selectedInstanceId" 
                  placeholder="请选择嵌入服务实例"
                  @change="onInstanceChange"
                  style="flex: 1"
                  :loading="instancesLoading"
                  clearable
                >
                  <el-option
                    v-for="instance in availableInstances"
                    :key="instance.instanceId"
                    :label="instance.name"
                    :value="instance.instanceId"
                  >
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                      <span>{{ instance.name }}</span>
                      <el-tag 
                        v-if="instance.headers && Object.keys(instance.headers).length > 0"
                        type="success" 
                        size="small"
                      >
                        {{ Object.keys(instance.headers).length }} 个请求头
                      </el-tag>
                    </div>
                  </el-option>
                  <template #empty>
                    <div style="padding: 10px; text-align: center; color: #999;">
                      {{ instancesLoading ? '加载中...' : '暂无可用实例，请先在实例管理中添加嵌入服务实例' }}
                    </div>
                  </template>
                </el-select>
                <el-button 
                  type="info" 
                  size="small" 
                  @click="fetchInstances"
                  :loading="instancesLoading"
                  title="刷新实例列表"
                >
                  <el-icon><Refresh /></el-icon>
                </el-button>
              </div>
            </el-form-item>

            <!-- 请求头配置 -->
            <el-form-item v-if="selectedInstanceInfo" label="请求头配置">
              <div class="headers-config">
                <div class="headers-list">
                  <div 
                    v-for="(header, index) in headersList" 
                    :key="index"
                    class="header-item"
                  >
                    <el-input
                      v-model="header.key"
                      placeholder="请求头名称"
                      size="small"
                      @input="onHeaderChange"
                      class="header-key"
                      :disabled="header.fromInstance"
                    />
                    <el-input
                      v-model="header.value"
                      placeholder="请求头值"
                      size="small"
                      @input="onHeaderChange"
                      class="header-value"
                      :type="header.key.toLowerCase().includes('authorization') || header.key.toLowerCase().includes('key') ? 'password' : 'text'"
                      :show-password="header.key.toLowerCase().includes('authorization') || header.key.toLowerCase().includes('key')"
                    />
                    <el-tag 
                      v-if="header.fromInstance" 
                      type="success" 
                      size="small"
                      class="instance-tag"
                    >
                      实例
                    </el-tag>
                    <el-button 
                      v-else
                      type="danger" 
                      size="small" 
                      @click="removeHeader(index)"
                      class="remove-btn"
                    >
                      <el-icon><Close /></el-icon>
                    </el-button>
                  </div>
                </div>
                <el-button 
                  type="primary" 
                  size="small" 
                  @click="addHeader"
                  class="add-header-btn"
                >
                  <el-icon><Plus /></el-icon>
                  添加请求头
                </el-button>
              </div>
            </el-form-item>

            <!-- 输入模式选择 -->
            <el-form-item label="输入模式">
              <el-radio-group v-model="inputMode" @change="onInputModeChange">
                <el-radio value="single">单个文本</el-radio>
                <el-radio value="batch">批量文本</el-radio>
              </el-radio-group>
            </el-form-item>

            <!-- 单个文本输入 -->
            <el-form-item v-if="inputMode === 'single'" label="输入文本" prop="input" required>
              <el-input v-model="singleInput" type="textarea" :rows="4" placeholder="请输入要嵌入的文本" maxlength="8192"
                show-word-limit @input="updateInput" />
            </el-form-item>

            <!-- 批量文本输入 -->
            <el-form-item v-if="inputMode === 'batch'" label="批量文本" prop="input" required>
              <div class="batch-input-container">
                <div v-for="(text, index) in batchInputs" :key="index" class="batch-input-item">
                  <el-input v-model="batchInputs[index]" type="textarea" :rows="2" :placeholder="`文本 ${index + 1}`"
                    maxlength="8192" show-word-limit @input="updateInput" />
                  <el-button type="danger" size="small" :icon="Delete" circle @click="removeBatchInput(index)"
                    :disabled="batchInputs.length <= 1" />
                </div>
                <el-button type="primary" size="small" :icon="Plus" @click="addBatchInput"
                  :disabled="batchInputs.length >= 10">
                  添加文本 ({{ batchInputs.length }}/10)
                </el-button>
              </div>
            </el-form-item>

            <!-- 高级参数 -->
            <el-divider content-position="left">高级参数</el-divider>

            <el-form-item label="编码格式">
              <el-select v-model="embeddingConfig.encodingFormat" placeholder="选择编码格式">
                <el-option label="float" value="float" />
                <el-option label="base64" value="base64" />
              </el-select>
            </el-form-item>

            <el-form-item label="维度">
              <el-input-number v-model="embeddingConfig.dimensions" :min="1" :max="3072" placeholder="嵌入向量维度（可选）"
                controls-position="right" style="width: 100%" />
            </el-form-item>

            <el-form-item label="用户标识">
              <el-input v-model="embeddingConfig.user" placeholder="用户标识（可选）" clearable />
            </el-form-item>

            <!-- 操作按钮 -->
            <el-form-item>
              <el-button type="primary" @click="sendRequest" :loading="loading" :disabled="!canSendRequest">
                <el-icon>
                  <Connection />
                </el-icon>
                发送请求
              </el-button>
              <el-button @click="resetForm">
                <el-icon>
                  <Refresh />
                </el-icon>
                重置
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card header="嵌入结果" class="result-card">
          <div class="embedding-result">
            <div v-if="loading" class="loading-state">
              <el-icon class="is-loading">
                <Loading />
              </el-icon>
              <p>正在生成嵌入向量...</p>
            </div>

            <div v-else-if="embeddingError" class="error-state">
              <el-alert :title="embeddingError" type="error" show-icon :closable="false" />
            </div>

            <div v-else-if="embeddingResponse" class="response-content">
              <div class="response-info">
                <el-tag type="success">
                  <el-icon>
                    <Check />
                  </el-icon>
                  嵌入生成成功
                </el-tag>
                <span class="duration-info">
                  耗时: {{ embeddingResponse.duration }}ms
                </span>
              </div>

              <div class="embeddings-list">
                <div v-for="(embedding, index) in getEmbeddings()" :key="index" class="embedding-item">
                  <div class="embedding-header">
                    <span class="embedding-index">嵌入 {{ index + 1 }}</span>
                    <el-button type="text" size="small" @click="copyEmbedding(embedding)">
                      <el-icon>
                        <DocumentCopy />
                      </el-icon>
                      复制向量
                    </el-button>
                  </div>
                  <div class="embedding-preview">
                    <span class="vector-info">
                      维度: {{ embedding.length }} |
                      前5个值: [{{embedding.slice(0, 5).map((v: number) => v.toFixed(6)).join(', ')}}...]
                    </span>
                  </div>
                </div>
              </div>

              <div v-if="getUsageInfo()" class="usage-info">
                <el-descriptions :column="2" size="small" border>
                  <el-descriptions-item label="提示令牌">
                    {{ getUsageInfo().prompt_tokens }}
                  </el-descriptions-item>
                  <el-descriptions-item label="总令牌">
                    {{ getUsageInfo().total_tokens }}
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </div>

            <div v-else class="empty-state">
              <el-empty description="发送嵌入请求后，生成的向量将在此处显示" :image-size="80" />
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Connection, Refresh, Plus, Delete, Loading, Check, DocumentCopy, Close } from '@element-plus/icons-vue'

import { sendUniversalRequest } from '@/api/universal'
import { getModelsByServiceType, getInstanceServiceType } from '@/api/models'
import { getServiceInstances } from '@/api/dashboard'
import type {
  EmbeddingRequestConfig,
  GlobalConfig,
  PlaygroundResponse,
  PlaygroundRequest
} from '../types/playground'
import { DEFAULT_CONFIGS } from '../types/playground'

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
  request: [request: any | null, size?: number]
  'update:globalConfig': [config: GlobalConfig]
}>()

// 表单引用
const formRef = ref<FormInstance>()

// 状态管理
const loading = ref(false)
const inputMode = ref<'single' | 'batch'>('single')
const singleInput = ref('')
const batchInputs = ref<string[]>([''])

// 嵌入配置
const embeddingConfig = reactive<EmbeddingRequestConfig>({
  model: '',
  input: '',
  encodingFormat: 'float',
  dimensions: undefined,
  user: ''
})

// 表单数据（包含所有需要验证的字段）
const formData = reactive({
  selectedInstanceId: '',
  model: embeddingConfig.model,
  input: embeddingConfig.input,
  encodingFormat: embeddingConfig.encodingFormat,
  dimensions: embeddingConfig.dimensions,
  user: embeddingConfig.user
})

// 当前请求信息
const currentRequest = ref<PlaygroundRequest | null>(null)

// 嵌入响应结果
const embeddingResponse = ref<PlaygroundResponse | null>(null)
const embeddingError = ref<string | null>(null)

// 模型列表（从实例管理获取）
const availableModels = ref<string[]>([])
const modelsLoading = ref(false)

// 实例选择相关状态
const availableInstances = ref<any[]>([])
const instancesLoading = ref(false)
const selectedInstanceId = ref('')
const selectedInstanceInfo = ref<any>(null)

// 请求头配置状态
interface HeaderItem {
  key: string
  value: string
  fromInstance: boolean
}

const headersList = ref<HeaderItem[]>([])
const currentHeaders = ref<Record<string, string>>({})

// 获取可用实例列表
const fetchInstances = async () => {
  instancesLoading.value = true
  try {
    const response = await getServiceInstances('embedding')
    if (response.data?.success) {
      availableInstances.value = response.data.data || []
      ElMessage.success(`已刷新实例列表，找到 ${availableInstances.value.length} 个可用实例`)
    } else {
      availableInstances.value = []
      ElMessage.warning('获取实例列表失败')
    }
  } catch (error) {
    console.error('获取实例列表失败:', error)
    availableInstances.value = []
    ElMessage.error('获取实例列表失败')
  } finally {
    instancesLoading.value = false
  }
}

// 获取可用模型列表
const fetchAvailableModels = async () => {
  modelsLoading.value = true
  try {
    const serviceType = getInstanceServiceType('embedding')
    const models = await getModelsByServiceType(serviceType)
    availableModels.value = models
    
    // 如果当前选择的模型不在可用列表中，给出提示
    if (embeddingConfig.model && !models.includes(embeddingConfig.model)) {
      ElMessage.warning(`当前选择的模型 "${embeddingConfig.model}" 在实例管理中不存在或未启用，请检查实例配置`)
    }
    
    ElMessage.success(`已刷新模型列表，找到 ${models.length} 个可用模型`)
  } catch (error) {
    console.error('获取模型列表失败:', error)
    ElMessage.error('获取模型列表失败')
  } finally {
    modelsLoading.value = false
  }
}

// 实例选择变化处理
const onInstanceChange = (instanceId: string) => {
  // 同步到formData
  formData.selectedInstanceId = instanceId
  
  if (!instanceId) {
    selectedInstanceInfo.value = null
    embeddingConfig.model = ''
    formData.model = ''
    headersList.value = []
    currentHeaders.value = {}
    return
  }
  
  const instance = availableInstances.value.find(inst => inst.instanceId === instanceId)
  if (instance) {
    selectedInstanceInfo.value = instance
    
    // 使用实例名称作为默认模型
    embeddingConfig.model = instance.name || 'default-model'
    formData.model = instance.name || 'default-model'
    
    // 初始化请求头列表
    initializeHeaders(instance.headers || {})
    
    ElMessage.success(`已选择实例 "${instance.name}"`)
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
  // 重新构建headers对象
  const newHeaders: Record<string, string> = {}
  
  headersList.value.forEach(header => {
    if (header.key.trim() && header.value.trim()) {
      newHeaders[header.key.trim()] = header.value.trim()
    }
  })
  
  currentHeaders.value = newHeaders
  
  // 更新全局配置
  const updatedGlobalConfig = {
    ...props.globalConfig,
    customHeaders: newHeaders
  }
  
  emit('update:globalConfig', updatedGlobalConfig)
}

// 表单验证规则
const formRules: FormRules = {
  selectedInstanceId: [
    { 
      required: true, 
      message: '请选择一个嵌入服务实例', 
      trigger: 'change',
      validator: (_rule, value, callback) => {
        if (!value || value.trim() === '') {
          callback(new Error('请选择一个嵌入服务实例'))
        } else {
          callback()
        }
      }
    }
  ],
  input: [
    { required: true, message: '请输入要嵌入的文本', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (inputMode.value === 'single') {
          if (!value || value.trim() === '') {
            callback(new Error('请输入要嵌入的文本'))
          } else if (value.length > 8192) {
            callback(new Error('单个文本长度不能超过 8192 个字符'))
          } else {
            callback()
          }
        } else {
          const inputs = Array.isArray(value) ? value : []
          if (inputs.length === 0 || inputs.every(text => !text || text.trim() === '')) {
            callback(new Error('请至少输入一个文本'))
          } else if (inputs.some(text => text && text.length > 8192)) {
            callback(new Error('每个文本长度不能超过 8192 个字符'))
          } else if (inputs.filter(text => text && text.trim()).length > 10) {
            callback(new Error('最多支持 10 个文本'))
          } else {
            callback()
          }
        }
      },
      trigger: 'blur'
    }
  ]
}

// 计算属性
const canSendRequest = computed(() => {
  const hasInstance = formData.selectedInstanceId && formData.selectedInstanceId.trim() !== ''
  const hasInput = inputMode.value === 'single' 
    ? singleInput.value && singleInput.value.trim() !== ''
    : batchInputs.value.some(text => text && text.trim() !== '')
  
  return hasInstance && hasInput && !loading.value
})

// 监听输入模式变化
const onInputModeChange = () => {
  if (inputMode.value === 'single') {
    singleInput.value = Array.isArray(embeddingConfig.input)
      ? embeddingConfig.input[0] || ''
      : embeddingConfig.input || ''
    updateInput()
  } else {
    batchInputs.value = Array.isArray(embeddingConfig.input)
      ? embeddingConfig.input.filter(text => text.trim() !== '')
      : [embeddingConfig.input || ''].filter(text => text.trim() !== '')

    if (batchInputs.value.length === 0) {
      batchInputs.value = ['']
    }
    updateInput()
  }
}

// 更新输入数据
const updateInput = () => {
  if (inputMode.value === 'single') {
    embeddingConfig.input = singleInput.value
    formData.input = singleInput.value
  } else {
    embeddingConfig.input = batchInputs.value.filter(text => text.trim() !== '')
    formData.input = batchInputs.value.filter(text => text.trim() !== '')
  }
}

// 添加批量输入项
const addBatchInput = () => {
  if (batchInputs.value.length < 10) {
    batchInputs.value.push('')
  }
}

// 删除批量输入项
const removeBatchInput = (index: number) => {
  if (batchInputs.value.length > 1) {
    batchInputs.value.splice(index, 1)
    updateInput()
  }
}

// 构建请求
const buildRequest = (): PlaygroundRequest => {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...currentHeaders.value
  }

  // 处理输入数据
  let processedInput: string | string[]
  if (inputMode.value === 'single') {
    processedInput = singleInput.value.trim()
  } else {
    processedInput = batchInputs.value
      .filter(text => text && text.trim())
      .map(text => text.trim())
  }

  // 构建请求体
  const requestBody: any = {
    model: embeddingConfig.model.trim(),
    input: processedInput
  }

  // 添加可选参数
  if (embeddingConfig.encodingFormat && embeddingConfig.encodingFormat !== 'float') {
    requestBody.encoding_format = embeddingConfig.encodingFormat
  }

  if (embeddingConfig.dimensions && embeddingConfig.dimensions > 0) {
    requestBody.dimensions = embeddingConfig.dimensions
  }

  if (embeddingConfig.user && embeddingConfig.user.trim()) {
    requestBody.user = embeddingConfig.user.trim()
  }

  return {
    endpoint: '/v1/embeddings',
    method: 'POST',
    headers,
    body: requestBody
  }
}

// 验证输入数据
const validateInput = (): string | null => {
  if (inputMode.value === 'single') {
    if (!singleInput.value || singleInput.value.trim() === '') {
      return '请输入要嵌入的文本'
    }
    if (singleInput.value.length > 8192) {
      return '单个文本长度不能超过 8192 个字符'
    }
  } else {
    const validInputs = batchInputs.value.filter(text => text && text.trim())
    if (validInputs.length === 0) {
      return '请至少输入一个文本'
    }
    if (validInputs.length > 10) {
      return '最多支持 10 个文本'
    }
    if (validInputs.some(text => text.length > 8192)) {
      return '每个文本长度不能超过 8192 个字符'
    }
  }
  return null
}

// 处理响应数据
const processResponse = (response: PlaygroundResponse): PlaygroundResponse => {
  // 如果响应包含嵌入数据，添加一些统计信息
  if (response.data && response.data.data && Array.isArray(response.data.data)) {
    const embeddings = response.data.data
    const stats = {
      count: embeddings.length,
      dimensions: embeddings[0]?.embedding?.length || 0,
      totalTokens: response.data.usage?.total_tokens || 0,
      promptTokens: response.data.usage?.prompt_tokens || 0
    }

    // 在响应数据中添加统计信息（不修改原始数据）
    const enhancedData = {
      ...response.data,
      _stats: stats
    }

    return {
      ...response,
      data: enhancedData
    }
  }

  return response
}

// 发送请求
const sendRequest = async () => {
  if (!formRef.value) return

  try {
    // 检查是否选择了实例
    if (!formData.selectedInstanceId) {
      ElMessage.error('请选择一个嵌入服务实例')
      return
    }

    // 输入验证
    const inputError = validateInput()
    if (inputError) {
      ElMessage.error(inputError)
      return
    }

    // 表单验证
    try {
      const valid = await formRef.value.validate()
      if (!valid) return
    } catch (error) {
      console.log('表单验证失败:', error)
      return
    }

    loading.value = true
    embeddingError.value = null
    embeddingResponse.value = null
    emit('response', null, true)

    // 构建请求
    const request = buildRequest()
    currentRequest.value = request

    console.log('发送嵌入请求:', request)

    // 发送request事件
    const requestSize = JSON.stringify(request.body).length
    emit('request', request, requestSize)

    // 发送请求
    const response = await sendUniversalRequest(request)

    // 处理响应
    const processedResponse = processResponse(response)
    embeddingResponse.value = processedResponse
    emit('response', processedResponse, false)

    // 显示成功消息
    if (response.status >= 200 && response.status < 300) {
      const embeddings = response.data?.data
      if (embeddings && Array.isArray(embeddings)) {
        ElMessage.success(`嵌入请求成功！生成了 ${embeddings.length} 个嵌入向量`)
      } else {
        ElMessage.success('嵌入请求发送成功')
      }
    } else {
      ElMessage.warning(`请求完成，状态码: ${response.status}`)
    }

  } catch (error: any) {
    console.error('嵌入请求失败:', error)

    // 构建详细的错误信息
    let errorMessage = '嵌入请求失败'
    if (error.status) {
      errorMessage += ` (${error.status})`
    }
    if (error.data?.error) {
      if (typeof error.data.error === 'string') {
        errorMessage += `: ${error.data.error}`
      } else if (error.data.error.message) {
        errorMessage += `: ${error.data.error.message}`
      }
    } else if (error.message) {
      errorMessage += `: ${error.message}`
    }

    embeddingError.value = errorMessage
    emit('response', null, false, errorMessage)
    ElMessage.error(errorMessage)
  } finally {
    loading.value = false
  }
}

// 重置表单
const resetForm = async () => {
  try {
    await ElMessageBox.confirm('确定要重置所有配置吗？', '确认重置', {
      type: 'warning'
    })

    // 重置配置
    Object.assign(embeddingConfig, {
      model: '',
      input: '',
      encodingFormat: 'float',
      dimensions: undefined,
      user: ''
    })
    
    // 重置表单数据
    Object.assign(formData, {
      selectedInstanceId: '',
      input: '',
      model: '',
      encodingFormat: 'float',
      dimensions: undefined,
      user: ''
    })
    
    // 重置实例选择
    selectedInstanceId.value = ''
    selectedInstanceInfo.value = null

    // 重置输入状态
    inputMode.value = 'single'
    singleInput.value = ''
    batchInputs.value = ['']

    // 清除请求和响应
    currentRequest.value = null
    emit('response', null)

    // 重置表单验证
    await nextTick()
    formRef.value?.clearValidate()

    ElMessage.success('配置已重置')
  } catch {
    // 用户取消重置
  }
}

// 获取实际的数据对象（处理包装格式）
const getActualData = () => {
  if (!embeddingResponse.value) return null
  
  let data = embeddingResponse.value.data
  
  // 检查是否是包装的响应格式 (有success, message, data字段)
  if (data.success !== undefined && data.data) {
    data = data.data
  }
  
  return data
}

// 获取嵌入向量
const getEmbeddings = () => {
  const data = getActualData()
  if (!data || !data.data) return []
  return data.data.map((item: any) => item.embedding || [])
}

// 获取使用信息
const getUsageInfo = () => {
  const data = getActualData()
  return data?.usage || null
}

// 复制嵌入向量
const copyEmbedding = async (embedding: number[]) => {
  try {
    const embeddingText = JSON.stringify(embedding)
    await navigator.clipboard.writeText(embeddingText)
    ElMessage.success('嵌入向量已复制到剪贴板')
  } catch (error) {
    ElMessage.error('复制失败')
  }
}

// 初始化默认配置
const initializeDefaults = () => {
  const defaults = DEFAULT_CONFIGS.embedding
  Object.assign(embeddingConfig, defaults)
}

// 组件挂载时初始化
onMounted(() => {
  initializeDefaults()
  fetchInstances()
  fetchAvailableModels()
  
  // 同步初始状态
  formData.selectedInstanceId = selectedInstanceId.value
  formData.input = embeddingConfig.input
})
</script>

<style scoped>
.embedding-playground {
  padding: 20px;
  height: 100%;
  overflow: hidden;
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

.playground-row {
  height: 100%;
}

.config-card,
.result-card {
  height: 100%;
}

.config-card :deep(.el-card__body),
.result-card :deep(.el-card__body) {
  height: calc(100% - 60px);
  overflow-y: auto;
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

.batch-input-container {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.batch-input-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.batch-input-item .el-textarea {
  flex: 1;
}

.batch-input-item .el-button {
  margin-top: 5px;
  flex-shrink: 0;
}

/* 表单样式优化 */
.el-form-item {
  margin-bottom: 20px;
}

.el-divider {
  margin: 20px 0;
}

/* 按钮组样式 */
.el-form-item:last-child {
  margin-bottom: 0;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-light);
}

.el-form-item:last-child .el-form-item__content {
  display: flex;
  gap: 10px;
}

/* 输入框样式 */
.el-textarea :deep(.el-textarea__inner) {
  resize: vertical;
  min-height: 80px;
}

.el-input-number {
  width: 100%;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .playground-row {
    flex-direction: column;
  }

  .playground-row .el-col {
    width: 100% !important;
    margin-bottom: 20px;
  }
}

@media (max-width: 768px) {
  .embedding-playground {
    padding: 10px;
  }

  .batch-input-item {
    flex-direction: column;
    align-items: stretch;
  }

  .batch-input-item .el-button {
    margin-top: 10px;
    align-self: flex-end;
  }
}

/* 嵌入结果样式 */
.embedding-result {
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

.embeddings-list {
  margin-bottom: 16px;
}

.embedding-item {
  margin-bottom: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  overflow: hidden;
}

.embedding-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background-color: var(--el-bg-color-page);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.embedding-index {
  font-size: 12px;
  font-weight: 500;
  color: var(--el-color-primary);
}

.embedding-preview {
  padding: 12px;
}

.vector-info {
  font-size: 12px;
  color: var(--el-text-color-regular);
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
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

/* 深色主题适配 */
@media (prefers-color-scheme: dark) {
  .el-form-item:last-child {
    border-top-color: var(--el-border-color-darker);
  }

  .embedding-header {
    background-color: var(--el-bg-color-overlay);
  }

  .embedding-item {
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