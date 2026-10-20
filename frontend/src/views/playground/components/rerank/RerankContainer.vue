<template>
  <div class="rerank-container">
    <!-- 顶部工具栏 -->
    <div class="service-toolbar">
      <div class="toolbar-left">
        <el-select
          v-model="selectedModel"
          :placeholder="t('playgroundRerank.modelPlaceholder')"
          :loading="instancesLoading"
          filterable
          class="model-select"
        >
          <template #prefix>
            <el-icon><Cpu /></el-icon>
          </template>
          <el-option
            v-for="inst in availableInstances"
            :key="inst.instanceId"
            :label="inst.name"
            :value="inst.name"
            :disabled="inst.healthStatus === 'UNHEALTHY'"
          >
            <div class="instance-option">
              <span
                class="health-indicator"
                :class="{
                  'health-healthy': inst.healthStatus === 'HEALTHY',
                  'health-unhealthy': inst.healthStatus === 'UNHEALTHY',
                  'health-unknown': inst.healthStatus === 'UNKNOWN' || !inst.healthStatus
                }"
              >
                ●
              </span>
              <span class="instance-name">{{ inst.name }}</span>
              <span
                v-if="inst.healthStatus === 'UNHEALTHY'"
                class="health-status-text"
              >
                {{ t('playgroundRerank.offlineStatus') }}
              </span>
              <span
                v-else-if="inst.healthStatus === 'UNKNOWN' || !inst.healthStatus"
                class="health-status-text unknown"
              >
                {{ t('playgroundRerank.unknownStatus') }}
              </span>
            </div>
          </el-option>
        </el-select>
        <el-button
          text
          @click="showConfig = !showConfig"
        >
          <el-icon><Setting /></el-icon>
          {{ showConfig ? t('playgroundRerank.hideConfig') : t('playgroundRerank.showConfig') }}
        </el-button>
      </div>
      <div class="toolbar-right">
        <el-button
          text
          @click="handleClear"
        >
          <el-icon><Delete /></el-icon>
          {{ t('playgroundRerank.clear') }}
        </el-button>
      </div>
    </div>

    <!-- 配置面板 -->
    <Transition name="slide-down">
      <div
        v-if="showConfig"
        class="config-panel"
      >
        <el-row :gutter="16">
          <el-col :span="6">
            <div class="config-item">
              <label class="config-label">{{ t('playgroundRerank.config.topN') }}</label>
              <el-input-number
                v-model="config.topN"
                :min="1"
                :max="100"
                size="small"
                controls-position="right"
              />
            </div>
          </el-col>
          <el-col :span="6">
            <div class="config-item">
              <label class="config-label">{{ t('playgroundRerank.config.returnDocuments') }}</label>
              <el-switch v-model="config.returnDocuments" />
            </div>
          </el-col>
        </el-row>
      </div>
    </Transition>

    <!-- 主内容区 -->
    <div class="service-content">
      <!-- 查询输入 -->
      <div class="input-card">
        <div class="card-header">
          <span class="card-title">{{ t('playgroundRerank.query.title') }}</span>
        </div>
        <el-input
          v-model="queryText"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          :placeholder="t('playgroundRerank.query.placeholder')"
          resize="none"
        />
      </div>

      <!-- 文档列表 -->
      <div class="input-card">
        <div class="card-header">
          <span class="card-title">{{ t('playgroundRerank.documents.title') }}</span>
          <el-button
            text
            size="small"
            @click="addDocument"
          >
            <el-icon><Plus /></el-icon>
            {{ t('playgroundRerank.documents.add') }}
          </el-button>
        </div>
        <div class="document-list">
          <div
            v-for="(doc, index) in documents"
            :key="index"
            class="document-item"
          >
            <div class="document-header">
              <span class="document-index">{{ index + 1 }}</span>
              <el-button
                v-if="documents.length > 1"
                type="danger"
                text
                size="small"
                @click="removeDocument(index)"
              >
                <el-icon><Close /></el-icon>
              </el-button>
            </div>
            <el-input
              v-model="documents[index]"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 6 }"
              :placeholder="t('playgroundRerank.documents.contentPlaceholder', { index: index + 1 })"
              resize="none"
            />
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-bar">
        <el-button
          type="primary"
          :loading="isLoading"
          :disabled="!selectedModel || !queryText.trim() || !hasValidDocuments"
          @click="handleRerank"
        >
          <el-icon><Sort /></el-icon>
          {{ t('playgroundRerank.run') }}
        </el-button>
      </div>

      <!-- 结果展示区 -->
      <div class="result-card">
        <div class="card-header">
          <span class="card-title">{{ t('playgroundRerank.result.title') }}</span>
        </div>

        <!-- 空状态 -->
        <div
          v-if="!result && !isLoading"
          class="empty-state"
        >
          <el-icon
            :size="48"
            color="var(--ja-ink-3)"
          >
            <Sort />
          </el-icon>
          <span class="empty-text">{{ t('playgroundRerank.result.empty') }}</span>
        </div>

        <!-- 加载状态 -->
        <div
          v-if="isLoading"
          class="loading-state"
        >
          <el-icon
            class="is-loading"
            :size="32"
          >
            <Loading />
          </el-icon>
          <span>{{ t('playgroundRerank.result.loading') }}</span>
        </div>

        <!-- 结果列表 -->
        <div
          v-if="result && !isLoading"
          class="result-list"
        >
          <div class="result-stats">
            <span class="stat-item">{{ t('playgroundRerank.result.duration', { duration: result.duration }) }}</span>
            <span class="stat-item">{{ t('playgroundRerank.result.returned', { count: rerankResults.length }) }}</span>
          </div>

          <div
            v-for="(item, idx) in rerankResults"
            :key="idx"
            class="result-item"
          >
            <div class="result-header">
              <div class="result-rank">
                <span class="rank-badge">#{{ (item.index ?? idx) + 1 }}</span>
                <span class="score-badge">{{ t('playgroundRerank.result.score', { score: (item.relevance_score || 0).toFixed(4) }) }}</span>
              </div>
            </div>
            <div
              v-if="item.document"
              class="result-document"
            >
              {{ item.document?.text || item.document }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Setting,
  Delete,
  Plus,
  Close,
  Sort,
  Loading,
  Cpu
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { usePlaygroundData } from '@/composables/usePlaygroundData'
import { useRoutePreselect, preselectInstanceName } from '@/composables/useRoutePreselect'
import { sendServiceRequest } from '@/api/playground'
import type { RerankRequestConfig, PlaygroundResponse } from '../../types/playground'
import { parseErrorMessage, getErrorSuggestion } from '../../utils/errorHandler'

const { t } = useI18n()

// 状态
const showConfig = ref(false)
const selectedModel = ref('')
const isLoading = ref(false)
const result = ref<PlaygroundResponse | null>(null)

interface RerankResultRow {
  index?: number
  relevance_score?: number
  document?: { text?: string }
  [key: string]: unknown
}

/** 从响应里取出 rerank 结果行（响应体是 unknown，按实际读取的字段收窄） */
const rerankResults = computed<RerankResultRow[]>(() => {
  const rows = (result.value?.data as { data?: { results?: RerankResultRow[] } } | null)?.data?.results
  return Array.isArray(rows) ? rows : []
})

// 输入
const queryText = ref('')
const documents = ref<string[]>([''])

// 配置
const config = ref<Partial<RerankRequestConfig>>({
  topN: 10,
  returnDocuments: true
})

// 数据获取
const { availableInstances, instancesLoading, initializeData } =
  usePlaygroundData('rerank')

// 是否有有效文档
const hasValidDocuments = computed(() => {
  return documents.value.some((doc) => doc.trim().length > 0)
})

// 初始化
onMounted(() => {
  initializeData()
})

// Route preselect: when serviceType=rerank in URL, auto-select a healthy instance
const { requestedServiceType } = useRoutePreselect()

watch(availableInstances, (instances) => {
  if (
    requestedServiceType() === 'rerank' &&
    instances.length > 0 &&
    !selectedModel.value
  ) {
    const name = preselectInstanceName(instances)
    if (name) selectedModel.value = name
  }
}, { immediate: true })

// 添加文档
const addDocument = () => {
  documents.value.push('')
}

// 删除文档
const removeDocument = (index: number) => {
  documents.value.splice(index, 1)
}

// 清空
const handleClear = () => {
  queryText.value = ''
  documents.value = ['']
  result.value = null
}

// 执行重排序
const handleRerank = async () => {
  if (!selectedModel.value || !queryText.value.trim() || !hasValidDocuments.value)
    return

  isLoading.value = true
  result.value = null

  try {
    const validDocs = documents.value.filter((doc) => doc.trim().length > 0)
    const requestConfig: RerankRequestConfig = {
      model: selectedModel.value,
      query: queryText.value,
      documents: validDocs,
      topN: config.value.topN || validDocs.length,
      returnDocuments: config.value.returnDocuments
    }

    const token = localStorage.getItem('admin_token')
    const headers: Record<string, string> = {}
    if (token) headers['Jairouter_Token'] = token

    const response = await sendServiceRequest('rerank', requestConfig, headers)
    result.value = response
    ElMessage.success(t('playgroundRerank.messages.rerankCompleted'))
  } catch (error) {
    const errorMsg = parseErrorMessage(error, t('playgroundRerank.messages.operation'))
    const suggestion = getErrorSuggestion(error)

    if (suggestion) {
      ElMessage({
        type: 'error',
        message: `${errorMsg}\n\n${t('playgroundRerank.messages.suggestion', { suggestion })}`,
        duration: 6000,
        showClose: true
      })
    } else {
      ElMessage.error(errorMsg)
    }
  } finally {
    isLoading.value = false
  }
}

// 暴露方法
defineExpose({
  handleClear
})
</script>

<style scoped>
.rerank-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  background-color: var(--ja-surface);
}

.service-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--ja-space-3) var(--ja-space-4);
  background-color: var(--ja-paper);
  border-bottom: 1px solid var(--ja-border);
  border-radius: var(--ja-radius-sm) var(--ja-radius-sm) 0 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: var(--ja-space-3);
}

.model-select {
  width: 240px;
}

.toolbar-right {
  display: flex;
  gap: var(--ja-space-1);
}

.config-panel {
  padding: var(--ja-space-3) var(--ja-space-4);
  background-color: var(--ja-paper);
  border-bottom: 1px solid var(--ja-border);
}

.config-item {
  display: flex;
  flex-direction: column;
  gap: var(--ja-space-2);
}

.config-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--ja-ink-2);
}

.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
}

.slide-down-enter-from,
.slide-down-leave-to {
  transform: translateY(-10px);
  opacity: 0;
}

.service-content {
  flex: 1;
  overflow-y: auto;
  padding: var(--ja-space-4);
}

.input-card,
.result-card {
  background-color: var(--ja-paper);
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius-sm);
  padding: var(--ja-space-4);
  margin-bottom: var(--ja-space-4);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ja-space-3);
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ja-ink);
}

.document-list {
  display: flex;
  flex-direction: column;
  gap: var(--ja-space-3);
}

.document-item {
  padding: var(--ja-space-3);
  background-color: var(--ja-surface-2);
  border-radius: var(--ja-radius-sm);
}

.document-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ja-space-2);
}

.document-index {
  font-size: 12px;
  font-weight: 600;
  color: var(--ja-ink-2);
  background-color: var(--ja-surface-2);
  padding: 2px 8px;
  border-radius: var(--ja-radius-sm);
}

.action-bar {
  display: flex;
  justify-content: center;
  gap: var(--ja-space-3);
  padding: 12px 0;
}

.result-card {
  min-height: 200px;
}

.empty-state,
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--ja-space-8) 0;
  gap: var(--ja-space-3);
  color: var(--ja-ink-3);
}

.empty-text {
  font-size: 14px;
}

.result-list {
  padding: 12px 0;
}

.result-stats {
  display: flex;
  gap: var(--ja-space-4);
  padding: var(--ja-space-2) var(--ja-space-3);
  background-color: var(--ja-surface-2);
  border-radius: var(--ja-radius-sm);
  margin-bottom: var(--ja-space-4);
  font-size: 12px;
  color: var(--ja-ink-2);
}

.result-item {
  padding: var(--ja-space-3);
  background-color: var(--ja-surface-2);
  border-radius: var(--ja-radius-sm);
  margin-bottom: var(--ja-space-2);
}

.result-header {
  margin-bottom: var(--ja-space-2);
}

.result-rank {
  display: flex;
  gap: var(--ja-space-2);
}

.rank-badge {
  font-size: 14px;
  font-weight: 700;
  color: var(--ja-primary);
}

.score-badge {
  font-size: 12px;
  color: var(--ja-ink-2);
  background-color: var(--ja-surface-2);
  padding: 2px 8px;
  border-radius: var(--ja-radius-sm);
}

.result-document {
  font-size: 13px;
  color: var(--ja-ink-2);
  line-height: 1.6;
  word-break: break-word;
}

/* 健康状态指示器样式 */
.instance-option {
  display: flex;
  align-items: center;
  gap: var(--ja-space-2);
}

.health-indicator {
  font-size: 12px;
}

.health-healthy {
  color: var(--ja-success);
}

.health-unhealthy {
  color: var(--ja-danger);
}

.health-unknown {
  color: var(--ja-ink-3);
}

.instance-name {
  flex: 1;
}

.health-status-text {
  font-size: 12px;
  color: var(--ja-danger);
}

.health-status-text.unknown {
  color: var(--ja-ink-3);
}
</style>