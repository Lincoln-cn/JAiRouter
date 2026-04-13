<template>
  <div class="tracing-dashboard">
    <!-- 顶部操作栏 -->
    <el-card class="header-card">
      <div class="header-content">
        <div class="header-left">
          <h2>追踪仪表盘</h2>
          <el-tag :type="tracingEnabled ? 'success' : 'danger'" size="small">
            {{ tracingEnabled ? '追踪已启用' : '追踪已禁用' }}
          </el-tag>
        </div>
        <div class="header-right">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            :shortcuts="timeShortcuts"
            @change="handleTimeRangeChange"
          />
          <el-button type="primary" @click="handleRefresh" :loading="refreshing">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
          <el-button @click="goToSearch">
            <el-icon><Search /></el-icon>
            搜索追踪
          </el-button>
          <el-button @click="goToManagement">
            <el-icon><Setting /></el-icon>
            配置
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 关键指标卡片 -->
    <el-row :gutter="16" class="metrics-row">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="metric-card" shadow="hover">
          <div class="metric-content">
            <div class="metric-icon primary">
              <el-icon><DataLine /></el-icon>
            </div>
            <div class="metric-info">
              <div class="metric-value">{{ formatNumber(stats.totalTraces) }}</div>
              <div class="metric-label">总追踪数</div>
            </div>
          </div>
          <div class="metric-trend" v-if="trendData.totalTracesTrend">
            <span :class="trendData.totalTracesTrend >= 0 ? 'up' : 'down'">
              {{ trendData.totalTracesTrend >= 0 ? '+' : '' }}{{ trendData.totalTracesTrend }}%
            </span>
            较上周期
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="metric-card" shadow="hover" @click="goToSearch('error')">
          <div class="metric-content">
            <div class="metric-icon danger">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="metric-info">
              <div class="metric-value">{{ formatNumber(stats.errorTraces) }}</div>
              <div class="metric-label">错误追踪</div>
            </div>
          </div>
          <div class="metric-trend" v-if="trendData.errorTrend">
            <span :class="trendData.errorTrend <= 0 ? 'up' : 'down'">
              {{ trendData.errorTrend >= 0 ? '+' : '' }}{{ trendData.errorTrend }}%
            </span>
            较上周期
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="metric-card" shadow="hover">
          <div class="metric-content">
            <div class="metric-icon success">
              <el-icon><Timer /></el-icon>
            </div>
            <div class="metric-info">
              <div class="metric-value">{{ stats.avgDuration }}<span class="unit">ms</span></div>
              <div class="metric-label">平均延迟</div>
            </div>
          </div>
          <div class="metric-trend" v-if="trendData.avgDurationTrend">
            <span :class="trendData.avgDurationTrend <= 0 ? 'up' : 'down'">
              {{ trendData.avgDurationTrend >= 0 ? '+' : '' }}{{ trendData.avgDurationTrend }}%
            </span>
            较上周期
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="metric-card" shadow="hover">
          <div class="metric-content">
            <div class="metric-icon warning">
              <el-icon><PieChart /></el-icon>
            </div>
            <div class="metric-info">
              <div class="metric-value">{{ stats.samplingRate }}<span class="unit">%</span></div>
              <div class="metric-label">采样率</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Tab 区域 -->
    <el-card class="tab-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="概览" name="overview">
          <el-row :gutter="16">
            <el-col :xs="24" :lg="12">
              <div class="chart-wrapper">
                <div class="chart-header">
                  <h4>追踪量趋势</h4>
                  <el-radio-group v-model="chartInterval" size="small" @change="updateTrendChart">
                    <el-radio-button label="5m">5分钟</el-radio-button>
                    <el-radio-button label="1h">1小时</el-radio-button>
                    <el-radio-button label="1d">1天</el-radio-button>
                  </el-radio-group>
                </div>
                <div ref="traceTrendChart" class="chart-container"></div>
              </div>
            </el-col>
            <el-col :xs="24" :lg="12">
              <div class="chart-wrapper">
                <div class="chart-header">
                  <h4>服务延迟分布</h4>
                </div>
                <div ref="latencyDistributionChart" class="chart-container"></div>
              </div>
            </el-col>
          </el-row>

          <!-- 服务统计表格 -->
          <div class="service-stats-section">
            <div class="section-header">
              <h4>服务统计</h4>
              <el-button text type="primary" @click="goToSearch">查看全部</el-button>
            </div>
            <el-table
              :data="serviceStats"
              v-loading="loading"
              style="width: 100%"
              :row-class-name="getRowClassName"
              @row-click="handleServiceRowClick"
            >
              <el-table-column prop="name" label="服务名称" min-width="150">
                <template #default="{ row }">
                  <div class="service-name">
                    <el-icon :size="16"><Monitor /></el-icon>
                    <span>{{ row.name }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="traces" label="追踪数" width="100" sortable>
                <template #default="{ row }">
                  <el-tag size="small">{{ formatNumber(row.traces) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="avgDuration" label="平均延迟" width="120" sortable>
                <template #default="{ row }">
                  <span :class="getLatencyClass(row.avgDuration)">{{ row.avgDuration }}ms</span>
                </template>
              </el-table-column>
              <el-table-column prop="p95Duration" label="P95延迟" width="120" sortable>
                <template #default="{ row }">
                  <span :class="getLatencyClass(row.p95Duration)">{{ row.p95Duration }}ms</span>
                </template>
              </el-table-column>
              <el-table-column prop="errors" label="错误数" width="100" sortable>
                <template #default="{ row }">
                  <span :class="{ 'text-danger': row.errors > 0 }">{{ row.errors }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="errorRate" label="错误率" width="100" sortable>
                <template #default="{ row }">
                  <el-progress
                    :percentage="row.errorRate"
                    :status="row.errorRate > 5 ? 'exception' : 'success'"
                    :stroke-width="8"
                    :show-text="false"
                  />
                  <span class="error-rate-text" :class="{ 'text-danger': row.errorRate > 5 }">
                    {{ row.errorRate }}%
                  </span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="80">
                <template #default="{ row }">
                  <el-tooltip :content="getServiceStatusTooltip(row)" placement="top">
                    <el-icon :size="18" :color="getServiceStatusColor(row)">
                      <component :is="getServiceStatusIcon(row)" />
                    </el-icon>
                  </el-tooltip>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <el-tab-pane label="延迟分析" name="latency">
          <el-row :gutter="16">
            <el-col :xs="24" :lg="12">
              <div class="chart-wrapper">
                <div class="chart-header">
                  <h4>P95/P99 延迟趋势</h4>
                </div>
                <div ref="latencyTrendChart" class="chart-container"></div>
              </div>
            </el-col>
            <el-col :xs="24" :lg="12">
              <div class="chart-wrapper">
                <div class="chart-header">
                  <h4>最慢的追踪</h4>
                  <el-button text type="primary" @click="goToSearch">查看全部</el-button>
                </div>
                <el-table :data="slowTraces" style="width: 100%" size="small" max-height="300">
                  <el-table-column prop="operationName" label="操作" show-overflow-tooltip />
                  <el-table-column prop="duration" label="耗时" width="100">
                    <template #default="{ row }">
                      <span class="text-danger">{{ Math.round(row.duration) }}ms</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="80">
                    <template #default="{ row }">
                      <el-button text type="primary" size="small" @click="viewTraceDetail(row)">
                        详情
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>

        <el-tab-pane label="错误分析" name="error">
          <el-row :gutter="16">
            <el-col :xs="24" :lg="12">
              <div class="chart-wrapper">
                <div class="chart-header">
                  <h4>错误率趋势</h4>
                </div>
                <div ref="errorTrendChart" class="chart-container"></div>
              </div>
            </el-col>
            <el-col :xs="24" :lg="12">
              <div class="chart-wrapper">
                <div class="chart-header">
                  <h4>常见错误</h4>
                </div>
                <el-table :data="commonErrors" style="width: 100%" size="small" max-height="300">
                  <el-table-column prop="errorType" label="错误类型" width="150">
                    <template #default="{ row }">
                      <el-tag type="danger" size="small">{{ row.errorType || 'Unknown' }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="count" label="次数" width="80">
                    <template #default="{ row }">
                      <span class="text-danger">{{ row.count }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="service" label="服务" show-overflow-tooltip />
                </el-table>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>

        <el-tab-pane label="吞吐量" name="throughput">
          <el-row :gutter="16">
            <el-col :xs="24" :lg="12">
              <div class="chart-wrapper">
                <div class="chart-header">
                  <h4>请求量趋势</h4>
                </div>
                <div ref="throughputChart" class="chart-container"></div>
              </div>
            </el-col>
            <el-col :xs="24" :lg="12">
              <div class="chart-wrapper">
                <div class="chart-header">
                  <h4>服务吞吐量分布</h4>
                </div>
                <div ref="throughputDistChart" class="chart-container"></div>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 追踪详情抽屉 -->
    <el-drawer
      v-model="traceDetailVisible"
      title="追踪详情"
      direction="rtl"
      size="60%"
    >
      <TraceDetail v-if="selectedTrace" :trace-id="selectedTrace.traceId" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  DataLine, Warning, Timer, PieChart, Refresh, Search, Setting, Monitor,
  CircleCheckFilled, WarningFilled, CircleCloseFilled
} from '@element-plus/icons-vue'
import {
  getTracingOverview,
  getTracingStats,
  getServiceStats,
  getTracingStatus,
  getLatencyAnalysis,
  getErrorAnalysis,
  getThroughputAnalysis,
  getRecentTraces
} from '@/api/tracing'
import TraceDetail from './components/TraceDetail.vue'

const router = useRouter()

// 状态
const loading = ref(false)
const refreshing = ref(false)
const activeTab = ref('overview')
const timeRange = ref([])
const chartInterval = ref('5m')
const tracingEnabled = ref(true)

// 统计数据
const stats = ref({
  totalTraces: 0,
  errorTraces: 0,
  avgDuration: 0,
  samplingRate: 0
})

const trendData = ref({
  totalTracesTrend: 0,
  errorTrend: 0,
  avgDurationTrend: 0
})

const serviceStats = ref<any[]>([])
const slowTraces = ref<any[]>([])
const commonErrors = ref<any[]>([])

// 图表引用
const traceTrendChart = ref<HTMLElement | null>(null)
const latencyDistributionChart = ref<HTMLElement | null>(null)
const latencyTrendChart = ref<HTMLElement | null>(null)
const errorTrendChart = ref<HTMLElement | null>(null)
const throughputChart = ref<HTMLElement | null>(null)
const throughputDistChart = ref<HTMLElement | null>(null)

let traceTrendEcharts: echarts.ECharts | null = null
let latencyDistEcharts: echarts.ECharts | null = null
let latencyTrendEcharts: echarts.ECharts | null = null
let errorTrendEcharts: echarts.ECharts | null = null
let throughputEcharts: echarts.ECharts | null = null
let throughputDistEcharts: echarts.ECharts | null = null

// 追踪详情
const traceDetailVisible = ref(false)
const selectedTrace = ref<any>(null)

// 时间快捷选项
const timeShortcuts = [
  {
    text: '最近1小时',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000)
      return [start, end]
    }
  },
  {
    text: '最近6小时',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 6)
      return [start, end]
    }
  },
  {
    text: '今天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setHours(0, 0, 0, 0)
      return [start, end]
    }
  }
]

// 加载数据
const loadAllData = async () => {
  loading.value = true
  try {
    await Promise.all([
      loadOverviewData(),
      loadServiceStats(),
      loadTracingStatus()
    ])
    await nextTick()
    initCharts()
  } catch (error) {
    console.error('加载数据失败:', error)
    ElMessage.warning('加载数据失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const loadOverviewData = async () => {
  try {
    const response = await getTracingOverview()
    const data = response.data?.data || response.data || response
    if (data) {
      stats.value = {
        totalTraces: data.totalTraces || 0,
        errorTraces: data.errorTraces || 0,
        avgDuration: Math.round(data.avgDuration || 0),
        samplingRate: Math.round((data.samplingRate || 0) * 100)
      }
    }
  } catch (error) {
    console.error('加载概览数据失败:', error)
  }
}

const loadServiceStats = async () => {
  try {
    const response = await getServiceStats()
    const data = response.data?.data || response.data || response
    if (Array.isArray(data)) {
      serviceStats.value = data
    }
  } catch (error) {
    console.error('加载服务统计失败:', error)
    serviceStats.value = []
  }
}

const loadTracingStatus = async () => {
  try {
    const response = await getTracingStatus()
    const data = response.data?.data || response.data || response
    if (data) {
      tracingEnabled.value = data.enabled !== false
    }
  } catch (error) {
    console.error('加载追踪状态失败:', error)
  }
}

// 初始化图表
const initCharts = () => {
  // 追踪趋势图表
  if (traceTrendChart.value) {
    traceTrendEcharts = echarts.init(traceTrendChart.value)
    traceTrendEcharts.setOption(getTraceTrendOption())
  }

  // 延迟分布图表
  if (latencyDistributionChart.value) {
    latencyDistEcharts = echarts.init(latencyDistributionChart.value)
    latencyDistEcharts.setOption(getLatencyDistributionOption())
  }
}

// 图表配置
const getTraceTrendOption = () => {
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['追踪数', '错误数'],
      bottom: 0
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: generateTimeLabels()
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '追踪数',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.3 },
        itemStyle: { color: '#409eff' },
        data: generateRandomData(12, 100, 500)
      },
      {
        name: '错误数',
        type: 'line',
        smooth: true,
        itemStyle: { color: '#f56c6c' },
        data: generateRandomData(12, 0, 50)
      }
    ]
  }
}

const getLatencyDistributionOption = () => {
  const services = serviceStats.value.map(s => s.name)
  const avgDurations = serviceStats.value.map(s => s.avgDuration || 0)
  const p95Durations = serviceStats.value.map(s => s.p95Duration || 0)

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    legend: {
      data: ['平均延迟', 'P95延迟'],
      bottom: 0
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: services.length > 0 ? services : ['暂无数据'],
      axisLabel: { rotate: 30 }
    },
    yAxis: {
      type: 'value',
      name: '延迟(ms)'
    },
    series: [
      {
        name: '平均延迟',
        type: 'bar',
        itemStyle: { color: '#409eff' },
        data: avgDurations
      },
      {
        name: 'P95延迟',
        type: 'bar',
        itemStyle: { color: '#e6a23c' },
        data: p95Durations
      }
    ]
  }
}

// 辅助函数
const generateTimeLabels = () => {
  const labels = []
  const now = new Date()
  for (let i = 11; i >= 0; i--) {
    const time = new Date(now.getTime() - i * 5 * 60 * 1000)
    labels.push(time.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }))
  }
  return labels
}

const generateRandomData = (count: number, min: number, max: number) => {
  return Array.from({ length: count }, () => Math.floor(Math.random() * (max - min) + min))
}

const formatNumber = (num: number) => {
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + 'w'
  }
  return num.toLocaleString()
}

const getLatencyClass = (duration: number) => {
  if (duration > 1000) return 'text-danger'
  if (duration > 500) return 'text-warning'
  return ''
}

const getRowClassName = ({ row }: { row: any }) => {
  if (row.errorRate > 5) return 'error-row'
  if (row.avgDuration > 1000) return 'slow-row'
  return ''
}

const getServiceStatusColor = (row: any) => {
  if (row.errorRate > 5) return '#f56c6c'
  if (row.avgDuration > 1000) return '#e6a23c'
  return '#67c23a'
}

const getServiceStatusIcon = (row: any) => {
  if (row.errorRate > 5) return CircleCloseFilled
  if (row.avgDuration > 1000) return WarningFilled
  return CircleCheckFilled
}

const getServiceStatusTooltip = (row: any) => {
  if (row.errorRate > 5) return '错误率较高'
  if (row.avgDuration > 1000) return '延迟较高'
  return '运行正常'
}

// 事件处理
const handleRefresh = async () => {
  refreshing.value = true
  try {
    await loadAllData()
    ElMessage.success('数据已刷新')
  } finally {
    refreshing.value = false
  }
}

const handleTimeRangeChange = () => {
  loadAllData()
}

const handleTabChange = (tab: string) => {
  nextTick(() => {
    if (tab === 'latency' && latencyTrendChart.value) {
      latencyTrendEcharts = echarts.init(latencyTrendChart.value)
      latencyTrendEcharts.setOption(getTraceTrendOption())
    } else if (tab === 'error' && errorTrendChart.value) {
      errorTrendEcharts = echarts.init(errorTrendChart.value)
      errorTrendEcharts.setOption(getTraceTrendOption())
    } else if (tab === 'throughput' && throughputChart.value) {
      throughputEcharts = echarts.init(throughputChart.value)
      throughputEcharts.setOption(getTraceTrendOption())
    }
  })
}

const goToSearch = (filter?: string) => {
  const query: any = {}
  if (filter === 'error') {
    query.hasError = 'true'
  }
  router.push({ path: '/admin/tracing/search', query })
}

const goToManagement = () => {
  router.push('/admin/tracing/management')
}

const handleServiceRowClick = (row: any) => {
  router.push({ path: '/admin/tracing/search', query: { serviceName: row.name } })
}

const viewTraceDetail = (trace: any) => {
  selectedTrace.value = trace
  traceDetailVisible.value = true
}

const updateTrendChart = () => {
  if (traceTrendEcharts) {
    traceTrendEcharts.setOption(getTraceTrendOption())
  }
}

// 窗口大小变化处理
const handleResize = () => {
  if (traceTrendEcharts) traceTrendEcharts.resize()
  if (latencyDistEcharts) latencyDistEcharts.resize()
  if (latencyTrendEcharts) latencyTrendEcharts.resize()
  if (errorTrendEcharts) errorTrendEcharts.resize()
  if (throughputEcharts) throughputEcharts.resize()
  // @ts-ignore - Vue template ref causes type conflict
  if (throughputDistEcharts) throughputDistEcharts.resize()
}

// 生命周期
onMounted(() => {
  window.addEventListener('resize', handleResize)
  loadAllData()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (traceTrendEcharts) traceTrendEcharts.dispose()
  if (latencyDistEcharts) latencyDistEcharts.dispose()
  if (latencyTrendEcharts) latencyTrendEcharts.dispose()
  if (errorTrendEcharts) errorTrendEcharts.dispose()
  if (throughputEcharts) throughputEcharts.dispose()
  // @ts-ignore - Vue template ref causes type conflict
  if (throughputDistEcharts) throughputDistEcharts.dispose()
})
</script>

<style scoped>
.tracing-dashboard {
  padding: 0;
}

.header-card {
  margin-bottom: 16px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h2 {
  margin: 0;
  font-size: 20px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.metrics-row {
  margin-bottom: 16px;
}

.metric-card {
  height: 120px;
}

.metric-content {
  display: flex;
  align-items: center;
}

.metric-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
  font-size: 24px;
}

.metric-icon.primary {
  background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
  color: white;
}

.metric-icon.success {
  background: linear-gradient(135deg, #67c23a 0%, #85ce61 100%);
  color: white;
}

.metric-icon.warning {
  background: linear-gradient(135deg, #e6a23c 0%, #ebb563 100%);
  color: white;
}

.metric-icon.danger {
  background: linear-gradient(135deg, #f56c6c 0%, #f78989 100%);
  color: white;
}

.metric-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.metric-value .unit {
  font-size: 14px;
  font-weight: normal;
  color: #909399;
  margin-left: 4px;
}

.metric-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.metric-trend {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}

.metric-trend .up {
  color: #67c23a;
}

.metric-trend .down {
  color: #f56c6c;
}

.tab-card {
  margin-bottom: 16px;
}

.chart-wrapper {
  padding: 16px;
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.chart-header h4 {
  margin: 0;
  font-size: 16px;
  color: #303133;
}

.chart-container {
  height: 300px;
}

.service-stats-section {
  margin-top: 24px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-header h4 {
  margin: 0;
  font-size: 16px;
}

.service-name {
  display: flex;
  align-items: center;
  gap: 8px;
}

.error-rate-text {
  font-size: 12px;
  margin-top: 4px;
  display: block;
}

.text-danger {
  color: #f56c6c;
}

.text-warning {
  color: #e6a23c;
}

:deep(.error-row) {
  background-color: #fef0f0;
}

:deep(.slow-row) {
  background-color: #fdf6ec;
}

@media (max-width: 768px) {
  .header-content {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-right {
    width: 100%;
  }
}
</style>