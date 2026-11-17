<template>
  <PageSkeleton :title="t('loadBalancer.monitoring.pageTitle')">
    <template #actions>
      <ConfirmAction
        v-if="canWrite"
        :title="t('loadBalancer.monitoring.confirmations.clearHistoryMessage')"
        :width="300"
        :confirm-text="t('loadBalancer.monitoring.confirmations.confirm')"
        :cancel-text="t('loadBalancer.monitoring.confirmations.cancel')"
        type="danger"
        :loading="clearingHistory"
        @confirm="clearHistory"
      >
        <el-button :loading="clearingHistory">
          {{ t('loadBalancer.monitoring.clearHistory') }}
        </el-button>
      </ConfirmAction>
      <el-dropdown @command="handleMoreCommand">
        <el-button :loading="togglingMonitor">
          {{ t('loadBalancer.monitoring.moreActions') }}
          <el-icon class="el-icon--right"><ArrowDown /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-if="canWrite" command="toggle">
              {{ monitorStatus.paused ? t('loadBalancer.monitoring.resumeMonitoring') : t('loadBalancer.monitoring.pauseMonitoring') }}
            </el-dropdown-item>
            <el-dropdown-item command="strategy" divided>
              {{ t('loadBalancer.monitoring.actions.strategyConfig') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </template>

    <template #stats>
      <!-- 页面级概览与其它观测页同构：一行 MetricStrip，语义色只给「已暂停服务」 -->
      <MetricStrip :items="metricItems" :columns="4" />
    </template>

    <!-- 工具栏顺序（设计 §P4）：时间范围 → 维度筛选 → 刷新 → 导出。
         状态 / 统计 / 历史三个接口都不接受时间区间（历史只有 limit），因此时间范围位空缺，
         顺序为「维度筛选 → 刷新 → 导出」 -->
    <template #toolbar>
      <el-select
        v-model="selectedServiceType"
        :placeholder="t('loadBalancer.monitoring.selectServiceType')"
        clearable
        style="width: 200px"
      >
        <el-option
          v-for="type in serviceTypes"
          :key="type"
          :label="type"
          :value="type"
        />
      </el-select>
      <el-button v-if="hasActiveFilter" @click="clearFilters">
        {{ t('loadBalancer.monitoring.clearFilters') }}
      </el-button>
      <el-button type="primary" :loading="refreshing" @click="refreshAll">
        {{ t('loadBalancer.monitoring.refresh') }}
      </el-button>
      <!-- 工具栏末位：导出（json / csv），与「刷新」同排，符合「…→ 刷新 → 导出」的顺序 -->
      <el-dropdown @command="handleExport">
        <el-button :loading="exporting">
          {{ t('loadBalancer.monitoring.export') }} <el-icon class="el-icon--right"><Download /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="json">{{ t('loadBalancer.monitoring.exportJson') }}</el-dropdown-item>
            <el-dropdown-item command="csv">{{ t('loadBalancer.monitoring.exportCsv') }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </template>

    <!-- 路由监控控制面板 -->
    <el-card class="control-panel" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('loadBalancer.monitoring.controlTitle') }}</span>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :xs="24" :sm="12" :md="6">
          <div class="config-item">
            <label>{{ t('loadBalancer.monitoring.sampleRate') }}</label>
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
        <el-col :xs="24" :sm="12" :md="6">
          <div class="config-item">
            <label>{{ t('loadBalancer.monitoring.historySize') }}</label>
            <el-input-number
              v-model="configForm.historySize"
              :min="100"
              :max="10000"
              :step="100"
              @change="updateHistorySize"
              :disabled="updatingConfig || !canWrite"
            />
          </div>
        </el-col>
        <el-col :xs="24" :sm="24" :md="12">
          <div class="connection-status">
            <el-tag :type="wsConnected ? 'success' : 'danger'">
              {{ wsConnected ? t('loadBalancer.monitoring.wsConnected') : t('loadBalancer.monitoring.wsDisconnected') }}
            </el-tag>
            <span class="sampled-count">
              {{ t('loadBalancer.monitoring.sampledCount', { count: monitorStatus.totalSampledCount }) }}
            </span>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 实时路由监控统计卡片：中性容器，状态色只落在「已暂停」标签上 -->
    <el-row :gutter="16" style="margin-top: var(--ja-space-4)">
      <el-col :xs="24" :sm="12" :md="6" v-for="(serviceStats, serviceType) in routingStats" :key="serviceType">
        <el-card shadow="hover" class="stats-card">
          <div class="service-stats">
            <div class="service-header">
              <el-tag type="info" size="large">{{ serviceType }}</el-tag>
              <el-tag
                :type="getServicePausedType(serviceType)"
                size="small"
                style="margin-left: 8px"
              >
                {{ isServicePaused(serviceType) ? t('loadBalancer.monitoring.pausedStatus') : t('loadBalancer.monitoring.monitoringActive') }}
              </el-tag>
            </div>
            <div class="stats-content">
              <!-- 服务级数字统一走 MetricStrip，列数 3 与三项一一对应 -->
              <MetricStrip
                :items="serviceMetricItems(serviceStats)"
                :columns="3"
                class="service-metric-strip"
              />
            </div>
            <div class="instance-distribution">
              <div class="distribution-title">
                {{ t('loadBalancer.monitoring.distributionTitle') }}
                <el-tooltip :content="t('loadBalancer.monitoring.countHint')" placement="top">
                  <el-icon class="hint-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </div>
              <!-- 按模型分组显示 -->
              <div
                v-for="(instanceCounts, modelName) in serviceStats.modelInstanceCounts"
                :key="modelName"
                class="model-group"
              >
                <div class="model-name">
                  <el-icon><Box /></el-icon>
                  <span>{{ modelName }}</span>
                </div>
                <div
                  v-for="(count, instance) in instanceCounts"
                  :key="instance"
                  class="distribution-item"
                >
                  <span class="instance-name">{{ getInstanceShortName(instance as string) }}</span>
                  <el-progress
                    :percentage="getPercentage(count as number, serviceStats.totalSelections)"
                    :stroke-width="6"
                    :show-text="false"
                  />
                  <span class="instance-count">{{ count }}</span>
                </div>
              </div>
              <!-- 如果没有模型数据，显示旧的实例分布 -->
              <div
                v-if="!serviceStats.modelInstanceCounts || Object.keys(serviceStats.modelInstanceCounts).length === 0"
                class="fallback-distribution"
              >
                <div
                  v-for="(count, instance) in serviceStats.instanceCounts"
                  :key="instance"
                  class="distribution-item"
                >
                  <span class="instance-name">{{ getInstanceShortName(instance as string) }}</span>
                  <el-progress
                    :percentage="getPercentage(count as number, serviceStats.totalSelections)"
                    :stroke-width="6"
                    :show-text="false"
                  />
                  <span class="instance-count">{{ count }}</span>
                </div>
              </div>
            </div>
            <div class="service-actions">
              <el-button
                v-if="canWrite"
                size="small"
                :type="isServicePaused(serviceType) ? 'success' : 'warning'"
                @click="toggleServiceMonitor(serviceType as string)"
              >
                {{ isServicePaused(serviceType) ? t('loadBalancer.monitoring.resume') : t('loadBalancer.monitoring.pause') }}
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 实时路由事件流 -->
    <el-card class="events-card" style="margin-top: var(--ja-space-4)" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('loadBalancer.monitoring.eventsTitle') }}</span>
        </div>
      </template>

      <el-table
        :data="filteredEvents"
        stripe
        max-height="400"
        v-loading="loadingEvents"
        class="events-table"
      >
        <el-table-column prop="timestamp" :label="t('loadBalancer.monitoring.columns.time')" width="180">
          <template #default="{ row }">
            <TimeText :value="row.timestamp" mode="absolute" precision="second" />
          </template>
        </el-table-column>
        <el-table-column prop="serviceType" :label="t('loadBalancer.monitoring.columns.serviceType')" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.serviceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="strategy" :label="t('loadBalancer.monitoring.columns.strategy')" width="150">
          <template #default="{ row }">
            <el-tag type="info" size="small">
              {{ row.strategy }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="selectedInstance" :label="t('loadBalancer.monitoring.columns.selectedInstance')" min-width="200">
          <template #default="{ row }">
            <el-tooltip :content="row.selectedInstanceUrl" placement="top">
              <span class="instance-id">{{ row.selectedInstance }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="clientId" :label="t('loadBalancer.monitoring.columns.clientId')" width="120">
          <template #default="{ row }">
            <span class="client-id">{{ row.clientId || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="candidateCount" :label="t('loadBalancer.monitoring.columns.candidateCount')" width="80" />
        <el-table-column prop="selectionTimeMs" :label="t('loadBalancer.monitoring.columns.selectionTimeMs')" width="90">
          <template #default="{ row }">
            <span :class="{ 'slow-select': row.selectionTimeMs > 10 }">
              {{ formatMilliseconds(row.selectionTimeMs) }}
            </span>
          </template>
        </el-table-column>

        <!-- 空态统一走 EmptyState，区分「暂无数据」与「筛选无结果」 -->
        <template #empty>
          <EmptyState
            icon="DataLine"
            :title="hasActiveFilter ? t('loadBalancer.monitoring.emptySearchTitle') : t('loadBalancer.monitoring.emptyTitle')"
            :description="hasActiveFilter ? t('loadBalancer.monitoring.emptySearchDescription') : t('loadBalancer.monitoring.emptyDescription')"
          >
            <template v-if="hasActiveFilter" #actions>
              <el-button size="small" @click="clearFilters">
                {{ t('loadBalancer.monitoring.clearFilters') }}
              </el-button>
            </template>
          </EmptyState>
        </template>
      </el-table>
    </el-card>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download, ArrowDown, Box, InfoFilled } from '@element-plus/icons-vue'
import request from '@/utils/request'
import ConfirmAction from '@/components/ConfirmAction.vue'
import EmptyState from '@/components/EmptyState.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import TimeText from '@/components/TimeText.vue'
import { usePermission } from '@/composables/usePermission'

interface MonitorStatus {
  enabled: boolean
  paused: boolean
  sampleRate: number
  historySize: number
  totalSampledCount: number
  pausedServices: string[]
}

interface ServiceRoutingStats {
  strategy: string
  totalSelections: number
  requestsPerSecond: number
  instanceCounts: Record<string, number>
  modelInstanceCounts: Record<string, Record<string, number>>
  recentSelections: string[]
  clientInstanceMap: Record<string, string>
}

interface RoutingEvent {
  timestamp: string
  serviceType: string
  modelName: string
  strategy: string
  selectedInstance: string
  selectedInstanceUrl: string
  clientId: string
  candidateCount: number
  selectionTimeMs: number
}

const apiBaseUrl = '/v1/routing-monitor'

const monitorStatus = ref<MonitorStatus>({
  enabled: true,
  paused: false,
  sampleRate: 0.1,
  historySize: 1000,
  totalSampledCount: 0,
  pausedServices: []
})

const routingStats = ref<Record<string, ServiceRoutingStats>>({})
const events = ref<RoutingEvent[]>([])
const selectedServiceType = ref<string>('')
const serviceTypes = ref<string[]>(['chat', 'embedding', 'rerank', 'tts', 'stt', 'imggen', 'imgedit'])

const configForm = ref({
  sampleRate: 10,
  historySize: 1000
})

const wsConnected = ref(false)
const togglingMonitor = ref(false)
const updatingConfig = ref(false)
const clearingHistory = ref(false)
const loadingEvents = ref(false)
const refreshing = ref(false)
const exporting = ref(false)
let ws: WebSocket | null = null
let reconnectTimer: number | null = null
// 主动断开标志：disconnectWebSocket 置 true，避免 close 事件再排重连
let isManualClose = false

const { t } = useI18n()
const router = useRouter()
const { hasPermission } = usePermission()
// 路由监控的写操作（暂停/恢复、采样率、历史大小、清空历史）在后端归 lb:config:write；
// 只读角色（仅持 lb:monitoring:read）看到这些控件只会点出 403，因此一律按写码收敛。
const canWrite = computed(() => hasPermission('lb:config:write'))

const filteredEvents = computed(() => {
  if (!selectedServiceType.value) {
    return events.value.slice(0, 50)
  }
  return events.value
    .filter(e => e.serviceType === selectedServiceType.value)
    .slice(0, 50)
})

/** 工具栏是否处于筛选态：决定空态文案取「暂无数据」还是「筛选无结果」 */
const hasActiveFilter = computed(() => Boolean(selectedServiceType.value))

const clearFilters = () => {
  selectedServiceType.value = ''
}

/**
 * 页面级概览：与其它观测页同口径（中性容器 + 语义色只给异常项）。
 * 数字全部来自既有 status / stats 响应，不额外发请求。
 */
const metricItems = computed<MetricStripItem[]>(() => {
  const serviceList = Object.values(routingStats.value)
  const totalQps = serviceList.reduce((sum, item) => sum + (item.requestsPerSecond || 0), 0)
  const pausedCount = monitorStatus.value.pausedServices?.length || 0
  return [
    {
      key: 'sampledTotal',
      label: t('loadBalancer.monitoring.sampledTotal'),
      value: monitorStatus.value.totalSampledCount || 0
    },
    {
      key: 'activeServices',
      label: t('loadBalancer.monitoring.activeServices'),
      value: serviceList.length
    },
    {
      key: 'pausedServices',
      label: t('loadBalancer.monitoring.pausedServices'),
      value: pausedCount,
      tone: pausedCount > 0 ? 'warning' : 'default'
    },
    {
      key: 'totalQps',
      label: t('loadBalancer.monitoring.totalQps'),
      value: totalQps.toFixed(2)
    }
  ]
})

/** 单个服务的三个数字（策略 / 采样数 / QPS），走同一个 MetricStrip 组件 */
const serviceMetricItems = (serviceStats: ServiceRoutingStats): MetricStripItem[] => [
  {
    key: 'strategy',
    label: t('loadBalancer.monitoring.strategy'),
    value: serviceStats.strategy || '-'
  },
  {
    key: 'selectionCount',
    label: t('loadBalancer.monitoring.selectionCount'),
    value: serviceStats.totalSelections || 0
  },
  {
    key: 'qps',
    label: t('loadBalancer.monitoring.qps'),
    value: (serviceStats.requestsPerSecond || 0).toFixed(2)
  }
]

const formatSampleRate = (val: number) => `${val}%`

/** 毫秒统一取整（同页数字口径） */
const formatMilliseconds = (value: number | null | undefined): number => {
  const num = Number(value)
  return Number.isFinite(num) ? Math.round(num) : 0
}

const getInstanceShortName = (instanceId: string) => {
  if (!instanceId) return '-'
  const parts = instanceId.split('-')
  return parts.length > 2 ? parts.slice(0, 2).join('-') : instanceId.substring(0, 12)
}

const getPercentage = (count: number, total: number) => {
  if (!total) return 0
  return Math.round((count / total) * 100)
}

const isServicePaused = (serviceType: string) => {
  return monitorStatus.value.pausedServices?.includes(serviceType) || false
}

const getServicePausedType = (serviceType: string) => {
  return isServicePaused(serviceType) ? 'warning' : 'success'
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

const loadStats = async () => {
  try {
    const response = await request.get(`${apiBaseUrl}/stats`)
    routingStats.value = response.data

    serviceTypes.value = Object.keys(routingStats.value)
  } catch (error) {
    console.error('Failed to load routing stats:', error)
  }
}

const loadHistory = async () => {
  loadingEvents.value = true
  try {
    const response = await request.get(`${apiBaseUrl}/history`, {
      params: { limit: 100 }
    })
    const allEvents: RoutingEvent[] = []
    Object.values(response.data as Record<string, RoutingEvent[]>).forEach((serviceEvents) => {
      allEvents.push(...serviceEvents)
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
  const wsUrl = `${protocol}//${window.location.host}/ws/routing-monitor${qs}`

  ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    wsConnected.value = true
  }

  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data)

      if (data.type === 'connected') {
        monitorStatus.value = data.status
        routingStats.value = data.stats || {}
      } else if (data.type === 'heartbeat') {
        // Heartbeat received
      } else {
        // Routing event
        const newEvent: RoutingEvent = data
        events.value.unshift(newEvent)
        if (events.value.length > 100) {
          events.value.pop()
        }

        loadStats()
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
    ElMessage.success(monitorStatus.value.paused ? t('loadBalancer.monitoring.messages.paused') : t('loadBalancer.monitoring.messages.resumed'))
  } catch (error) {
    ElMessage.error(t('loadBalancer.monitoring.messages.operationFailed'))
  } finally {
    togglingMonitor.value = false
  }
}

const toggleServiceMonitor = async (serviceType: string) => {
  try {
    const isPaused = isServicePaused(serviceType)
    const action = isPaused ? 'resume' : 'pause'
    await request.post(`${apiBaseUrl}/${action}/${serviceType}`)

    if (isPaused) {
      monitorStatus.value.pausedServices = monitorStatus.value.pausedServices.filter(s => s !== serviceType)
    } else {
      monitorStatus.value.pausedServices.push(serviceType)
    }

    ElMessage.success(
      isPaused
        ? t('loadBalancer.monitoring.messages.serviceResumed', { serviceType })
        : t('loadBalancer.monitoring.messages.servicePaused', { serviceType })
    )
  } catch (error) {
    ElMessage.error(t('loadBalancer.monitoring.messages.operationFailed'))
  }
}

const updateSampleRate = async (value: number) => {
  updatingConfig.value = true
  try {
    await request.put(`${apiBaseUrl}/config/sample-rate`, {
      sampleRate: value / 100
    })
    monitorStatus.value.sampleRate = value / 100
    ElMessage.success(t('loadBalancer.monitoring.messages.sampleRateUpdated', { value }))
  } catch (error) {
    ElMessage.error(t('loadBalancer.monitoring.messages.sampleRateUpdateFailed'))
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
    ElMessage.success(t('loadBalancer.monitoring.messages.historySizeUpdated', { value }))
  } catch (error) {
    ElMessage.error(t('loadBalancer.monitoring.messages.historySizeUpdateFailed'))
    configForm.value.historySize = monitorStatus.value.historySize
  } finally {
    updatingConfig.value = false
  }
}

// 清空路由监控历史不可恢复 ⇒ 二次确认由模板里的 ConfirmAction（type=danger）承担，
// 这里只做「确认之后」的执行。守卫见 frontend/e2e/specs/05-traffic-governance.mjs 的
// lb-clear-history:* 三条断言（其中「取消不发写请求」做过 A/B 反证）。
const clearHistory = async () => {
  clearingHistory.value = true
  try {
    await request.delete(`${apiBaseUrl}/history`)
    events.value = []
    monitorStatus.value.totalSampledCount = 0
    ElMessage.success(t('loadBalancer.monitoring.messages.historyCleared'))
  } catch (error) {
    ElMessage.error(t('loadBalancer.monitoring.messages.clearHistoryFailed'))
  } finally {
    clearingHistory.value = false
  }
}

/** 工具栏刷新：三个只读接口重取一次（WebSocket 连接不动） */
const refreshAll = async () => {
  refreshing.value = true
  try {
    await Promise.all([loadStatus(), loadStats(), loadHistory()])
  } finally {
    refreshing.value = false
  }
}

const handleExport = async (command: string) => {
  exporting.value = true
  try {
    const response = await request.get(`${apiBaseUrl}/export/${command}`, {
      params: { limit: 5000 },
      responseType: 'blob'
    })

    const blob = new Blob([response.data], {
      type: command === 'json' ? 'application/json' : 'text/csv'
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `routing-history.${command}`
    link.click()
    window.URL.revokeObjectURL(url)

    ElMessage.success(t('loadBalancer.monitoring.messages.exportSuccess', { format: command.toUpperCase() }))
  } catch (error) {
    ElMessage.error(t('loadBalancer.monitoring.messages.exportFailed'))
  } finally {
    exporting.value = false
  }
}

// 「更多」菜单：暂停/恢复监控、跳到策略配置
const handleMoreCommand = (command: string) => {
  if (command === 'toggle') {
    void toggleMonitor()
    return
  }
  if (command === 'strategy') {
    router.push({ name: 'load-balancer-strategy-config' })
  }
}

onMounted(() => {
  loadStatus()
  loadStats()
  loadHistory()
  connectWebSocket()
})

onUnmounted(() => {
  disconnectWebSocket()
})
</script>

<style scoped>
.control-panel {
  margin-bottom: var(--ja-space-4);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ja-ink);
}

.config-item {
  display: flex;
  flex-direction: column;
  gap: var(--ja-space-2);
}

.config-item label {
  font-size: 14px;
  color: var(--ja-ink-2);
  font-weight: 500;
}

.connection-status {
  display: flex;
  align-items: center;
  gap: var(--ja-space-4);
  padding-top: var(--ja-space-6);
}

.sampled-count {
  font-size: 14px;
  color: var(--ja-ink-3);
}

.stats-card {
  height: 100%;
}

.service-stats {
  padding: var(--ja-space-2);
}

.service-header {
  display: flex;
  align-items: center;
  margin-bottom: var(--ja-space-3);
}

.stats-content {
  margin-bottom: var(--ja-space-3);
}

/* 服务级 MetricStrip：卡片内空间小，收敛内边距与数值字号，避免与页面级指标条抢视觉 */
.service-metric-strip {
  padding: 0;
  border: none;
  background: transparent;
  row-gap: var(--ja-space-2);
}

.service-metric-strip :deep(.metric) {
  padding-left: var(--ja-space-3);
}

.service-metric-strip :deep(.metric__value) {
  font-size: 14px;
}

.service-metric-strip :deep(.metric__label) {
  font-size: 11px;
}

.instance-distribution {
  background: var(--ja-surface-2);
  border-radius: var(--ja-radius);
  padding: var(--ja-space-3);
  margin-bottom: var(--ja-space-3);
  max-height: 300px;
  overflow-y: auto;
}

.distribution-title {
  font-size: 12px;
  color: var(--ja-ink-3);
  margin-bottom: var(--ja-space-2);
}

.distribution-title .hint-icon {
  margin-left: 4px;
  vertical-align: -2px;
  cursor: help;
}

.model-group {
  margin-bottom: var(--ja-space-3);
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--ja-border-dark);
}

.model-group:last-child {
  margin-bottom: 0;
  padding-bottom: 0;
  border-bottom: none;
}

.model-name {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--ja-ink-2);
  margin-bottom: var(--ja-space-1);
}

.fallback-distribution {
  /* 无模型数据时的备用显示 */
}

.distribution-item {
  display: flex;
  align-items: center;
  gap: var(--ja-space-2);
  margin-bottom: var(--ja-space-1);
}

.distribution-item:last-child {
  margin-bottom: 0;
}

.instance-name {
  font-size: 12px;
  color: var(--ja-ink-2);
  width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.distribution-item .el-progress {
  flex: 1;
}

.instance-count {
  font-size: 12px;
  color: var(--ja-ink-2);
  width: 40px;
  text-align: right;
}

.service-actions {
  text-align: center;
}

.events-card {
  box-shadow: var(--ja-shadow-sm);
  border-radius: var(--ja-radius-lg);
}

.events-table {
  width: 100%;
}

.instance-id {
  font-family: monospace;
  font-size: 12px;
}

.client-id {
  font-family: monospace;
  font-size: 12px;
  color: var(--ja-ink-3);
}

.slow-select {
  color: var(--ja-warning);
  font-weight: 500;
}
</style>
