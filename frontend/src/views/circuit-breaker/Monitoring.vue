<template>
  <div class="circuit-breaker-monitoring">
    <!-- 监控控制面板 -->
    <el-card class="control-panel" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('circuitBreaker.monitoring.controlTitle') }}</span>
          <div class="control-buttons">
            <el-button
              :type="monitorStatus.paused ? 'success' : 'warning'"
              @click="toggleMonitor"
              :loading="togglingMonitor"
            >
              {{ monitorStatus.paused ? t('circuitBreaker.monitoring.resumeMonitoring') : t('circuitBreaker.monitoring.pauseMonitoring') }}
            </el-button>
            <el-button @click="clearHistory" :loading="clearingHistory">
              {{ t('circuitBreaker.monitoring.clearHistory') }}
            </el-button>
            <el-button
              type="danger"
              @click="resetAllCircuitBreakersHandler"
              :loading="resettingCbs"
            >
              {{ t('circuitBreaker.monitoring.clearAllCircuitBreakers') }}
            </el-button>
            <el-dropdown @command="handleExport">
              <el-button type="primary">
                {{ t('circuitBreaker.monitoring.export') }} <el-icon class="el-icon--right"><Download /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="json">{{ t('circuitBreaker.monitoring.exportJson') }}</el-dropdown-item>
                  <el-dropdown-item command="csv">{{ t('circuitBreaker.monitoring.exportCsv') }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="6">
          <div class="config-item">
            <label>{{ t('circuitBreaker.monitoring.sampleRate') }}</label>
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
            <label>{{ t('circuitBreaker.monitoring.historySize') }}</label>
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
              {{ wsConnected ? t('circuitBreaker.monitoring.wsConnected') : t('circuitBreaker.monitoring.wsDisconnected') }}
            </el-tag>
            <span class="sampled-count">
              {{ t('circuitBreaker.monitoring.sampledCount', { count: monitorStatus.totalSampledCount }) }}
            </span>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 熔断器状态概览 -->
    <el-card class="status-overview" style="margin-top: 16px" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('circuitBreaker.monitoring.statusOverviewTitle') }}</span>
          <el-button type="primary" size="small" @click="loadCircuitBreakerStatus">
            {{ t('circuitBreaker.monitoring.refreshStatus') }}
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

      <!-- 按实例熔断器状态 & 重置 -->
      <el-table
        v-if="circuitBreakerStatuses.length > 0"
        :data="circuitBreakerStatuses"
        stripe
        size="small"
        style="margin-top: 16px"
      >
        <el-table-column prop="instanceId" :label="t('circuitBreaker.monitoring.columns.instanceId')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="serviceType" :label="t('circuitBreaker.monitoring.columns.serviceType')" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.serviceType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="state" :label="t('circuitBreaker.monitoring.columns.state')" width="120">
          <template #default="{ row }">
            <el-tag :type="getStateTagType(row.state)" size="small">
              {{ row.state }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('circuitBreaker.monitoring.columns.actions')" width="100" align="center">
          <template #default="{ row }">
            <el-button
              type="warning"
              size="small"
              :icon="RefreshRight"
              @click="resetSingleCircuitBreaker(row)"
            >
              {{ t('circuitBreaker.monitoring.reset') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 实时事件流 -->
    <el-card class="events-card" style="margin-top: 16px" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('circuitBreaker.monitoring.eventsTitle') }}</span>
          <el-select v-model="selectedEventType" :placeholder="t('circuitBreaker.monitoring.eventTypePlaceholder')" clearable style="width: 150px">
            <el-option :label="t('circuitBreaker.monitoring.eventTypes.stateChange')" value="STATE_CHANGE" />
            <el-option :label="t('circuitBreaker.monitoring.eventTypes.success')" value="SUCCESS" />
            <el-option :label="t('circuitBreaker.monitoring.eventTypes.failure')" value="FAILURE" />
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
        <el-table-column prop="timestamp" :label="t('circuitBreaker.monitoring.columns.time')" width="180">
          <template #default="{ row }">
            {{ formatTimestamp(row.timestamp) }}
          </template>
        </el-table-column>
        <el-table-column prop="instanceId" :label="t('circuitBreaker.monitoring.columns.instanceIdCompact')" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip :content="row.instanceId" placement="top">
              <span class="instance-id">{{ getInstanceShortName(row.instanceId) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="instanceName" :label="t('circuitBreaker.monitoring.columns.instanceName')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="serviceType" :label="t('circuitBreaker.monitoring.columns.serviceType')" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.serviceType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="eventType" :label="t('circuitBreaker.monitoring.columns.eventType')" width="120">
          <template #default="{ row }">
            <el-tag :type="getEventTypeTagType(row.eventType)" size="small">
              {{ getEventTypeLabel(row.eventType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="previousState" :label="t('circuitBreaker.monitoring.columns.previousState')" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.previousState" :type="getStateTagType(row.previousState)" size="small">
              {{ row.previousState }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="currentState" :label="t('circuitBreaker.monitoring.columns.currentState')" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.currentState" :type="getStateTagType(row.currentState)" size="small">
              {{ row.currentState }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="failureCount" :label="t('circuitBreaker.monitoring.columns.failureCount')" width="80" />
        <el-table-column prop="successCount" :label="t('circuitBreaker.monitoring.columns.successCount')" width="80" />
        <el-table-column prop="triggerReason" :label="t('circuitBreaker.monitoring.columns.triggerReason')" min-width="120" show-overflow-tooltip>
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
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, RefreshRight } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { resetCircuitBreakerById, clearAllCircuitBreakers } from '@/api/instance'

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
  baseUrl?: string
}

interface StateSummary {
  state: string
  count: number
}

const { t } = useI18n()

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
const resettingCbs = ref(false)
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
      return t('circuitBreaker.monitoring.eventTypes.stateChange')
    case 'SUCCESS':
      return t('circuitBreaker.monitoring.eventTypes.success')
    case 'FAILURE':
      return t('circuitBreaker.monitoring.eventTypes.failure')
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
      const raw = response.data.data
      const parsed: CircuitBreakerStatus[] = []
      // Backend returns Map<string, string> — handle as object entries
      if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
        for (const [key, state] of Object.entries(raw as Record<string, string>)) {
          // Key format: "<instanceId>-<baseUrl>" or plain instanceId
          const dashIdx = key.indexOf('-')
          const instanceId = dashIdx > 0 ? key.substring(0, dashIdx) : key
          const baseUrl = dashIdx > 0 ? key.substring(dashIdx + 1) : undefined
          parsed.push({
            instanceId,
            instanceName: baseUrl || '-',
            serviceType: '-',
            state: (state as string) || 'CLOSED',
            failureCount: 0,
            successCount: 0,
            baseUrl
          })
        }
      } else if (Array.isArray(raw)) {
        // Fallback: if backend ever returns an array
        raw.forEach((item: any) => {
          parsed.push({
            instanceId: item.instanceId || '-',
            instanceName: item.instanceName || item.baseUrl || '-',
            serviceType: item.serviceType || '-',
            state: item.state || 'CLOSED',
            failureCount: item.failureCount || 0,
            successCount: item.successCount || 0,
            baseUrl: item.baseUrl
          })
        })
      }
      circuitBreakerStatuses.value = parsed
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
    ElMessage.success(monitorStatus.value.paused ? t('circuitBreaker.monitoring.messages.paused') : t('circuitBreaker.monitoring.messages.resumed'))
  } catch (error) {
    ElMessage.error(t('circuitBreaker.monitoring.messages.operationFailed'))
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
    ElMessage.success(t('circuitBreaker.monitoring.messages.sampleRateUpdated', { value }))
  } catch (error) {
    ElMessage.error(t('circuitBreaker.monitoring.messages.sampleRateUpdateFailed'))
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
    ElMessage.success(t('circuitBreaker.monitoring.messages.historySizeUpdated', { value }))
  } catch (error) {
    ElMessage.error(t('circuitBreaker.monitoring.messages.historySizeUpdateFailed'))
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
    ElMessage.success(t('circuitBreaker.monitoring.messages.historyCleared'))
  } catch (error) {
    ElMessage.error(t('circuitBreaker.monitoring.messages.clearHistoryFailed'))
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

    ElMessage.success(t('circuitBreaker.monitoring.messages.exportSuccess', { format: command.toUpperCase() }))
  } catch (error) {
    ElMessage.error(t('circuitBreaker.monitoring.messages.exportFailed'))
  }
}

const resetSingleCircuitBreaker = (row: CircuitBreakerStatus) => {
  ElMessageBox.confirm(
    t('circuitBreaker.monitoring.confirmations.resetSingleMessage', { name: row.instanceName || row.instanceId }),
    t('circuitBreaker.monitoring.confirmations.resetSingleTitle'),
    { confirmButtonText: t('circuitBreaker.monitoring.confirmations.confirmReset'), cancelButtonText: t('circuitBreaker.monitoring.confirmations.cancel'), type: 'warning' }
  ).then(async () => {
    try {
      await resetCircuitBreakerById(row.instanceId)
      ElMessage.success(t('circuitBreaker.monitoring.messages.stateReset'))
      await loadCircuitBreakerStatus()
      await loadHistory()
    } catch (error) {
      ElMessage.error(t('circuitBreaker.monitoring.messages.resetFailed'))
    }
  }).catch(() => { /* cancelled */ })
}

const resetAllCircuitBreakersHandler = () => {
  ElMessageBox.confirm(
    t('circuitBreaker.monitoring.confirmations.resetAllMessage'),
    t('circuitBreaker.monitoring.confirmations.resetAllTitle'),
    { confirmButtonText: t('circuitBreaker.monitoring.confirmations.confirmClear'), cancelButtonText: t('circuitBreaker.monitoring.confirmations.cancel'), type: 'warning' }
  ).then(async () => {
    resettingCbs.value = true
    try {
      await clearAllCircuitBreakers()
      ElMessage.success(t('circuitBreaker.monitoring.messages.allCleared'))
      await loadCircuitBreakerStatus()
      await loadHistory()
    } catch (error) {
      ElMessage.error(t('circuitBreaker.monitoring.messages.clearAllFailed'))
    } finally {
      resettingCbs.value = false
    }
  }).catch(() => { /* cancelled */ })
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
  background: var(--ja-main-bg-gradient);
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
  color: var(--ja-text-primary);
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
  color: var(--ja-text-regular);
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
  color: var(--ja-text-secondary);
}

.status-overview {
  margin-bottom: 16px;
}

.summary-card {
  text-align: center;
  padding: 20px;
  border-radius: 8px;
  background: var(--ja-primary-light-9, #f5f7fa);
}

.summary-card.summary-closed {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.summary-card.summary-open {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.summary-card.summary-half_open {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
}

.summary-card.summary-closed .summary-count,
.summary-card.summary-open .summary-count,
.summary-card.summary-half_open .summary-count,
.summary-card.summary-closed .summary-label,
.summary-card.summary-open .summary-label,
.summary-card.summary-half_open .summary-label {
  color: inherit;
}

.summary-count {
  font-size: 32px;
  font-weight: bold;
  color: var(--ja-text-primary);
}

.summary-label {
  font-size: 14px;
  color: var(--ja-text-regular);
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
