<template>
  <PageSkeleton :title="t('circuitBreaker.monitoring.pageTitle')">
    <template #actions>
      <el-button
        v-if="canWrite"
        :type="monitorStatus.paused ? 'success' : 'warning'"
        @click="toggleMonitor"
        :loading="togglingMonitor"
      >
        {{ monitorStatus.paused ? t('circuitBreaker.monitoring.resumeMonitoring') : t('circuitBreaker.monitoring.pauseMonitoring') }}
      </el-button>
      <el-button v-if="canWrite" @click="clearHistory" :loading="clearingHistory">
        {{ t('circuitBreaker.monitoring.clearHistory') }}
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
      <el-button type="primary" plain @click="router.push({ name: 'circuit-breaker-global-config' })">
        {{ t('circuitBreaker.monitoring.actions.globalConfig') }}
      </el-button>
      <el-button type="primary" plain @click="router.push({ name: 'circuit-breaker-history' })">
        {{ t('circuitBreaker.monitoring.actions.history') }}
      </el-button>
    </template>

    <!-- 监控控制面板 -->
    <el-card class="control-panel" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('circuitBreaker.monitoring.controlTitle') }}</span>
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
              :disabled="updatingConfig || !canWrite"
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
              :disabled="updatingConfig || !canWrite"
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

    <!-- 熔断器实例：概览数字 + 筛选 + 批量/单个重置 -->
    <el-card class="instances-card" style="margin-top: 16px" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('circuitBreaker.monitoring.instancesTitle') }}</span>
          <el-button type="primary" size="small" :loading="loadingInstances" @click="loadInstances">
            {{ t('circuitBreaker.monitoring.refreshStatus') }}
          </el-button>
        </div>
      </template>

      <el-row :gutter="16" class="summary-row">
        <el-col :span="6" v-for="summary in stateSummary" :key="summary.state">
          <div
            class="summary-card"
            :class="[`summary-${summary.state.toLowerCase()}`, { 'is-active': filterState === summary.state }]"
            role="button"
            tabindex="0"
            @click="toggleStateFilter(summary.state)"
            @keydown.enter="toggleStateFilter(summary.state)"
          >
            <div class="summary-count">{{ summary.count }}</div>
            <div class="summary-label">{{ stateLabel(summary.state) }}</div>
          </div>
        </el-col>
      </el-row>

      <div class="instance-toolbar">
        <el-select
          v-model="filterServiceType"
          :placeholder="t('circuitBreaker.monitoring.filterServiceType')"
          clearable
          style="width: 170px"
        >
          <el-option v-for="type in serviceTypes" :key="type" :label="type" :value="type" />
        </el-select>
        <el-select
          v-model="filterState"
          :placeholder="t('circuitBreaker.monitoring.filterState')"
          clearable
          style="width: 150px"
        >
          <el-option v-for="state in breakerStates" :key="state" :label="stateLabel(state)" :value="state" />
        </el-select>
        <el-input
          v-model="keyword"
          :placeholder="t('circuitBreaker.monitoring.searchPlaceholder')"
          clearable
          style="width: 240px"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button v-if="hasActiveFilter" @click="clearFilters">
          {{ t('circuitBreaker.monitoring.clearFilters') }}
        </el-button>
        <div class="toolbar-spacer" />
        <template v-if="canWrite">
          <el-button
            type="warning"
            :disabled="selectedInstances.length === 0"
            :loading="resettingInstances"
            @click="resetSelected"
          >
            {{ t('circuitBreaker.monitoring.resetSelected', { count: selectedInstances.length }) }}
          </el-button>
          <el-button
            type="danger"
            plain
            :disabled="filteredInstances.length === 0"
            :loading="resettingInstances"
            @click="resetFiltered"
          >
            {{ t('circuitBreaker.monitoring.resetFiltered', { count: filteredInstances.length }) }}
          </el-button>
        </template>
      </div>

      <el-table
        v-loading="loadingInstances"
        :data="pagedInstances"
        row-key="instanceId"
        stripe
        size="small"
        class="instances-table"
        @selection-change="handleSelectionChange"
      >
        <el-table-column v-if="canWrite" type="selection" width="44" />
        <el-table-column :label="t('circuitBreaker.monitoring.columns.instanceName')" min-width="150">
          <template #default="{ row }">
            <el-tooltip :content="row.instanceId" placement="top">
              <span class="instance-name">{{ row.instanceName || row.instanceId }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column :label="t('circuitBreaker.monitoring.columns.serviceType')" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.serviceType" size="small">{{ row.serviceType }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('circuitBreaker.monitoring.columns.baseUrl')"
          min-width="220"
          show-overflow-tooltip
        >
          <template #default="{ row }">{{ row.baseUrl || '-' }}</template>
        </el-table-column>
        <el-table-column :label="t('circuitBreaker.monitoring.columns.state')" width="110">
          <template #default="{ row }">
            <el-tag :type="getStateTagType(row.state)" size="small">{{ stateLabel(row.state) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('circuitBreaker.monitoring.columns.failureRatio')" width="140">
          <template #default="{ row }">
            <div class="ratio-cell">
              <span>{{ row.failureCount }} / {{ row.failureThreshold }}</span>
              <el-progress
                :percentage="failureRatio(row)"
                :show-text="false"
                :stroke-width="4"
                :status="row.state === 'OPEN' ? 'exception' : undefined"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column :label="t('circuitBreaker.monitoring.columns.lastFailure')" width="150">
          <template #default="{ row }">
            <el-tooltip v-if="row.lastFailureTime" :content="formatAbsoluteTime(row.lastFailureTime)" placement="top">
              <span>{{ formatRelativeTime(row.lastFailureTime) }}</span>
            </el-tooltip>
            <span v-else class="muted">{{ t('circuitBreaker.monitoring.neverFailed') }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('circuitBreaker.monitoring.columns.actions')" width="150" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">
              {{ t('circuitBreaker.monitoring.actions.detail') }}
            </el-button>
            <el-button
              v-if="canWrite"
              type="warning"
              size="small"
              :icon="RefreshRight"
              @click="resetSingle(row)"
            >
              {{ t('circuitBreaker.monitoring.reset') }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <div class="empty-state">
            <p>{{ hasActiveFilter ? t('circuitBreaker.monitoring.messages.noMatchingInstances') : t('circuitBreaker.monitoring.messages.noInstances') }}</p>
            <el-button v-if="hasActiveFilter" size="small" @click="clearFilters">
              {{ t('circuitBreaker.monitoring.clearFilters') }}
            </el-button>
          </div>
        </template>
      </el-table>

      <div v-if="filteredInstances.length > pageSize" class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="filteredInstances.length"
          layout="total, prev, pager, next"
        />
      </div>
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

    <!-- 单实例详情 -->
    <el-drawer
      v-model="detailVisible"
      :title="t('circuitBreaker.monitoring.drawer.title')"
      size="420px"
    >
      <template v-if="detailRow">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="t('circuitBreaker.monitoring.columns.instanceName')">
            {{ detailRow.instanceName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('circuitBreaker.monitoring.drawer.instanceId')">
            <span class="instance-id">{{ detailRow.instanceId }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('circuitBreaker.monitoring.columns.serviceType')">
            {{ detailRow.serviceType || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('circuitBreaker.monitoring.columns.baseUrl')">
            {{ detailRow.baseUrl || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('circuitBreaker.monitoring.columns.state')">
            <el-tag :type="getStateTagType(detailRow.state)" size="small">{{ stateLabel(detailRow.state) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('circuitBreaker.monitoring.drawer.failureCount')">
            {{ detailRow.failureCount }} / {{ detailRow.failureThreshold }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('circuitBreaker.monitoring.drawer.successCount')">
            {{ detailRow.successCount }} / {{ detailRow.successThreshold }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('circuitBreaker.monitoring.drawer.timeout')">
            {{ detailRow.timeoutMillis }} ms
          </el-descriptions-item>
          <el-descriptions-item :label="t('circuitBreaker.monitoring.columns.lastFailure')">
            {{ detailRow.lastFailureTime ? formatAbsoluteTime(detailRow.lastFailureTime) : t('circuitBreaker.monitoring.neverFailed') }}
          </el-descriptions-item>
        </el-descriptions>
        <p class="drawer-hint">{{ t('circuitBreaker.monitoring.drawer.resetHint') }}</p>
        <el-button
          v-if="canWrite"
          type="warning"
          :loading="resettingInstances"
          @click="resetSingle(detailRow)"
        >
          {{ t('circuitBreaker.monitoring.confirmations.resetSingleTitle') }}
        </el-button>
      </template>
    </el-drawer>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, RefreshRight, Search } from '@element-plus/icons-vue'
import request from '@/utils/request'
import PageSkeleton from '@/components/PageSkeleton.vue'
import { usePermission } from '@/composables/usePermission'
import {
  getCircuitBreakerInstances,
  resetCircuitBreakerInstance,
  resetCircuitBreakerInstances,
  type CircuitBreakerInstance
} from '@/api/circuitBreakerMonitor'

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

interface StateSummary {
  state: string
  count: number
}

const { t, locale } = useI18n()
const router = useRouter()
const { hasPermission } = usePermission()

const apiBaseUrl = '/v1/circuit-breaker-monitor'
const breakerStates = ['CLOSED', 'OPEN', 'HALF_OPEN']
const pageSize = 20

const canWrite = computed(() => hasPermission('config:circuitbreaker:write'))

const monitorStatus = ref<MonitorStatus>({
  enabled: true,
  paused: false,
  sampleRate: 0.1,
  historySize: 500,
  totalSampledCount: 0
})

const events = ref<CircuitBreakerEvent[]>([])
const instances = ref<CircuitBreakerInstance[]>([])
const selectedInstances = ref<CircuitBreakerInstance[]>([])
const selectedEventType = ref<string>('')
const configForm = ref({
  sampleRate: 10,
  historySize: 500
})

const filterServiceType = ref<string>('')
const filterState = ref<string>('')
const keyword = ref<string>('')
const currentPage = ref(1)

const detailVisible = ref(false)
const detailRow = ref<CircuitBreakerInstance | null>(null)

const wsConnected = ref(false)
const togglingMonitor = ref(false)
const updatingConfig = ref(false)
const clearingHistory = ref(false)
const loadingEvents = ref(false)
const loadingInstances = ref(false)
const resettingInstances = ref(false)
let ws: WebSocket | null = null
let reconnectTimer: number | null = null
// 主动断开标志：disconnectWebSocket 置 true，避免 close 事件再排重连
let isManualClose = false

const serviceTypes = computed<string[]>(() => {
  const types = new Set<string>()
  instances.value.forEach(item => {
    if (item.serviceType) types.add(item.serviceType)
  })
  return Array.from(types).sort()
})

const stateSummary = computed<StateSummary[]>(() => {
  const counts: Record<string, number> = { CLOSED: 0, OPEN: 0, HALF_OPEN: 0 }
  instances.value.forEach(cb => {
    if (counts[cb.state] !== undefined) {
      counts[cb.state]++
    }
  })
  const total = { state: 'TOTAL', count: instances.value.length }
  return [total, ...Object.entries(counts).map(([state, count]) => ({ state, count }))]
})

const hasActiveFilter = computed(() => Boolean(filterServiceType.value || filterState.value || keyword.value.trim()))

const filteredInstances = computed<CircuitBreakerInstance[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  return instances.value.filter(item => {
    if (filterServiceType.value && item.serviceType !== filterServiceType.value) return false
    if (filterState.value && item.state !== filterState.value) return false
    if (!kw) return true
    return [item.instanceName, item.instanceId, item.baseUrl, item.serviceType]
      .some(field => (field || '').toLowerCase().includes(kw))
  })
})

const pagedInstances = computed<CircuitBreakerInstance[]>(() => {
  const start = (currentPage.value - 1) * pageSize
  return filteredInstances.value.slice(start, start + pageSize)
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
  return `${date.toLocaleTimeString(locale.value === 'en-US' ? 'en-US' : 'zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })}.${String(date.getMilliseconds()).padStart(3, '0')}`
}

const formatAbsoluteTime = (value: string) => new Date(value).toLocaleString(locale.value === 'en-US' ? 'en-US' : 'zh-CN')

const formatRelativeTime = (value: string) => {
  const diffMs = Date.now() - new Date(value).getTime()
  const seconds = Math.max(0, Math.floor(diffMs / 1000))
  if (seconds < 10) return t('circuitBreaker.monitoring.relative.justNow')
  if (seconds < 60) return t('circuitBreaker.monitoring.relative.secondsAgo', { n: seconds })
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return t('circuitBreaker.monitoring.relative.minutesAgo', { n: minutes })
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return t('circuitBreaker.monitoring.relative.hoursAgo', { n: hours })
  return t('circuitBreaker.monitoring.relative.daysAgo', { n: Math.floor(hours / 24) })
}

const getInstanceShortName = (instanceId: string) => {
  if (!instanceId) return '-'
  const parts = instanceId.split('-')
  return parts.length > 2 ? parts.slice(0, 2).join('-') : instanceId.substring(0, 12)
}

const failureRatio = (row: CircuitBreakerInstance) => {
  if (!row.failureThreshold || row.failureThreshold <= 0) return 0
  return Math.min(100, Math.round((row.failureCount / row.failureThreshold) * 100))
}

const stateLabel = (state: string) => {
  if (state === 'TOTAL') return t('circuitBreaker.monitoring.states.total')
  switch (state) {
    case 'CLOSED':
      return t('circuitBreaker.monitoring.states.closed')
    case 'OPEN':
      return t('circuitBreaker.monitoring.states.open')
    case 'HALF_OPEN':
      return t('circuitBreaker.monitoring.states.halfOpen')
    default:
      return state
  }
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

const toggleStateFilter = (state: string) => {
  if (state === 'TOTAL') {
    filterState.value = ''
  } else {
    filterState.value = filterState.value === state ? '' : state
  }
  currentPage.value = 1
}

const clearFilters = () => {
  filterServiceType.value = ''
  filterState.value = ''
  keyword.value = ''
  currentPage.value = 1
}

const handleSelectionChange = (selection: CircuitBreakerInstance[]) => {
  selectedInstances.value = selection
}

const openDetail = (row: CircuitBreakerInstance) => {
  detailRow.value = row
  detailVisible.value = true
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

const loadInstances = async () => {
  loadingInstances.value = true
  try {
    const response = await getCircuitBreakerInstances()
    instances.value = Array.isArray(response.data) ? response.data : []
    if ((currentPage.value - 1) * pageSize >= filteredInstances.value.length) {
      currentPage.value = 1
    }
  } catch (error) {
    console.error('Failed to load circuit breaker instances:', error)
    ElMessage.error(t('circuitBreaker.monitoring.messages.loadInstancesFailed'))
  } finally {
    loadingInstances.value = false
  }
}

const loadHistory = async () => {
  loadingEvents.value = true
  try {
    const response = await request.get(`${apiBaseUrl}/history`, {
      params: { limit: 200 }
    })
    const allEvents: CircuitBreakerEvent[] = []
    Object.values(response.data as Record<string, CircuitBreakerEvent[]>).forEach((instanceEvents) => {
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
  isManualClose = false
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const token = localStorage.getItem('admin_token')
  const qs = token ? `?token=${encodeURIComponent(token)}` : ''
  const wsUrl = `${protocol}//${window.location.host}/ws/circuit-breaker-monitor${qs}`

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
        const newEvent: CircuitBreakerEvent = data
        events.value.unshift(newEvent)
        if (events.value.length > 100) {
          events.value.pop()
        }

        if (newEvent.eventType === 'STATE_CHANGE' && newEvent.currentState) {
          const idx = instances.value.findIndex(cb => cb.instanceId === newEvent.instanceId)
          if (idx >= 0) {
            instances.value[idx] = {
              ...instances.value[idx],
              state: newEvent.currentState,
              failureCount: newEvent.failureCount,
              successCount: newEvent.successCount,
              lastFailureTime: newEvent.currentState === 'OPEN'
                ? newEvent.timestamp
                : instances.value[idx].lastFailureTime
            }
          }
        }
      }
    } catch (error) {
      console.error('Failed to parse WebSocket message:', error)
    }
  }

  ws.onclose = () => {
    wsConnected.value = false
    if (isManualClose) return
    reconnectTimer = window.setTimeout(() => {
      connectWebSocket()
    }, 3000)
  }

  ws.onerror = (error) => {
    if (isManualClose) return
    console.error('WebSocket error:', error)
  }
}

const disconnectWebSocket = () => {
  isManualClose = true
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

// 清空路由监控历史不可恢复 ⇒ 必须二次确认。
// 审计 #460：本函数的确认被同文件里 reset 类操作的确认语句「掩盖」了——按文件看有确认，
// 按调用点看没有（文件级规则查不出这类）。守卫见 05-traffic-governance.mjs 的 cb-clear-history:*。
const clearHistory = () => {
  ElMessageBox.confirm(
    t('circuitBreaker.monitoring.confirmations.clearHistoryMessage'),
    t('circuitBreaker.monitoring.confirmations.clearHistoryTitle'),
    {
      confirmButtonText: t('circuitBreaker.monitoring.confirmations.confirmClear'),
      cancelButtonText: t('circuitBreaker.monitoring.confirmations.cancel'),
      type: 'warning'
    }
  ).then(async () => {
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
  }).catch(() => { /* cancelled */ })
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

const resetSingle = (row: CircuitBreakerInstance) => {
  ElMessageBox.confirm(
    t('circuitBreaker.monitoring.confirmations.resetSingleMessage', {
      name: row.instanceName || row.instanceId,
      state: stateLabel(row.state)
    }),
    t('circuitBreaker.monitoring.confirmations.resetSingleTitle'),
    {
      confirmButtonText: t('circuitBreaker.monitoring.confirmations.confirmReset'),
      cancelButtonText: t('circuitBreaker.monitoring.confirmations.cancel'),
      type: 'warning'
    }
  ).then(async () => {
    resettingInstances.value = true
    try {
      const response = await resetCircuitBreakerInstance(row.instanceId)
      const result = response.data?.data
      if (result?.persistedStateDeleted === false) {
        ElMessage.warning(t('circuitBreaker.monitoring.messages.resetPersistWarning'))
      } else {
        ElMessage.success(t('circuitBreaker.monitoring.messages.resetSuccess', {
          name: row.instanceName || row.instanceId,
          from: stateLabel(result?.previousState || row.state),
          to: stateLabel(result?.currentState || 'CLOSED')
        }))
      }
      await Promise.all([loadInstances(), loadHistory()])
    } catch (error) {
      ElMessage.error(t('circuitBreaker.monitoring.messages.resetFailed'))
    } finally {
      resettingInstances.value = false
    }
  }).catch(() => { /* cancelled */ })
}

const resetSelected = () => {
  const ids = selectedInstances.value.map(item => item.instanceId)
  ElMessageBox.confirm(
    t('circuitBreaker.monitoring.confirmations.resetSelectedMessage', { count: ids.length }),
    t('circuitBreaker.monitoring.confirmations.resetSelectedTitle'),
    {
      confirmButtonText: t('circuitBreaker.monitoring.confirmations.confirmReset'),
      cancelButtonText: t('circuitBreaker.monitoring.confirmations.cancel'),
      type: 'warning'
    }
  ).then(async () => {
    resettingInstances.value = true
    try {
      const response = await resetCircuitBreakerInstances({ instanceIds: ids })
      const matched = response.data?.data?.matched ?? 0
      ElMessage.success(t('circuitBreaker.monitoring.messages.batchResetSuccess', { count: matched }))
      await Promise.all([loadInstances(), loadHistory()])
    } catch (error) {
      ElMessage.error(t('circuitBreaker.monitoring.messages.resetFailed'))
    } finally {
      resettingInstances.value = false
    }
  }).catch(() => { /* cancelled */ })
}

const resetFiltered = () => {
  const payload = {
    serviceType: filterServiceType.value || undefined,
    states: filterState.value ? [filterState.value] : undefined
  }
  ElMessageBox.confirm(
    t('circuitBreaker.monitoring.confirmations.resetFilteredMessage', {
      count: filteredInstances.value.length,
      scope: filterState.value ? stateLabel(filterState.value) : t('circuitBreaker.monitoring.states.all')
    }),
    t('circuitBreaker.monitoring.confirmations.resetFilteredTitle'),
    {
      confirmButtonText: t('circuitBreaker.monitoring.confirmations.confirmReset'),
      cancelButtonText: t('circuitBreaker.monitoring.confirmations.cancel'),
      type: 'warning'
    }
  ).then(async () => {
    resettingInstances.value = true
    try {
      const response = await resetCircuitBreakerInstances(payload)
      const matched = response.data?.data?.matched ?? 0
      ElMessage.success(t('circuitBreaker.monitoring.messages.batchResetSuccess', { count: matched }))
      selectedInstances.value = []
      await Promise.all([loadInstances(), loadHistory()])
    } catch (error) {
      ElMessage.error(t('circuitBreaker.monitoring.messages.resetFailed'))
    } finally {
      resettingInstances.value = false
    }
  }).catch(() => { /* cancelled */ })
}

onMounted(() => {
  loadStatus()
  loadInstances()
  loadHistory()
  connectWebSocket()
})

onUnmounted(() => {
  disconnectWebSocket()
})
</script>

<style scoped>
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

.summary-row {
  margin-bottom: 16px;
}

.summary-card {
  text-align: center;
  padding: 20px;
  border-radius: 8px;
  background: var(--ja-primary-light-9, #f5f7fa);
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
  border: 2px solid transparent;
}

.summary-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

.summary-card.is-active {
  border-color: var(--el-color-primary);
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

.instance-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.toolbar-spacer {
  flex: 1;
}

.instances-table {
  width: 100%;
}

.instance-name {
  font-weight: 500;
}

.instance-id {
  font-family: monospace;
  font-size: 12px;
}

.ratio-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}

.muted {
  color: var(--el-text-color-placeholder);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 0;
  color: var(--el-text-color-secondary);
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.drawer-hint {
  margin: 16px 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.events-card {
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.06);
  border-radius: 12px;
}

.events-table {
  width: 100%;
}
</style>
