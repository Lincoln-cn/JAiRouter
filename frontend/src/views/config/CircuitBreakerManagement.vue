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
              <el-switch v-model="globalConfig.adaptiveThresholdEnabled" disabled />
              <el-tag type="info" size="small" style="margin-left: 10px">待实现</el-tag>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态同步间隔(分钟)">
              <el-input-number
                v-model="globalConfig.stateSyncIntervalMinutes"
                :min="1"
                :max="60"
                disabled
              />
              <el-tag type="info" size="small" style="margin-left: 10px">待实现</el-tag>
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
                disabled
              />
              <el-tag type="info" size="small" style="margin-left: 10px">待实现</el-tag>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item>
          <el-button type="primary" @click="saveGlobalConfig" disabled>保存全局配置</el-button>
          <el-button @click="resetGlobalConfig" disabled>重置</el-button>
          <el-tag type="warning" size="small" style="margin-left: 10px">全局配置功能待后端实现</el-tag>
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

      <el-table :data="circuitBreakerStatus" stripe v-loading="loadingStatus" class="flex-table">
        <el-table-column prop="instanceId" label="实例ID" min-width="150" />
        <el-table-column prop="state" label="状态" min-width="100">
          <template #default="{ row }">
            <el-tag :type="getStateTagType(row.state)">
              {{ row.state }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="failureCount" label="失败次数" min-width="80" />
        <el-table-column prop="successCount" label="成功次数" min-width="80" />
        <el-table-column prop="lastFailureTime" label="最后失败时间" min-width="150">
          <template #default="{ row }">
            {{ formatTime(row.lastFailureTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="failureThreshold" label="失败阈值" min-width="80" />
        <el-table-column prop="successThreshold" label="成功阈值" min-width="80" />
        <el-table-column prop="timeout" label="超时(ms)" min-width="80" />
        <el-table-column label="操作" min-width="120">
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

    <el-card class="history-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">熔断器历史记录</span>
          <el-tag type="info" size="small">功能待后端实现</el-tag>
        </div>
      </template>

      <el-empty description="历史记录功能待后端实现" :image-size="80">
        <template #description>
          <p style="color: #909399; font-size: 14px;">
            熔断器状态变化历史记录需要后端新增 API 支持
          </p>
        </template>
      </el-empty>
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

const globalConfig = ref<GlobalConfig>({
  adaptiveThresholdEnabled: true,
  stateSyncIntervalMinutes: 5,
  cleanupIntervalMinutes: 30
})

const circuitBreakerStatus = ref<CircuitBreakerStatus[]>([])
const loadingStatus = ref(false)

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
    // 获取实例级熔断器状态
    const response = await request.get('/config/instance/circuit-breaker/states')
    if (response.data?.success) {
      const states = response.data.data
      // 将状态映射转换为状态列表
      const statusList: CircuitBreakerStatus[] = []
      if (states && typeof states === 'object') {
        Object.entries(states).forEach(([instanceId, state]) => {
          statusList.push({
            instanceId,
            state: state as string,
            failureCount: '-',
            successCount: '-',
            lastFailureTime: null,
            failureThreshold: '-',
            successThreshold: '-',
            timeout: '-'
          })
        })
      }
      
      // 如果没有实例熔断器，显示监控熔断器状态
      if (statusList.length === 0) {
        const monitoringResponse = await request.get(`${apiBaseUrl}/stats`)
        if (monitoringResponse.data?.success) {
          const stats = monitoringResponse.data.data
          statusList.push({
            instanceId: 'monitoring-system',
            state: stats.state || 'CLOSED',
            failureCount: stats.failureCount || 0,
            successCount: stats.successCount || 0,
            lastFailureTime: null,
            failureThreshold: '-',
            successThreshold: '-',
            timeout: '-'
          })
        }
      }
      
      circuitBreakerStatus.value = statusList
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
    // 调用实例级熔断器重置 API
    await request.post(`/config/instance/circuit-breaker/reset`, {
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

/* 弹性表格样式 */
.flex-table {
  width: 100%;
  table-layout: auto;
}

/* 操作按钮垂直排列 */
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
</style>