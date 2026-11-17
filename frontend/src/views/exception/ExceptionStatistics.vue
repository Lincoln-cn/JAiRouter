<template>
  <PageSkeleton :title="t('exception.statistics.title')">
    <!-- 工具栏顺序固定：时间范围 → 维度筛选（本页无）→ 刷新；重置紧随其后 -->
    <template #toolbar>
      <FilterBar :collapsible="false">
        <el-form :inline="true" class="toolbar-form">
          <el-form-item :label="t('exception.statistics.timeRange')">
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              :range-separator="t('exception.statistics.dateRangeSeparator')"
              :start-placeholder="t('exception.statistics.startTime')"
              :end-placeholder="t('exception.statistics.endTime')"
              value-format="YYYY-MM-DD HH:mm:ss"
              @change="handleDateChange"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="refreshing" @click="loadData">
              <el-icon><Refresh /></el-icon>
              {{ t('exception.statistics.refresh') }}
            </el-button>
            <el-button @click="handleReset">{{ t('exception.statistics.reset') }}</el-button>
          </el-form-item>
        </el-form>
      </FilterBar>
    </template>

    <!-- 统计概览：中性指标条，异常总数 > 0 才上语义色 -->
    <template #stats>
      <MetricStrip :items="metricItems" :columns="4" />
    </template>

    <!-- 图表区域 -->
    <el-row :gutter="16">
      <!-- 按类型统计 -->
      <el-col :span="12">
        <ChartCard
          :title="t('exception.statistics.typeDistribution')"
          :empty="typeEmpty"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
          :height="300"
        >
          <div ref="typeChartRef" class="chart-container"></div>
        </ChartCard>
      </el-col>

      <!-- 按分类统计 -->
      <el-col :span="12">
        <ChartCard
          :title="t('exception.statistics.categoryStatistics')"
          :empty="categoryEmpty"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
          :height="300"
        >
          <div ref="categoryChartRef" class="chart-container"></div>
        </ChartCard>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="row-gap-top">
      <!-- 按 HTTP 状态码统计 -->
      <el-col :span="12">
        <ChartCard
          :title="t('exception.statistics.httpStatusDistribution')"
          :empty="httpStatusEmpty"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
          :height="300"
        >
          <div ref="httpStatusChartRef" class="chart-container"></div>
        </ChartCard>
      </el-col>

      <!-- 小时分布 -->
      <el-col :span="12">
        <ChartCard
          :title="t('exception.statistics.hourlyDistribution')"
          :empty="hourlyEmpty"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
          :height="300"
        >
          <div ref="hourlyChartRef" class="chart-container"></div>
        </ChartCard>
      </el-col>
    </el-row>

    <!-- Top 客户端 IP -->
    <el-card shadow="hover" class="row-gap-top">
      <template #header>
        <span class="chart-title">{{ t('exception.statistics.topClientIps') }}</span>
      </template>
      <el-table :data="topClientIps" border stripe :max-height="300">
        <template #empty>
          <EmptyState
            :title="t('common.chartEmpty')"
            :description="t('common.chartEmptyHint')"
          />
        </template>
        <el-table-column :label="t('exception.statistics.rank')" type="index" width="60" :index="indexMethod" />
        <el-table-column :label="t('exception.statistics.ipAddress')" prop="ip" min-width="200" />
        <el-table-column :label="t('exception.statistics.exceptionCount')" prop="count" width="120" align="right">
          <template #default="scope">
            <el-tag type="info">{{ formatNumber(scope.row.count) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('exception.statistics.percentage')" prop="percentage" width="120" align="right">
          <template #default="scope">
            <el-progress
              :percentage="calculatePercentage(scope.row.count)"
              :color="getProgressColor(calculatePercentage(scope.row.count))"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 最近异常事件 -->
    <el-card shadow="hover" class="row-gap-top">
      <template #header>
        <div class="card-header">
          <span class="chart-title">{{ t('exception.statistics.recentEvents') }}</span>
          <el-button link type="primary" @click="goToList">{{ t('exception.statistics.viewAll') }}</el-button>
        </div>
      </template>
      <el-table :data="recentEvents" border stripe :max-height="300">
        <template #empty>
          <EmptyState
            :title="t('common.chartEmpty')"
            :description="t('common.chartEmptyHint')"
          />
        </template>
        <el-table-column :label="t('exception.statistics.eventId')" prop="eventId" width="200" show-overflow-tooltip />
        <el-table-column :label="t('exception.statistics.exceptionType')" prop="exceptionType" min-width="180" show-overflow-tooltip />
        <el-table-column :label="t('exception.statistics.errorCode')" prop="errorCode" width="100">
          <template #default="scope">
            <el-tag :type="getErrorTagType(scope.row.errorCode)" size="small">
              {{ scope.row.errorCode }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('exception.statistics.errorCategory')" prop="errorCategory" width="120">
          <template #default="scope">
            <el-tag :type="getCategoryTagType(scope.row.errorCategory)" size="small">
              {{ formatCategory(scope.row.errorCategory) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('exception.statistics.occurredAt')" prop="occurredAt" width="180">
          <template #default="scope">
            <TimeText :value="scope.row.occurredAt" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getErrorMessage } from '@/utils/error'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import {
  getExceptionStatistics,
  getRecentExceptionEvents
} from '@/api/exception'
import type { ExceptionEvent, ExceptionStatistics } from '@/types/exception'
import MetricStrip, { type MetricStripItem } from '@/components/MetricStrip.vue'
import ChartCard from '@/components/ChartCard.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import FilterBar from '@/components/FilterBar.vue'
import EmptyState from '@/components/EmptyState.vue'
import TimeText from '@/components/TimeText.vue'
import { useChartTheme } from '@/composables/useChartTheme'
import { useChartSkin } from '@/composables/useChartSkin'
import { useChartAutoRefresh } from '@/composables/useChartAutoRefresh'
import { formatNumber } from '@/utils/format'

const { getChartTheme } = useChartTheme()
const { skin } = useChartSkin()

const { t, te } = useI18n()

const router = useRouter()

// 时间范围
const dateRange = ref<[string, string] | null>(null)

// 刷新中（仅工具栏按钮的加载态，不参与数据逻辑）
const refreshing = ref(false)

// 统计数据
const statistics = ref<ExceptionStatistics>({
  startTime: '',
  endTime: '',
  totalEvents: 0,
  totalTypes: 0,
  eventsByType: {},
  eventsByCategory: {},
  eventsByOperation: {},
  eventsByHttpStatus: {},
  topClientIps: [],
  hourlyDistribution: []
})

// 最近异常事件
const recentEvents = ref<ExceptionEvent[]>([])

// 图表引用
const typeChartRef = ref<HTMLElement | null>(null)
const categoryChartRef = ref<HTMLElement | null>(null)
const httpStatusChartRef = ref<HTMLElement | null>(null)
const hourlyChartRef = ref<HTMLElement | null>(null)

// 图表实例
let typeChart: ECharts | null = null
let categoryChart: ECharts | null = null
let httpStatusChart: ECharts | null = null
let hourlyChart: ECharts | null = null

// 计算最多错误类型
const topErrorType = computed(() => {
  const entries = Object.entries(statistics.value.eventsByType)
  if (entries.length === 0) return { type: '-', count: 0 }
  const sorted = entries.sort((a, b) => b[1] - a[1])
  return { type: sorted[0][0], count: sorted[0][1] }
})

// 计算成功率
const successRate = computed(() => {
  const total = statistics.value.totalEvents
  if (total === 0) return 100
  // 这里假设非 5xx 错误都算成功
  const serverErrors = statistics.value.eventsByCategory?.['SERVER_ERROR'] || 0
  // 统一返回 number：另一条分支返回 100（number），若这里返回字符串会让 formatPercent 的参数变成联合类型
  return Number((((total - serverErrors) / total) * 100).toFixed(1))
})

// Top 客户端 IP
const topClientIps = computed(() => {
  return statistics.value.topClientIps || []
})

// 顶部指标条：只有真异常才上语义色；无事件时成功率给「—」而不是 100%
const metricItems = computed<MetricStripItem[]>(() => {
  const s = statistics.value
  const hasEvents = s.totalEvents > 0
  return [
    {
      key: 'total',
      label: t('exception.statistics.totalEventsLabel'),
      value: formatNumber(s.totalEvents, 0),
      tone: hasEvents ? 'danger' : 'default'
    },
    {
      key: 'types',
      label: t('exception.statistics.totalTypesLabel'),
      value: formatNumber(s.totalTypes, 0)
    },
    {
      key: 'topType',
      label: t('exception.statistics.topErrorTypeLabel'),
      value: formatNumber(topErrorType.value.count, 0),
      hint: hasEvents ? topErrorType.value.type.split('.').pop() : undefined
    },
    {
      key: 'successRate',
      label: t('exception.statistics.successRateLabel'),
      value: hasEvents ? formatPercent(successRate.value) : '—'
    }
  ]
})

const hasKeys = (obj?: Record<string, number>) => Object.keys(obj ?? {}).length > 0

// 四张图各自的空态判定（空态由 ChartCard 覆盖层渲染，图表容器始终挂载）
const typeEmpty = computed(() => !hasKeys(statistics.value.eventsByType))
const categoryEmpty = computed(() => !hasKeys(statistics.value.eventsByCategory))
const httpStatusEmpty = computed(() => !hasKeys(statistics.value.eventsByHttpStatus))
const hourlyEmpty = computed(() => (statistics.value.hourlyDistribution ?? []).length === 0)

// 索引方法
const indexMethod = (index: number) => index + 1

/** 百分比一律保留 1 位；无事件时给「—」而不是 100% */
const formatPercent = (value?: number | null): string => {
  const num = Number(value)
  return Number.isFinite(num) ? `${num.toFixed(1)}%` : '—'
}

// 获取错误标签类型
const getErrorTagType = (code?: string) => {
  if (!code) return 'info'
  const num = parseInt(code)
  if (num >= 500) return 'danger'
  if (num >= 400) return 'warning'
  return 'info'
}

// 获取分类标签类型
const getCategoryTagType = (category?: string) => {
  switch (category) {
    case 'SERVER_ERROR':
      return 'danger'
    case 'CLIENT_ERROR':
      return 'warning'
    case 'SECURITY_ERROR':
      return 'danger'
    default:
      return 'info'
  }
}

// 格式化分类：优先用 i18n 里的中文名，未知分类退化为「去掉下划线」的可读文本
const formatCategory = (category?: string) => {
  if (!category) return '-'
  const key = `exception.list.errorCategories.${category}`
  return te(key) ? t(key) : category.replace(/_/g, ' ')
}

// 计算百分比
const calculatePercentage = (count: number) => {
  const total = statistics.value.totalEvents
  if (total === 0) return 0
  return Math.round((count / total) * 100)
}

// 获取进度条颜色
const getProgressColor = (percentage: number) => {
  const theme = getChartTheme()
  if (percentage >= 50) return theme.danger
  if (percentage >= 30) return theme.warning
  return theme.success
}

// 初始化图表
const initCharts = () => {
  nextTick(() => {
    // 类型分布图
    if (typeChartRef.value) {
      typeChart = echarts.init(typeChartRef.value)
      updateTypeChart()
    }

    // 分类统计图
    if (categoryChartRef.value) {
      categoryChart = echarts.init(categoryChartRef.value)
      updateCategoryChart()
    }

    // HTTP 状态码图
    if (httpStatusChartRef.value) {
      httpStatusChart = echarts.init(httpStatusChartRef.value)
      updateHttpStatusChart()
    }

    // 小时分布图
    if (hourlyChartRef.value) {
      hourlyChart = echarts.init(hourlyChartRef.value)
      updateHourlyChart()
    }
  })
}

// 更新类型分布图
const updateTypeChart = () => {
  if (!typeChart) return

  const data = Object.entries(statistics.value.eventsByType || {})
    .map(([type, count]) => ({ 
      name: type, 
      value: count,
      // 简化显示名称：只显示类名，去掉包名
      shortName: type.includes('.') ? type.substring(type.lastIndexOf('.') + 1) : type
    }))
    .sort((a, b) => b.value - a.value)
    .slice(0, 10) // 只显示前 10

  const option = {
    color: skin.value.palette,
    tooltip: {
      trigger: 'item',
      formatter: (params: unknown) => {
        const p = params as { data: { shortName?: string }; name?: string; value?: number; percent?: number }
        return `<div style="max-width: 300px;">
          <div style="font-weight: bold; margin-bottom: 5px;">${p.data.shortName}</div>
          <div style="font-size: 12px; color: ${skin.value.textMuted};">${p.name}</div>
          <div style="margin-top: 5px;">${t('exception.statistics.tooltipCount')}<strong>${p.value}</strong> (${p.percent}%)</div>
        </div>`
      }
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      type: 'scroll',
      formatter: (name: string) => {
        const item = data.find(d => d.name === name)
        return item ? item.shortName : name
      },
      textStyle: {
        width: 150,
        overflow: 'truncate'
      }
    },
    series: [
      {
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['32%', '52%'],
        // 引线标签会与右侧图例打架（长类名尤其明显）⇒ 只留图例 + tooltip
        label: { show: false },
        labelLine: { show: false },
        data: data.map(d => ({ ...d, name: d.shortName })),
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.35)'
          }
        }
      }
    ]
  }

  typeChart.setOption(option, { notMerge: true })
}

// 更新分类统计图
const updateCategoryChart = () => {
  if (!categoryChart) return

  const data = Object.entries(statistics.value.eventsByCategory || {})
    .map(([category, count]) => ({ name: formatCategory(category), value: count }))

  const option = {
    grid: skin.value.grid,
    tooltip: {
      ...skin.value.tooltip,
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      ...skin.value.categoryAxis,
      type: 'category',
      data: data.map(item => item.name),
      axisLabel: {
        ...(skin.value.categoryAxis.axisLabel as Record<string, unknown>),
        interval: 0,
        rotate: 0
      }
    },
    yAxis: {
      ...skin.value.valueAxis,
      type: 'value'
    },
    series: [
      {
        type: 'bar',
        data: data.map(item => item.value),
        barMaxWidth: 24,
        itemStyle: {
          color: skin.value.palette[0]
        },
        label: {
          show: true,
          position: 'top',
          color: skin.value.textMuted
        }
      }
    ]
  }

  categoryChart.setOption(option, { notMerge: true })
}

// 更新 HTTP 状态码图
const updateHttpStatusChart = () => {
  if (!httpStatusChart) return

  const theme = getChartTheme()
  const data = Object.entries(statistics.value.eventsByHttpStatus || {})
    .map(([status, count]) => ({ name: status, value: count }))
    .sort((a, b) => parseInt(a.name) - parseInt(b.name))

  const option = {
    grid: skin.value.grid,
    tooltip: {
      ...skin.value.tooltip,
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      ...skin.value.categoryAxis,
      type: 'category',
      data: data.map(item => item.name)
    },
    yAxis: {
      ...skin.value.valueAxis,
      type: 'value'
    },
    series: [
      {
        type: 'bar',
        data: data.map(item => item.value),
        barMaxWidth: 24,
        itemStyle: {
          color: (params: unknown) => {
            const status = parseInt(String((params as { name?: string }).name ?? ''), 10)
            if (status >= 500) return theme.danger
            if (status >= 400) return theme.warning
            return theme.success
          }
        },
        label: {
          show: true,
          position: 'top',
          color: skin.value.textMuted
        }
      }
    ]
  }

  httpStatusChart.setOption(option, { notMerge: true })
}

// 更新小时分布图
const updateHourlyChart = () => {
  if (!hourlyChart) return

  // 初始化 24 小时数据
  const hourlyData = new Array(24).fill(0)
  ;(statistics.value.hourlyDistribution || []).forEach(item => {
    const hour = parseInt(item.hour)
    if (!isNaN(hour) && hour >= 0 && hour < 24) {
      hourlyData[hour] = item.count
    }
  })

  const hours = Array.from({ length: 24 }, (_, i) => `${i.toString().padStart(2, '0')}:00`)

  const option = {
    grid: skin.value.grid,
    tooltip: {
      ...skin.value.tooltip,
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      ...skin.value.categoryAxis,
      type: 'category',
      data: hours,
      axisLabel: {
        ...(skin.value.categoryAxis.axisLabel as Record<string, unknown>),
        interval: 1,
        rotate: 45
      }
    },
    yAxis: {
      ...skin.value.valueAxis,
      type: 'value'
    },
    series: [
      {
        type: 'line',
        data: hourlyData,
        smooth: true,
        areaStyle: {
          opacity: 0.18,
          color: skin.value.palette[0]
        },
        itemStyle: {
          color: skin.value.palette[0]
        },
        label: {
          show: false
        }
      }
    ]
  }

  hourlyChart.setOption(option, { notMerge: true })
}

// 加载数据
const loadData = async () => {
  refreshing.value = true
  try {
    let startTime: string | undefined
    let endTime: string | undefined

    if (dateRange.value && dateRange.value.length === 2) {
      startTime = dateRange.value[0]
      endTime = dateRange.value[1]
    }

    // 加载统计数据
    const stats = await getExceptionStatistics(startTime, endTime)
    statistics.value = stats

    // 加载最近异常事件
    recentEvents.value = await getRecentExceptionEvents(10)

    // 更新图表
    initCharts()
  } catch (error) {
    console.error('加载统计数据失败:', error)
    ElMessage.error(t('exception.statistics.loadFailed', { message: getErrorMessage(error) || t('exception.statistics.unknownError') }))
  } finally {
    refreshing.value = false
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

// 跳转到列表页
const goToList = () => {
  router.push('/exceptions/list')
}

// 窗口大小变化时重新渲染图表
const handleResize = () => {
  typeChart?.resize()
  categoryChart?.resize()
  httpStatusChart?.resize()
  hourlyChart?.resize()
}

// 语言 / 主题切换后基于已加载统计数据重绘全部图表（纯重绘，无网络请求）
const rebuildAll = () => {
  updateTypeChart()
  updateCategoryChart()
  updateHttpStatusChart()
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
  typeChart?.dispose()
  categoryChart?.dispose()
  httpStatusChart?.dispose()
  hourlyChart?.dispose()
})
</script>

<style scoped>
/* 工具栏内联表单：与其它观测页同一套行内节奏 */
.toolbar-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--ja-space-2);
}

.toolbar-form :deep(.el-form-item) {
  margin: 0;
}

/* 窄屏时时间范围选择器不撑破工具栏 */
.toolbar-form :deep(.el-date-editor) {
  max-width: 100%;
}

/* 图表行 / 明细卡之间的统一间距（原来是 20px 行内 style） */
.row-gap-top {
  margin-top: var(--ja-space-4);
}

.chart-container {
  height: 100%;
  width: 100%;
}

.chart-title {
  font-size: 16px;
  font-weight: 600;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
