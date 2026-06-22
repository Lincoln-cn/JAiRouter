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
              <div class="adaptive-switch-container">
                <el-switch v-model="globalConfig.adaptiveThresholdEnabled" />
                <span class="adaptive-hint" v-if="globalConfig.adaptiveThresholdEnabled">
                  <el-tag type="success" size="small">已启用</el-tag>
                  <span class="hint-text">系统将根据失败率自动调整阈值</span>
                </span>
                <span class="adaptive-hint" v-else>
                  <el-tag type="info" size="small">已禁用</el-tag>
                </span>
              </div>
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
          <el-col :span="12">
            <el-form-item label="历史记录保留天数">
              <el-input-number
                v-model="globalConfig.historyRetentionDays"
                :min="1"
                :max="365"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="默认失败阈值">
              <el-input-number v-model="globalConfig.defaultFailureThreshold" :min="1" :max="100" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="默认成功阈值">
              <el-input-number v-model="globalConfig.defaultSuccessThreshold" :min="1" :max="20" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="默认超时(ms)">
              <el-input-number v-model="globalConfig.defaultTimeoutMs" :min="1000" :max="300000" :step="1000" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item>
          <el-button type="primary" @click="saveGlobalConfig" :loading="savingConfig">保存全局配置</el-button>
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

    <el-card class="history-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">熔断器历史记录</span>
          <div class="header-actions">
            <el-button size="small" @click="loadHistory" :loading="loadingHistory">刷新</el-button>
            <el-button size="small" type="danger" @click="cleanupHistory">清理过期记录</el-button>
          </div>
        </div>
      </template>

      <!-- 历史记录统计 -->
      <el-row :gutter="20" style="margin-bottom: 16px">
        <el-col :span="6">
          <el-statistic title="总记录数" :value="historyStats.totalCount" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="今日记录" :value="historyStats.todayCount" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="最近7天" :value="historyStats.weekCount" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="最近30天" :value="historyStats.monthCount" />
        </el-col>
      </el-row>

      <el-table :data="historyRecords" stripe v-loading="loadingHistory" class="flex-table">
        <el-table-column prop="instanceId" label="实例ID" min-width="200" show-overflow-tooltip />
        <el-table-column prop="instanceName" label="实例名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="serviceType" label="服务类型" min-width="80">
          <template #default="{ row }">
            <el-tag size="small">{{ row.serviceType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态变化" min-width="150">
          <template #default="{ row }">
            <el-tag :type="getStateTagType(row.previousState)" size="small">{{ row.previousState }}</el-tag>
            <span style="margin: 0 8px">→</span>
            <el-tag :type="getStateTagType(row.currentState)" size="small">{{ row.currentState }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggerReasonDesc" label="触发原因" min-width="150" />
        <el-table-column prop="failureCount" label="失败次数" min-width="80" />
        <el-table-column prop="successCount" label="成功次数" min-width="80" />
        <el-table-column prop="changedAt" label="变化时间" min-width="150">
          <template #default="{ row }">
            {{ formatDateTime(row.changedAt) }}
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="historyPage"
        v-model:page-size="historyPageSize"
        :total="historyTotal"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @size-change="loadHistory"
        @current-change="loadHistory"
      />
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
  historyRetentionDays: number
  defaultFailureThreshold: number
  defaultSuccessThreshold: number
  defaultTimeoutMs: number
}

interface CircuitBreakerStatus {
  instanceId: string      // UUID
  dbId?: number           // 数据库 ID
  instanceName: string    // 实例名称
  serviceType: string     // 服务类型
  baseUrl: string         // Host
  path: string            // Path
  state: string           // CLOSED/OPEN/HALF_OPEN
  failureCount: number
  successCount: number
  lastFailureTime: number | null
  failureThreshold: number
  successThreshold: number
  timeout: number
  enabled?: boolean       // 熔断器是否启用
  instanceStatus?: string // 实例状态
  healthStatus?: string   // 健康状态
}

interface HistoryRecord {
  id: number
  instanceId: string
  instanceName: string
  serviceType: string
  previousState: string
  currentState: string
  triggerReason: string
  triggerReasonDesc: string
  failureCount: number
  successCount: number
  changedAt: string
}

interface HistoryStats {
  totalCount: number
  todayCount: number
  weekCount: number
  monthCount: number
}

const globalConfig = ref<GlobalConfig>({
  adaptiveThresholdEnabled: false,
  stateSyncIntervalMinutes: 5,
  cleanupIntervalMinutes: 30,
  historyRetentionDays: 30,
  defaultFailureThreshold: 5,
  defaultSuccessThreshold: 2,
  defaultTimeoutMs: 60000
})

const savingConfig = ref(false)
const circuitBreakerStatus = ref<CircuitBreakerStatus[]>([])
const loadingStatus = ref(false)

// 历史记录相关
const historyRecords = ref<HistoryRecord[]>([])
const loadingHistory = ref(false)
const historyPage = ref(1)
const historyPageSize = ref(20)
const historyTotal = ref(0)
const historyStats = ref<HistoryStats>({
  totalCount: 0,
  todayCount: 0,
  weekCount: 0,
  monthCount: 0
})

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
    return '#67C23A' // 绿色 - 低失败率
  } else if (failureRate < 0.05) {
    return '#E6A23C' // 黄色 - 中等失败率
  } else if (failureRate < 0.10) {
    return '#F56C6C' // 橙色 - 较高失败率
  } else {
    return '#F56C6C' // 红色 - 高失败率
  }
}

const formatTime = (timestamp: number | null) => {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

const formatDateTime = (datetime: string | null) => {
  if (!datetime) return '-'
  return new Date(datetime).toLocaleString('zh-CN')
}

// 加载全局配置
const loadGlobalConfig = async () => {
  try {
    const response = await request.get('/config/circuit-breaker/global-config')
    if (response.data?.success) {
      const config = response.data.data
      if (config) {
        globalConfig.value = {
          adaptiveThresholdEnabled: config.adaptiveThresholdEnabled || false,
          stateSyncIntervalMinutes: config.stateSyncIntervalMinutes || 5,
          cleanupIntervalMinutes: config.cleanupIntervalMinutes || 30,
          historyRetentionDays: config.historyRetentionDays || 30,
          defaultFailureThreshold: config.defaultFailureThreshold || 5,
          defaultSuccessThreshold: config.defaultSuccessThreshold || 2,
          defaultTimeoutMs: config.defaultTimeoutMs || 60000
        }
      }
    }
  } catch (error: any) {
    console.error('Failed to load global config:', error)
  }
}

const saveGlobalConfig = async () => {
  savingConfig.value = true
  try {
    const response = await request.put('/config/circuit-breaker/global-config', globalConfig.value)
    if (response.data?.success) {
      ElMessage.success('全局配置保存成功')
    } else {
      ElMessage.error(response.data?.message || '保存全局配置失败')
    }
  } catch (error: any) {
    console.error('Failed to save global config:', error)
    ElMessage.error('保存全局配置失败')
  } finally {
    savingConfig.value = false
  }
}

const resetGlobalConfig = async () => {
  try {
    const response = await request.post('/config/circuit-breaker/global-config/reset')
    if (response.data?.success) {
      const config = response.data.data
      if (config) {
        globalConfig.value = config
      }
      ElMessage.info('已重置为默认配置')
    }
  } catch (error: any) {
    console.error('Failed to reset global config:', error)
    ElMessage.error('重置全局配置失败')
  }
}

// 加载历史记录
const loadHistory = async () => {
  loadingHistory.value = true
  try {
    const response = await request.get('/config/circuit-breaker/history', {
      params: {
        page: historyPage.value - 1,
        size: historyPageSize.value
      }
    })
    if (response.data?.success) {
      const pageData = response.data.data
      if (pageData) {
        historyRecords.value = pageData.content || []
        historyTotal.value = pageData.totalElements || 0
      }
    }
  } catch (error: any) {
    console.error('Failed to load history:', error)
    ElMessage.error('加载历史记录失败')
  } finally {
    loadingHistory.value = false
  }
}

// 加载历史记录统计
const loadHistoryStats = async () => {
  try {
    const response = await request.get('/config/circuit-breaker/history/stats')
    if (response.data?.success) {
      historyStats.value = response.data.data
    }
  } catch (error: any) {
    console.error('Failed to load history stats:', error)
  }
}

// 清理过期历史记录
const cleanupHistory = async () => {
  try {
    await ElMessageBox.confirm('确定要清理过期的历史记录吗？', '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const response = await request.delete('/config/circuit-breaker/history/cleanup')
    if (response.data?.success) {
      ElMessage.success(response.data.message || '清理完成')
      loadHistory()
      loadHistoryStats()
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('Failed to cleanup history:', error)
      ElMessage.error('清理历史记录失败')
    }
  }
}

const loadCircuitBreakerStatus = async () => {
  loadingStatus.value = true
  try {
    // 使用新的详细状态 API
    const response = await request.get('/config/instance/circuit-breaker/states')
    if (response.data?.success) {
      const states = response.data.data
      if (Array.isArray(states)) {
        // 新的 API 返回 CircuitBreakerStatusDTO 列表
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
    // 使用实例熔断器重置 API（不传 instanceId 则重置所有）
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
  loadGlobalConfig()
  loadCircuitBreakerStatus()
  loadHistory()
  loadHistoryStats()
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

.header-actions {
  display: flex;
  gap: 8px;
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

/* 自适应开关容器 */
.adaptive-switch-container {
  display: flex;
  align-items: center;
  gap: 12px;
}

.adaptive-hint {
  display: flex;
  align-items: center;
  gap: 8px;
}

.hint-text {
  color: #606266;
  font-size: 12px;
}

/* 失败率显示 */
.failure-rate-text {
  font-size: 12px;
  color: #606266;
  margin-left: 8px;
}

.no-data {
  color: #909399;
}
</style>