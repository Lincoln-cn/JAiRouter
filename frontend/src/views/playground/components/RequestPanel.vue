<template>
  <div class="request-panel">
    <div class="panel-header">
      <h3>请求详情</h3>
      <div class="header-actions">
        <el-button 
          size="small" 
          type="primary" 
          :icon="DocumentCopy"
          @click="copyRequest"
        >
          复制请求
        </el-button>
        <el-button 
          size="small" 
          :icon="isExpanded ? Minus : Plus"
          @click="toggleExpanded"
        >
          {{ isExpanded ? '收起' : '展开' }}
        </el-button>
      </div>
    </div>

    <div v-if="!request" class="no-request">
      <el-empty description="暂无请求信息" />
    </div>

    <div v-else class="request-content" :class="{ expanded: isExpanded }">
      <!-- 请求基本信息 -->
      <div class="request-summary">
        <el-tag :type="getMethodTagType(request.method)" size="large">
          {{ request.method }}
        </el-tag>
        <span class="request-url">{{ request.endpoint }}</span>
        <el-tag v-if="requestSize" type="info" size="small">
          {{ formatFileSize(requestSize) }}
        </el-tag>
      </div>

      <!-- 请求头 -->
      <el-collapse v-model="activeCollapse" class="request-details">
        <el-collapse-item title="请求头" name="headers">
          <template #title>
            <span>请求头</span>
            <el-tag size="small" type="info" style="margin-left: 8px">
              {{ Object.keys(request.headers || {}).length }} 项
            </el-tag>
          </template>
          
          <div v-if="!request.headers || Object.keys(request.headers).length === 0" class="empty-content">
            <el-text type="info">无请求头</el-text>
          </div>
          <div v-else class="headers-content">
            <div 
              v-for="[key, value] in Object.entries(request.headers)" 
              :key="key" 
              class="header-item"
            >
              <span class="header-key">{{ key }}:</span>
              <span class="header-value">{{ value }}</span>
              <el-button 
                size="small" 
                text 
                :icon="DocumentCopy"
                @click="copyText(`${key}: ${value}`)"
              />
            </div>
          </div>
        </el-collapse-item>

        <!-- 请求体 -->
        <el-collapse-item title="请求体" name="body" v-if="hasBody">
          <template #title>
            <span>请求体</span>
            <el-tag size="small" type="info" style="margin-left: 8px">
              {{ getBodyType() }}
            </el-tag>
          </template>
          
          <div class="body-content">
            <!-- JSON 请求体 -->
            <div v-if="isJsonBody" class="json-body">
              <div class="code-header">
                <span>JSON</span>
                <el-button 
                  size="small" 
                  text 
                  :icon="DocumentCopy"
                  @click="copyText(formattedJsonBody)"
                >
                  复制
                </el-button>
              </div>
              <pre class="json-content"><code>{{ formattedJsonBody }}</code></pre>
            </div>

            <!-- 文件上传 -->
            <div v-else-if="request.files && request.files.length > 0" class="files-body">
              <div class="files-header">
                <span>文件上传 (multipart/form-data)</span>
              </div>
              <div class="files-list">
                <div v-for="(file, index) in request.files" :key="index" class="file-item">
                  <el-icon><Document /></el-icon>
                  <span class="file-name">{{ file.name }}</span>
                  <span class="file-size">{{ formatFileSize(file.size) }}</span>
                  <span class="file-type">{{ file.type || 'unknown' }}</span>
                </div>
              </div>
              
              <!-- 表单数据 -->
              <div v-if="request.body && Object.keys(request.body).length > 0" class="form-data">
                <div class="form-data-header">表单数据:</div>
                <div class="form-data-content">
                  <div 
                    v-for="[key, value] in Object.entries(request.body)" 
                    :key="key" 
                    class="form-field"
                  >
                    <span class="field-key">{{ key }}:</span>
                    <span class="field-value">{{ value }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 其他类型请求体 -->
            <div v-else class="raw-body">
              <div class="code-header">
                <span>Raw Body</span>
                <el-button 
                  size="small" 
                  text 
                  :icon="DocumentCopy"
                  @click="copyText(String(request.body))"
                >
                  复制
                </el-button>
              </div>
              <pre class="raw-content"><code>{{ request.body }}</code></pre>
            </div>
          </div>
        </el-collapse-item>

        <!-- cURL 命令 -->
        <el-collapse-item title="cURL 命令" name="curl">
          <div class="curl-content">
            <div class="code-header">
              <span>cURL</span>
              <el-button 
                size="small" 
                text 
                :icon="DocumentCopy"
                @click="copyText(curlCommand)"
              >
                复制
              </el-button>
            </div>
            <pre class="curl-command"><code>{{ curlCommand }}</code></pre>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentCopy, Plus, Minus, Document } from '@element-plus/icons-vue'
import type { PlaygroundRequest } from '../types/playground'
import { formatFileSize } from '@/api/playground'

interface Props {
  request: PlaygroundRequest | null
  requestSize?: number
}

const props = defineProps<Props>()

const isExpanded = ref(false)
const activeCollapse = ref<string[]>(['headers'])

// 计算属性
const hasBody = computed(() => {
  return props.request && (
    props.request.body || 
    (props.request.files && props.request.files.length > 0)
  )
})

const isJsonBody = computed(() => {
  return props.request?.body && 
         typeof props.request.body === 'object' && 
         !props.request.files?.length
})

const formattedJsonBody = computed(() => {
  if (!isJsonBody.value) return ''
  try {
    return JSON.stringify(props.request!.body, null, 2)
  } catch {
    return String(props.request!.body)
  }
})

const curlCommand = computed(() => {
  if (!props.request) return ''
  
  let curl = `curl -X ${props.request.method}`
  
  // 添加请求头
  if (props.request.headers) {
    Object.entries(props.request.headers).forEach(([key, value]) => {
      curl += ` \\\n  -H "${key}: ${value}"`
    })
  }
  
  // 添加请求体
  if (props.request.files && props.request.files.length > 0) {
    // 文件上传
    props.request.files.forEach((file, index) => {
      curl += ` \\\n  -F "file${index === 0 ? '' : index}=@${file.name}"`
    })
    
    // 表单数据
    if (props.request.body) {
      Object.entries(props.request.body).forEach(([key, value]) => {
        curl += ` \\\n  -F "${key}=${value}"`
      })
    }
  } else if (props.request.body) {
    // JSON 数据
    const bodyStr = typeof props.request.body === 'string' 
      ? props.request.body 
      : JSON.stringify(props.request.body)
    curl += ` \\\n  -d '${bodyStr}'`
  }
  
  // 添加URL
  curl += ` \\\n  "${import.meta.env.VITE_API_BASE_URL}${props.request.endpoint}"`
  
  return curl
})

// 方法
const getMethodTagType = (method: string) => {
  const types: Record<string, string> = {
    GET: 'success',
    POST: 'primary',
    PUT: 'warning',
    DELETE: 'danger',
    PATCH: 'info'
  }
  return types[method] || 'info'
}

const getBodyType = () => {
  if (!props.request) return ''
  
  if (props.request.files && props.request.files.length > 0) {
    return 'multipart/form-data'
  } else if (props.request.body) {
    return typeof props.request.body === 'object' ? 'application/json' : 'text/plain'
  }
  return ''
}

const toggleExpanded = () => {
  isExpanded.value = !isExpanded.value
  if (isExpanded.value) {
    activeCollapse.value = ['headers', 'body', 'curl']
  } else {
    activeCollapse.value = ['headers']
  }
}

const copyText = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch {
    // 降级方案
    const textArea = document.createElement('textarea')
    textArea.value = text
    document.body.appendChild(textArea)
    textArea.select()
    document.execCommand('copy')
    document.body.removeChild(textArea)
    ElMessage.success('已复制到剪贴板')
  }
}

const copyRequest = () => {
  if (!props.request) return
  
  const requestInfo = {
    method: props.request.method,
    url: props.request.endpoint,
    headers: props.request.headers,
    body: props.request.body,
    files: props.request.files?.map(f => ({ name: f.name, size: f.size, type: f.type }))
  }
  
  copyText(JSON.stringify(requestInfo, null, 2))
}

// 监听请求变化，自动展开相关部分
watch(() => props.request, (newRequest) => {
  if (newRequest && hasBody.value && !activeCollapse.value.includes('body')) {
    activeCollapse.value.push('body')
  }
}, { immediate: true })
</script>

<style scoped>
.request-panel {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-bg-color);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color-page);
}

.panel-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.header-actions {
  display: flex;
  gap: 8px;
}

.no-request {
  padding: 40px;
  text-align: center;
}

.request-content {
  padding: 16px;
}

.request-content.expanded {
  max-height: none;
}

.request-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px;
  background: var(--el-bg-color-page);
  border-radius: 6px;
}

.request-url {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 14px;
  color: var(--el-text-color-primary);
  flex: 1;
}

.request-details {
  border: none;
}

.request-details :deep(.el-collapse-item__header) {
  background: var(--el-bg-color-page);
  border: none;
  padding: 12px 16px;
  font-weight: 500;
}

.request-details :deep(.el-collapse-item__content) {
  padding: 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-top: none;
}

.empty-content {
  text-align: center;
  padding: 20px;
  color: var(--el-text-color-secondary);
}

.headers-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.header-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  background: var(--el-bg-color-page);
  border-radius: 4px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
}

.header-key {
  font-weight: 600;
  color: var(--el-color-primary);
  margin-right: 8px;
  min-width: 120px;
}

.header-value {
  flex: 1;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.body-content {
  background: var(--el-bg-color-page);
  border-radius: 6px;
  overflow: hidden;
}

.code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--el-color-info-light-9);
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}

.json-content, .raw-content, .curl-command {
  margin: 0;
  padding: 16px;
  background: var(--el-bg-color);
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.5;
  color: var(--el-text-color-primary);
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
}

.files-body {
  padding: 16px;
}

.files-header {
  font-weight: 500;
  margin-bottom: 12px;
  color: var(--el-text-color-primary);
}

.files-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--el-bg-color);
  border-radius: 4px;
  border: 1px solid var(--el-border-color-lighter);
}

.file-name {
  flex: 1;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.file-size, .file-type {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.form-data {
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 16px;
}

.form-data-header {
  font-weight: 500;
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
}

.form-data-content {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-field {
  display: flex;
  align-items: center;
  padding: 6px 12px;
  background: var(--el-bg-color);
  border-radius: 4px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
}

.field-key {
  font-weight: 600;
  color: var(--el-color-primary);
  margin-right: 8px;
  min-width: 100px;
}

.field-value {
  flex: 1;
  color: var(--el-text-color-primary);
}

.curl-content {
  background: var(--el-color-info-light-9);
  border-radius: 6px;
  overflow: hidden;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .panel-header {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
  
  .header-actions {
    justify-content: center;
  }
  
  .request-summary {
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }
  
  .header-item {
    flex-direction: column;
    align-items: stretch;
  }
  
  .header-key {
    min-width: auto;
    margin-bottom: 4px;
  }
}
</style>