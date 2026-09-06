<template>
  <PageSkeleton :title="t('callHistory.tokenUsage.pageTitle')">
    <template #toolbar>
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
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #409EFF;">
              <el-icon><DataAnalysis /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(statistics.totalTokens) }}</div>
              <div class="stat-label">{{ t('callHistory.tokenUsage.stats.totalTokenUsed') }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #67C23A;">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(statistics.totalPromptTokens) }}</div>
              <div class="stat-label">{{ t('callHistory.tokenUsage.stats.inputTokens') }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #E6A23C;">
              <el-icon><ChatDotSquare /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(statistics.totalCompletionTokens) }}</div>
              <div class="stat-label">{{ t('callHistory.tokenUsage.stats.outputTokens') }}</div>
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
              <div class="stat-label">{{ t('callHistory.tokenUsage.stats.avgResponseTimeMs') }}</div>
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
              <el-icon><Tickets /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(statistics.totalRequests) }}</div>
              <div class="stat-label">{{ t('callHistory.tokenUsage.stats.totalRequests') }}</div>
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
              <div class="stat-label">{{ t('callHistory.tokenUsage.stats.successRate') }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #409EFF;">
              <el-icon><Grid /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.byModel?.length || 0 }}</div>
              <div class="stat-label">{{ t('callHistory.tokenUsage.stats.modelUsedCount') }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #E6A23C;">
              <el-icon><Service /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.byServiceType?.length || 0 }}</div>
              <div class="stat-label">{{ t('callHistory.tokenUsage.stats.serviceTypeCount') }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    </template>

    <!-- 图表区域 -->
    <el-row :gutter="20">
      <!-- 按模型统计 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">{{ t('callHistory.tokenUsage.chart.modelTokenTopTitle') }}</span>
          </template>
          <div ref="modelChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 按服务类型统计 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">{{ t('callHistory.tokenUsage.chart.serviceTypeTokenTitle') }}</span>
          </template>
          <div ref="serviceTypeChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 按日统计 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">{{ t('callHistory.tokenUsage.chart.dailyTrendTitle') }}</span>
          </template>
          <div ref="dailyChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 按周统计 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">{{ t('callHistory.tokenUsage.chart.weeklyTrendTitle') }}</span>
          </template>
          <div ref="weeklyChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 按月统计 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">{{ t('callHistory.tokenUsage.chart.monthlyTrendTitle') }}</span>
          </template>
          <div ref="monthlyChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 小时分布 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">{{ t('callHistory.tokenUsage.chart.hourlyDistributionTitle') }}</span>
          </template>
          <div ref="hourlyChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近使用记录 -->
    <el-card shadow="hover" style="margin-top: 20px;">
      <template #header>
        <span class="chart-title">{{ t('callHistory.tokenUsage.chart.recentUsageTitle') }}</span>
      </template>
      <el-table :data="recentUsage" border stripe :max-height="400">
        <el-table-column :label="t('callHistory.common.time')" prop="occurredAt" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.occurredAt) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.common.modelName')" prop="modelName" min-width="180" show-overflow-tooltip />
        <el-table-column :label="t('callHistory.common.serviceType')" prop="serviceType" width="120">
          <template #default="scope">
            <el-tag size="small">{{ getServiceTypeLabel(scope.row.serviceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.common.provider')" prop="provider" width="120" />
        <el-table-column :label="t('callHistory.tokenUsage.stats.inputTokens')" prop="promptTokens" width="100" align="right">
          <template #default="scope">
            {{ formatNumber(scope.row.promptTokens) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.tokenUsage.stats.outputTokens')" prop="completionTokens" width="100" align="right">
          <template #default="scope">
            {{ formatNumber(scope.row.completionTokens) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.common.totalToken')" prop="totalTokens" width="100" align="right">
          <template #default="scope">
            <el-tag type="success" size="small">{{ formatNumber(scope.row.totalTokens) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.common.responseTimeMs')" prop="responseTimeMs" width="110" align="right">
          <template #default="scope">
            {{ scope.row.responseTimeMs?.toFixed(0) || '-' }}
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.common.status')" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.isSuccess ? 'success' : 'danger'" size="small">
              {{ scope.row.isSuccess ? t('callHistory.common.success') : t('callHistory.common.failed') }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  DataAnalysis,
  Document,
  ChatDotSquare,
  Clock,
  Tickets,
  CircleCheck,
  Grid,
  Service,
  Search,
  Refresh
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import {
  getTokenUsageStatistics,
  getRecentUsage,
  getTopModels,
  getTopServiceTypes
} from '@/api/tokenUsage'
import type { TokenUsageStatistics, TokenUsageRecord } from '@/types/tokenUsage'
import { formatDateTime as formatDateTimeBase, formatNumber as formatNumberBase } from '@/utils/format'
import { useChartAutoRefresh } from '@/composables/useChartAutoRefresh'
import PageSkeleton from '@/components/PageSkeleton.vue'

const { t } = useI18n()

// 时间范围
const dateRange = ref<[string, string] | null>(null)

// 统计数据
const statistics = ref<TokenUsageStatistics>({
  startTime: '',
  endTime: '',
  totalRequests: 0,
  successfulRequests: 0,
  failedRequests: 0,
  totalTokens: 0,
  totalPromptTokens: 0,
  totalCompletionTokens: 0,
  avgResponseTimeMs: 0,
  successRate: 0,
  byModel: [],
  byServiceType: [],
  byProvider: [],
  byDay: [],
  byWeek: [],
  byMonth: [],
  byHour: [],
  byApiKey: [],
  byUser: []
})

// 最近使用记录
const recentUsage = ref<TokenUsageRecord[]>([])

// 图表引用
const modelChartRef = ref<HTMLElement | null>(null)
const serviceTypeChartRef = ref<HTMLElement | null>(null)
const dailyChartRef = ref<HTMLElement | null>(null)
const weeklyChartRef = ref<HTMLElement | null>(null)
const monthlyChartRef = ref<HTMLElement | null>(null)
const hourlyChartRef = ref<HTMLElement | null>(null)

// 图表实例
let modelChart: ECharts | null = null
let serviceTypeChart: ECharts | null = null
let dailyChart: ECharts | null = null
let weeklyChart: ECharts | null = null
let monthlyChart: ECharts | null = null
let hourlyChart: ECharts | null = null

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

// 初始化图表
const initCharts = () => {
  nextTick(() => {
    if (modelChartRef.value) {
      modelChart = echarts.init(modelChartRef.value)
      updateModelChart()
    }
    if (serviceTypeChartRef.value) {
      serviceTypeChart = echarts.init(serviceTypeChartRef.value)
      updateServiceTypeChart()
    }
    if (dailyChartRef.value) {
      dailyChart = echarts.init(dailyChartRef.value)
      updateDailyChart()
    }
    if (weeklyChartRef.value) {
      weeklyChart = echarts.init(weeklyChartRef.value)
      updateWeeklyChart()
    }
    if (monthlyChartRef.value) {
      monthlyChart = echarts.init(monthlyChartRef.value)
      updateMonthlyChart()
    }
    if (hourlyChartRef.value) {
      hourlyChart = echarts.init(hourlyChartRef.value)
      updateHourlyChart()
    }
  })
}

// 更新模型图表
const updateModelChart = () => {
  if (!modelChart) return

  const data = (statistics.value.byModel || [])
    .slice(0, 10)
    .map(item => ({
      name: item.modelName,
      value: item.totalTokens
    }))

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
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
    value: item.totalTokens
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

// 更新每日趋势图表
const updateDailyChart = () => {
  if (!dailyChart) return

  const data = (statistics.value.byDay || []).map(item => ({
    date: item.date,
    totalTokens: item.totalTokens,
    promptTokens: item.promptTokens,
    completionTokens: item.completionTokens
  }))

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: [t('callHistory.tokenUsage.stats.inputTokens'), t('callHistory.tokenUsage.stats.outputTokens'), t('callHistory.common.totalToken')]
    },
    xAxis: {
      type: 'category',
      data: data.map(item => item.date),
      boundaryGap: false
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: t('callHistory.tokenUsage.stats.inputTokens'),
        type: 'line',
        stack: 'Total',
        areaStyle: { opacity: 0.3 },
        data: data.map(item => item.promptTokens),
        itemStyle: { color: '#67C23A' }
      },
      {
        name: t('callHistory.tokenUsage.stats.outputTokens'),
        type: 'line',
        stack: 'Total',
        areaStyle: { opacity: 0.3 },
        data: data.map(item => item.completionTokens),
        itemStyle: { color: '#E6A23C' }
      },
      {
        name: t('callHistory.common.totalToken'),
        type: 'line',
        data: data.map(item => item.totalTokens),
        itemStyle: { color: '#409EFF' },
        lineStyle: { width: 3 }
      }
    ]
  }

  dailyChart.setOption(option)
}

// 更新每周趋势图表
const updateWeeklyChart = () => {
  if (!weeklyChart) return

  const data = (statistics.value.byWeek || []).map(item => ({
    week: item.weekLabel,
    totalTokens: item.totalTokens
  }))

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: data.map(item => item.week),
      boundaryGap: false
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: t('callHistory.common.totalToken'),
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.3 },
        data: data.map(item => item.totalTokens),
        itemStyle: { color: '#409EFF' }
      }
    ]
  }

  weeklyChart.setOption(option)
}

// 更新每月趋势图表
const updateMonthlyChart = () => {
  if (!monthlyChart) return

  const data = (statistics.value.byMonth || []).map(item => ({
    month: item.monthLabel,
    totalTokens: item.totalTokens
  }))

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: data.map(item => item.month),
      boundaryGap: false
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: t('callHistory.common.totalToken'),
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.3 },
        data: data.map(item => item.totalTokens),
        itemStyle: { color: '#E6A23C' }
      }
    ]
  }

  monthlyChart.setOption(option)
}

// 更新小时分布图表
const updateHourlyChart = () => {
  if (!hourlyChart) return

  // 初始化 24 小时数据
  const hourlyData = new Array(24).fill(0)
  ;(statistics.value.byHour || []).forEach(item => {
    if (item.hour >= 0 && item.hour < 24) {
      hourlyData[item.hour] = item.totalTokens
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
        name: t('callHistory.tokenUsage.chart.tokenUsage'),
        type: 'bar',
        data: hourlyData,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#83bff6' },
            { offset: 0.5, color: '#188df0' },
            { offset: 1, color: '#188df0' }
          ])
        },
        label: {
          show: false
        }
      }
    ]
  }

  hourlyChart.setOption(option)
}

// 加载数据
const loadData = async () => {
  try {
    let startTime: string | undefined
    let endTime: string | undefined

    if (dateRange.value && dateRange.value.length === 2) {
      // 转换为 ISO-8601 格式 (YYYY-MM-DDTHH:mm:ss)
      startTime = dateRange.value[0].replace(' ', 'T')
      endTime = dateRange.value[1].replace(' ', 'T')
    }

    // 加载统计数据
    statistics.value = await getTokenUsageStatistics(startTime, endTime)

    // 加载最近使用记录
    recentUsage.value = await getRecentUsage(50)

    // 更新图表
    initCharts()
  } catch (error: any) {
    console.error('加载统计数据失败:', error)
    ElMessage.error(t('callHistory.tokenUsage.loadFailed', { message: error.message || t('callHistory.common.unknownError') }))
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
  modelChart?.resize()
  serviceTypeChart?.resize()
  dailyChart?.resize()
  weeklyChart?.resize()
  monthlyChart?.resize()
  hourlyChart?.resize()
}

// 语言 / 主题切换后基于已加载统计数据重绘全部图表（纯重绘，无网络请求）
const rebuildAll = () => {
  updateModelChart()
  updateServiceTypeChart()
  updateDailyChart()
  updateWeeklyChart()
  updateMonthlyChart()
  updateHourlyChart()
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
  modelChart?.dispose()
  serviceTypeChart?.dispose()
  dailyChart?.dispose()
  weeklyChart?.dispose()
  monthlyChart?.dispose()
  hourlyChart?.dispose()
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

.stats-row .stat-card .stat-content {
  display: flex;
  align-items: center;
}

.stats-row .stat-card .stat-icon {
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

.stats-row .stat-card .stat-info .stat-value {
  font-size: 24px;
  font-weight: bold;
  color: var(--ja-text-primary);
}

.stats-row .stat-card .stat-info .stat-label {
  font-size: 14px;
  color: var(--ja-text-regular);
  margin-top: 5px;
}

.chart-container {
  height: 300px;
  width: 100%;
}

.chart-title {
  font-size: 16px;
  font-weight: bold;
}
</style>
