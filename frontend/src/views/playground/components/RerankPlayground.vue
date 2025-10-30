<template>
  <div class="rerank-playground">
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card header="重排序配置">
          <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px"
            @submit.prevent="sendRerankRequest">
            <!-- 实例选择 -->
            <el-form-item label="选择实例">
              <div style="display: flex; gap: 8px; align-items: center; width: 100%;">
                <el-select v-model="selectedInstanceId" placeholder="请选择重排序服务实例" @change="onInstanceChange"
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
                      {{ instancesLoading ? '加载中...' : '暂无可用实例，请先在实例管理中添加重排序服务实例' }}
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
                <el-text type="danger" size="small">请选择一个重排序服务实例</el-text>
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

            <!-- 查询输入 -->
            <el-form-item label="查询" prop="query" required>
              <el-input v-model="rerankConfig.query" type="textarea" :rows="3" placeholder="请输入查询文本" clearable
                show-word-limit maxlength="1000" />
            </el-form-item>

            <!-- 文档列表 -->
            <el-form-item label="文档列表" prop="documents" required>
              <div class="documents-container">
                <div v-for="(document, index) in rerankConfig.documents" :key="index" class="document-item">
                  <div class="document-header">
                    <el-tag type="primary" size="small">文档 {{ index + 1 }}</el-tag>
                    <el-button type="danger" size="small" :icon="Delete" circle @click="removeDocument(index)"
                      :disabled="rerankConfig.documents.length <= 1" />
                  </div>
                  <el-input v-model="rerankConfig.documents[index]" type="textarea" :rows="3"
                    :placeholder="`请输入第 ${index + 1} 个文档内容`" class="document-content" show-word-limit
                    maxlength="2000" />
                </div>
                <el-button type="primary" size="small" :icon="Plus" @click="addDocument"
                  :disabled="rerankConfig.documents.length >= 20">
                  添加文档
                </el-button>
              </div>
            </el-form-item>

            <!-- 高级参数 -->
            <el-collapse v-model="activeCollapse" class="config-collapse">
              <el-collapse-item title="高级参数" name="advanced">
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="返回数量">
                      <el-input-number v-model="rerankConfig.topN" :min="1" :max="rerankConfig.documents.length || 20"
                        style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="返回文档内容">
                      <el-switch v-model="rerankConfig.returnDocuments" />
                    </el-form-item>
                  </el-col>
                </el-row>
              </el-collapse-item>
            </el-collapse>

            <!-- 发送按钮 -->
            <el-form-item class="send-form-item">
              <div class="action-buttons">
                <el-button type="primary" :loading="requestState.loading" @click="sendRerankRequest"
                  :disabled="!canSendRequest" size="default" class="send-button">
                  <template #loading>
                    <div class="loading-content">
                      <el-icon class="is-loading">
                        <Loading />
                      </el-icon>
                      <span>处理中...</span>
                    </div>
                  </template>
                  <template #default>
                    <el-icon>
                      <Position />
                    </el-icon>
                    <span>发送请求</span>
                  </template>
                </el-button>
                <el-button @click="resetForm" :disabled="requestState.loading">
                  <el-icon>
                    <RefreshLeft />
                  </el-icon>
                  重置
                </el-button>
                <el-button v-if="requestState.loading" type="danger" @click="cancelRequest" size="default">
                  <el-icon>
                    <Close />
                  </el-icon>
                  取消
                </el-button>
              </div>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card class="response-card">
          <template #header>
            <div class="card-header">
              <span>重排序结果</span>
              <div class="response-actions" v-if="requestState.response">
                <el-tag :type="getStatusTagType(requestState.response.status)" size="small">
                  {{ requestState.response.status }} {{ requestState.response.statusText }}
                </el-tag>
                <el-tag size="small" type="info">
                  {{ requestState.response.duration }}ms
                </el-tag>
              </div>
            </div>
          </template>

          <div class="response-content">
            <!-- 加载状态 -->
            <div v-if="requestState.loading" class="loading-state">
              <el-skeleton :rows="5" animated />
              <div class="loading-text">正在处理重排序请求...</div>
            </div>

            <!-- 错误状态 -->
            <div v-else-if="requestState.error" class="error-state">
              <el-alert title="请求失败" :description="requestState.error" type="error" show-icon :closable="false" />
            </div>

            <!-- 成功响应 -->
            <div v-else-if="requestState.response" class="success-response">
              <div class="response-summary">
                <el-descriptions :column="2" size="small" border>
                  <el-descriptions-item label="模型">
                    {{ getActualData(requestState.response)?.model || rerankConfig.model }}
                  </el-descriptions-item>
                  <el-descriptions-item label="请求ID">
                    {{ getActualData(requestState.response)?.id || 'N/A' }}
                  </el-descriptions-item>
                  <el-descriptions-item label="结果数量">
                    {{ getActualData(requestState.response)?.results?.length || 0 }}
                  </el-descriptions-item>
                  <el-descriptions-item label="Token使用">
                    {{ getActualData(requestState.response)?.usage?.total_tokens || 'N/A' }}
                  </el-descriptions-item>
                </el-descriptions>
              </div>

              <!-- 重排序结果列表 -->
              <div class="rerank-results" v-if="getActualData(requestState.response)?.results">
                <h4>重排序结果</h4>
                <div class="results-list">
                  <div v-for="(result, index) in getActualData(requestState.response).results" :key="index"
                    class="result-item">
                    <div class="result-header">
                      <div class="result-rank">
                        <el-tag type="primary" size="small">
                          排名 {{ index + 1 }}
                        </el-tag>
                        <el-tag type="success" size="small">
                          原索引: {{ result.index }}
                        </el-tag>
                      </div>
                      <div class="result-score">
                        <el-progress :percentage="Math.round(result.score * 100)" :color="getScoreColor(result.score)"
                          :stroke-width="8" text-inside />
                        <span class="score-value">{{ result.score.toFixed(4) }}</span>
                      </div>
                    </div>

                    <div class="result-content" v-if="result.document">
                      <div class="content-label">文档内容:</div>
                      <div class="content-text">{{ result.document }}</div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 原始响应数据 -->
              <el-collapse v-model="responseCollapse" class="raw-response">
                <el-collapse-item title="原始响应数据" name="raw">
                  <pre class="json-content">{{ JSON.stringify(getActualData(requestState.response), null, 2) }}</pre>
                </el-collapse-item>
              </el-collapse>
            </div>

            <!-- 空状态 -->
            <div v-else class="empty-state">
              <el-empty description="配置重排序参数并发送请求以查看结果" :image-size="120" />
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Position, Plus, Delete, Close, Refresh, Loading, RefreshLeft, Check } from '@element-plus/icons-vue'
import { sendUniversalRequest } from '@/api/universal'
import { usePlaygroundData } from '@/composables/usePlaygroundData'
import type { UniversalApiRequest } from '@/api/universal'
import type {
  RerankRequestConfig,
  GlobalConfig,
  PlaygroundResponse,
  RequestState
} from '../types/playground'

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
const formRef = ref()

// 重排序配置
const rerankConfig = ref<RerankRequestConfig>({
  model: 'bge-reranker-v2-m3',
  query: '',
  documents: [],
  topN: undefined,
  returnDocuments: true
})

// 表单数据（包含所有需要验证的字段）
const formData = reactive({
  selectedInstanceId: '',
  model: rerankConfig.value.model,
  query: rerankConfig.value.query,
  documents: rerankConfig.value.documents,
  topN: rerankConfig.value.topN,
  returnDocuments: rerankConfig.value.returnDocuments
})

// 请求状态
const requestState = reactive<RequestState>({
  loading: false,
  error: null,
  response: null
})

// 折叠面板状态
const activeCollapse = ref<string[]>([])
const responseCollapse = ref<string[]>([])



// 使用优化后的数据管理
const {
  availableInstances,
  instancesLoading,
  selectedInstanceId,
  selectedInstanceInfo,
  hasInstances,
  onInstanceChange: handleInstanceChange,
  initializeData,
  refreshData,
  fetchInstances
} = usePlaygroundData('rerank')

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
  // 同步到formData
  formData.selectedInstanceId = instanceId

  if (!instanceId) {
    rerankConfig.value.model = ''
    formData.model = ''
    headersList.value = []
    currentHeaders.value = {}
    return
  }

  const instance = availableInstances.value.find(inst => inst.instanceId === instanceId)
  if (instance) {

    // 使用实例名称作为默认模型
    rerankConfig.value.model = instance.name || 'default-model'
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
const formRules: any = {
  selectedInstanceId: [
    {
      required: true,
      message: '请选择一个重排序服务实例',
      trigger: 'change',
      validator: (_rule: any, value: string, callback: Function) => {
        if (!value || value.trim() === '') {
          callback(new Error('请选择一个重排序服务实例'))
        } else {
          callback()
        }
      }
    }
  ],
  query: [
    { required: true, message: '请输入查询文本', trigger: 'blur' },
    { min: 1, max: 1000, message: '查询文本长度应在1-1000字符之间', trigger: 'blur' }
  ],
  documents: [
    {
      required: true,
      message: '请至少添加一个文档',
      trigger: 'change',
      validator: (_rule: any, value: string[], callback: Function) => {
        if (!value || value.length === 0) {
          callback(new Error('请至少添加一个文档'))
        } else if (value.some(doc => !doc.trim())) {
          callback(new Error('文档内容不能为空'))
        } else {
          callback()
        }
      }
    }
  ]
}

// 计算属性
const canSendRequest = computed(() => {
  return formData.selectedInstanceId &&
    formData.query.trim() &&
    formData.documents.length > 0 &&
    formData.documents.every(doc => doc.trim()) &&
    !requestState.loading
})

// 文档管理方法
const addDocument = () => {
  if (rerankConfig.value.documents.length >= 20) {
    ElMessage.warning('最多只能添加20个文档')
    return
  }
  rerankConfig.value.documents.push('')
}

const removeDocument = (index: number) => {
  rerankConfig.value.documents.splice(index, 1)
}

const clearAllDocuments = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要清空所有文档吗？此操作不可恢复。',
      '确认清空',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    rerankConfig.value.documents = []
    ElMessage.success('已清空所有文档')
  } catch {
    // 用户取消操作
  }
}



// 重置表单
const resetForm = async () => {
  try {
    await ElMessageBox.confirm('确定要重置所有配置吗？', '确认重置', {
      type: 'warning'
    })

    rerankConfig.value = {
      model: 'bge-reranker-v2-m3',
      query: '',
      documents: [''],
      topN: undefined,
      returnDocuments: true
    }

    // 同步到formData
    Object.assign(formData, {
      selectedInstanceId: '',
      model: rerankConfig.value.model,
      query: rerankConfig.value.query,
      documents: rerankConfig.value.documents,
      topN: rerankConfig.value.topN,
      returnDocuments: rerankConfig.value.returnDocuments
    })

    // 清空选择的实例
    selectedInstanceId.value = ''
    headersList.value = []
    currentHeaders.value = {}

    ElMessage.success('配置已重置')
  } catch {
    // 用户取消重置
  }
}

// 取消请求
const cancelRequest = () => {
  requestState.loading = false
  emit('response', null, false, '请求已取消')
  ElMessage.warning('请求已取消')
}

// 发送重排序请求
const sendRerankRequest = async () => {
  try {
    // 检查是否选择了实例
    if (!formData.selectedInstanceId) {
      ElMessage.error('请选择一个重排序服务实例')
      return
    }

    // 表单验证
    try {
      const valid = await formRef.value?.validate()
      if (!valid) return
    } catch (error) {
      console.log('表单验证失败:', error)
      return
    }

    // 设置加载状态
    requestState.loading = true
    requestState.error = null
    requestState.response = null
    emit('response', null, true, null)

    // 构建请求
    const requestBody = {
      model: rerankConfig.value.model.trim(),
      query: rerankConfig.value.query.trim(),
      documents: rerankConfig.value.documents.filter(doc => doc.trim()),
      ...(rerankConfig.value.topN && { top_n: rerankConfig.value.topN }),
      ...(rerankConfig.value.returnDocuments !== undefined && {
        return_documents: rerankConfig.value.returnDocuments
      })
    }

    const apiRequest: UniversalApiRequest = {
      endpoint: '/v1/rerank',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...currentHeaders.value
      },
      body: requestBody
    }

    // 发送request事件
    const requestSize = JSON.stringify(apiRequest.body).length
    emit('request', apiRequest, requestSize)

    // 发送请求
    const response = await sendUniversalRequest(apiRequest)

    // 处理成功响应
    requestState.response = response
    requestState.error = null
    emit('response', response, false, null)
    ElMessage.success('重排序请求成功')

  } catch (error: any) {
    // 处理错误
    const errorMessage = error.data?.error?.message ||
      error.data?.message ||
      error.message ||
      '重排序请求失败'

    requestState.error = errorMessage
    requestState.response = error.status ? error : null
    emit('response', error.status ? error : null, false, errorMessage)
    ElMessage.error(errorMessage)
  } finally {
    requestState.loading = false
  }
}

// 获取状态标签类型
const getStatusTagType = (status: number) => {
  if (status >= 200 && status < 300) return 'success'
  if (status >= 400 && status < 500) return 'warning'
  if (status >= 500) return 'danger'
  return 'info'
}

// 获取实际的数据对象（处理包装格式）
const getActualData = (response: any) => {
  if (!response) return null

  let data = response.data

  // 检查是否是包装的响应格式 (有success, message, data字段)
  if (data.success !== undefined && data.data) {
    data = data.data
  }

  return data
}

// 获取分数颜色
const getScoreColor = (score: number) => {
  if (score >= 0.8) return '#67c23a'
  if (score >= 0.6) return '#e6a23c'
  if (score >= 0.4) return '#f56c6c'
  return '#909399'
}



// 监听全局配置变化
watch(() => props.globalConfig, () => {
  // 可以在这里处理全局配置变化
}, { deep: true })

// 同步 rerankConfig 和 formData
watch(() => rerankConfig.value.query, (newQuery) => {
  formData.query = newQuery
})

watch(() => rerankConfig.value.documents, (newDocuments) => {
  formData.documents = newDocuments
}, { deep: true })

watch(() => rerankConfig.value.topN, (newTopN) => {
  formData.topN = newTopN
})

watch(() => rerankConfig.value.returnDocuments, (newReturnDocuments) => {
  formData.returnDocuments = newReturnDocuments
})

// 初始化时添加一个示例文档
if (rerankConfig.value.documents.length === 0) {
  rerankConfig.value.documents.push('')
}

// 监听全局刷新实例事件
const handleRefreshModels = () => {
  fetchInstances()
}

// 组件挂载时获取实例列表
onMounted(() => {
  // 初始化数据（使用缓存，静默加载）
  initializeData()

  // 同步初始状态
  formData.selectedInstanceId = selectedInstanceId.value
  formData.query = rerankConfig.value.query
  formData.documents = rerankConfig.value.documents
  formData.topN = rerankConfig.value.topN
  formData.returnDocuments = rerankConfig.value.returnDocuments

  // 监听全局刷新事件
  document.addEventListener('playground-refresh-models', handleGlobalRefresh)
})

// 处理全局刷新事件
const handleGlobalRefresh = () => {
  refreshData()
}

// 组件卸载时清理事件监听器
onUnmounted(() => {
  document.removeEventListener('playground-refresh-models', handleGlobalRefresh)
})
</script>

<style scoped>
.rerank-playground {
  padding: 20px;
  height: 100%;
}

/* 基础样式 */
.el-form-item {
  width: 100%;
}

.el-form-item__content {
  width: 100% !important;
}

/* 实例选择样式 */
.instance-required {
  border-color: var(--el-color-danger) !important;
}

.instance-hint {
  margin-top: 8px;
}

.instance-selected {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.response-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.rerank-form {
  height: 100%;
}

/* 文档管理样式 */
.documents-container {
  width: 100%;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 12px;
  background-color: var(--el-bg-color-page);
}

.document-item {
  width: 100%;
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background-color: var(--el-bg-color);
  box-sizing: border-box;
}

.document-item:last-child {
  margin-bottom: 0;
}

.document-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  width: 100%;
}

.document-content {
  margin-top: 8px;
  width: 100%;
}

.document-content :deep(.el-textarea) {
  width: 100%;
}

.document-content :deep(.el-textarea__inner) {
  width: 100%;
  box-sizing: border-box;
}

/* 配置折叠面板样式 */
.config-collapse {
  border: none;
}

.config-collapse :deep(.el-collapse-item__header) {
  background-color: var(--el-bg-color-page);
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 0 12px;
}

.config-collapse :deep(.el-collapse-item__content) {
  padding: 12px 0 0 0;
}

/* 响应内容样式 */
.response-content {
  height: 100%;
}

.loading-state {
  text-align: center;
  padding: 20px;
}

.loading-text {
  margin-top: 16px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.error-state {
  padding: 20px;
}

.success-response {
  padding: 16px;
}

.response-summary {
  margin-bottom: 20px;
}

.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 重排序结果样式 */
.rerank-results h4 {
  margin: 0 0 16px 0;
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 600;
}

.results-list {
  max-height: 500px;
  overflow-y: auto;
}

.result-item {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background-color: var(--el-bg-color);
  transition: all 0.3s ease;
}

.result-item:hover {
  border-color: var(--el-color-primary);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.result-item:last-child {
  margin-bottom: 0;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.result-rank {
  display: flex;
  gap: 8px;
  align-items: center;
}

.result-score {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 200px;
}

.result-score .el-progress {
  flex: 1;
}

.score-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  min-width: 60px;
  text-align: right;
}

.result-content {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.content-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
  font-weight: 500;
}

.content-text {
  font-size: 14px;
  color: var(--el-text-color-primary);
  line-height: 1.6;
  padding: 8px 12px;
  background-color: var(--el-bg-color-page);
  border-radius: 4px;
  border: 1px solid var(--el-border-color-lighter);
}

/* 原始响应数据样式 */
.raw-response {
  margin-top: 20px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
}

.raw-response :deep(.el-collapse-item__header) {
  background-color: var(--el-bg-color-page);
  padding: 0 16px;
}

.json-content {
  background-color: var(--el-bg-color-page);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 12px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-primary);
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .rerank-playground {
    padding: 16px;
  }

  .result-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .result-score {
    width: 100%;
    min-width: auto;
  }
}

@media (max-width: 768px) {
  .rerank-playground {
    padding: 12px;
  }

  .card-header {
    flex-direction: column;
    gap: 8px;
    align-items: flex-start;
  }

  .response-actions {
    width: 100%;
    justify-content: flex-end;
  }

  .action-buttons {
    flex-direction: column;
    align-items: stretch;
  }

  .action-buttons .el-button {
    width: 100%;
  }
}





/* 请求头配置样式 */
.headers-config {
  width: 100%;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 15px;
  background-color: var(--el-bg-color-page);
  box-sizing: border-box;
}

.headers-list {
  width: 100%;
  max-height: 300px;
  overflow-y: auto;
  margin-bottom: 10px;
}

.header-item {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
  align-items: center;
  width: 100%;
}

.header-key {
  flex: 1;
  min-width: 120px;
}

.header-key :deep(.el-input) {
  width: 100%;
}

.header-value {
  flex: 2;
}

.header-value :deep(.el-input) {
  width: 100%;
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

/* 发送按钮样式 */
.send-form-item {
  margin-top: 24px;
}

.action-buttons {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.send-button {
  min-width: 120px;
}

.loading-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.loading-content .el-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}
</style>