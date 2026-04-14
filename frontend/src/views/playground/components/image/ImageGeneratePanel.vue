<template>
  <div class="image-generate-panel">
    <!-- 模型选择和配置 -->
    <div class="panel-toolbar">
      <el-select
        v-model="selectedModel"
        :placeholder="'选择图像生成模型'"
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
          <el-col :span="4">
            <div class="config-item">
              <label class="config-label">生成数量</label>
              <el-input-number
                v-model="config.n"
                :min="1"
                :max="10"
                size="small"
              />
            </div>
          </el-col>
          <el-col :span="4">
            <div class="config-item">
              <label class="config-label">图像尺寸</label>
              <el-select
                v-model="config.size"
                size="small"
              >
                <el-option
                  label="256x256"
                  value="256x256"
                />
                <el-option
                  label="512x512"
                  value="512x512"
                />
                <el-option
                  label="1024x1024"
                  value="1024x1024"
                />
                <el-option
                  label="1024x1792"
                  value="1024x1792"
                />
                <el-option
                  label="1792x1024"
                  value="1792x1024"
                />
              </el-select>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="config-item">
              <label class="config-label">质量</label>
              <el-select
                v-model="config.quality"
                size="small"
              >
                <el-option
                  label="standard"
                  value="standard"
                />
                <el-option
                  label="hd"
                  value="hd"
                />
              </el-select>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="config-item">
              <label class="config-label">风格</label>
              <el-select
                v-model="config.style"
                size="small"
              >
                <el-option
                  label="vivid"
                  value="vivid"
                />
                <el-option
                  label="natural"
                  value="natural"
                />
              </el-select>
            </div>
          </el-col>
        </el-row>
      </div>
    </Transition>

    <!-- 主内容 -->
    <div class="panel-content">
      <!-- 提示词输入 -->
      <div class="input-card">
        <div class="card-header">
          <span class="card-title">提示词</span>
        </div>
        <el-input
          v-model="promptText"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 6 }"
          placeholder="描述你想生成的图像，例如：一只可爱的猫咪坐在窗台上，阳光明媚..."
          resize="none"
        />
      </div>

      <!-- 操作按钮 -->
      <div class="action-bar">
        <el-button
          type="primary"
          :loading="isLoading"
          :disabled="!selectedModel || !promptText.trim()"
          @click="handleGenerate"
        >
          <el-icon><Picture /></el-icon>
          生成图像
        </el-button>
      </div>

      <!-- 结果展示 -->
      <div class="result-card">
        <div class="card-header">
          <span class="card-title">生成结果</span>
        </div>

        <!-- 空状态 -->
        <div
          v-if="images.length === 0 && !isLoading"
          class="empty-state"
        >
          <el-icon
            :size="48"
            color="#909399"
          >
            <Picture />
          </el-icon>
          <span>输入提示词后生成图像</span>
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
          <span>正在生成图像...</span>
        </div>

        <!-- 图像展示 -->
        <div
          v-if="images.length > 0 && !isLoading"
          class="image-grid"
        >
          <div
            v-for="(img, index) in images"
            :key="index"
            class="image-item"
          >
            <el-image
              :src="img.url || img.b64_json"
              fit="contain"
              class="generated-image"
              :preview-src-list="imageUrls"
              :initial-index="index"
            />
            <div class="image-actions">
              <el-button
                text
                size="small"
                @click="downloadImage(img)"
              >
                <el-icon><Download /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Setting, Picture, Download, Loading, Cpu } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { sendServiceRequest } from '@/api/playground'
import type { ImageGenerateRequestConfig } from '../../types/playground'

interface Props {
  instances: any[]
  loading: boolean
}

const props = defineProps<Props>()

interface GeneratedImage {
  url?: string
  b64_json?: string
}

// 状态
const showConfig = ref(false)
const selectedModel = ref('')
const isLoading = ref(false)
const promptText = ref('')
const images = ref<GeneratedImage[]>([])

// 配置
const config = ref<Partial<ImageGenerateRequestConfig>>({
  n: 1,
  size: '1024x1024',
  quality: 'standard',
  style: 'vivid',
  responseFormat: 'url'
})

// 图片 URL 列表（用于预览）
const imageUrls = computed(() => {
  return images.value.map((img) => img.url || img.b64_json || '')
})

// 生成图像
const handleGenerate = async () => {
  if (!selectedModel.value || !promptText.value.trim()) return

  isLoading.value = true
  images.value = []

  try {
    const requestConfig: ImageGenerateRequestConfig = {
      model: selectedModel.value,
      prompt: promptText.value,
      n: config.value.n || 1,
      size: config.value.size,
      quality: config.value.quality,
      style: config.value.style,
      responseFormat: config.value.responseFormat
    }

    const token = localStorage.getItem('admin_token')
    const headers: Record<string, string> = {}
    if (token) headers['Jairouter_Token'] = token

    const response = await sendServiceRequest('imageGenerate', requestConfig, headers)
    images.value = response.data?.data || []
    ElMessage.success(`成功生成 ${images.value.length} 张图像`)
  } catch (error: any) {
    const errorMsg = error.data?.error?.message || '生成图像失败'
    ElMessage.error(errorMsg)
  } finally {
    isLoading.value = false
  }
}

// 下载图像
const downloadImage = async (img: GeneratedImage) => {
  const url = img.url || img.b64_json
  if (!url) return

  const link = document.createElement('a')
  link.href = url
  link.download = `generated_${Date.now()}.png`
  link.click()
}

// 清空
const handleClear = () => {
  promptText.value = ''
  images.value = []
}

// 暴露方法
defineExpose({
  refreshData: () => {},
  handleClear
})
</script>

<style scoped>
.image-generate-panel {
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

.action-bar {
  display: flex;
  justify-content: center;
  padding: 12px 0;
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

.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.image-item {
  position: relative;
  background-color: #f5f5f5;
  border-radius: 4px;
  overflow: hidden;
}

.generated-image {
  width: 100%;
  height: 200px;
}

.image-actions {
  position: absolute;
  bottom: 8px;
  right: 8px;
}
</style>