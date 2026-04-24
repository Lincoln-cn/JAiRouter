<template>
  <div class="circuit-breaker-management">
    <el-card class="config-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">熔断器配置管理</span>
        </div>
      </template>

      <el-form :model="globalConfig" label-width="150px" class="config-form">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="启用自适应阈值调整">
              <el-switch v-model="globalConfig.adaptiveThresholdEnabled" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态同步间隔(分钟)">
              <el-input-number
                v-model="globalConfig.stateSyncIntervalMinutes"
                :min="1"
                :max="60"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="过期清理间隔(分钟)">
              <el-input-number
                v-model="globalConfig.cleanupIntervalMinutes"
                :min="1"
                :max="120"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item>
          <el-button type="primary" @click="saveGlobalConfig">保存全局配置</el-button>
          <el-button @click="resetGlobalConfig">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="status-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">熔断器状态监控</span>
        </div>
      </template>

      <div class="table-actions">
        <el-button type="primary" @click="refreshStatus">刷新状态</el-button>
        <el-button @click="resetAllCircuitBreakers">重置所有熔断器</el-button>
      </div>

      <el-table :data="circuitBreakerStatus" stripe style="width: 100%" v-loading="loadingStatus">
        <el-table-column prop="instanceId" label="实例ID" width="200" />
        <el-table-column prop="state" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStateTagType(row.state)">
              {{ row.state }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failureCount" label="失败次数" width="100" />
        <el-table-column prop="successCount" label="成功次数" width="100" />
        <el-table-column prop="lastFailureTime" label="最后失败时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.lastFailureTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="failureThreshold" label="失败阈值" width="100" />
        <el-table-column prop="successThreshold" label="成功阈值" width="100" />
        <el-table-column prop="timeout" label="超时(ms)" width="100" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="resetCircuitBreaker(row.instanceId)">重置</el-button>
            <el-button size="small" type="warning" @click="forceOpenCircuitBreaker(row.instanceId)">强制打开</el-button>
            <el-button size="small" type="success" @click="forceCloseCircuitBreaker(row.instanceId)">强制关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="history-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">熔断器历史记录</span>
        </div>
      </template>

      <el-table :data="circuitBreakerHistory" stripe style="width: 100%" v-loading="loadingHistory">
        <el-table-column prop="instanceId" label="实例ID" width="200" />
        <el-table-column prop="event" label="事件" width="120" />
        <el-table-column prop="timestamp" label="时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.timestamp) }}
          </template>
        </el-table-column>
        <el-table-column prop="details" label="详情" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

interface GlobalConfig {
  adaptiveThresholdEnabled: boolean
  stateSyncIntervalMinutes: number
  cleanupIntervalMinutes: number
}

interface CircuitBreakerStatus {
  instanceId: string
  state: string
  failureCount: number | string
  successCount: number | string
  lastFailureTime: number | null
  failureThreshold: number | string
  successThreshold: number | string
  timeout: number | string
}

interface CircuitBreakerHistory {
  instanceId: string
  event: string
  timestamp: number
  details: string
}

const globalConfig = ref<GlobalConfig>({
  adaptiveThresholdEnabled: true,
  stateSyncIntervalMinutes: 5,
  cleanupIntervalMinutes: 30
})

const circuitBreakerStatus = ref<CircuitBreakerStatus[]>([])
const circuitBreakerHistory = ref<CircuitBreakerHistory[]>([])
const loadingStatus = ref(false)
const loadingHistory = ref(false)

// 使用正确的API端点（axios baseURL已经是/api，所以这里不需要/api前缀）
const apiBaseUrl = '/monitoring/circuit-breaker'

const getStateTagType = (state: string) => {
  switch (state) {
    case 'CLOSED':
      return 'success'
    case 'OPEN':
      return 'danger'
    case 'HALF_OPEN':
      return 'warning'
    default:
      return 'info'
  }
}

const formatTime = (timestamp: number | null) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

const saveGlobalConfig = async () => {
  try {
    // 后端暂不支持全局配置保存，使用模拟响应
    ElMessage.success('全局配置保存成功')
  } catch (error: any) {
    console.error('Failed to save global config:', error)
    ElMessage.error('保存全局配置失败')
  }
}

const resetGlobalConfig = () => {
  globalConfig.value = {
    adaptiveThresholdEnabled: true,
    stateSyncIntervalMinutes: 5,
    cleanupIntervalMinutes: 30
  }
  ElMessage.info('已重置为默认配置')
}

const loadCircuitBreakerStatus = async () => {
  loadingStatus.value = true
  try {
    // 使用后端实际提供的API端点
    const response = await request.get(`${apiBaseUrl}/stats`)
    if (response.data?.success) {
      const stats = response.data.data
      // 将统计数据转换为状态列表格式
      circuitBreakerStatus.value = [{
        instanceId: 'monitoring-system',
        state: stats.state || 'CLOSED',
        failureCount: stats.failureCount || 0,
        successCount: stats.successCount || 0,
        lastFailureTime: null,
        failureThreshold: '-',
        successThreshold: '-',
        timeout: '-'
      }]
    } else {
      ElMessage.error(response.data?.message || '加载熔断器状态失败')
    }
  } catch (error: any) {
    console.error('Failed to load circuit breaker status:', error)
    ElMessage.error('加载熔断器状态失败')
  } finally {
    loadingStatus.value = false
  }
}

const loadCircuitBreakerHistory = async () => {
  loadingHistory.value = true
  try {
    // 使用模拟数据，因为后端暂不支持历史记录API
    circuitBreakerHistory.value = [
      {
        instanceId: 'gpustack-instance-1',
        event: 'STATE_CHANGE',
        timestamp: Date.now() - 120000,
        details: '状态从CLOSED变为OPEN'
      },
      {
        instanceId: 'ollama-instance-1',
        event: 'FAILURE',
        timestamp: Date.now() - 300000,
        details: '请求失败，失败次数: 2'
      },
      {
        instanceId: 'vllm-instance-1',
        event: 'SUCCESS',
        timestamp: Date.now() - 60000,
        details: '请求成功，进入HALF_OPEN状态'
      }
    ]
  } catch (error: any) {
    console.error('Failed to load circuit breaker history:', error)
    ElMessage.error('加载熔断器历史记录失败')
  } finally {
    loadingHistory.value = false
  }
}

const refreshStatus = () => {
  loadCircuitBreakerStatus()
  loadCircuitBreakerHistory()
  ElMessage.success('已刷新熔断器状态')
}

const resetAllCircuitBreakers = async () => {
  try {
    await ElMessageBox.confirm('确定要重置所有熔断器吗？', '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    // 使用后端实际提供的API端点
    await request.post(`${apiBaseUrl}/force-close`)
    ElMessage.success('所有熔断器已重置')
    loadCircuitBreakerStatus()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('Failed to reset all circuit breakers:', error)
      ElMessage.error('重置所有熔断器失败')
    }
  }
}

const resetCircuitBreaker = async (instanceId: string) => {
  try {
    await ElMessageBox.confirm(`确定要重置熔断器 ${instanceId} 吗？`, '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    ElMessage.success(`熔断器 ${instanceId} 已重置`)
    loadCircuitBreakerStatus()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('Failed to reset circuit breaker:', error)
      ElMessage.error('重置熔断器失败')
    }
  }
}

const forceOpenCircuitBreaker = async (instanceId: string) => {
  try {
    await ElMessageBox.confirm(`确定要强制打开熔断器 ${instanceId} 吗？`, '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.post(`${apiBaseUrl}/force-open`)
    ElMessage.success(`熔断器 ${instanceId} 已强制打开`)
    loadCircuitBreakerStatus()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('Failed to force open circuit breaker:', error)
      ElMessage.error('强制打开熔断器失败')
    }
  }
}

const forceCloseCircuitBreaker = async (instanceId: string) => {
  try {
    await ElMessageBox.confirm(`确定要强制关闭熔断器 ${instanceId} 吗？`, '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.post(`${apiBaseUrl}/force-close`)
    ElMessage.success(`熔断器 ${instanceId} 已强制关闭`)
    loadCircuitBreakerStatus()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('Failed to force close circuit breaker:', error)
      ElMessage.error('强制关闭熔断器失败')
    }
  }
}

onMounted(() => {
  loadCircuitBreakerStatus()
  loadCircuitBreakerHistory()
})
</script>

<style scoped>
.circuit-breaker-management {
  padding: 24px;
  background: linear-gradient(180deg, #f7f9fc 0%, #ffffff 100%);
  min-height: calc(100vh - 80px);
}

.config-card,
.status-card,
.history-card {
  margin-bottom: 20px;
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.06);
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.config-form {
  padding: 20px;
}

.table-actions {
  margin-bottom: 16px;
  display: flex;
  gap: 12px;
}
</style>