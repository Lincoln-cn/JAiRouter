<template>
  <div class="adapter-test-panel">
    <el-form :model="form" label-width="100px" size="default">
      <el-form-item label="测试类型">
        <el-radio-group v-model="form.testType">
          <el-radio-button value="PING">连通性测试</el-radio-button>
          <el-radio-button value="CHAT">对话测试</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="form.testType === 'CHAT'" label="测试模型">
        <el-input v-model="form.model" placeholder="输入模型名称，如 deepseek-chat" />
      </el-form-item>

      <el-form-item v-if="showApiKey" label="API Key">
        <el-input
          v-model="form.apiKey"
          type="password"
          show-password
          placeholder="输入用于测试的 API Key（不会保存）"
        />
      </el-form-item>

      <el-form-item label="Base URL">
        <el-input v-model="form.baseUrl" placeholder="API 地址，留空使用适配器配置" />
      </el-form-item>

      <el-form-item>
        <el-button
          type="primary"
          :loading="testing"
          :disabled="testing"
          @click="handleTest"
        >
          <el-icon v-if="!testing"><Connection /></el-icon>
          <span>{{ testing ? '测试中...' : '测试连接' }}</span>
        </el-button>
      </el-form-item>
    </el-form>

    <!-- 测试结果 -->
    <div v-if="result" class="test-result" :class="result.success ? 'success' : 'error'">
      <div class="result-header">
        <el-icon :class="result.success ? 'icon-success' : 'icon-error'">
          <CircleCheck v-if="result.success" />
          <CircleClose v-else />
        </el-icon>
        <span class="result-status">{{ result.success ? '连接成功' : '连接失败' }}</span>
        <span v-if="result.latencyMs > 0" class="result-latency">
          延迟 {{ result.latencyMs }}ms
        </span>
      </div>
      <div class="result-message">{{ result.message }}</div>
      <div v-if="result.details?.models" class="result-details">
        <div class="detail-title">可用模型：</div>
        <el-tag
          v-for="m in result.details.models"
          :key="m"
          size="small"
          class="model-tag"
        >
          {{ m }}
        </el-tag>
      </div>
      <div v-if="result.details?.responsePreview" class="result-preview">
        <div class="detail-title">响应预览：</div>
        <pre class="preview-text">{{ result.details.responsePreview }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Connection, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import {
  testAdapter,
  testAdapterConfig,
  type AdapterTestResult
} from '@/api/adapter'

const props = defineProps<{
  adapterName?: string
  showApiKey?: boolean
}>()

const form = reactive({
  testType: 'PING' as 'PING' | 'CHAT',
  model: '',
  apiKey: '',
  baseUrl: ''
})

const testing = ref(false)
const result = ref<AdapterTestResult | null>(null)

const handleTest = async () => {
  if (form.testType === 'CHAT' && !form.model) {
    ElMessage.warning('请输入测试模型名称')
    return
  }

  testing.value = true
  result.value = null
  try {
    const requestData = {
      testType: form.testType,
      apiKey: form.apiKey || undefined,
      model: form.testType === 'CHAT' ? form.model : undefined,
      baseUrl: form.baseUrl || undefined
    }

    let res
    if (props.adapterName) {
      res = await testAdapter(props.adapterName, requestData)
    } else {
      res = await testAdapterConfig({
        ...requestData,
        type: 'openai-compatible',
        baseUrl: form.baseUrl || 'http://localhost:8080',
        authHeaderName: 'Authorization',
        authHeaderPrefix: 'Bearer '
      })
    }

    if (res.data?.success && res.data.data) {
      result.value = res.data.data
    } else {
      ElMessage.error(res.data?.message || '测试请求失败')
    }
  } catch (e: any) {
    ElMessage.error('测试失败: ' + (e.message || ''))
  } finally {
    testing.value = false
  }
}

defineExpose({
  getResult: () => result.value,
  reset: () => {
    result.value = null
    form.testType = 'PING'
    form.model = ''
  }
})
</script>

<style scoped>
.adapter-test-panel {
  padding: 8px 0;
}

.test-result {
  margin-top: 16px;
  padding: 12px 16px;
  border-radius: 8px;
}

.test-result.success {
  background: #f0f9eb;
  border: 1px solid #e1f3d8;
}

.test-result.error {
  background: #fef0f0;
  border: 1px solid #fde2e2;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.icon-success {
  color: #67c23a;
  font-size: 18px;
}

.icon-error {
  color: #f56c6c;
  font-size: 18px;
}

.result-status {
  font-weight: 600;
  font-size: 14px;
}

.result-latency {
  font-size: 12px;
  color: #909399;
}

.result-message {
  font-size: 13px;
  color: #606266;
}

.result-details,
.result-preview {
  margin-top: 8px;
}

.detail-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.model-tag {
  margin: 0 4px 4px 0;
}

.preview-text {
  font-size: 12px;
  color: #606266;
  background: #fff;
  border-radius: 4px;
  padding: 8px;
  max-height: 160px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
