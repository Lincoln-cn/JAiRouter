<template>
  <div class="stt-panel">
    <!-- 模型选择和配置 -->
    <div class="panel-toolbar">
      <el-select
        v-model="selectedModel"
        :placeholder="t('playgroundAudio.stt.modelPlaceholder')"
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
              {{ t('playgroundAudio.offlineStatus') }}
            </span>
            <span
              v-else-if="inst.healthStatus === 'UNKNOWN' || !inst.healthStatus"
              class="health-status-text unknown"
            >
              {{ t('playgroundAudio.unknownStatus') }}
            </span>
          </div>
        </el-option>
      </el-select>
      <el-button
        text
        @click="showConfig = !showConfig"
      >
        <el-icon><Setting /></el-icon>
        {{ showConfig ? t('playgroundAudio.hideConfig') : t('playgroundAudio.showConfig') }}
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
              <label class="config-label">{{ t('playgroundAudio.stt.language') }}</label>
              <el-select
                v-model="config.language"
                size="small"
              >
                <el-option
                  :label="t('playgroundAudio.stt.languageZh')"
                  value="zh"
                />
                <el-option
                  :label="t('playgroundAudio.stt.languageEn')"
                  value="en"
                />
                <el-option
                  :label="t('playgroundAudio.stt.languageAuto')"
                  value=""
                />
              </el-select>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="config-item">
              <label class="config-label">{{ t('playgroundAudio.stt.responseFormat') }}</label>
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
          <span class="card-title">{{ t('playgroundAudio.stt.uploadTitle') }}</span>
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
            {{ t('playgroundAudio.stt.dragText') }}
            <em>{{ t('playgroundAudio.stt.clickUpload') }}</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              {{ t('playgroundAudio.stt.uploadTip') }}
            </div>
          </template>
        </el-upload>
      </div>

      <!-- 提示词输入 -->
      <div class="input-card">
        <div class="card-header">
          <span class="card-title">{{ t('playgroundAudio.stt.promptTitle') }}</span>
        </div>
        <el-input
          v-model="promptText"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 4 }"
          :placeholder="t('playgroundAudio.stt.promptPlaceholder')"
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
          {{ t('playgroundAudio.stt.start') }}
        </el-button>
      </div>

      <!-- 结果展示 -->
      <div class="result-card">
        <div class="card-header">
          <span class="card-title">{{ t('playgroundAudio.stt.resultTitle') }}</span>
        </div>

        <!-- 空状态 -->
        <div
          v-if="!result && !isLoading"
          class="empty-state"
        >
          <el-icon
            :size="48"
            color="var(--ja-text-secondary)"
          >
            <Document />
          </el-icon>
          <span>{{ t('playgroundAudio.stt.empty') }}</span>
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
          <span>{{ t('playgroundAudio.stt.loading') }}</span>
        </div>

        <!-- 结果文本 -->
        <div
          v-if="result && !isLoading"
          class="result-content"
        >
          <div class="result-stats">
            <span>{{ t('playgroundAudio.stt.duration', { duration: result.duration }) }}</span>
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
              {{ t('playgroundAudio.stt.copy') }}
            </el-button>
            <el-button
              text
              size="small"
              @click="handleClear"
            >
              <el-icon><Delete /></el-icon>
              {{ t('playgroundAudio.clear') }}
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Setting, Headset, UploadFilled, Document, DocumentCopy, Delete, Loading, Cpu } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { sendServiceRequest } from '@/api/playground'
import type { SttRequestConfig, PlaygroundResponse } from '../../types/playground'
import { parseErrorMessage, getErrorSuggestion } from '../../utils/errorHandler'

const { t } = useI18n()

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
    ElMessage.success(t('playgroundAudio.stt.messages.transcribed'))
  } catch (error: any) {
    const errorMsg = parseErrorMessage(error, t('playgroundAudio.stt.messages.operation'))
    const suggestion = getErrorSuggestion(error)

    if (suggestion) {
      ElMessage({
        type: 'error',
        message: `${errorMsg}\n\n${t('playgroundAudio.messages.suggestion', { suggestion })}`,
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

// 复制结果
const handleCopy = async () => {
  try {
    await navigator.clipboard.writeText(resultText.value)
    ElMessage.success(t('playgroundAudio.stt.messages.copied'))
  } catch {
    ElMessage.error(t('playgroundAudio.stt.messages.copyFailed'))
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
  padding: var(--ja-space-3) var(--ja-space-4);
  background-color: var(--ja-bg-page);
  border-bottom: 1px solid var(--el-border-color);
}

.model-select {
  width: 240px;
}

.config-panel {
  padding: var(--ja-space-3) var(--ja-space-4);
  background-color: var(--ja-bg-page);
  border-bottom: 1px solid var(--el-border-color);
}

.config-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.config-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--ja-text-regular);
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
  padding: var(--ja-space-4);
}

.upload-card,
.input-card,
.result-card {
  background-color: var(--ja-bg-page);
  border-radius: 4px;
  padding: 16px;
  margin-bottom: 16px;
}

.card-header {
  margin-bottom: var(--ja-space-3);
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ja-text-primary);
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
  color: var(--ja-text-secondary);
}

.result-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.result-stats {
  font-size: 12px;
  color: var(--ja-text-secondary);
}

.result-text {
  padding: 12px;
  background-color: var(--ja-main-bg);
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