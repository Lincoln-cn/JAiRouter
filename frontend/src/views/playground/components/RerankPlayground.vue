<template>
  <div class="rerank-playground">
    <el-row :gutter="20" class="playground-row">
      <el-col :span="12">
        <el-card class="config-card">
          <template #header>
            <div class="card-header">
              <span>重排序配置</span>
              <el-button type="primary" size="small" :loading="requestState.loading" @click="sendRerankRequest"
                :disabled="!canSendRequest">
                <el-icon>
                  <Position />
                </el-icon>
                发送请求
              </el-button>
            </div>
          </template>

          <el-form ref="formRef" :model="rerankConfig" :rules="formRules" label-width="120px" class="rerank-form">
            <!-- 模型选择 -->
            <el-form-item label="模型" prop="model" required>
              <div class="model-select-container">
                <el-select v-model="rerankConfig.model" placeholder="请选择模型" filterable allow-create style="width: 100%"
                  :loading="modelsLoading">
                  <el-option v-for="model in availableModels" :key="model" :label="model" :value="model" />
                  <template #empty>
                    <div style="padding: 10px; text-align: center; color: #999;">
                      {{ modelsLoading ? '加载中...' : '暂无可用模型，请先在实例管理中添加重排序服务实例' }}
                    </div>
                  </template>
                </el-select>
                <el-button type="info" size="small" @click="fetchAvailableModels" class="refresh-btn"
                  :loading="modelsLoading" title="刷新模型列表">
                  <el-icon>
                    <Refresh />
                  </el-icon>
                </el-button>
              </div>
            </el-form-item>

            <!-- 查询输入 -->
            <el-form-item label="查询" prop="query" required>
              <el-input v-model="rerankConfig.query" type="textarea" :rows="3" placeholder="请输入查询文本" clearable
                show-word-limit maxlength="1000" />
            </el-form-item>

            <!-- 文档列表管理 -->
            <el-form-item label="文档列表" prop="documents" required>
              <div class="documents-section">
                <div class="documents-header">
                  <span class="documents-count">文档数量: {{ rerankConfig.documents.length }}</span>
                  <div class="documents-actions">
                    <el-button type="primary" size="small" @click="addDocument"
                      :disabled="rerankConfig.documents.length >= 20">
                      <el-icon>
                        <Plus />
                      </el-icon>
                      添加文档
                    </el-button>
                    <el-button type="danger" size="small" @click="clearAllDocuments"
                      :disabled="rerankConfig.documents.length === 0">
                      <el-icon>
                        <Delete />
                      </el-icon>
                      清空全部
                    </el-button>
                  </div>
                </div>

                <div class="documents-list">
                  <div v-for="(document, index) in rerankConfig.documents" :key="index" class="document-item">
                    <div class="document-header">
                      <span class="document-index">文档 {{ index + 1 }}</span>
                      <el-button type="danger" size="small" text @click="removeDocument(index)">
                        <el-icon>
                          <Close />
                        </el-icon>
                      </el-button>
                    </div>
                    <el-input v-model="rerankConfig.documents[index]" type="textarea" :rows="2"
                      :placeholder="`请输入第 ${index + 1} 个文档内容`" show-word-limit maxlength="2000" />
                  </div>

                  <el-empty v-if="rerankConfig.documents.length === 0" description="暂无文档，请添加文档进行重排序" :image-size="80" />
                </div>
              </div>
            </el-form-item>

            <!-- 重排序参数配置 -->
            <el-form-item>
              <el-collapse v-model="activeCollapse" class="params-collapse">
                <el-collapse-item title="高级参数" name="params">
                  <el-form-item label="返回数量" prop="topN">
                    <el-input-number v-model="rerankConfig.topN" :min="1" :max="rerankConfig.documents.length || 20"
                      placeholder="返回文档数量" style="width: 100%" />
                    <div class="param-description">
                      指定返回重排序后的前N个文档，默认为全部文档
                    </div>
                  </el-form-item>

                  <el-form-item label="返回文档内容" prop="returnDocuments">
                    <el-switch v-model="rerankConfig.returnDocuments" active-text="是" inactive-text="否" />
                    <div class="param-description">
                      是否在响应中包含原始文档内容
                    </div>
                  </el-form-item>
                </el-collapse-item>
              </el-collapse>
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
import { Position, Plus, Delete, Close, Refresh } from '@element-plus/icons-vue'
import { sendUniversalRequest } from '@/api/universal'
import { getModelsByServiceType, getInstanceServiceType } from '@/api/models'
import type { UniversalApiRequest } from '@/api/universal'
import type {
  RerankRequestConfig,
  GlobalConfig,
  PlaygroundResponse,
  RequestState,
  ValidationRules
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

// 请求状态
const requestState = reactive<RequestState>({
  loading: false,
  error: null,
  response: null
})

// 折叠面板状态
const activeCollapse = ref<string[]>([])
const responseCollapse = ref<string[]>([])

// 模型列表（从实例管理获取）
const availableModels = ref<string[]>([])
const modelsLoading = ref(false)

// 获取可用模型列表
const fetchAvailableModels = async () => {
  modelsLoading.value = true
  try {
    const serviceType = getInstanceServiceType('rerank')
    const models = await getModelsByServiceType(serviceType)
    availableModels.value = models
  } catch (error) {
    console.error('获取模型列表失败:', error)
    ElMessage.error('获取模型列表失败')
  } finally {
    modelsLoading.value = false
  }
}

// 表单验证规则
const formRules: any = {
  model: [
    { required: true, message: '请输入模型名称', trigger: 'blur' }
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
      validator: (rule: any, value: string[], callback: Function) => {
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
  return rerankConfig.value.model.trim() &&
    rerankConfig.value.query.trim() &&
    rerankConfig.value.documents.length > 0 &&
    rerankConfig.value.documents.every(doc => doc.trim()) &&
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

// 发送重排序请求
const sendRerankRequest = async () => {
  try {
    // 表单验证
    const valid = await formRef.value?.validate()
    if (!valid) {
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
        ...(props.globalConfig.authorization && {
          'Authorization': props.globalConfig.authorization
        }),
        ...props.globalConfig.customHeaders
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
watch(() => props.globalConfig, (newConfig) => {
  // 可以在这里处理全局配置变化
}, { deep: true })

// 初始化时添加一个示例文档
if (rerankConfig.value.documents.length === 0) {
  rerankConfig.value.documents.push('')
}

// 监听全局刷新模型事件
const handleRefreshModels = () => {
  fetchAvailableModels()
}

// 组件挂载时获取模型列表
onMounted(() => {
  fetchAvailableModels()

  // 监听全局刷新事件
  document.addEventListener('playground-refresh-models', handleRefreshModels)
})

// 组件卸载时清理事件监听器
onUnmounted(() => {
  document.removeEventListener('playground-refresh-models', handleRefreshModels)
})
</script>

<style scoped>
.rerank-playground {
  padding: 20px;
  height: 100%;
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
.response-card {
  height: 100%;
}

.config-card :deep(.el-card__body),
.response-card :deep(.el-card__body) {
  height: calc(100% - 60px);
  overflow-y: auto;
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
.documents-section {
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 12px;
  background-color: var(--el-bg-color-page);
}

.documents-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.documents-count {
  font-size: 14px;
  color: var(--el-text-color-regular);
  font-weight: 500;
}

.documents-actions {
  display: flex;
  gap: 8px;
}

.documents-list {
  max-height: 400px;
  overflow-y: auto;
}

.document-item {
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background-color: var(--el-bg-color);
}

.document-item:last-child {
  margin-bottom: 0;
}

.document-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.document-index {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

/* 参数配置样式 */
.params-collapse {
  border: none;
}

.params-collapse :deep(.el-collapse-item__header) {
  background-color: var(--el-bg-color-page);
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 0 12px;
}

.params-collapse :deep(.el-collapse-item__content) {
  padding: 12px 0 0 0;
}

.param-description {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
  line-height: 1.4;
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

  .playground-row {
    flex-direction: column;
  }

  .documents-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .documents-actions {
    width: 100%;
    justify-content: flex-end;
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
}

/* 深色主题适配 */
@media (prefers-color-scheme: dark) {
  .documents-section {
    background-color: var(--el-bg-color-overlay);
  }

  .document-item {
    background-color: var(--el-bg-color-overlay);
  }

  .result-item {
    background-color: var(--el-bg-color-overlay);
  }

  .content-text {
    background-color: var(--el-bg-color);
  }

  .json-content {
    background-color: var(--el-bg-color);
  }
}

/* 滚动条样式 */
.documents-list::-webkit-scrollbar,
.results-list::-webkit-scrollbar {
  width: 6px;
}

.documents-list::-webkit-scrollbar-track,
.results-list::-webkit-scrollbar-track {
  background: var(--el-bg-color-page);
  border-radius: 3px;
}

.documents-list::-webkit-scrollbar-thumb,
.results-list::-webkit-scrollbar-thumb {
  background: var(--el-border-color-dark);
  border-radius: 3px;
}

.documents-list::-webkit-scrollbar-thumb:hover,
.results-list::-webkit-scrollbar-thumb:hover {
  background: var(--el-text-color-disabled);
}
</style>