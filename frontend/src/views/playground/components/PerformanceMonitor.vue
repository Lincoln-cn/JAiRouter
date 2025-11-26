<template>
  <div class="performance-monitor">
    <div class="monitor-header">
      <h4>性能监控</h4>
      <el-button 
        size="small" 
        text 
        :icon="Refresh"
        @click="clearHistory"
        :disabled="!hasHistory"
      >
        清除历史
      </el-button>
    </div>

    <div v-if="!currentMetrics && !hasHistory" class="no-data">
      <el-empty description="暂无性能数据" />
    </div>

    <div v-else class="monitor-content">
      <!-- 当前请求指标 -->
      <div v-if="currentMetrics" class="current-metrics">
        <div class="metrics-title">当前请求</div>
        <div class="metrics-grid">
          <div class="metric-item">
            <div class="metric-label">响应时间</div>
            <div class="metric-value" :class="getDurationClass(currentMetrics.duration)">
              {{ formatDuration(currentMetrics.duration) }}
            </div>
          </div>
          
          <div class="metric-item">
            <div class="metric-label">请求大小</div>
            <div class="metric-value">
              {{ formatFileSize(currentMetrics.requestSize) }}
            </div>
          </div>
          
          <div class="metric-item">
            <div class="metric-label">响应大小</div>
            <div class="metric-value">
              {{ formatFileSize(currentMetrics.responseSize) }}
            </div>
          </div>
          
          <div class="metric-item">
            <div class="metric-label">状态码</div>
            <div class="metric-value">
              <el-tag :type="getStatusTagType(currentMetrics.status)" size="small">
                {{ currentMetrics.status }}
              </el-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- 历史统计 -->
      <div v-if="hasHistory" class="history-stats">
        <div class="stats-title">历史统计</div>
        <div class="stats-grid">
          <div class="stat-item">
            <div class="stat-label">总请求数</div>
            <div class="stat-value">{{ historyStats.totalRequests }}</div>
          </div>
          
          <div class="stat-item">
            <div class="stat-label">成功率</div>
            <div class="stat-value" :class="getSuccessRateClass(historyStats.successRate)">
              {{ (historyStats.successRate * 100).toFixed(1) }}%
            </div>
          </div>
          
          <div class="stat-item">
            <div class="stat-label">平均响应时间</div>
            <div class="stat-value">
              {{ formatDuration(historyStats.avgDuration) }}
            </div>
          </div>
          
          <div class="stat-item">
            <div class="stat-label">最快响应</div>
            <div class="stat-value text-success">
              {{ formatDuration(historyStats.minDuration) }}
            </div>
          </div>
          
          <div class="stat-item">
            <div class="stat-label">最慢响应</div>
            <div class="stat-value text-warning">
              {{ formatDuration(historyStats.maxDuration) }}
            </div>
          </div>
          
          <div class="stat-item">
            <div class="stat-label">总传输量</div>
            <div class="stat-value">
              {{ formatFileSize(historyStats.totalBytes) }}
            </div>
          </div>
        </div>
      </div>

      <!-- 响应时间趋势图 -->
      <div v-if="hasHistory && performanceHistory.length > 1" class="performance-chart">
        <div class="chart-title">响应时间趋势</div>
        <div class="chart-container">
          <canvas ref="chartCanvas" width="400" height="200"></canvas>
        </div>
      </div>

      <!-- 最近请求列表 -->
      <div v-if="hasHistory" class="recent-requests">
        <div class="requests-title">
          最近请求
          <el-tag size="small" type="info">{{ Math.min(performanceHistory.length, 10) }}/{{ performanceHistory.length }}</el-tag>
        </div>
        <div class="requests-list">
          <div 
            v-for="(record, index) in recentRequests" 
            :key="index"
            class="request-record"
            :class="{ 'current': index === 0 && currentMetrics }"
          >
            <div class="record-time">
              {{ formatTime(record.timestamp) }}
            </div>
            <div class="record-method">
              <el-tag :type="getMethodTagType(record.method)" size="small">
                {{ record.method }}
              </el-tag>
            </div>
            <div class="record-endpoint">
              {{ record.endpoint }}
            </div>
            <div class="record-status">
              <el-tag :type="getStatusTagType(record.status)" size="small">
                {{ record.status }}
              </el-tag>
            </div>
            <div class="record-duration" :class="getDurationClass(record.duration)">
              {{ formatDuration(record.duration) }}
            </div>
            <div class="record-size">
              {{ formatFileSize(record.responseSize) }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { formatFileSize, formatDuration } from '@/api/playground'

interface PerformanceMetrics {
  duration: number
  requestSize: number
  responseSize: number
  status: number
  method: string
  endpoint: string
  timestamp: string
}

interface Props {
  currentMetrics?: PerformanceMetrics | null
}

const props = defineProps<Props>()

const chartCanvas = ref<HTMLCanvasElement>()
const performanceHistory = ref<PerformanceMetrics[]>([])

// 计算属性
const hasHistory = computed(() => performanceHistory.value.length > 0)

const historyStats = computed(() => {
  if (!hasHistory.value) {
    return {
      totalRequests: 0,
      successRate: 0,
      avgDuration: 0,
      minDuration: 0,
      maxDuration: 0,
      totalBytes: 0
    }
  }

  const history = performanceHistory.value
  const successCount = history.filter(r => r.status >= 200 && r.status < 400).length
  const durations = history.map(r => r.duration)
  const totalBytes = history.reduce((sum, r) => sum + r.requestSize + r.responseSize, 0)

  return {
    totalRequests: history.length,
    successRate: successCount / history.length,
    avgDuration: durations.reduce((sum, d) => sum + d, 0) / durations.length,
    minDuration: Math.min(...durations),
    maxDuration: Math.max(...durations),
    totalBytes
  }
})

const recentRequests = computed(() => {
  return performanceHistory.value.slice(0, 10)
})

// 方法
const addMetrics = (metrics: PerformanceMetrics) => {
  performanceHistory.value.unshift(metrics)
  
  // 限制历史记录数量
  if (performanceHistory.value.length > 100) {
    performanceHistory.value = performanceHistory.value.slice(0, 100)
  }
  
  // 更新图表
  nextTick(() => {
    updateChart()
  })
}

const clearHistory = () => {
  performanceHistory.value = []
  clearChart()
}

const getDurationClass = (duration: number) => {
  if (duration < 1000) return 'text-success'
  if (duration < 3000) return 'text-warning'
  return 'text-danger'
}

const getStatusTagType = (status: number) => {
  if (status >= 200 && status < 300) return 'success'
  if (status >= 300 && status < 400) return 'info'
  if (status >= 400 && status < 500) return 'warning'
  return 'danger'
}

const getMethodTagType = (method: string) => {
  const types: Record<string, string> = {
    GET: 'success',
    POST: 'primary',
    PUT: 'warning',
    DELETE: 'danger',
    PATCH: 'info'
  }
  return types[method] || 'info'
}

const getSuccessRateClass = (rate: number) => {
  if (rate >= 0.95) return 'text-success'
  if (rate >= 0.8) return 'text-warning'
  return 'text-danger'
}

const formatTime = (timestamp: string) => {
  return new Date(timestamp).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const updateChart = () => {
  if (!chartCanvas.value || performanceHistory.value.length < 2) return

  const canvas = chartCanvas.value
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const width = canvas.width
  const height = canvas.height
  const padding = 40

  // 清空画布
  ctx.clearRect(0, 0, width, height)

  // 获取最近20个数据点
  const data = performanceHistory.value.slice(0, 20).reverse()
  const maxDuration = Math.max(...data.map(d => d.duration))
  const minDuration = Math.min(...data.map(d => d.duration))
  const range = maxDuration - minDuration || 1

  // 绘制网格线
  ctx.strokeStyle = '#e4e7ed'
  ctx.lineWidth = 1
  
  // 水平网格线
  for (let i = 0; i <= 4; i++) {
    const y = padding + (height - 2 * padding) * i / 4
    ctx.beginPath()
    ctx.moveTo(padding, y)
    ctx.lineTo(width - padding, y)
    ctx.stroke()
  }
  
  // 垂直网格线
  for (let i = 0; i <= 4; i++) {
    const x = padding + (width - 2 * padding) * i / 4
    ctx.beginPath()
    ctx.moveTo(x, padding)
    ctx.lineTo(x, height - padding)
    ctx.stroke()
  }

  // 绘制数据线
  ctx.strokeStyle = '#409eff'
  ctx.lineWidth = 2
  ctx.beginPath()

  data.forEach((point, index) => {
    const x = padding + (width - 2 * padding) * index / (data.length - 1)
    const y = height - padding - (height - 2 * padding) * (point.duration - minDuration) / range
    
    if (index === 0) {
      ctx.moveTo(x, y)
    } else {
      ctx.lineTo(x, y)
    }
  })
  
  ctx.stroke()

  // 绘制数据点
  ctx.fillStyle = '#409eff'
  data.forEach((point, index) => {
    const x = padding + (width - 2 * padding) * index / (data.length - 1)
    const y = height - padding - (height - 2 * padding) * (point.duration - minDuration) / range
    
    ctx.beginPath()
    ctx.arc(x, y, 3, 0, 2 * Math.PI)
    ctx.fill()
    
    // 标记错误状态的点
    if (point.status >= 400) {
      ctx.fillStyle = '#f56c6c'
      ctx.beginPath()
      ctx.arc(x, y, 5, 0, 2 * Math.PI)
      ctx.fill()
      ctx.fillStyle = '#409eff'
    }
  })

  // 绘制Y轴标签
  ctx.fillStyle = '#606266'
  ctx.font = '12px Arial'
  ctx.textAlign = 'right'
  
  for (let i = 0; i <= 4; i++) {
    const value = minDuration + range * (4 - i) / 4
    const y = padding + (height - 2 * padding) * i / 4
    ctx.fillText(formatDuration(value), padding - 5, y + 4)
  }
}

const clearChart = () => {
  if (!chartCanvas.value) return
  
  const ctx = chartCanvas.value.getContext('2d')
  if (ctx) {
    ctx.clearRect(0, 0, chartCanvas.value.width, chartCanvas.value.height)
  }
}

// 监听当前指标变化
watch(() => props.currentMetrics, (newMetrics) => {
  if (newMetrics) {
    addMetrics(newMetrics)
  }
}, { immediate: true })

// 组件挂载后初始化图表
onMounted(() => {
  if (hasHistory.value) {
    nextTick(() => {
      updateChart()
    })
  }
})

// 暴露方法给父组件
defineExpose({
  addMetrics,
  clearHistory
})
</script>

<style scoped>
.performance-monitor {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-bg-color);
}

.monitor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color-page);
}

.monitor-header h4 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.no-data {
  padding: 40px;
  text-align: center;
}

.monitor-content {
  padding: 16px;
}

.current-metrics {
  margin-bottom: 24px;
}

.metrics-title, .stats-title, .chart-title, .requests-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.metrics-grid, .stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 16px;
}

.metric-item, .stat-item {
  padding: 12px;
  background: var(--el-bg-color-page);
  border-radius: 6px;
  text-align: center;
}

.metric-label, .stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 4px;
}

.metric-value, .stat-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.text-success {
  color: var(--el-color-success) !important;
}

.text-warning {
  color: var(--el-color-warning) !important;
}

.text-danger {
  color: var(--el-color-danger) !important;
}

.history-stats {
  margin-bottom: 24px;
}

.performance-chart {
  margin-bottom: 24px;
}

.chart-container {
  padding: 16px;
  background: var(--el-bg-color-page);
  border-radius: 6px;
  text-align: center;
}

.chart-container canvas {
  max-width: 100%;
  height: auto;
}

.recent-requests {
  margin-bottom: 16px;
}

.requests-list {
  background: var(--el-bg-color-page);
  border-radius: 6px;
  overflow: hidden;
}

.request-record {
  display: grid;
  grid-template-columns: 80px 60px 1fr 60px 80px 80px;
  gap: 12px;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-size: 12px;
  transition: background-color 0.2s;
}

.request-record:hover {
  background: var(--el-bg-color);
}

.request-record.current {
  background: var(--el-color-primary-light-9);
  border-left: 3px solid var(--el-color-primary);
}

.request-record:last-child {
  border-bottom: none;
}

.record-time {
  color: var(--el-text-color-secondary);
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
}

.record-endpoint {
  color: var(--el-text-color-primary);
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-duration {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-weight: 600;
  text-align: right;
}

.record-size {
  color: var(--el-text-color-secondary);
  text-align: right;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .metrics-grid, .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .request-record {
    grid-template-columns: 1fr;
    gap: 4px;
    padding: 12px;
  }
  
  .record-time, .record-method, .record-status, .record-duration, .record-size {
    justify-self: start;
  }
  
  .chart-container canvas {
    width: 100%;
    height: 150px;
  }
}

@media (max-width: 480px) {
  .metrics-grid, .stats-grid {
    grid-template-columns: 1fr;
  }
  
  .monitor-header {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
  }
}
</style>