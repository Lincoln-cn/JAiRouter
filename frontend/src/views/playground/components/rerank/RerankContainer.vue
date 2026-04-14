<template>
  <div class="rerank-container">
    <!-- 顶部工具栏 -->
    <div class="service-toolbar">
      <div class="toolbar-left">
        <el-select
          v-model="selectedModel"
          :placeholder="'选择 Rerank 模型'"
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
                (离线)
              </span>
              <span
                v-else-if="inst.healthStatus === 'UNKNOWN' || !inst.healthStatus"
                class="health-status-text unknown"
              >
                (未知)
              </span>
            </div>
          </el-option>
        </el-select>
        <el-button
          text
          @click="showConfig = !showConfig"
        >
          <el-icon><Setting /></el-icon>
          {{ showConfig ? '隐藏配置' : '参数配置' }}
        </el-button>
      </div>
      <div class="toolbar-right">
        <el-button
          text
          @click="handleClear"
        >
          <el-icon><Delete /></el-icon>
          清空
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
              <label class="config-label">返回数量 (Top N)</label>
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
              <label class="config-label">返回文档内容</label>
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
          <span class="card-title">查询文本</span>
        </div>
        <el-input
          v-model="queryText"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          placeholder="输入查询文本，例如：什么是机器学习？"
          resize="none"
        />
      </div>

      <!-- 文档列表 -->
      <div class="input-card">
        <div class="card-header">
          <span class="card-title">文档列表</span>
          <el-button
            text
            size="small"
            @click="addDocument"
          >
            <el-icon><Plus /></el-icon>
            添加文档
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
              :placeholder="`文档 ${index + 1} 内容`"
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
          执行重排序
        </el-button>
      </div>

      <!-- 结果展示区 -->
      <div class="result-card">
        <div class="card-header">
          <span class="card-title">重排序结果</span>
        </div>

        <!-- 空状态 -->
        <div
          v-if="!result && !isLoading"
          class="empty-state"
        >
          <el-icon
            :size="48"
            color="#909399"
          >
            <Sort />
          </el-icon>
          <span class="empty-text">输入查询和文档后执行重排序</span>
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
          <span>正在重排序...</span>
        </div>

        <!-- 结果列表 -->
        <div
          v-if="result && !isLoading"
          class="result-list"
        >
          <div class="result-stats">
            <span class="stat-item">耗时: {{ result.duration }}ms</span>
            <span class="stat-item">返回: {{ result.data?.results?.length || 0 }} 条</span>
          </div>

          <div
            v-for="(item, idx) in result.data?.results"
            :key="idx"
            class="result-item"
          >
            <div class="result-header">
              <div class="result-rank">
                <span class="rank-badge">#{{ item.index + 1 }}</span>
                <span class="score-badge">分数: {{ (item.relevance_score || 0).toFixed(4) }}</span>
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
import { ref, computed, onMounted } from 'vue'
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
import { sendServiceRequest } from '@/api/playground'
import type { RerankRequestConfig, PlaygroundResponse } from '../../types/playground'

// 状态
const showConfig = ref(false)
const selectedModel = ref('')
const isLoading = ref(false)
const result = ref<PlaygroundResponse | null>(null)

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
    ElMessage.success('重排序完成')
  } catch (error: any) {
    const errorMsg = error.data?.error?.message || '重排序失败'
    ElMessage.error(errorMsg)
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
  background-color: #f5f5f5;
}

.service-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  border-radius: 4px 4px 0 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.model-select {
  width: 240px;
}

.toolbar-right {
  display: flex;
  gap: 4px;
}

.config-panel {
  padding: 12px 16px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.config-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.config-label {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
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
  padding: 16px;
}

.input-card,
.result-card {
  background-color: #fff;
  border-radius: 4px;
  padding: 16px;
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.document-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.document-item {
  padding: 12px;
  background-color: #f5f5f5;
  border-radius: 4px;
}

.document-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.document-index {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  background-color: #e5e5e5;
  padding: 2px 8px;
  border-radius: 4px;
}

.action-bar {
  display: flex;
  justify-content: center;
  gap: 12px;
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
  padding: 40px 0;
  gap: 12px;
  color: #909399;
}

.empty-text {
  font-size: 14px;
}

.result-list {
  padding: 12px 0;
}

.result-stats {
  display: flex;
  gap: 16px;
  padding: 8px 12px;
  background-color: #f5f5f5;
  border-radius: 4px;
  margin-bottom: 16px;
  font-size: 12px;
  color: #606266;
}

.result-item {
  padding: 12px;
  background-color: #f5f5f5;
  border-radius: 4px;
  margin-bottom: 8px;
}

.result-header {
  margin-bottom: 8px;
}

.result-rank {
  display: flex;
  gap: 8px;
}

.rank-badge {
  font-size: 14px;
  font-weight: 700;
  color: #409eff;
}

.score-badge {
  font-size: 12px;
  color: #67c23a;
  background-color: #f0f9eb;
  padding: 2px 8px;
  border-radius: 4px;
}

.result-document {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  word-break: break-word;
}

/* 健康状态指示器样式 */
.instance-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.health-indicator {
  font-size: 12px;
}

.health-healthy {
  color: #67c23a;
}

.health-unhealthy {
  color: #f56c6c;
}

.health-unknown {
  color: #909399;
}

.instance-name {
  flex: 1;
}

.health-status-text {
  font-size: 12px;
  color: #f56c6c;
}

.health-status-text.unknown {
  color: #909399;
}
</style>