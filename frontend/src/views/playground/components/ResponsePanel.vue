<template>
  <div class="response-panel">
    <el-card class="response-card">
      <template #header>
        <div class="response-header">
          <span>响应详情</span>
          <div class="header-actions" v-if="response">
            <el-tag 
              :type="statusTagType" 
              size="small"
              class="status-tag"
            >
              {{ response.status }} {{ response.statusText }}
            </el-tag>
            <el-tag type="info" size="small" v-if="response.duration">
              {{ response.duration }}ms
            </el-tag>
            <el-button 
              type="primary" 
              size="small" 
              @click="copyResponse"
              :disabled="!response"
            >
              <el-icon><DocumentCopy /></el-icon>
              复制响应
            </el-button>
          </div>
        </div>
      </template>
      
      <div class="response-content">
        <!-- 加载状态 -->
        <div v-if="loading" class="loading-state">
          <el-skeleton :rows="5" animated />
          <div class="loading-text">
            <el-icon class="is-loading"><Loading /></el-icon>
            正在发送请求...
          </div>
        </div>
        
        <!-- 错误状态 -->
        <div v-else-if="error" class="error-state">
          <el-alert
            :title="error"
            type="error"
            :closable="false"
            show-icon
          >
            <template #default>
              <div class="error-details">
                <p>请求失败，请检查以下内容：</p>
                <ul>
                  <li>网络连接是否正常</li>
                  <li>认证信息是否正确</li>
                  <li>请求参数是否有效</li>
                  <li>服务端点是否可用</li>
                </ul>
              </div>
            </template>
          </el-alert>
        </div>
        
        <!-- 响应内容 -->
        <div v-else-if="response" class="response-data">
          <el-tabs v-model="activeTab" type="border-card">
            <!-- 响应体 -->
            <el-tab-pane label="响应体" name="body">
              <div class="response-body">
                <div class="body-actions">
                  <el-button-group size="small">
                    <el-button 
                      :type="viewMode === 'formatted' ? 'primary' : ''"
                      @click="viewMode = 'formatted'"
                    >
                      格式化
                    </el-button>
                    <el-button 
                      :type="viewMode === 'raw' ? 'primary' : ''"
                      @click="viewMode = 'raw'"
                    >
                      原始
                    </el-button>
                  </el-button-group>
                  <el-button size="small" @click="copyResponseBody">
                    <el-icon><DocumentCopy /></el-icon>
                    复制内容
                  </el-button>
                </div>
                
                <div class="body-content">
                  <!-- 格式化视图 -->
                  <div v-if="viewMode === 'formatted'" class="formatted-content">
                    <pre v-if="isJsonResponse" class="json-content">{{ formattedJson }}</pre>
                    <div v-else-if="isImageResponse" class="image-content">
                      <img 
                        v-for="(url, index) in imageUrls" 
                        :key="index"
                        :src="url" 
                        :alt="`Generated image ${index + 1}`"
                        class="response-image"
                        @error="onImageError"
                      />
                    </div>
                    <div v-else-if="isAudioResponse" class="audio-content">
                      <audio controls class="response-audio">
                        <source :src="audioUrl" type="audio/mpeg">
                        您的浏览器不支持音频播放。
                      </audio>
                      <el-button size="small" @click="downloadAudio">
                        <el-icon><Download /></el-icon>
                        下载音频
                      </el-button>
                    </div>
                    <pre v-else class="text-content">{{ response.data }}</pre>
                  </div>
                  
                  <!-- 原始视图 -->
                  <div v-else class="raw-content">
                    <pre class="raw-text">{{ rawResponseText }}</pre>
                  </div>
                </div>
              </div>
            </el-tab-pane>
            
            <!-- 响应头 -->
            <el-tab-pane label="响应头" name="headers">
              <div class="response-headers">
                <el-table :data="headersTableData" size="small" stripe>
                  <el-table-column prop="name" label="名称" width="200" />
                  <el-table-column prop="value" label="值" show-overflow-tooltip />
                  <el-table-column label="操作" width="100">
                    <template #default="{ row }">
                      <el-button 
                        size="small" 
                        type="text" 
                        @click="copyHeaderValue(row.value)"
                      >
                        复制
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </el-tab-pane>
            
            <!-- 请求信息 -->
            <el-tab-pane label="请求信息" name="request">
              <div class="request-info">
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item label="时间戳">
                    {{ formatTimestamp(response.timestamp) }}
                  </el-descriptions-item>
                  <el-descriptions-item label="响应时间">
                    {{ response.duration }}ms
                  </el-descriptions-item>
                  <el-descriptions-item label="状态码">
                    <el-tag :type="statusTagType" size="small">
                      {{ response.status }}
                    </el-tag>
                  </el-descriptions-item>
                  <el-descriptions-item label="状态文本">
                    {{ response.statusText }}
                  </el-descriptions-item>
                  <el-descriptions-item label="响应大小" span="2">
                    {{ formatResponseSize(response.data) }}
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
        
        <!-- 空状态 -->
        <div v-else class="empty-state">
          <el-empty 
            description="暂无响应数据" 
            :image-size="100"
          >
            <template #description>
              <p>发送请求后，响应内容将在此处显示</p>
            </template>
          </el-empty>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { 
  DocumentCopy, 
  Loading, 
  Download 
} from '@element-plus/icons-vue'
import type { PlaygroundResponse } from '../types/playground'
import { getResponseSize } from '@/api/playground'

// Props
interface Props {
  response: PlaygroundResponse | null
  loading?: boolean
  error?: string | null
  requestSize?: number
  method?: string
  endpoint?: string
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  error: null,
  requestSize: 0,
  method: '',
  endpoint: ''
})

// Emits
const emit = defineEmits<{
  performanceMetrics: [metrics: {
    duration: number
    requestSize: number
    responseSize: number
    status: number
    method: string
    endpoint: string
    timestamp: string
  }]
}>()

// 响应式状态
const activeTab = ref('body')
const viewMode = ref<'formatted' | 'raw'>('formatted')

// 计算属性
const statusTagType = computed(() => {
  if (!props.response) return 'info'
  const status = props.response.status
  if (status >= 200 && status < 300) return 'success'
  if (status >= 300 && status < 400) return 'warning'
  if (status >= 400) return 'danger'
  return 'info'
})

const isJsonResponse = computed(() => {
  if (!props.response) return false
  try {
    JSON.parse(JSON.stringify(props.response.data))
    return typeof props.response.data === 'object'
  } catch {
    return false
  }
})

const isImageResponse = computed(() => {
  if (!props.response) return false
  const data = props.response.data
  return data && (
    (Array.isArray(data.data) && data.data.some((item: any) => item.url)) ||
    (data.url && typeof data.url === 'string')
  )
})

const isAudioResponse = computed(() => {
  if (!props.response) return false
  const contentType = props.response.headers['content-type'] || ''
  return contentType.includes('audio/') || 
         (props.response.data instanceof Blob && props.response.data.type.includes('audio/'))
})

const formattedJson = computed(() => {
  if (!isJsonResponse.value || !props.response) return ''
  try {
    return JSON.stringify(props.response.data, null, 2)
  } catch {
    return String(props.response.data)
  }
})

const rawResponseText = computed(() => {
  if (!props.response) return ''
  if (typeof props.response.data === 'string') {
    return props.response.data
  }
  return JSON.stringify(props.response.data)
})

const imageUrls = computed(() => {
  if (!isImageResponse.value || !props.response) return []
  const data = props.response.data
  if (Array.isArray(data.data)) {
    return data.data.map((item: any) => item.url).filter(Boolean)
  }
  if (data.url) {
    return [data.url]
  }
  return []
})

const audioUrl = computed(() => {
  if (!isAudioResponse.value || !props.response) return ''
  if (props.response.data instanceof Blob) {
    return URL.createObjectURL(props.response.data)
  }
  return ''
})

const headersTableData = computed(() => {
  if (!props.response) return []
  return Object.entries(props.response.headers).map(([name, value]) => ({
    name,
    value
  }))
})

// 方法
const copyResponse = async () => {
  if (!props.response) return
  
  try {
    const responseText = JSON.stringify({
      status: props.response.status,
      statusText: props.response.statusText,
      headers: props.response.headers,
      data: props.response.data,
      duration: props.response.duration,
      timestamp: props.response.timestamp
    }, null, 2)
    
    await navigator.clipboard.writeText(responseText)
    ElMessage.success('响应内容已复制到剪贴板')
  } catch (error) {
    ElMessage.error('复制失败')
  }
}

const copyResponseBody = async () => {
  if (!props.response) return
  
  try {
    const bodyText = viewMode.value === 'formatted' 
      ? formattedJson.value || String(props.response.data)
      : rawResponseText.value
    
    await navigator.clipboard.writeText(bodyText)
    ElMessage.success('响应体已复制到剪贴板')
  } catch (error) {
    ElMessage.error('复制失败')
  }
}

const copyHeaderValue = async (value: string) => {
  try {
    await navigator.clipboard.writeText(value)
    ElMessage.success('请求头值已复制到剪贴板')
  } catch (error) {
    ElMessage.error('复制失败')
  }
}

const downloadAudio = () => {
  if (!audioUrl.value) return
  
  const link = document.createElement('a')
  link.href = audioUrl.value
  link.download = `audio_${Date.now()}.mp3`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const onImageError = (event: Event) => {
  const img = event.target as HTMLImageElement
  img.style.display = 'none'
  ElMessage.error('图像加载失败')
}

const formatTimestamp = (timestamp: string) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

const formatResponseSize = (data: any) => {
  if (!data) return '0 B'
  
  const size = getResponseSize({ 
    status: 200, 
    statusText: 'OK', 
    headers: {}, 
    data, 
    duration: 0, 
    timestamp: '' 
  })
  
  const units = ['B', 'KB', 'MB', 'GB']
  let unitIndex = 0
  let sizeValue = size
  
  while (sizeValue >= 1024 && unitIndex < units.length - 1) {
    sizeValue /= 1024
    unitIndex++
  }
  
  return `${sizeValue.toFixed(1)} ${units[unitIndex]}`
}

// 监听响应变化，自动切换到响应体选项卡
watch(() => props.response, (newResponse) => {
  if (newResponse) {
    activeTab.value = 'body'
  }
})

// 用于跟踪是否已发送性能指标
const metricsSent = ref(false)

// 监听loading状态变化，只在请求完成时发送性能指标
watch(() => props.loading, (newLoading, oldLoading) => {
  // 当loading从true变为false时，表示请求完成
  if (oldLoading && !newLoading && props.response && !metricsSent.value) {
    const responseSize = getResponseSize(props.response)
    emit('performanceMetrics', {
      duration: props.response.duration,
      requestSize: props.requestSize || 0,
      responseSize,
      status: props.response.status,
      method: props.method || 'POST',
      endpoint: props.endpoint || '',
      timestamp: props.response.timestamp
    })
    metricsSent.value = true
  }
})

// 重置指标发送状态
watch(() => props.response, (newResponse, oldResponse) => {
  // 当开始新请求时重置状态
  if (newResponse !== oldResponse) {
    metricsSent.value = false
  }
})

// 清理音频URL
watch(() => props.response, (newResponse, oldResponse) => {
  if (oldResponse && audioUrl.value && audioUrl.value.startsWith('blob:')) {
    URL.revokeObjectURL(audioUrl.value)
  }
})

// 暴露方法
defineExpose({
  copyResponse,
  copyResponseBody,
  downloadAudio
})
</script>

<style scoped>
.response-panel {
  margin-bottom: 20px;
}

.response-card {
  height: 100%;
}

.response-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.status-tag {
  font-weight: bold;
}

.response-content {
  min-height: 300px;
  max-height: 600px;
  overflow: hidden;
}

/* 加载状态 */
.loading-state {
  padding: 20px;
}

.loading-text {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 20px;
  color: var(--el-color-primary);
  font-size: 14px;
}

/* 错误状态 */
.error-state {
  padding: 20px;
}

.error-details ul {
  margin: 10px 0;
  padding-left: 20px;
}

.error-details li {
  margin: 5px 0;
}

/* 响应数据 */
.response-data {
  height: 100%;
}

.response-data :deep(.el-tabs__content) {
  height: calc(100% - 40px);
  overflow: auto;
}

.response-data :deep(.el-tab-pane) {
  height: 100%;
}

/* 响应体 */
.response-body {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.body-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  padding: 10px;
  background-color: var(--el-bg-color-page);
  border-radius: 4px;
}

.body-content {
  flex: 1;
  overflow: auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  max-height: 400px;
  min-height: 200px;
}

.formatted-content,
.raw-content {
  height: 100%;
  overflow: auto;
}

.json-content,
.text-content,
.raw-text {
  margin: 0;
  padding: 16px;
  background: var(--el-bg-color);
  border: none;
  border-radius: 4px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow-y: auto;
}

.json-content {
  color: var(--el-text-color-primary);
}

/* 图像响应 */
.image-content {
  padding: 15px;
  display: flex;
  flex-wrap: wrap;
  gap: 15px;
  justify-content: center;
}

.response-image {
  max-width: 100%;
  max-height: 400px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: transform 0.3s ease;
}

.response-image:hover {
  transform: scale(1.05);
}

/* 音频响应 */
.audio-content {
  padding: 15px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 15px;
}

.response-audio {
  width: 100%;
  max-width: 400px;
}

/* 响应头表格 */
.response-headers {
  height: 100%;
  overflow: auto;
}

/* 请求信息 */
.request-info {
  padding: 15px;
}

/* 空状态 */
.empty-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .response-header {
    flex-direction: column;
    align-items: stretch;
  }
  
  .header-actions {
    justify-content: center;
  }
  
  .body-actions {
    flex-direction: column;
    gap: 10px;
  }
  
  .json-content,
  .text-content,
  .raw-text {
    font-size: 11px;
    padding: 10px;
  }
  
  .image-content {
    flex-direction: column;
    align-items: center;
  }
}

/* 滚动条样式 */
.json-content::-webkit-scrollbar,
.text-content::-webkit-scrollbar,
.raw-text::-webkit-scrollbar,
.body-content::-webkit-scrollbar,
.response-headers::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.json-content::-webkit-scrollbar-track,
.text-content::-webkit-scrollbar-track,
.raw-text::-webkit-scrollbar-track,
.body-content::-webkit-scrollbar-track,
.response-headers::-webkit-scrollbar-track {
  background: var(--el-bg-color-page);
  border-radius: 4px;
}

.json-content::-webkit-scrollbar-thumb,
.text-content::-webkit-scrollbar-thumb,
.raw-text::-webkit-scrollbar-thumb,
.body-content::-webkit-scrollbar-thumb,
.response-headers::-webkit-scrollbar-thumb {
  background: var(--el-border-color-dark);
  border-radius: 4px;
  transition: background 0.3s ease;
}

.json-content::-webkit-scrollbar-thumb:hover,
.text-content::-webkit-scrollbar-thumb:hover,
.raw-text::-webkit-scrollbar-thumb:hover,
.body-content::-webkit-scrollbar-thumb:hover,
.response-headers::-webkit-scrollbar-thumb:hover {
  background: var(--el-color-primary-light-5);
}

/* 语法高亮 */
.json-content {
  background-color: #f8f9fa;
}

.json-content:deep(.string) {
  color: #032f62;
}

.json-content:deep(.number) {
  color: #005cc5;
}

.json-content:deep(.boolean) {
  color: #d73a49;
}

.json-content:deep(.null) {
  color: #6f42c1;
}

.json-content:deep(.key) {
  color: #22863a;
  font-weight: bold;
}

/* 深色主题适配 */
@media (prefers-color-scheme: dark) {
  .json-content {
    background-color: #1e1e1e;
    color: #d4d4d4;
  }
  
  .json-content:deep(.string) {
    color: #ce9178;
  }
  
  .json-content:deep(.number) {
    color: #b5cea8;
  }
  
  .json-content:deep(.boolean) {
    color: #569cd6;
  }
  
  .json-content:deep(.null) {
    color: #569cd6;
  }
  
  .json-content:deep(.key) {
    color: #9cdcfe;
  }
}

/* 动画效果 */
.response-data {
  animation: fadeIn 0.3s ease-in-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 加载动画 */
.loading-text .el-icon {
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