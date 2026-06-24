<template>
  <div class="circuit-breaker-monitoring">
    <el-card class="status-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">熔断器实时监控</span>
        </div>
      </template>

      <div class="table-actions">
        <el-button type="primary" @click="refreshStatus">刷新状态</el-button>
        <el-button @click="resetAllCircuitBreakers">重置所有熔断器</el-button>
      </div>

      <el-table :data="circuitBreakerStatus" stripe v-loading="loadingStatus" class="flex-table">
        <el-table-column prop="instanceId" label="实例ID (UUID)" min-width="280" show-overflow-tooltip />
        <el-table-column prop="instanceName" label="实例名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="serviceType" label="服务类型" min-width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.serviceType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="baseUrl" label="Host" min-width="200" show-overflow-tooltip />
        <el-table-column prop="path" label="Path" min-width="150" show-overflow-tooltip />
        <el-table-column prop="state" label="状态" min-width="100" fixed="right">
          <template #default="{ row }">
            <el-tag :type="getStateTagType(row.state)">
              {{ row.state }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failureCount" label="失败次数" min-width="80" />
        <el-table-column prop="successCount" label="成功次数" min-width="80" />
        <el-table-column label="失败率" min-width="100">
          <template #default="{ row }">
            <div v-if="row.failureCount + row.successCount > 0">
              <el-progress
                :percentage="Math.round((row.failureCount / (row.failureCount + row.successCount)) * 100)"
                :color="getFailureRateColor(row.failureCount / (row.failureCount + row.successCount))"
                :stroke-width="8"
              />
              <span class="failure-rate-text">
                {{ (row.failureCount / (row.failureCount + row.successCount) * 100).toFixed(1) }}%
              </span>
            </div>
            <span v-else class="no-data">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="lastFailureTime" label="最后失败时间" min-width="150">
          <template #default="{ row }">
            {{ formatTime(row.lastFailureTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="failureThreshold" label="失败阈值" min-width="80" />
        <el-table-column prop="successThreshold" label="成功阈值" min-width="80" />
        <el-table-column prop="timeout" label="超时(ms)" min-width="80" />
        <el-table-column label="操作" min-width="120" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button size="small" @click="resetCircuitBreaker(row.instanceId)">重置</el-button>
              <el-button size="small" type="warning" @click="forceOpenCircuitBreaker(row.instanceId)">强制打开</el-button>
              <el-button size="small" type="success" @click="forceCloseCircuitBreaker(row.instanceId)">强制关闭</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

interface CircuitBreakerStatus {
  instanceId: string
  dbId?: number
  instanceName: string
  serviceType: string
  baseUrl: string
  path: string
  state: string
  failureCount: number
  successCount: number
  lastFailureTime: number | null
  failureThreshold: number
  successThreshold: number
  timeout: number
  enabled?: boolean
  instanceStatus?: string
  healthStatus?: string
}

const circuitBreakerStatus = ref<CircuitBreakerStatus[]>([])
const loadingStatus = ref(false)

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

const getFailureRateColor = (failureRate: number) => {
  if (failureRate < 0.01) {
    return '#67C23A'
  } else if (failureRate < 0.05) {
    return '#E6A23C'
  } else if (failureRate < 0.10) {
    return '#F56C6C'
  } else {
    return '#F56C6C'
  }
}

const formatTime = (timestamp: number | null) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

const loadCircuitBreakerStatus = async () => {
  loadingStatus.value = true
  try {
    const response = await request.get('/config/instance/circuit-breaker/states')
    if (response.data?.success) {
      const states = response.data.data
      if (Array.isArray(states)) {
        circuitBreakerStatus.value = states.map((item: any) => ({
          instanceId: item.instanceId || '-',
          dbId: item.dbId,
          instanceName: item.instanceName || '-',
          serviceType: item.serviceType || '-',
          baseUrl: item.baseUrl || '-',
          path: item.path || '-',
          state: item.state || 'CLOSED',
          failureCount: item.failureCount || 0,
          successCount: item.successCount || 0,
          lastFailureTime: item.lastFailureTime || null,
          failureThreshold: item.failureThreshold || 5,
          successThreshold: item.successThreshold || 2,
          timeout: item.timeout || 60000,
          enabled: item.enabled,
          instanceStatus: item.instanceStatus,
          healthStatus: item.healthStatus
        }))
      } else {
        circuitBreakerStatus.value = []
      }
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

const refreshStatus = () => {
  loadCircuitBreakerStatus()
  ElMessage.success('已刷新熔断器状态')
}

const resetAllCircuitBreakers = async () => {
  try {
    await ElMessageBox.confirm('确定要重置所有熔断器吗？', '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await request.post('/config/instance/circuit-breaker/force-close')
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
    await request.post('/config/instance/circuit-breaker/reset', {
      instanceId
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
    await request.post('/config/instance/circuit-breaker/force-open', {
      instanceId
    })
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
    await request.post('/config/instance/circuit-breaker/force-close', {
      instanceId
    })
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
})
</script>

<style scoped>
.circuit-breaker-monitoring {
  padding: 24px;
  background: linear-gradient(180deg, #f7f9fc 0%, #ffffff 100%);
  min-height: calc(100vh - 80px);
}

.status-card {
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

.table-actions {
  margin-bottom: 16px;
  display: flex;
  gap: 12px;
}

.flex-table {
  width: 100%;
  table-layout: auto;
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0;
}

.action-buttons .el-button {
  width: 100%;
  margin: 0;
}

.failure-rate-text {
  font-size: 12px;
  color: #606266;
  margin-left: 8px;
}

.no-data {
  color: #909399;
}
</style>
