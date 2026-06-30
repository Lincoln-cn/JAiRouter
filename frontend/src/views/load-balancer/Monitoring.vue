<template>
  <div class="load-balancer-monitoring">
    <!-- 路由监控控制面板 -->
    <el-card class="control-panel" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">路由监控控制</span>
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
              :min="100"
              :max="10000"
              :step="100"
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

    <!-- 实时路由监控统计卡片 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="6" v-for="(serviceStats, serviceType) in routingStats" :key="serviceType">
        <el-card shadow="hover" class="stats-card">
          <div class="service-stats">
            <div class="service-header">
              <el-tag type="primary" size="large">{{ serviceType }}</el-tag>
              <el-tag
                :type="getServicePausedType(serviceType)"
                size="small"
                style="margin-left: 8px"
              >
                {{ isServicePaused(serviceType) ? '已暂停' : '监控中' }}
              </el-tag>
            </div>
            <div class="stats-content">
              <div class="stat-row">
                <span class="stat-label">策略</span>
                <span class="stat-value">{{ serviceStats.strategy || '-' }}</span>
              </div>
              <div class="stat-row">
                <span class="stat-label">采样数</span>
                <span class="stat-value">{{ serviceStats.totalSelections || 0 }}</span>
              </div>
              <div class="stat-row">
                <span class="stat-label">QPS</span>
                <span class="stat-value">{{ serviceStats.requestsPerSecond?.toFixed(2) || '0.00' }}</span>
              </div>
            </div>
            <div class="instance-distribution">
              <div class="distribution-title">模型-实例分布</div>
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
                size="small"
                :type="isServicePaused(serviceType) ? 'success' : 'warning'"
                @click="toggleServiceMonitor(serviceType as string)"
              >
                {{ isServicePaused(serviceType) ? '恢复' : '暂停' }}
              </el-button>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 实时路由事件流 -->
    <el-card class="events-card" style="margin-top: 16px" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">实时路由事件</span>
          <el-select v-model="selectedServiceType" placeholder="选择服务类型" clearable style="width: 200px">
            <el-option
              v-for="type in serviceTypes"
              :key="type"
              :label="type"
              :value="type"
            />
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
        <el-table-column prop="serviceType" label="服务类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.serviceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="strategy" label="策略" width="150">
          <template #default="{ row }">
            <el-tag :type="getStrategyTagType(row.strategy)" size="small">
              {{ row.strategy }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="selectedInstance" label="选中实例" min-width="200">
          <template #default="{ row }">
            <el-tooltip :content="row.selectedInstanceUrl" placement="top">
              <span class="instance-id">{{ row.selectedInstance }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="clientId" label="客户端ID" width="120">
          <template #default="{ row }">
            <span class="client-id">{{ row.clientId || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="candidateCount" label="候选数" width="80" />
        <el-table-column prop="selectionTimeMs" label="耗时(ms)" width="90">
          <template #default="{ row }">
            <span :class="{ 'slow-select': row.selectionTimeMs > 10 }">
              {{ row.selectionTimeMs || 0 }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Box } from '@element-plus/icons-vue'
import request from '@/utils/request'

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
let ws: WebSocket | null = null
let reconnectTimer: number | null = null

const filteredEvents = computed(() => {
  if (!selectedServiceType.value) {
    return events.value.slice(0, 50)
  }
  return events.value
    .filter(e => e.serviceType === selectedServiceType.value)
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

const getPercentage = (count: number, total: number) => {
  if (!total) return 0
  return Math.round((count / total) * 100)
}

const getStrategyTagType = (strategy: string) => {
  const typeMap: Record<string, string> = {
    'RandomLoadBalancer': 'info',
    'RoundRobinLoadBalancer': 'primary',
    'LeastConnectionsLoadBalancer': 'success',
    'IpHashLoadBalancer': 'warning',
    'ConsistentHashLoadBalancer': 'danger'
  }
  return typeMap[strategy] || ''
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
    Object.values(response.data).forEach((serviceEvents: any) => {
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
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const wsUrl = `${protocol}//${window.location.host}/ws/routing-monitor`

  ws = new WebSocket(wsUrl)

  ws.onopen = () => {
    wsConnected.value = true
    console.log('WebSocket connected to routing monitor')
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
    console.log('WebSocket disconnected from routing monitor')
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

    ElMessage.success(`${serviceType} 监控${isPaused ? '已恢复' : '已暂停'}`)
  } catch (error) {
    ElMessage.error('操作失败')
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

    ElMessage.success(`已导出 ${command.toUpperCase()} 文件`)
  } catch (error) {
    ElMessage.error('导出失败')
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
.load-balancer-monitoring {
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

.stats-card {
  height: 100%;
}

.service-stats {
  padding: 8px;
}

.service-header {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.stats-content {
  margin-bottom: 12px;
}

.stat-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  border-bottom: 1px dashed #ebeef5;
}

.stat-row:last-child {
  border-bottom: none;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

.stat-value {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.instance-distribution {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
  max-height: 300px;
  overflow-y: auto;
}

.distribution-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.model-group {
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #e4e7ed;
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
  color: #409eff;
  margin-bottom: 6px;
}

.fallback-distribution {
  /* 无模型数据时的备用显示 */
}

.distribution-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.distribution-item:last-child {
  margin-bottom: 0;
}

.instance-name {
  font-size: 12px;
  color: #606266;
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
  color: #409eff;
  width: 40px;
  text-align: right;
}

.service-actions {
  text-align: center;
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

.client-id {
  font-family: monospace;
  font-size: 12px;
  color: #909399;
}

.slow-select {
  color: #e6a23c;
  font-weight: 500;
}
</style>
