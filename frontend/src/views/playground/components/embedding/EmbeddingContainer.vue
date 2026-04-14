<template>
  <div class="embedding-container">
    <!-- 顶部工具栏 -->
    <div class="service-toolbar">
      <div class="toolbar-left">
        <el-select
          v-model="selectedModel"
          :placeholder="'选择 Embedding 模型'"
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
              <label class="config-label">编码格式</label>
              <el-select
                v-model="config.encodingFormat"
                size="small"
              >
                <el-option
                  label="float"
                  value="float"
                />
                <el-option
                  label="base64"
                  value="base64"
                />
              </el-select>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="config-item">
              <label class="config-label">向量维度</label>
              <el-input-number
                v-model="config.dimensions"
                :min="0"
                :max="4096"
                size="small"
                controls-position="right"
              />
            </div>
          </el-col>
        </el-row>
      </div>
    </Transition>

    <!-- 主内容区 -->
    <div class="service-content">
      <!-- 输入区 -->
      <div class="input-card">
        <div class="card-header">
          <span class="card-title">输入文本</span>
          <el-button
            text
            size="small"
            @click="addInput"
          >
            <el-icon><Plus /></el-icon>
            添加输入
          </el-button>
        </div>
        <div class="input-list">
          <div
            v-for="(text, index) in inputTexts"
            :key="index"
            class="input-item"
          >
            <el-input
              v-model="inputTexts[index]"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 4 }"
              :placeholder="`输入文本 ${index + 1}`"
              resize="none"
            />
            <el-button
              v-if="inputTexts.length > 1"
              type="danger"
              text
              size="small"
              class="remove-btn"
              @click="removeInput(index)"
            >
              <el-icon><Close /></el-icon>
            </el-button>
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-bar">
        <el-button
          type="primary"
          :loading="isLoading"
          :disabled="!selectedModel || !hasValidInput"
          @click="handleGenerate"
        >
          <el-icon><Promotion /></el-icon>
          生成向量
        </el-button>
      </div>

      <!-- 结果展示区 -->
      <div class="result-card">
        <div class="card-header">
          <span class="card-title">向量结果</span>
          <el-button
            v-if="result"
            text
            size="small"
            @click="handleCopyResult"
          >
            <el-icon><DocumentCopy /></el-icon>
            复制
          </el-button>
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
            <DataLine />
          </el-icon>
          <span class="empty-text">输入文本后生成向量</span>
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
          <span>正在生成向量...</span>
        </div>

        <!-- 结果显示 -->
        <div
          v-if="result && !isLoading"
          class="result-content"
        >
          <!-- 统计信息 -->
          <div class="result-stats">
            <div class="stat-item">
              <span class="stat-label">向量数量</span>
              <span class="stat-value">{{ result.data?.length || 0 }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">向量维度</span>
              <span class="stat-value">{{ vectorDimension }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-label">耗时</span>
              <span class="stat-value">{{ result.duration }}ms</span>
            </div>
          </div>

          <!-- 向量列表 -->
          <div class="vector-list">
            <el-collapse accordion>
              <el-collapse-item
                v-for="(embedding, idx) in result.data"
                :key="idx"
                :title="`向量 ${idx + 1}`"
              >
                <div class="vector-preview">
                  <div class="vector-text">
                    {{ formatVectorPreview(embedding.embedding) }}
                  </div>
                  <el-button
                    text
                    size="small"
                    @click="copyVector(embedding.embedding)"
                  >
                    复制完整向量
                  </el-button>
                </div>
              </el-collapse-item>
            </el-collapse>
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
  Promotion,
  DocumentCopy,
  DataLine,
  Loading,
  Cpu
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { usePlaygroundData } from '@/composables/usePlaygroundData'
import { sendServiceRequest } from '@/api/playground'
import type { EmbeddingRequestConfig, PlaygroundResponse } from '../../types/playground'

// 状态
const showConfig = ref(false)
const selectedModel = ref('')
const isLoading = ref(false)
const result = ref<PlaygroundResponse | null>(null)

// 输入文本
const inputTexts = ref<string[]>([''])

// 配置
const config = ref<Partial<EmbeddingRequestConfig>>({
  encodingFormat: 'float',
  dimensions: 0
})

// 数据获取
const { availableInstances, instancesLoading, initializeData } =
  usePlaygroundData('embedding')

// 是否有有效输入
const hasValidInput = computed(() => {
  return inputTexts.value.some((text) => text.trim().length > 0)
})

// 向量维度
const vectorDimension = computed(() => {
  if (result.value?.data?.[0]?.embedding) {
    return result.value.data[0].embedding.length
  }
  return config.value.dimensions || '未知'
})

// 初始化
onMounted(() => {
  initializeData()
})

// 添加输入
const addInput = () => {
  inputTexts.value.push('')
}

// 删除输入
const removeInput = (index: number) => {
  inputTexts.value.splice(index, 1)
}

// 清空
const handleClear = () => {
  inputTexts.value = ['']
  result.value = null
}

// 生成向量
const handleGenerate = async () => {
  if (!selectedModel.value || !hasValidInput.value) return

  isLoading.value = true
  result.value = null

  try {
    const validInputs = inputTexts.value.filter((text) => text.trim().length > 0)
    const requestConfig: EmbeddingRequestConfig = {
      model: selectedModel.value,
      input: validInputs.length === 1 ? validInputs[0] : validInputs,
      encodingFormat: config.value.encodingFormat,
      dimensions: config.value.dimensions || undefined
    }

    const token = localStorage.getItem('admin_token')
    const headers: Record<string, string> = {}
    if (token) headers['Jairouter_Token'] = token

    const response = await sendServiceRequest('embedding', requestConfig, headers)
    result.value = response
    ElMessage.success(`成功生成 ${response.data?.data?.length || 0} 个向量`)
  } catch (error: any) {
    const errorMsg = error.data?.error?.message || '生成向量失败'
    ElMessage.error(errorMsg)
  } finally {
    isLoading.value = false
  }
}

// 格式化向量预览
const formatVectorPreview = (embedding: number[]) => {
  if (!embedding || embedding.length === 0) return '[]'
  const preview = embedding.slice(0, 10).map((v) => v.toFixed(4))
  return `[${preview.join(', ')}, ...共 ${embedding.length} 维]`
}

// 复制向量
const copyVector = async (embedding: number[]) => {
  try {
    await navigator.clipboard.writeText(JSON.stringify(embedding))
    ElMessage.success('向量已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

// 复制完整结果
const handleCopyResult = async () => {
  if (!result.value) return
  try {
    await navigator.clipboard.writeText(JSON.stringify(result.value.data, null, 2))
    ElMessage.success('结果已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

// 暴露方法
defineExpose({
  handleClear
})
</script>

<style scoped>
.embedding-container {
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

.input-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.input-item {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.input-item :deep(.el-input) {
  flex: 1;
}

.remove-btn {
  margin-top: 4px;
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

.result-content {
  padding: 12px 0;
}

.result-stats {
  display: flex;
  gap: 24px;
  padding: 12px;
  background-color: #f5f5f5;
  border-radius: 4px;
  margin-bottom: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 12px;
  color: #909399;
}

.stat-value {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.vector-preview {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.vector-text {
  font-family: monospace;
  font-size: 12px;
  color: #606266;
  word-break: break-all;
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