<template>
  <div class="stt-panel">
    <!-- 模型选择和配置 -->
    <div class="panel-toolbar">
      <el-select
        v-model="selectedModel"
        :placeholder="'选择 STT 模型'"
        :loading="loading"
        filterable
        class="model-select"
      >
        <template #prefix>
          <el-icon><Cpu /></el-icon>
        </template>
        <el-option
          v-for="inst in instances"
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

    <!-- 配置面板 -->
    <Transition name="slide-down">
      <div
        v-if="showConfig"
        class="config-panel"
      >
        <el-row :gutter="16">
          <el-col :span="6">
            <div class="config-item">
              <label class="config-label">语言</label>
              <el-select
                v-model="config.language"
                size="small"
              >
                <el-option
                  label="中文"
                  value="zh"
                />
                <el-option
                  label="英文"
                  value="en"
                />
                <el-option
                  label="自动检测"
                  value=""
                />
              </el-select>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="config-item">
              <label class="config-label">响应格式</label>
              <el-select
                v-model="config.responseFormat"
                size="small"
              >
                <el-option
                  label="json"
                  value="json"
                />
                <el-option
                  label="text"
                  value="text"
                />
                <el-option
                  label="srt"
                  value="srt"
                />
                <el-option
                  label="verbose_json"
                  value="verbose_json"
                />
              </el-select>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="config-item">
              <label class="config-label">Temperature</label>
              <el-slider
                v-model="config.temperature"
                :min="0"
                :max="1"
                :step="0.1"
                show-input
                size="small"
              />
            </div>
          </el-col>
        </el-row>
      </div>
    </Transition>

    <!-- 主内容 -->
    <div class="panel-content">
      <!-- 文件上传 -->
      <div class="upload-card">
        <div class="card-header">
          <span class="card-title">上传音频文件</span>
        </div>
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
          accept=".mp3,.mp4,.mpeg,.mpga,.m4a,.wav,.webm"
          drag
          class="upload-area"
        >
          <el-icon
            class="el-icon--upload"
            :size="48"
          >
            <UploadFilled />
          </el-icon>
          <div class="el-upload__text">
            拖拽音频文件到此处，或 <em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              支持 mp3, mp4, wav, webm 等格式，最大 25MB
            </div>
          </template>
        </el-upload>
      </div>

      <!-- 提示词输入 -->
      <div class="input-card">
        <div class="card-header">
          <span class="card-title">提示词 (可选)</span>
        </div>
        <el-input
          v-model="promptText"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          placeholder="输入可选的提示词，帮助提高识别准确度..."
          resize="none"
        />
      </div>

      <!-- 操作按钮 -->
      <div class="action-bar">
        <el-button
          type="primary"
          :loading="isLoading"
          :disabled="!selectedModel || !audioFile"
          @click="handleTranscribe"
        >
          <el-icon><Headset /></el-icon>
          开始识别
        </el-button>
      </div>

      <!-- 结果展示 -->
      <div class="result-card">
        <div class="card-header">
          <span class="card-title">识别结果</span>
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
            <Document />
          </el-icon>
          <span>上传音频文件后开始识别</span>
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
          <span>正在识别...</span>
        </div>

        <!-- 结果文本 -->
        <div
          v-if="result && !isLoading"
          class="result-content"
        >
          <div class="result-stats">
            <span>耗时: {{ result.duration }}ms</span>
          </div>
          <div class="result-text">
            {{ resultText }}
          </div>
          <div class="result-actions">
            <el-button
              text
              size="small"
              @click="handleCopy"
            >
              <el-icon><DocumentCopy /></el-icon>
              复制文本
            </el-button>
            <el-button
              text
              size="small"
              @click="handleClear"
            >
              <el-icon><Delete /></el-icon>
              清空
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Setting, Headset, UploadFilled, Document, DocumentCopy, Delete, Loading, Cpu } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { sendServiceRequest } from '@/api/playground'
import type { SttRequestConfig, PlaygroundResponse } from '../../types/playground'

interface Props {
  instances: any[]
  loading: boolean
}

const props = defineProps<Props>()

// 状态
const showConfig = ref(false)
const selectedModel = ref('')
const isLoading = ref(false)
const audioFile = ref<File | null>(null)
const promptText = ref('')
const result = ref<PlaygroundResponse | null>(null)

// 配置
const config = ref<Partial<Omit<SttRequestConfig, 'file'>>>({
  language: 'zh',
  responseFormat: 'json',
  temperature: 0
})

// 结果文本
const resultText = computed(() => {
  if (!result.value?.data) return ''
  if (typeof result.value.data === 'string') return result.value.data
  return result.value.data.text || JSON.stringify(result.value.data, null, 2)
})

// 文件变化
const handleFileChange = (file: any) => {
  audioFile.value = file.raw
}

// 文件移除
const handleFileRemove = () => {
  audioFile.value = null
}

// 开始识别
const handleTranscribe = async () => {
  if (!selectedModel.value || !audioFile.value) return

  isLoading.value = true
  result.value = null

  try {
    const requestConfig: SttRequestConfig = {
      model: selectedModel.value,
      file: audioFile.value,
      language: config.value.language,
      prompt: promptText.value,
      responseFormat: config.value.responseFormat,
      temperature: config.value.temperature
    }

    const token = localStorage.getItem('admin_token')
    const headers: Record<string, string> = {}
    if (token) headers['Jairouter_Token'] = token

    const response = await sendServiceRequest('stt', requestConfig, headers)
    result.value = response
    ElMessage.success('识别完成')
  } catch (error: any) {
    const errorMsg = error.data?.error?.message || '识别失败'
    ElMessage.error(errorMsg)
  } finally {
    isLoading.value = false
  }
}

// 复制结果
const handleCopy = async () => {
  try {
    await navigator.clipboard.writeText(resultText.value)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

// 清空
const handleClear = () => {
  audioFile.value = null
  promptText.value = ''
  result.value = null
}

// 暴露方法
defineExpose({
  refreshData: () => {},
  handleClear
})
</script>

<style scoped>
.stt-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow: hidden;
}

.panel-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.model-select {
  width: 240px;
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

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.upload-card,
.input-card,
.result-card {
  background-color: #fff;
  border-radius: 4px;
  padding: 16px;
  margin-bottom: 16px;
}

.card-header {
  margin-bottom: 12px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.upload-area :deep(.el-upload-dragger) {
  width: 100%;
  height: 150px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.action-bar {
  display: flex;
  justify-content: center;
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

.result-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-stats {
  font-size: 12px;
  color: #909399;
}

.result-text {
  padding: 12px;
  background-color: #f5f5f5;
  border-radius: 4px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.result-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}
</style>