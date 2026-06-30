<template>
  <div class="circuit-breaker-monitoring">
    <!-- 监控控制面板 -->
    <el-card class="control-panel" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">熔断器监控控制</span>
          <div class="control-buttons">
            <el-button
              :type="monitorStatus.paused ? 'success' : 'warning'"
              @click="toggleMonitor"
              :loading="togglingMonitor"
            >
              {{ monitorStatus.paused ? '恢复监控' : '暂停监控' }}
            </el-button>
            <el-button @click="clearHistory" :loading="clearingHistory">
              清空历史
            </el-button>
            <el-dropdown @command="handleExport">
              <el-button type="primary">
                导出 <el-icon class="el-icon--right"><Download /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="json">导出 JSON</el-dropdown-item>
                  <el-dropdown-item command="csv">导出 CSV</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="6">
          <div class="config-item">
            <label>采样率</label>
            <el-slider
              v-model="configForm.sampleRate"
              :min="1"
              :max="100"
              :format-tooltip="formatSampleRate"
              @change="updateSampleRate"
              :disabled="updatingConfig"
            />
          </div>
        </el-col>
        <el-col :span="6">
          <div class="config-item">
            <label>历史记录大小</label>
            <el-input-number
              v-model="configForm.historySize"
              :min="50"
              :max="5000"
              :step="50"
              @change="updateHistorySize"
              :disabled="updatingConfig"
            />
          </div>
        </el-col>
        <el-col :span="12">
          <div class="connection-status">
            <el-tag :type="wsConnected ? 'success' : 'danger'">
              {{ wsConnected ? 'WebSocket 已连接' : 'WebSocket 未连接' }}
            </el-tag>
            <span class="sampled-count">
              已采样: {{ monitorStatus.totalSampledCount }} 条
            </span>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 熔断器状态概览 -->
    <el-card class="status-overview" style="margin-top: 16px" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">熔断器状态概览</span>
          <el-button type="primary" size="small" @click="loadCircuitBreakerStatus">
            刷新状态
          </el-button>
        </div>
      </template>

      <el-row :gutter="16">
        <el-col :span="6" v-for="summary in stateSummary" :key="summary.state">
          <div class="summary-card" :class="`summary-${summary.state.toLowerCase()}`">
            <div class="summary-count">{{ summary.count }}</div>
            <div class="summary-label">{{ summary.state }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 实时事件流 -->
    <el-card class="events-card" style="margin-top: 16px" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">实时熔断器事件</span>
          <el-select v-model="selectedEventType" placeholder="事件类型" clearable style="width: 150px">
            <el-option label="状态变化" value="STATE_CHANGE" />
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILURE" />
          </el-select>
        </div>
      </template>

      <el-table
        :data="filteredEvents"
        stripe
        max-height="400"
        v-loading="loadingEvents"
        class="events-table"
      >
        <el-table-column prop="timestamp" label="时间" width="180">
          <template #default="{ row }">
            {{ formatTimestamp(row.timestamp) }}
          </template>
        </el-table-column>
        <el-table-column prop="instanceId" label="实例ID" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip :content="row.instanceId" placement="top">
              <span class="instance-id">{{ getInstanceShortName(row.instanceId) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="instanceName" label="实例名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="serviceType" label="服务类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.serviceType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="eventType" label="事件类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getEventTypeTagType(row.eventType)" size="small">
              {{ getEventTypeLabel(row.eventType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="previousState" label="原状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.previousState" :type="getStateTagType(row.previousState)" size="small">
              {{ row.previousState }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="currentState" label="新状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.currentState" :type="getStateTagType(row.currentState)" size="small">
              {{ row.currentState }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="failureCount" label="失败次数" width="80" />
        <el-table-column prop="successCount" label="成功次数" width="80" />
        <el-table-column prop="triggerReason" label="触发原因" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.triggerReason || '-' }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import request from '@/utils/request'

interface MonitorStatus {
  enabled: boolean
  paused: boolean
  sampleRate: number
  historySize: number
  totalSampledCount: number
}

interface CircuitBreakerEvent {
  timestamp: string
  instanceId: string
  instanceName: string
  serviceType: string
  eventType: 'STATE_CHANGE' | 'SUCCESS' | 'FAILURE'
  previousState: string | null
  currentState: string | null
  failureCount: number
  successCount: number
  triggerReason: string | null
}

interface CircuitBreakerStatus {
  instanceId: string
  instanceName: string
  serviceType: string
  state: string
  failureCount: number
  successCount: number
}

interface StateSummary {
  state: string
  count: number
}

const apiBaseUrl = '/v1/circuit-breaker-monitor'

const monitorStatus = ref<MonitorStatus>({
  enabled: true,
  paused: false,
  sampleRate: 0.1,
  historySize: 500,
  totalSampledCount: 0
})

const events = ref<CircuitBreakerEvent[]>([])
const circuitBreakerStatuses = ref<CircuitBreakerStatus[]>([])
const selectedEventType = ref<string>('')
const configForm = ref({
  sampleRate: 10,
  historySize: 500
})

const wsConnected = ref(false)
const togglingMonitor = ref(false)
const updatingConfig = ref(false)
const clearingHistory = ref(false)
const loadingEvents = ref(false)
let ws: WebSocket | null = null
let reconnectTimer: number | null = null

const stateSummary = computed<StateSummary[]>(() => {
  const counts: Record<string, number> = {
    CLOSED: 0,
    OPEN: 0,
    HALF_OPEN: 0
  }
  circuitBreakerStatuses.value.forEach(cb => {
    if (counts[cb.state] !== undefined) {
      counts[cb.state]++
    }
  })
  return Object.entries(counts).map(([state, count]) => ({ state, count }))
})

const filteredEvents = computed(() => {
  if (!selectedEventType.value) {
    return events.value.slice(0, 50)
  }
  return events.value
    .filter(e => e.eventType === selectedEventType.value)
    .slice(0, 50)
})

const formatSampleRate = (val: number) => `${val}%`

const formatTimestamp = (timestamp: string) => {
  const date = new Date(timestamp)
  return `${date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })  }.${  String(date.getMilliseconds()).padStart(3, '0')}`
}

const getInstanceShortName = (instanceId: string) => {
  if (!instanceId) return '-'
  const parts = instanceId.split('-')
  return parts.length > 2 ? parts.slice(0, 2).join('-') : instanceId.substring(0, 12)
}

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

const getEventTypeTagType = (eventType: string) => {
  switch (eventType) {
    case 'STATE_CHANGE':
      return 'danger'
    case 'SUCCESS':
      return 'success'
    case 'FAILURE':
      return 'warning'
    default:
      return 'info'
  }
}

const getEventTypeLabel = (eventType: string) => {
  switch (eventType) {
    case 'STATE_CHANGE':
      return '状态变化'
    case 'SUCCESS':
      return '成功'
    case 'FAILURE':
      return '失败'
    default:
      return eventType
  }
}

const loadStatus = async () => {
  try {
    const response = await request.get(`${apiBaseUrl}/status`)
    monitorStatus.value = response.data
    configForm.value.sampleRate = Math.round(monitorStatus.value.sampleRate * 100)
    configForm.value.historySize = monitorStatus.value.historySize
  } catch (error) {
    console.error('Failed to load monitor status:', error)
  }
}

const loadCircuitBreakerStatus = async () => {
  try {
    const response = await request.get('/config/instance/circuit-breaker/states')
    if (response.data?.success) {
      const states = response.data.data
      if (Array.isArray(states)) {
        circuitBreakerStatuses.value = states.map((item: any) => ({
          instanceId: item.instanceId || '-',
          instanceName: item.instanceName || '-',
          serviceType: item.serviceType || '-',
          state: item.state || 'CLOSED',
          failureCount: item.failureCount || 0,
          successCount: item.successCount || 0
        }))
      }
    }
  } catch (error) {
    console.error('Failed to load circuit breaker status:', error)
  }
}

const loadHistory = async () => {
  loadingEvents.value = true
  try {
    const response = await request.get(`${apiBaseUrl}/history`, {
      params: { limit: 200 }
    })
    const allEvents: CircuitBreakerEvent[] = []
    Object.values(response.data).forEach((instanceEvents: any) => {
      allEvents.push(...instanceEvents)
    })
    allEvents.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
    events.value = allEvents
  } catch (error) {
    console.error('Failed to load history:', error)
  } finally {
    loadingEvents.value = false
  }
}

const connectWebSocket = () => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/ws/circuit-breaker-monitor`

  ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    wsConnected.value = true
    console.log('WebSocket connected to circuit breaker monitor')
  }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)

      if (data.type === 'connected') {
        monitorStatus.value = data.status
      } else if (data.type === 'heartbeat') {
        // Heartbeat received
      } else {
        // Circuit breaker event
        const newEvent: CircuitBreakerEvent = data
        events.value.unshift(newEvent)
        if (events.value.length > 100) {
          events.value.pop()
        }

        // 如果是状态变化事件，更新状态列表
        if (newEvent.eventType === 'STATE_CHANGE' && newEvent.currentState) {
          const idx = circuitBreakerStatuses.value.findIndex(
            cb => cb.instanceId === newEvent.instanceId
          )
          if (idx >= 0) {
            circuitBreakerStatuses.value[idx].state = newEvent.currentState
            circuitBreakerStatuses.value[idx].failureCount = newEvent.failureCount
            circuitBreakerStatuses.value[idx].successCount = newEvent.successCount
          }
        }
      }
    } catch (error) {
      console.error('Failed to parse WebSocket message:', error)
    }
  }

  ws.onclose = () => {
    wsConnected.value = false
    console.log('WebSocket disconnected from circuit breaker monitor')
    reconnectTimer = window.setTimeout(() => {
      connectWebSocket()
    }, 3000)
  }

  ws.onerror = (error) => {
    console.error('WebSocket error:', error)
  }
}

const disconnectWebSocket = () => {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (ws) {
    ws.close()
    ws = null
  }
}

const toggleMonitor = async () => {
  togglingMonitor.value = true
  try {
    const action = monitorStatus.value.paused ? 'resume' : 'pause'
    await request.post(`${apiBaseUrl}/${action}`)
    monitorStatus.value.paused = !monitorStatus.value.paused
    ElMessage.success(monitorStatus.value.paused ? '监控已暂停' : '监控已恢复')
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    togglingMonitor.value = false
  }
}

const updateSampleRate = async (value: number) => {
  updatingConfig.value = true
  try {
    await request.put(`${apiBaseUrl}/config/sample-rate`, {
      sampleRate: value / 100
    })
    monitorStatus.value.sampleRate = value / 100
    ElMessage.success(`采样率已更新为 ${value}%`)
  } catch (error) {
    ElMessage.error('更新采样率失败')
    configForm.value.sampleRate = Math.round(monitorStatus.value.sampleRate * 100)
  } finally {
    updatingConfig.value = false
  }
}

const updateHistorySize = async (value: number) => {
  updatingConfig.value = true
  try {
    await request.put(`${apiBaseUrl}/config/history-size`, {
      historySize: value
    })
    monitorStatus.value.historySize = value
    ElMessage.success(`历史记录大小已更新为 ${value}`)
  } catch (error) {
    ElMessage.error('更新历史记录大小失败')
    configForm.value.historySize = monitorStatus.value.historySize
  } finally {
    updatingConfig.value = false
  }
}

const clearHistory = async () => {
  clearingHistory.value = true
  try {
    await request.delete(`${apiBaseUrl}/history`)
    events.value = []
    monitorStatus.value.totalSampledCount = 0
    ElMessage.success('历史记录已清空')
  } catch (error) {
    ElMessage.error('清空历史记录失败')
  } finally {
    clearingHistory.value = false
  }
}

const handleExport = async (command: string) => {
  try {
    const response = await request.get(`${apiBaseUrl}/export/${command}`, {
      params: { limit: 2000 },
      responseType: 'blob'
    })

    const blob = new Blob([response.data], {
      type: command === 'json' ? 'application/json' : 'text/csv'
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `circuit-breaker-history.${command}`
    link.click()
    window.URL.revokeObjectURL(url)

    ElMessage.success(`已导出 ${command.toUpperCase()} 文件`)
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

onMounted(() => {
  loadStatus()
  loadCircuitBreakerStatus()
  loadHistory()
  connectWebSocket()
})

onUnmounted(() => {
  disconnectWebSocket()
})
</script>

<style scoped>
.circuit-breaker-monitoring {
  padding: 24px;
  background: linear-gradient(180deg, #f7f9fc 0%, #ffffff 100%);
  min-height: calc(100vh - 80px);
}

.control-panel {
  margin-bottom: 16px;
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

.control-buttons {
  display: flex;
  gap: 8px;
}

.config-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.config-item label {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-top: 24px;
}

.sampled-count {
  font-size: 14px;
  color: #909399;
}

.status-overview {
  margin-bottom: 16px;
}

.summary-card {
  text-align: center;
  padding: 20px;
  border-radius: 8px;
  background: #f5f7fa;
}

.summary-card.summary-closed {
  background: linear-gradient(135deg, #e8f5e9 0%, #c8e6c9 100%);
}

.summary-card.summary-open {
  background: linear-gradient(135deg, #ffebee 0%, #ffcdd2 100%);
}

.summary-card.summary-half_open {
  background: linear-gradient(135deg, #fff3e0 0%, #ffe0b2 100%);
}

.summary-count {
  font-size: 32px;
  font-weight: bold;
  color: #303133;
}

.summary-label {
  font-size: 14px;
  color: #606266;
  margin-top: 8px;
}

.events-card {
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.06);
  border-radius: 12px;
}

.events-table {
  width: 100%;
}

.instance-id {
  font-family: monospace;
  font-size: 12px;
}
</style>
