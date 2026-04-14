<template>
  <div class="tts-panel">
    <!-- 模型选择和配置 -->
    <div class="panel-toolbar">
      <el-select
        v-model="selectedModel"
        :placeholder="'选择 TTS 模型'"
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
              <label class="config-label">语音类型</label>
              <el-select
                v-model="config.voice"
                size="small"
              >
                <el-option
                  label="alloy"
                  value="alloy"
                />
                <el-option
                  label="echo"
                  value="echo"
                />
                <el-option
                  label="fable"
                  value="fable"
                />
                <el-option
                  label="onyx"
                  value="onyx"
                />
                <el-option
                  label="nova"
                  value="nova"
                />
                <el-option
                  label="shimmer"
                  value="shimmer"
                />
              </el-select>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="config-item">
              <label class="config-label">输出格式</label>
              <el-select
                v-model="config.responseFormat"
                size="small"
              >
                <el-option
                  label="mp3"
                  value="mp3"
                />
                <el-option
                  label="opus"
                  value="opus"
                />
                <el-option
                  label="aac"
                  value="aac"
                />
                <el-option
                  label="flac"
                  value="flac"
                />
              </el-select>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="config-item">
              <label class="config-label">语速</label>
              <el-slider
                v-model="config.speed"
                :min="0.25"
                :max="4"
                :step="0.25"
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
      <!-- 文本输入 -->
      <div class="input-card">
        <div class="card-header">
          <span class="card-title">输入文本</span>
        </div>
        <el-input
          v-model="inputText"
          type="textarea"
          :autosize="{ minRows: 4, maxRows: 8 }"
          placeholder="输入要转换为语音的文本..."
          resize="none"
        />
        <div class="input-info">
          <span>{{ inputText.length }} 字符</span>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-bar">
        <el-button
          type="primary"
          :loading="isLoading"
          :disabled="!selectedModel || !inputText.trim()"
          @click="handleGenerate"
        >
          <el-icon><Microphone /></el-icon>
          生成语音
        </el-button>
      </div>

      <!-- 结果展示 -->
      <div class="result-card">
        <div class="card-header">
          <span class="card-title">语音结果</span>
        </div>

        <!-- 空状态 -->
        <div
          v-if="!audioUrl && !isLoading"
          class="empty-state"
        >
          <el-icon
            :size="48"
            color="#909399"
          >
            <Microphone />
          </el-icon>
          <span>输入文本后生成语音</span>
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
          <span>正在生成语音...</span>
        </div>

        <!-- 音频播放器 -->
        <div
          v-if="audioUrl && !isLoading"
          class="audio-player"
        >
          <audio
            ref="audioRef"
            :src="audioUrl"
            controls
            class="player"
          />
          <div class="audio-actions">
            <el-button
              text
              size="small"
              @click="handleDownload"
            >
              <el-icon><Download /></el-icon>
              下载音频
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
import { Setting, Microphone, Download, Delete, Loading, Cpu } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { sendServiceRequest } from '@/api/playground'
import type { TtsRequestConfig } from '../../types/playground'

interface Props {
  instances: any[]
  loading: boolean
}

const props = defineProps<Props>()

// 状态
const showConfig = ref(false)
const selectedModel = ref('')
const isLoading = ref(false)
const inputText = ref('')
const audioUrl = ref('')
const audioRef = ref<HTMLAudioElement>()

// 配置
const config = ref<Partial<TtsRequestConfig>>({
  voice: 'alloy',
  responseFormat: 'mp3',
  speed: 1.0
})

// 生成语音
const handleGenerate = async () => {
  if (!selectedModel.value || !inputText.value.trim()) return

  isLoading.value = true
  audioUrl.value = ''

  try {
    const requestConfig: TtsRequestConfig = {
      model: selectedModel.value,
      input: inputText.value,
      voice: config.value.voice,
      responseFormat: config.value.responseFormat,
      speed: config.value.speed
    }

    const token = localStorage.getItem('admin_token')
    const headers: Record<string, string> = {}
    if (token) headers['Jairouter_Token'] = token

    const response = await sendServiceRequest('tts', requestConfig, headers)

    // 处理 Blob 响应
    if (response.data instanceof Blob) {
      audioUrl.value = URL.createObjectURL(response.data)
    } else {
      audioUrl.value = response.data
    }

    ElMessage.success('语音生成成功')
  } catch (error: any) {
    const errorMsg = error.data?.error?.message || '生成语音失败'
    ElMessage.error(errorMsg)
  } finally {
    isLoading.value = false
  }
}

// 下载音频
const handleDownload = () => {
  if (!audioUrl.value) return

  const link = document.createElement('a')
  link.href = audioUrl.value
  link.download = `tts_${Date.now()}.${config.value.responseFormat}`
  link.click()
}

// 清空
const handleClear = () => {
  inputText.value = ''
  audioUrl.value = ''
}

// 暴露方法
defineExpose({
  refreshData: () => {},
  handleClear
})
</script>

<style scoped>
.tts-panel {
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

.input-info {
  text-align: right;
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
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

.audio-player {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.player {
  width: 100%;
  height: 40px;
}

.audio-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}
</style>