<template>
  <div class="call-history-dashboard">
    <!-- 时间筛选 -->
    <el-card class="filter-card" shadow="hover">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 400px"
            @change="handleDateChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计概览卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #409EFF;">
              <el-icon><DataAnalysis /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(statistics.totalRequests) }}</div>
              <div class="stat-label">总请求数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #67C23A;">
              <el-icon><CircleCheck /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.successRate?.toFixed(2) || 0 }}%</div>
              <div class="stat-label">成功率</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #909399;">
              <el-icon><Clock /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.avgResponseTimeMs?.toFixed(0) || 0 }}</div>
              <div class="stat-label">平均响应时间 (ms)</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #E6A23C;">
              <el-icon><Tickets /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(statistics.totalTokens) }}</div>
              <div class="stat-label">总 Token 消耗</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #F56C6C;">
              <el-icon><WarningFilled /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(statistics.failedRequests) }}</div>
              <div class="stat-label">失败请求数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #67C23A;">
              <el-icon><Grid /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.byModel?.length || 0 }}</div>
              <div class="stat-label">模型数量</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #409EFF;">
              <el-icon><Service /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.byServiceType?.length || 0 }}</div>
              <div class="stat-label">服务类型数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #E6A23C;">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(statistics.avgTokensPerRequest) }}</div>
              <div class="stat-label">平均 Token / 请求</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20">
      <!-- 请求趋势 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">每日请求趋势</span>
          </template>
          <div ref="dailyChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 模型分布 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">模型调用分布 Top 10</span>
          </template>
          <div ref="modelChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 服务类型分布 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">服务类型调用分布</span>
          </template>
          <div ref="serviceTypeChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 小时分布 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">24 小时请求分布</span>
          </template>
          <div ref="hourlyChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 状态码分布 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">HTTP 状态码分布</span>
          </template>
          <div ref="statusCodeChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 错误码分布 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">错误码分布</span>
          </template>
          <div ref="errorCodeChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近调用记录 -->
    <el-card shadow="hover" style="margin-top: 20px;">
      <template #header>
        <span class="chart-title">最近调用记录</span>
      </template>
      <el-table :data="recentCalls" border stripe :max-height="400">
        <el-table-column label="时间" prop="createdAt" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="模型名称" prop="modelName" min-width="160" show-overflow-tooltip />
        <el-table-column label="服务类型" prop="serviceType" width="110">
          <template #default="scope">
            <el-tag size="small">{{ getServiceTypeLabel(scope.row.serviceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提供商" prop="provider" width="100" show-overflow-tooltip />
        <el-table-column label="HTTP 状态" prop="httpStatusCode" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.httpStatusCode)" size="small">
              {{ scope.row.httpStatusCode || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Token" prop="totalTokens" width="100" align="right">
          <template #default="scope">
            {{ formatNumber(scope.row.totalTokens) }}
          </template>
        </el-table-column>
        <el-table-column label="响应时间 (ms)" prop="responseTimeMs" width="120" align="right">
          <template #default="scope">
            <el-tag :type="getResponseTimeType(scope.row.responseTimeMs)" size="small">
              {{ scope.row.responseTimeMs?.toFixed(0) || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.isSuccess ? 'success' : 'danger'" size="small">
              {{ scope.row.isSuccess ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="错误信息" prop="errorMessage" min-width="180" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.errorMessage" class="error-message">
              {{ scope.row.errorMessage }}
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  DataAnalysis,
  CircleCheck,
  Clock,
  Tickets,
  WarningFilled,
  Grid,
  Service,
  Document,
  Search,
  Refresh
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import {
  getCallHistoryDashboard,
  getCallHistoryStatistics
} from '@/api/callHistory'
import type {
  CallHistoryStatistics,
  ApiCallHistoryRecord,
  RecorderStats
} from '@/types/callHistory'

// 时间范围
const dateRange = ref<[string, string] | null>(null)

// 统计数据
const statistics = ref<CallHistoryStatistics>({
  startTime: '',
  endTime: '',
  totalRequests: 0,
  successfulRequests: 0,
  failedRequests: 0,
  successRate: 0,
  totalTokens: 0,
  avgResponseTimeMs: 0,
  avgTokensPerRequest: 0,
  byModel: [],
  byServiceType: [],
  byDay: [],
  byHour: [],
  byStatusCode: [],
  byErrorCode: []
})

// 最近调用记录
const recentCalls = ref<ApiCallHistoryRecord[]>([])

// 图表引用
const dailyChartRef = ref<HTMLElement | null>(null)
const modelChartRef = ref<HTMLElement | null>(null)
const serviceTypeChartRef = ref<HTMLElement | null>(null)
const hourlyChartRef = ref<HTMLElement | null>(null)
const statusCodeChartRef = ref<HTMLElement | null>(null)
const errorCodeChartRef = ref<HTMLElement | null>(null)

// 图表实例
let dailyChart: ECharts | null = null
let modelChart: ECharts | null = null
let serviceTypeChart: ECharts | null = null
let hourlyChart: ECharts | null = null
let statusCodeChart: ECharts | null = null
let errorCodeChart: ECharts | null = null

// 格式化数字（千分位）
const formatNumber = (num?: number): string => {
  if (!num && num !== 0) return '0'
  return num.toLocaleString()
}

// 格式化时间
const formatTime = (time?: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

// 获取服务类型标签
const getServiceTypeLabel = (type?: string) => {
  if (!type) return '未知'
  const labels: Record<string, string> = {
    chat: '聊天',
    embedding: '嵌入',
    rerank: '重排序',
    tts: '语音合成',
    stt: '语音识别',
    imgGen: '图像生成',
    imgEdit: '图像编辑'
  }
  return labels[type] || type
}

// 获取 HTTP 状态码标签类型
const getStatusType = (code?: number) => {
  if (!code) return 'info'
  if (code >= 200 && code < 300) return 'success'
  if (code >= 400 && code < 500) return 'warning'
  if (code >= 500) return 'danger'
  return 'info'
}

// 获取响应时间标签类型
const getResponseTimeType = (ms?: number) => {
  if (!ms) return 'info'
  if (ms < 500) return 'success'
  if (ms < 1000) return 'warning'
  return 'danger'
}

// 初始化图表
const initCharts = () => {
  nextTick(() => {
    if (dailyChartRef.value) {
      dailyChart = echarts.init(dailyChartRef.value)
      updateDailyChart()
    }
    if (modelChartRef.value) {
      modelChart = echarts.init(modelChartRef.value)
      updateModelChart()
    }
    if (serviceTypeChartRef.value) {
      serviceTypeChart = echarts.init(serviceTypeChartRef.value)
      updateServiceTypeChart()
    }
    if (hourlyChartRef.value) {
      hourlyChart = echarts.init(hourlyChartRef.value)
      updateHourlyChart()
    }
    if (statusCodeChartRef.value) {
      statusCodeChart = echarts.init(statusCodeChartRef.value)
      updateStatusCodeChart()
    }
    if (errorCodeChartRef.value) {
      errorCodeChart = echarts.init(errorCodeChartRef.value)
      updateErrorCodeChart()
    }
  })
}

// 更新每日趋势图表
const updateDailyChart = () => {
  if (!dailyChart) return

  const data = (statistics.value.byDay || []).map(item => ({
    date: item.date,
    requestCount: item.requestCount,
    totalTokens: item.totalTokens
  }))

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: ['请求数', '总 Token']
    },
    xAxis: {
      type: 'category',
      data: data.map(item => item.date),
      boundaryGap: false
    },
    yAxis: [
      {
        type: 'value',
        name: '请求数',
        position: 'left'
      },
      {
        type: 'value',
        name: 'Token',
        position: 'right'
      }
    ],
    series: [
      {
        name: '请求数',
        type: 'bar',
        data: data.map(item => item.requestCount),
        itemStyle: { color: '#409EFF' }
      },
      {
        name: '总 Token',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: data.map(item => item.totalTokens),
        itemStyle: { color: '#E6A23C' }
      }
    ]
  }

  dailyChart.setOption(option)
}

// 更新模型图表
const updateModelChart = () => {
  if (!modelChart) return

  const data = (statistics.value.byModel || [])
    .slice(0, 10)
    .map(item => ({
      name: item.modelName,
      value: item.requestCount
    }))

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} 次 ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      type: 'scroll'
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        left: '15%',
        data,
        label: {
          show: true,
          formatter: '{b}: {c}'
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }

  modelChart.setOption(option)
}

// 更新服务类型图表
const updateServiceTypeChart = () => {
  if (!serviceTypeChart) return

  const data = (statistics.value.byServiceType || []).map(item => ({
    name: getServiceTypeLabel(item.serviceType),
    value: item.requestCount
  }))

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      type: 'category',
      data: data.map(item => item.name),
      axisLabel: {
        interval: 0,
        rotate: 0
      }
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        type: 'bar',
        data: data.map(item => item.value),
        itemStyle: {
          color: '#409EFF'
        },
        label: {
          show: true,
          position: 'top'
        }
      }
    ]
  }

  serviceTypeChart.setOption(option)
}

// 更新小时分布图表
const updateHourlyChart = () => {
  if (!hourlyChart) return

  // 初始化 24 小时数据
  const hourlyData = new Array(24).fill(0)
  ;(statistics.value.byHour || []).forEach(item => {
    if (item.hour >= 0 && item.hour < 24) {
      hourlyData[item.hour] = item.requestCount
    }
  })

  const hours = Array.from({ length: 24 }, (_, i) => `${i.toString().padStart(2, '0')}:00`)

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      type: 'category',
      data: hours
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '请求数',
        type: 'bar',
        data: hourlyData,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#83bff6' },
            { offset: 0.5, color: '#188df0' },
            { offset: 1, color: '#188df0' }
          ])
        }
      }
    ]
  }

  hourlyChart.setOption(option)
}

// 更新状态码图表
const updateStatusCodeChart = () => {
  if (!statusCodeChart) return

  const data = (statistics.value.byStatusCode || []).map(item => ({
    name: `${item.statusCode}`,
    value: item.count
  }))

  const getColor = (code: string) => {
    const num = parseInt(code)
    if (num >= 200 && num < 300) return '#67C23A'
    if (num >= 300 && num < 400) return '#409EFF'
    if (num >= 400 && num < 500) return '#E6A23C'
    if (num >= 500) return '#F56C6C'
    return '#909399'
  }

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} 次 ({d}%)'
    },
    series: [
      {
        type: 'pie',
        radius: ['35%', '65%'],
        data: data.map(item => ({
          name: item.name,
          value: item.value,
          itemStyle: { color: getColor(item.name) }
        })),
        label: {
          show: true,
          formatter: '{b}: {c}'
        }
      }
    ]
  }

  statusCodeChart.setOption(option)
}

// 更新错误码图表
const updateErrorCodeChart = () => {
  if (!errorCodeChart) return

  const data = (statistics.value.byErrorCode || [])
    .filter(item => item.errorCode)
    .slice(0, 10)
    .map(item => ({
      name: item.errorCode,
      value: item.count
    }))

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      type: 'category',
      data: data.map(item => item.name),
      axisLabel: {
        interval: 0,
        rotate: 30,
        fontSize: 11
      }
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        type: 'bar',
        data: data.map(item => item.value),
        itemStyle: {
          color: '#F56C6C'
        },
        label: {
          show: true,
          position: 'top'
        }
      }
    ]
  }

  errorCodeChart.setOption(option)
}

// 加载数据
const loadData = async () => {
  try {
    let startTime: string | undefined
    let endTime: string | undefined

    if (dateRange.value && dateRange.value.length === 2) {
      startTime = dateRange.value[0].replace(' ', 'T')
      endTime = dateRange.value[1].replace(' ', 'T')
    }

    // 加载统计数据
    statistics.value = await getCallHistoryStatistics(startTime, endTime)

    // 加载最近调用记录
    const dashboard = await getCallHistoryDashboard(startTime, endTime)
    recentCalls.value = dashboard.recentCalls || []

    // 更新图表
    initCharts()
  } catch (error: any) {
    console.error('加载调用历史数据失败:', error)
    ElMessage.error(`加载调用历史数据失败：${error.message || '未知错误'}`)
  }
}

// 日期变化处理
const handleDateChange = () => {
  loadData()
}

// 重置
const handleReset = () => {
  dateRange.value = null
  loadData()
}

// 窗口大小变化时重新渲染图表
const handleResize = () => {
  dailyChart?.resize()
  modelChart?.resize()
  serviceTypeChart?.resize()
  hourlyChart?.resize()
  statusCodeChart?.resize()
  errorCodeChart?.resize()
}

// 初始化
onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

// 组件卸载时清理
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  dailyChart?.dispose()
  modelChart?.dispose()
  serviceTypeChart?.dispose()
  hourlyChart?.dispose()
  statusCodeChart?.dispose()
  errorCodeChart?.dispose()
})
</script>

<style scoped>
.call-history-dashboard {
  padding: 20px;
}

.call-history-dashboard .filter-card {
  margin-bottom: 20px;
}

.call-history-dashboard .filter-card .filter-form {
  display: flex;
  justify-content: center;
}

.call-history-dashboard .stats-row {
  margin-bottom: 20px;
}

.call-history-dashboard .stats-row .stat-card .stat-content {
  display: flex;
  align-items: center;
}

.call-history-dashboard .stats-row .stat-card .stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 15px;
  color: white;
  font-size: 28px;
}

.call-history-dashboard .stats-row .stat-card .stat-info .stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

.call-history-dashboard .stats-row .stat-card .stat-info .stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 5px;
}

.call-history-dashboard .chart-container {
  height: 300px;
  width: 100%;
}

.call-history-dashboard .chart-title {
  font-size: 16px;
  font-weight: bold;
}

.call-history-dashboard .error-message {
  color: #F56C6C;
  font-size: 12px;
}

.call-history-dashboard .text-muted {
  color: #C0C4CC;
}
</style>
