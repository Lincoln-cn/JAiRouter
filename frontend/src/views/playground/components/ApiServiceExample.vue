<template>
  <div class="api-service-example">
    <h3>API服务使用示例</h3>
    
    <el-form @submit.prevent="sendChatRequest">
      <el-form-item label="模型">
        <el-input v-model="config.model" placeholder="gpt-3.5-turbo" />
      </el-form-item>
      
      <el-form-item label="消息">
        <el-input 
          v-model="userMessage" 
          type="textarea" 
          placeholder="输入您的消息..."
          :rows="3"
        />
      </el-form-item>
      
      <el-form-item label="授权">
        <el-input 
          v-model="config.authorization" 
          placeholder="Bearer your-token"
          type="password"
        />
      </el-form-item>
      
      <el-form-item>
        <el-button 
          type="primary" 
          native-type="submit"
          :loading="loading"
        >
          发送请求
        </el-button>
        <el-button @click="clearHistory">清除历史</el-button>
      </el-form-item>
    </el-form>
    
    <!-- 请求详情 -->
    <div v-if="lastRequest" class="request-info">
      <h4>最后请求</h4>
      <RequestPanel 
        :request="lastRequest"
        :request-size="lastRequestSize"
      />
    </div>
    
    <!-- 响应详情 -->
    <div v-if="lastResponse" class="response-info">
      <h4>最后响应</h4>
      <ResponsePanel 
        :response="lastResponse"
        :loading="loading"
        :error="error"
        :request-size="lastRequestSize"
        :method="lastRequest?.method"
        :endpoint="lastRequest?.endpoint"
        @performance-metrics="onPerformanceMetrics"
      />
    </div>
    
    <!-- 性能监控 -->
    <div class="performance-info">
      <PerformanceMonitor 
        ref="performanceMonitor"
        :current-metrics="null"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { sendServiceRequest, getRequestSize } from '@/api/playground'
import RequestPanel from './RequestPanel.vue'
import ResponsePanel from './ResponsePanel.vue'
import PerformanceMonitor from './PerformanceMonitor.vue'
import type { 
  ChatRequestConfig, 
  PlaygroundRequest, 
  PlaygroundResponse 
} from '../types/playground'

// 响应式数据
const loading = ref(false)
const error = ref<string | null>(null)
const userMessage = ref('')
const lastRequest = ref<PlaygroundRequest | null>(null)
const lastResponse = ref<PlaygroundResponse | null>(null)
const lastRequestSize = ref(0)
const performanceMonitor = ref<InstanceType<typeof PerformanceMonitor>>()

// 配置
const config = reactive<ChatRequestConfig>({
  model: 'gpt-3.5-turbo',
  messages: [],
  temperature: 0.7,
  maxTokens: 1000,
  authorization: ''
})

// 发送聊天请求
const sendChatRequest = async () => {
  if (!config.model || !userMessage.value) {
    ElMessage.warning('请填写模型和消息')
    return
  }
  
  loading.value = true
  error.value = null
  
  try {
    // 构建消息列表
    const messages = [
      { role: 'user' as const, content: userMessage.value }
    ]
    
    const requestConfig = {
      ...config,
      messages
    }
    
    // 计算请求大小
    const mockRequest: PlaygroundRequest = {
      endpoint: '/api/universal/chat/completions',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(config.authorization && { 'Authorization': config.authorization })
      },
      body: requestConfig
    }
    
    lastRequest.value = mockRequest
    lastRequestSize.value = getRequestSize(mockRequest)
    
    // 发送请求
    const response = await sendServiceRequest('chat', requestConfig)
    
    lastResponse.value = response
    ElMessage.success('请求发送成功')
    
  } catch (err: any) {
    error.value = err.message || '请求失败'
    lastResponse.value = err
    ElMessage.error('请求失败: ' + error.value)
  } finally {
    loading.value = false
  }
}

// 性能指标处理
const onPerformanceMetrics = (metrics: any) => {
  performanceMonitor.value?.addMetrics(metrics)
}

// 清除历史
const clearHistory = () => {
  lastRequest.value = null
  lastResponse.value = null
  error.value = null
  userMessage.value = ''
  performanceMonitor.value?.clearHistory()
  ElMessage.success('历史记录已清除')
}
</script>

<style scoped>
.api-service-example {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.request-info,
.response-info,
.performance-info {
  margin-top: 20px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-bg-color-page);
}

.request-info h4,
.response-info h4 {
  margin: 0 0 16px 0;
  color: var(--el-text-color-primary);
}
</style>