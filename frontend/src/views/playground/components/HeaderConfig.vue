<template>
  <div class="header-config">
    <el-row :gutter="20">
      <el-col :span="12">
        <el-card header="认证配置" class="config-card">
          <el-form :model="localConfig" label-width="100px" size="small">
            <el-form-item label="Authorization">
              <el-input 
                v-model="localConfig.authorization" 
                placeholder="Bearer your-token-here 或 sk-xxx"
                type="password"
                show-password
                clearable
                @input="onConfigChange"
              >
                <template #prepend>
                  <el-select v-model="authType" style="width: 80px" @change="onAuthTypeChange">
                    <el-option label="Bearer" value="Bearer" />
                    <el-option label="API Key" value="ApiKey" />
                    <el-option label="Custom" value="Custom" />
                  </el-select>
                </template>
              </el-input>
            </el-form-item>
            
            <el-form-item>
              <el-button type="success" size="small" @click="testConnection" :loading="testing">
                <el-icon><Connection /></el-icon>
                测试连接
              </el-button>
              <el-button type="info" size="small" @click="clearAuth">
                <el-icon><Delete /></el-icon>
                清除认证
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      
      <el-col :span="12">
        <el-card header="自定义请求头" class="config-card">
          <div class="custom-headers">
            <div class="header-actions">
              <el-button type="primary" size="small" @click="addCustomHeader">
                <el-icon><Plus /></el-icon>
                添加请求头
              </el-button>
              <el-button type="warning" size="small" @click="clearAllHeaders" v-if="customHeadersList.length > 0">
                <el-icon><Delete /></el-icon>
                清除所有
              </el-button>
            </div>
            
            <div class="headers-list" v-if="customHeadersList.length > 0">
              <div 
                v-for="(header, index) in customHeadersList" 
                :key="index"
                class="header-item"
              >
                <el-input
                  v-model="header.key"
                  placeholder="请求头名称"
                  size="small"
                  @input="onCustomHeaderChange"
                  class="header-key"
                />
                <el-input
                  v-model="header.value"
                  placeholder="请求头值"
                  size="small"
                  @input="onCustomHeaderChange"
                  class="header-value"
                />
                <el-button 
                  type="danger" 
                  size="small" 
                  @click="removeCustomHeader(index)"
                  class="remove-btn"
                >
                  <el-icon><Close /></el-icon>
                </el-button>
              </div>
            </div>
            
            <el-empty 
              v-else 
              description="暂无自定义请求头" 
              :image-size="60"
              class="empty-headers"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>
    
    <!-- 预设模板 -->
    <el-card header="常用模板" class="templates-card">
      <div class="templates">
        <el-button 
          v-for="template in headerTemplates" 
          :key="template.name"
          type="info" 
          size="small" 
          @click="applyTemplate(template)"
          class="template-btn"
        >
          {{ template.name }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete, Close, Connection } from '@element-plus/icons-vue'
import type { GlobalConfig } from '../types/playground'

// Props 和 Emits
interface Props {
  globalConfig: GlobalConfig
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'config-updated': [config: GlobalConfig]
  'update:globalConfig': [config: GlobalConfig]
}>()

// 本地配置状态
const localConfig = reactive<GlobalConfig>({
  authorization: '',
  customHeaders: {}
})

// 认证类型
const authType = ref('Bearer')
const testing = ref(false)

// 自定义请求头列表
interface HeaderItem {
  key: string
  value: string
}

const customHeadersList = ref<HeaderItem[]>([])

// 预设模板
const headerTemplates = [
  {
    name: 'OpenAI API',
    config: {
      authorization: 'Bearer sk-',
      customHeaders: {
        'Content-Type': 'application/json',
        'User-Agent': 'JAiRouter-Playground/1.0'
      }
    }
  },
  {
    name: 'Anthropic API',
    config: {
      authorization: '',
      customHeaders: {
        'x-api-key': '',
        'Content-Type': 'application/json',
        'anthropic-version': '2023-06-01'
      }
    }
  },
  {
    name: 'Azure OpenAI',
    config: {
      authorization: '',
      customHeaders: {
        'api-key': '',
        'Content-Type': 'application/json'
      }
    }
  },
  {
    name: '清除所有',
    config: {
      authorization: '',
      customHeaders: {}
    }
  }
]

// 计算属性
const hasCustomHeaders = computed(() => customHeadersList.value.length > 0)

// 方法定义
const syncCustomHeadersList = () => {
  // 将对象转换为数组
  customHeadersList.value = Object.entries(localConfig.customHeaders || {}).map(([key, value]) => ({
    key,
    value
  }))
}

// 监听 props 变化
watch(() => props.globalConfig, (newConfig) => {
  Object.assign(localConfig, newConfig)
  syncCustomHeadersList()
}, { immediate: true, deep: true })

// 监听本地配置变化
watch(localConfig, (newConfig) => {
  emit('config-updated', { ...newConfig })
  emit('update:globalConfig', { ...newConfig })
}, { deep: true })

// 方法
const onConfigChange = () => {
  // 配置变化时的处理逻辑
}

const onAuthTypeChange = () => {
  if (authType.value === 'Bearer' && localConfig.authorization && !localConfig.authorization.startsWith('Bearer ')) {
    localConfig.authorization = `Bearer ${localConfig.authorization}`
  } else if (authType.value === 'ApiKey' && localConfig.authorization.startsWith('Bearer ')) {
    localConfig.authorization = localConfig.authorization.replace('Bearer ', '')
  }
}

const addCustomHeader = () => {
  customHeadersList.value.push({ key: '', value: '' })
}

const removeCustomHeader = (index: number) => {
  customHeadersList.value.splice(index, 1)
  onCustomHeaderChange()
}

const onCustomHeaderChange = () => {
  // 将数组转换为对象
  const headers: Record<string, string> = {}
  customHeadersList.value.forEach(header => {
    if (header.key.trim() && header.value.trim()) {
      headers[header.key.trim()] = header.value.trim()
    }
  })
  localConfig.customHeaders = headers
}



const clearAuth = () => {
  localConfig.authorization = ''
  ElMessage.success('认证信息已清除')
}

const clearAllHeaders = () => {
  customHeadersList.value = []
  localConfig.customHeaders = {}
  ElMessage.success('自定义请求头已清除')
}

const testConnection = async () => {
  if (!localConfig.authorization) {
    ElMessage.warning('请先配置认证信息')
    return
  }
  
  testing.value = true
  try {
    // 这里可以添加实际的连接测试逻辑
    await new Promise(resolve => setTimeout(resolve, 1000))
    ElMessage.success('连接测试成功')
  } catch (error) {
    ElMessage.error('连接测试失败')
  } finally {
    testing.value = false
  }
}

const applyTemplate = (template: typeof headerTemplates[0]) => {
  Object.assign(localConfig, template.config)
  syncCustomHeadersList()
  ElMessage.success(`已应用模板: ${template.name}`)
}

// 生命周期
onMounted(() => {
  syncCustomHeadersList()
})

// 暴露方法
defineExpose({
  clearAuth,
  clearAllHeaders,
  testConnection,
  applyTemplate
})
</script>

<style scoped>
.header-config {
  margin-bottom: 20px;
}

.config-card {
  height: 100%;
}

.config-card :deep(.el-card__body) {
  padding: 15px;
}

.custom-headers {
  min-height: 200px;
}

.header-actions {
  display: flex;
  gap: 10px;
  margin-bottom: 15px;
}

.headers-list {
  max-height: 300px;
  overflow-y: auto;
}

.header-item {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
  align-items: center;
}

.header-key {
  flex: 1;
}

.header-value {
  flex: 2;
}

.remove-btn {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  padding: 0;
}

.empty-headers {
  margin: 20px 0;
}

.templates-card {
  margin-top: 20px;
}

.templates {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.template-btn {
  margin: 0;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .header-item {
    flex-direction: column;
    align-items: stretch;
  }
  
  .header-key,
  .header-value {
    flex: none;
  }
  
  .remove-btn {
    align-self: center;
    margin-top: 5px;
  }
  
  .templates {
    justify-content: center;
  }
}

/* 滚动条样式 */
.headers-list::-webkit-scrollbar {
  width: 6px;
}

.headers-list::-webkit-scrollbar-track {
  background: var(--el-bg-color-page);
  border-radius: 3px;
}

.headers-list::-webkit-scrollbar-thumb {
  background: var(--el-border-color);
  border-radius: 3px;
}

.headers-list::-webkit-scrollbar-thumb:hover {
  background: var(--el-border-color-dark);
}

/* 动画效果 */
.header-item {
  transition: all 0.3s ease;
}

.header-item:hover {
  background-color: var(--el-bg-color-page);
  border-radius: 4px;
  padding: 5px;
  margin: 5px 0;
}

/* 表单验证样式 */
.header-key :deep(.el-input__wrapper),
.header-value :deep(.el-input__wrapper) {
  transition: border-color 0.3s ease;
}

.header-key :deep(.el-input__wrapper:focus),
.header-value :deep(.el-input__wrapper:focus) {
  border-color: var(--el-color-primary);
}

/* 模板按钮样式 */
.template-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
</style>