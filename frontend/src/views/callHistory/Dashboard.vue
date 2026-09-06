<template>
  <PageSkeleton :title="t('callHistory.dashboard.title')">
    <template #toolbar>
      <!-- 时间筛选 -->
      <el-form :inline="true" class="filter-form">
        <el-form-item :label="t('callHistory.common.timeRange')">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            :range-separator="t('callHistory.common.rangeSeparator')"
            :start-placeholder="t('callHistory.common.startTime')"
            :end-placeholder="t('callHistory.common.endTime')"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 400px"
            @change="handleDateChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">{{ t('callHistory.common.query') }}</el-button>
          <el-button icon="Refresh" @click="handleReset">{{ t('callHistory.common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </template>

    <template #stats>
      <!-- 统计概览卡片 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :span="6">
          <StatCard :icon="DataAnalysis" :label="t('callHistory.dashboard.stats.totalRequests')" :value="statistics.totalRequests" tone="primary" />
        </el-col>
        <el-col :span="6">
          <StatCard :icon="CircleCheck" :label="t('callHistory.dashboard.stats.successRate')" :value="statistics.successRate?.toFixed(2) || '0'" unit="%" tone="success" />
        </el-col>
        <el-col :span="6">
          <StatCard :icon="Clock" :label="t('callHistory.dashboard.stats.avgResponseTime')" :value="statistics.avgResponseTimeMs?.toFixed(0) || '0'" unit="ms" tone="info" />
        </el-col>
        <el-col :span="6">
          <StatCard :icon="Tickets" :label="t('callHistory.dashboard.stats.totalTokenConsumed')" :value="statistics.totalTokens" tone="warning" />
        </el-col>
      </el-row>

      <!-- 第二行统计卡片 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :span="6">
          <StatCard :icon="WarningFilled" :label="t('callHistory.dashboard.stats.failedRequests')" :value="statistics.failedRequests" tone="danger" />
        </el-col>
        <el-col :span="6">
          <StatCard :icon="Grid" :label="t('callHistory.dashboard.stats.modelCount')" :value="statistics.byModel?.length || 0" tone="success" />
        </el-col>
        <el-col :span="6">
          <StatCard :icon="Service" :label="t('callHistory.dashboard.stats.serviceTypeCount')" :value="statistics.byServiceType?.length || 0" tone="primary" />
        </el-col>
        <el-col :span="6">
          <StatCard :icon="Document" :label="t('callHistory.dashboard.stats.avgTokensPerRequest')" :value="statistics.avgTokensPerRequest" tone="warning" />
        </el-col>
      </el-row>
    </template>

    <!-- 图表区域 -->
    <el-row :gutter="20">
      <!-- 请求趋势 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">{{ t('callHistory.dashboard.chart.dailyTrendTitle') }}</span>
          </template>
          <div ref="dailyChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 模型分布 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">{{ t('callHistory.dashboard.chart.modelDistributionTitle') }}</span>
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
            <span class="chart-title">{{ t('callHistory.dashboard.chart.serviceTypeDistributionTitle') }}</span>
          </template>
          <div ref="serviceTypeChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 小时分布 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">{{ t('callHistory.dashboard.chart.hourlyDistributionTitle') }}</span>
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
            <span class="chart-title">{{ t('callHistory.dashboard.chart.statusCodeDistributionTitle') }}</span>
          </template>
          <div ref="statusCodeChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 错误码分布 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">{{ t('callHistory.dashboard.chart.errorCodeDistributionTitle') }}</span>
          </template>
          <div ref="errorCodeChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近调用记录 -->
    <el-card shadow="hover" style="margin-top: 20px;">
      <template #header>
        <span class="chart-title">{{ t('callHistory.dashboard.chart.recentCallsTitle') }}</span>
      </template>
      <el-table :data="recentCalls" border stripe :max-height="400">
        <el-table-column :label="t('callHistory.common.time')" prop="createdAt" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.common.modelName')" prop="modelName" min-width="160" show-overflow-tooltip />
        <el-table-column :label="t('callHistory.common.serviceType')" prop="serviceType" width="110">
          <template #default="scope">
            <el-tag size="small">{{ getServiceTypeLabel(scope.row.serviceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.common.provider')" prop="provider" width="100" show-overflow-tooltip />
        <el-table-column :label="t('callHistory.common.httpStatus')" prop="httpStatusCode" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.httpStatusCode)" size="small">
              {{ scope.row.httpStatusCode || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.common.token')" prop="totalTokens" width="100" align="right">
          <template #default="scope">
            {{ formatNumber(scope.row.totalTokens) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.common.responseTimeMs')" prop="responseTimeMs" width="120" align="right">
          <template #default="scope">
            <el-tag :type="getResponseTimeType(scope.row.responseTimeMs)" size="small">
              {{ scope.row.responseTimeMs?.toFixed(0) || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.common.status')" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.isSuccess ? 'success' : 'danger'" size="small">
              {{ scope.row.isSuccess ? t('callHistory.common.success') : t('callHistory.common.failed') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.common.errorMessage')" prop="errorMessage" min-width="180" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.errorMessage" class="error-message">
              {{ scope.row.errorMessage }}
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
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
import StatCard from '@/components/StatCard.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import { useChartTheme } from '@/composables/useChartTheme'
import { useChartAutoRefresh } from '@/composables/useChartAutoRefresh'
import { formatDateTime as formatDateTimeBase, formatNumber as formatNumberBase } from '@/utils/format'

const { getChartTheme } = useChartTheme()
const { t } = useI18n()

/** 将 #rrggbb hex 转为 rgba 字符串 */
function colorWithAlpha(hex: string, alpha: number): string {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

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

// 格式化数字（千分位，委托共享 format.ts）
const formatNumber = (num?: number): string => {
  if (!num && num !== 0) return '0'
  return formatNumberBase(num)
}

// 格式化时间（委托共享 format.ts）
const formatTime = (time?: string) => {
  if (!time) return '-'
  return formatDateTimeBase(time)
}

// 服务类型 key -> i18n 键名映射（保持原有顺序）
const serviceTypeKeyMap: Record<string, string> = {
  chat: 'chat',
  embedding: 'embedding',
  rerank: 'rerank',
  tts: 'tts',
  stt: 'stt',
  imgGen: 'imgGen',
  imgEdit: 'imgEdit'
}

// 获取服务类型标签（v2.10.4: 文案收敛到顶层 serviceTypes.*，未知类型回退原值）
const getServiceTypeLabel = (type?: string) => {
  if (!type) return t('callHistory.common.unknown')
  return serviceTypeKeyMap[type] ? t(`serviceTypes.${serviceTypeKeyMap[type]}`) : type
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

  const theme = getChartTheme()
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
      data: [t('callHistory.common.requestCount'), t('callHistory.common.totalToken')]
    },
    xAxis: {
      type: 'category',
      data: data.map(item => item.date),
      boundaryGap: false
    },
    yAxis: [
      {
        type: 'value',
        name: t('callHistory.common.requestCount'),
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
        name: t('callHistory.common.requestCount'),
        type: 'bar',
        data: data.map(item => item.requestCount),
        itemStyle: { color: theme.primary }
      },
      {
        name: t('callHistory.common.totalToken'),
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: data.map(item => item.totalTokens),
        itemStyle: { color: theme.warning }
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
      formatter: '{b}: {c} ' + t('callHistory.dashboard.chart.countUnit') + ' ({d}%)'
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

  const theme = getChartTheme()
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
          color: theme.primary
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

  const theme = getChartTheme()
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
        name: t('callHistory.common.requestCount'),
        type: 'bar',
        data: hourlyData,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: colorWithAlpha(theme.primary, 0.5) },
            { offset: 0.5, color: theme.primary },
            { offset: 1, color: theme.primary }
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

  const theme = getChartTheme()
  const data = (statistics.value.byStatusCode || []).map(item => ({
    name: `${item.statusCode}`,
    value: item.count
  }))

  const getColor = (code: string) => {
    const num = parseInt(code)
    if (num >= 200 && num < 300) return theme.success
    if (num >= 300 && num < 400) return theme.primary
    if (num >= 400 && num < 500) return theme.warning
    if (num >= 500) return theme.danger
    return theme.info
  }

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ' + t('callHistory.dashboard.chart.countUnit') + ' ({d}%)'
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

  const theme = getChartTheme()
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
          color: theme.danger
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
    ElMessage.error(t('callHistory.dashboard.loadFailed', { message: error.message || t('callHistory.common.unknownError') }))
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

// 语言 / 主题切换后基于已加载统计数据重绘全部图表（纯重绘，无网络请求）
const rebuildAll = () => {
  updateDailyChart()
  updateModelChart()
  updateServiceTypeChart()
  updateHourlyChart()
  updateStatusCodeChart()
  updateErrorCodeChart()
}

// 初始化
onMounted(() => {
  useChartAutoRefresh(rebuildAll)
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
.filter-form {
  display: flex;
  justify-content: center;
}

.stats-row {
  margin-bottom: 20px;
}

.chart-container {
  height: 300px;
  width: 100%;
}

.chart-title {
  font-size: 16px;
  font-weight: bold;
}

.error-message {
  color: var(--ja-danger);
  font-size: 12px;
}

.text-muted {
  color: var(--ja-text-placeholder);
}
</style>
