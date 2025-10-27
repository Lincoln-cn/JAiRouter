<template>
  <div class="embedding-playground">
    <el-row :gutter="20" class="playground-row">
      <el-col :span="12">
        <el-card header="嵌入配置" class="config-card">
          <el-form ref="formRef" :model="embeddingConfig" :rules="formRules" label-width="120px"
            @submit.prevent="sendRequest">
            <!-- 模型选择 -->
            <el-form-item label="模型" prop="model" required>
              <div style="display: flex; gap: 8px; align-items: center;">
                <el-select 
                  v-model="embeddingConfig.model" 
                  placeholder="请选择模型"
                  filterable
                  allow-create
                  style="flex: 1"
                  :loading="modelsLoading"
                >
                  <el-option
                    v-for="model in availableModels"
                    :key="model"
                    :label="model"
                    :value="model"
                  />
                  <template #empty>
                    <div style="padding: 10px; text-align: center; color: #999;">
                      {{ modelsLoading ? '加载中...' : '暂无可用模型，请先在实例管理中添加嵌入服务实例' }}
                    </div>
                  </template>
                </el-select>
                <el-button 
                  type="info" 
                  size="small" 
                  @click="fetchAvailableModels"
                  :loading="modelsLoading"
                  title="刷新模型列表"
                >
                  <el-icon><Refresh /></el-icon>
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
import { ref, reactive, computed, watch, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Connection, Refresh, Plus, Delete, Loading, Check, DocumentCopy } from '@element-plus/icons-vue'

import { sendUniversalRequest } from '@/api/universal'
import { getModelsByServiceType, getInstanceServiceType } from '@/api/models'
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

// 当前请求信息
const currentRequest = ref<PlaygroundRequest | null>(null)

// 嵌入响应结果
const embeddingResponse = ref<PlaygroundResponse | null>(null)
const embeddingError = ref<string | null>(null)

// 模型列表（从实例管理获取）
const availableModels = ref<string[]>([])
const modelsLoading = ref(false)

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

// 表单验证规则
const formRules: FormRules = {
  model: [
    { required: true, message: '请输入模型名称', trigger: 'blur' },
    { min: 1, max: 100, message: '模型名称长度应在 1 到 100 个字符之间', trigger: 'blur' }
  ],
  input: [
    { required: true, message: '请输入要嵌入的文本', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
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
  return embeddingConfig.model.trim() !== '' &&
    embeddingConfig.input !== '' &&
    !loading.value
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
  } else {
    embeddingConfig.input = batchInputs.value.filter(text => text.trim() !== '')
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
    ...props.globalConfig.customHeaders
  }

  if (props.globalConfig.authorization) {
    headers['Authorization'] = props.globalConfig.authorization
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
    // 输入验证
    const inputError = validateInput()
    if (inputError) {
      ElMessage.error(inputError)
      return
    }

    // 表单验证
    const valid = await formRef.value.validate()
    if (!valid) return

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
  fetchAvailableModels()
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
}
</style>