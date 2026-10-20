<template>
  <ListPageLayout>
    <template #header>
      <h2 class="page-title">{{ t('callHistory.tokenUsage.pageTitle') }}</h2>
      <p class="page-subtitle">{{ t('callHistory.tokenUsage.pageDescription') }}</p>
    </template>

    <!-- 时间范围筛选 -->
    <template #filters>
      <FilterBar>
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
      </FilterBar>
    </template>

    <!-- 概览指标：8 张彩色图标 KPI 卡 → 一条中性 MetricStrip（P1-2） -->
    <MetricStrip :items="kpiItems" :loading="loading" />

    <!-- 图表：每张图一张 ChartCard（自带边框，不再套 el-card） -->
    <el-row :gutter="20">
      <!-- 按模型统计 -->
      <el-col :span="12">
        <ChartCard
          :title="t('callHistory.tokenUsage.chart.modelTokenTopTitle')"
          :loading="loading"
          :empty="modelEmpty"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
          :height="320"
        >
          <!-- 单类数据：环形图无意义 ⇒ 「大数字 + 说明」，且不渲染图表容器 -->
          <div v-if="modelSingle" class="chart-single">
            <div class="chart-single__value">{{ formatNumber(modelSingleItem?.totalTokens) }}</div>
            <div class="chart-single__label">{{ modelSingleItem?.modelName || t('callHistory.common.unknown') }}</div>
            <div class="chart-single__hint">{{ t('callHistory.tokenUsage.chart.singleCategoryHint') }}</div>
          </div>
          <div v-else ref="modelChartRef" class="chart-container"></div>
        </ChartCard>
      </el-col>

      <!-- 按服务类型统计 -->
      <el-col :span="12">
        <ChartCard
          :title="t('callHistory.tokenUsage.chart.serviceTypeTokenTitle')"
          :loading="loading"
          :empty="serviceTypeEmpty"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
          :height="320"
        >
          <div ref="serviceTypeChartRef" class="chart-container"></div>
        </ChartCard>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <!-- 按日统计 -->
      <el-col :span="12">
        <ChartCard
          :title="t('callHistory.tokenUsage.chart.dailyTrendTitle')"
          :loading="loading"
          :empty="dailyEmpty"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
          :height="320"
        >
          <div ref="dailyChartRef" class="chart-container"></div>
        </ChartCard>
      </el-col>

      <!-- 按周统计 -->
      <el-col :span="12">
        <ChartCard
          :title="t('callHistory.tokenUsage.chart.weeklyTrendTitle')"
          :loading="loading"
          :empty="weeklyEmpty"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
          :height="320"
        >
          <div ref="weeklyChartRef" class="chart-container"></div>
        </ChartCard>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <!-- 按月统计 -->
      <el-col :span="12">
        <ChartCard
          :title="t('callHistory.tokenUsage.chart.monthlyTrendTitle')"
          :loading="loading"
          :empty="monthlyEmpty"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
          :height="320"
        >
          <div ref="monthlyChartRef" class="chart-container"></div>
        </ChartCard>
      </el-col>

      <!-- 小时分布 -->
      <el-col :span="12">
        <ChartCard
          :title="t('callHistory.tokenUsage.chart.hourlyDistributionTitle')"
          :loading="loading"
          :empty="hourlyEmpty"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
          :height="320"
        >
          <div ref="hourlyChartRef" class="chart-container"></div>
        </ChartCard>
      </el-col>
    </el-row>

    <!-- 最近使用记录 -->
    <el-card shadow="hover">
      <template #header>
        <span class="chart-title">{{ t('callHistory.tokenUsage.chart.recentUsageTitle') }}</span>
      </template>
      <el-table :data="recentUsage" border stripe :max-height="400">
        <el-table-column :label="t('callHistory.common.time')" prop="occurredAt" width="180">
          <template #default="scope">
            <TimeText :value="scope.row.occurredAt" />
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
  </ListPageLayout>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getErrorMessage } from '@/utils/error'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import {
  getTokenUsageStatistics,
  getRecentUsage
} from '@/api/tokenUsage'
import type {
  ModelTokenStats,
  TokenUsageStatistics,
  TokenUsageRecord
} from '@/types/tokenUsage'
import { formatNumber as formatNumberBase } from '@/utils/format'
import { useChartAutoRefresh } from '@/composables/useChartAutoRefresh'
import { useChartSkin } from '@/composables/useChartSkin'
import ListPageLayout from '@/components/ListPageLayout.vue'
import FilterBar from '@/components/FilterBar.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import ChartCard from '@/components/ChartCard.vue'
import TimeText from '@/components/TimeText.vue'

const { t } = useI18n()

/** 图表统一皮肤（分类色板只含主色相 + 中性阶，语义色不进分类着色）。 */
const { skin } = useChartSkin()

// 时间范围
const dateRange = ref<[string, string] | null>(null)

// 加载中（驱动 MetricStrip 骨架与 ChartCard 加载覆盖层）
const loading = ref(false)

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

/** 平均响应时间：整数毫秒，不参与千分位（长度固定，指标条不需要分隔符）。 */
const avgResponseTimeText = computed(() => (statistics.value.avgResponseTimeMs || 0).toFixed(0))

/** 成功率：两位小数百分比。 */
const successRateText = computed(() => `${(statistics.value.successRate || 0).toFixed(2)}%`)

/**
 * 指标条数据。
 *
 * 8 项都只是「用量 / 计数」，没有异常状态语义 ⇒ `tone` 一律缺省（default）。
 */
const kpiItems = computed<MetricStripItem[]>(() => [
  {
    key: 'totalTokens',
    label: t('callHistory.tokenUsage.stats.totalTokenUsed'),
    value: formatNumber(statistics.value.totalTokens)
  },
  {
    key: 'promptTokens',
    label: t('callHistory.tokenUsage.stats.inputTokens'),
    value: formatNumber(statistics.value.totalPromptTokens)
  },
  {
    key: 'completionTokens',
    label: t('callHistory.tokenUsage.stats.outputTokens'),
    value: formatNumber(statistics.value.totalCompletionTokens)
  },
  {
    key: 'avgResponseTime',
    label: t('callHistory.tokenUsage.stats.avgResponseTimeMs'),
    value: avgResponseTimeText.value
  },
  {
    key: 'totalRequests',
    label: t('callHistory.tokenUsage.stats.totalRequests'),
    value: formatNumber(statistics.value.totalRequests)
  },
  {
    key: 'successRate',
    label: t('callHistory.tokenUsage.stats.successRate'),
    value: successRateText.value
  },
  {
    key: 'modelCount',
    label: t('callHistory.tokenUsage.stats.modelUsedCount'),
    value: statistics.value.byModel?.length ?? 0
  },
  {
    key: 'serviceTypeCount',
    label: t('callHistory.tokenUsage.stats.serviceTypeCount'),
    value: statistics.value.byServiceType?.length ?? 0
  }
])

// 每张图的空态判定：只看各自的原始数据源是否为空
const modelEmpty = computed(() => (statistics.value.byModel || []).length === 0)
const serviceTypeEmpty = computed(() => (statistics.value.byServiceType || []).length === 0)
const dailyEmpty = computed(() => (statistics.value.byDay || []).length === 0)
const weeklyEmpty = computed(() => (statistics.value.byWeek || []).length === 0)
const monthlyEmpty = computed(() => (statistics.value.byMonth || []).length === 0)
const hourlyEmpty = computed(() => (statistics.value.byHour || []).length === 0)

// 模型占比的单类情形：环形图只剩一整圈（无对比意义）⇒ 改渲染「大数字」
const modelSingle = computed(() => (statistics.value.byModel || []).length === 1)
const modelSingleItem = computed<ModelTokenStats | null>(() =>
  modelSingle.value ? statistics.value.byModel[0] || null : null
)

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

// 初始化图表（容器存在才建实例；单类情形下 modelChartRef 为空，直接跳过）
const initCharts = () => {
  nextTick(() => {
    if (modelChartRef.value && !modelChart) {
      modelChart = echarts.init(modelChartRef.value)
    }
    if (serviceTypeChartRef.value && !serviceTypeChart) {
      serviceTypeChart = echarts.init(serviceTypeChartRef.value)
    }
    if (dailyChartRef.value && !dailyChart) {
      dailyChart = echarts.init(dailyChartRef.value)
    }
    if (weeklyChartRef.value && !weeklyChart) {
      weeklyChart = echarts.init(weeklyChartRef.value)
    }
    if (monthlyChartRef.value && !monthlyChart) {
      monthlyChart = echarts.init(monthlyChartRef.value)
    }
    if (hourlyChartRef.value && !hourlyChart) {
      hourlyChart = echarts.init(hourlyChartRef.value)
    }

    updateModelChart()
    updateServiceTypeChart()
    updateDailyChart()
    updateWeeklyChart()
    updateMonthlyChart()
    updateHourlyChart()
  })
}

// 更新模型图表（环形图；分类色板来自皮肤）
const updateModelChart = () => {
  if (!modelChart) return

  const data = (statistics.value.byModel || [])
    .slice(0, 10)
    .map(item => ({
      name: item.modelName || t('callHistory.common.unknown'),
      value: item.totalTokens
    }))

  const chartSkin = skin.value

  const option = {
    // 饼图各扇区没有系列级颜色可设，必须给 option 级 `color`，
    // 否则 ECharts 会回落到内置调色板（红绿蓝），与本页统一皮肤冲突。
    color: chartSkin.palette,
    tooltip: {
      ...chartSkin.tooltip,
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      ...chartSkin.legend,
      type: 'scroll',
      orient: 'vertical',
      right: 0,
      top: 'middle'
    },
    series: [
      {
        type: 'pie',
        radius: ['46%', '68%'],
        center: ['40%', '52%'],
        avoidLabelOverlap: true,
        data,
        itemStyle: {
          borderColor: chartSkin.surface,
          borderWidth: 2
        },
        label: { show: false },
        labelLine: { show: false },
        emphasis: {
          scale: true,
          scaleSize: 4,
          label: { show: true, fontWeight: 600, color: chartSkin.text, fontSize: 13 }
        }
      }
    ]
  }

  modelChart.setOption(option, { notMerge: true })
}

// 更新服务类型图表（纵向柱图 + 基线）
const updateServiceTypeChart = () => {
  if (!serviceTypeChart) return

  const data = (statistics.value.byServiceType || []).map(item => ({
    name: getServiceTypeLabel(item.serviceType),
    value: item.totalTokens
  }))

  const chartSkin = skin.value

  const option = {
    tooltip: {
      ...chartSkin.tooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: { ...chartSkin.grid },
    xAxis: {
      ...chartSkin.categoryAxis,
      type: 'category',
      data: data.map(item => item.name),
      axisLabel: { color: chartSkin.textMuted, fontSize: 12, interval: 0, rotate: 0 }
    },
    yAxis: {
      ...chartSkin.valueAxis,
      type: 'value'
    },
    series: [
      {
        type: 'bar',
        data: data.map(item => item.value),
        barMaxWidth: 32,
        itemStyle: {
          color: chartSkin.palette[0],
          borderRadius: [4, 4, 0, 0]
        },
        label: {
          show: true,
          position: 'top',
          color: chartSkin.textMuted,
          fontSize: 12
        }
      }
    ]
  }

  serviceTypeChart.setOption(option, { notMerge: true })
}

// 更新每日趋势图表（输入 / 输出堆叠面 + 总量线）
const updateDailyChart = () => {
  if (!dailyChart) return

  const data = (statistics.value.byDay || []).map(item => ({
    date: item.date,
    totalTokens: item.totalTokens,
    promptTokens: item.promptTokens,
    completionTokens: item.completionTokens
  }))

  const chartSkin = skin.value
  const inputLabel = t('callHistory.tokenUsage.stats.inputTokens')
  const outputLabel = t('callHistory.tokenUsage.stats.outputTokens')
  const totalLabel = t('callHistory.common.totalToken')

  const option = {
    tooltip: {
      ...chartSkin.tooltip,
      trigger: 'axis'
    },
    legend: {
      ...chartSkin.legend,
      data: [inputLabel, outputLabel, totalLabel]
    },
    grid: { ...chartSkin.grid },
    xAxis: {
      ...chartSkin.categoryAxis,
      type: 'category',
      data: data.map(item => item.date),
      boundaryGap: false
    },
    yAxis: {
      ...chartSkin.valueAxis,
      type: 'value'
    },
    series: [
      {
        name: inputLabel,
        type: 'line',
        stack: 'Total',
        smooth: true,
        showSymbol: false,
        areaStyle: { opacity: 0.2 },
        data: data.map(item => item.promptTokens),
        itemStyle: { color: chartSkin.palette[0] }
      },
      {
        name: outputLabel,
        type: 'line',
        stack: 'Total',
        smooth: true,
        showSymbol: false,
        areaStyle: { opacity: 0.2 },
        data: data.map(item => item.completionTokens),
        itemStyle: { color: chartSkin.palette[1] }
      },
      {
        name: totalLabel,
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: data.map(item => item.totalTokens),
        itemStyle: { color: chartSkin.palette[4] },
        lineStyle: { width: 2 }
      }
    ]
  }

  dailyChart.setOption(option, { notMerge: true })
}

// 更新每周趋势图表
const updateWeeklyChart = () => {
  if (!weeklyChart) return

  const data = (statistics.value.byWeek || []).map(item => ({
    week: item.weekLabel,
    totalTokens: item.totalTokens
  }))

  const chartSkin = skin.value

  const option = {
    tooltip: {
      ...chartSkin.tooltip,
      trigger: 'axis'
    },
    grid: { ...chartSkin.grid },
    xAxis: {
      ...chartSkin.categoryAxis,
      type: 'category',
      data: data.map(item => item.week),
      boundaryGap: false
    },
    yAxis: {
      ...chartSkin.valueAxis,
      type: 'value'
    },
    series: [
      {
        name: t('callHistory.common.totalToken'),
        type: 'line',
        smooth: true,
        showSymbol: false,
        areaStyle: { opacity: 0.2 },
        data: data.map(item => item.totalTokens),
        itemStyle: { color: chartSkin.palette[0] }
      }
    ]
  }

  weeklyChart.setOption(option, { notMerge: true })
}

// 更新每月趋势图表
const updateMonthlyChart = () => {
  if (!monthlyChart) return

  const data = (statistics.value.byMonth || []).map(item => ({
    month: item.monthLabel,
    totalTokens: item.totalTokens
  }))

  const chartSkin = skin.value

  const option = {
    tooltip: {
      ...chartSkin.tooltip,
      trigger: 'axis'
    },
    grid: { ...chartSkin.grid },
    xAxis: {
      ...chartSkin.categoryAxis,
      type: 'category',
      data: data.map(item => item.month),
      boundaryGap: false
    },
    yAxis: {
      ...chartSkin.valueAxis,
      type: 'value'
    },
    series: [
      {
        name: t('callHistory.common.totalToken'),
        type: 'line',
        smooth: true,
        showSymbol: false,
        areaStyle: { opacity: 0.2 },
        data: data.map(item => item.totalTokens),
        itemStyle: { color: chartSkin.palette[0] }
      }
    ]
  }

  monthlyChart.setOption(option, { notMerge: true })
}

// 更新小时分布图表（24 根柱 + 基线，不再用渐变填充）
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
  const chartSkin = skin.value

  const option = {
    tooltip: {
      ...chartSkin.tooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: { ...chartSkin.grid },
    xAxis: {
      ...chartSkin.categoryAxis,
      type: 'category',
      data: hours
    },
    yAxis: {
      ...chartSkin.valueAxis,
      type: 'value'
    },
    series: [
      {
        name: t('callHistory.tokenUsage.chart.tokenUsage'),
        type: 'bar',
        data: hourlyData,
        barMaxWidth: 20,
        itemStyle: {
          color: chartSkin.palette[0],
          borderRadius: [4, 4, 0, 0]
        },
        label: { show: false }
      }
    ]
  }

  hourlyChart.setOption(option, { notMerge: true })
}

// 加载数据
const loadData = async () => {
  loading.value = true
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
  } catch (error) {
    console.error('加载统计数据失败:', error)
    ElMessage.error(t('callHistory.tokenUsage.loadFailed', { message: getErrorMessage(error) || t('callHistory.common.unknownError') }))
  } finally {
    loading.value = false
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

/**
 * 单类 ⇄ 多类切换时维护环形图实例。
 *
 * 模型占比容器只在「多于 1 个模型」时渲染（单类改渲染大数字）：
 * - ≤1 → >1：等 DOM 挂上容器后再 init + setOption，否则 ref 为空、图空白；
 * - >1 → ≤1：容器被卸载，必须 dispose，否则实例随节点一起泄漏。
 *
 * 注意 ≤1 期间容器是被 `v-if` 卸载的，旧实例即使还活着也绑在已脱离文档的节点上，
 * 所以进入 >1 分支时先无条件 dispose 再重建，不能只判 `!modelChart`。
 */
watch(
  () => (statistics.value.byModel || []).length,
  async (length, previous) => {
    const prev = previous ?? 0
    if (length > 1 && prev <= 1) {
      await nextTick()
      modelChart?.dispose()
      modelChart = null
      if (modelChartRef.value) {
        modelChart = echarts.init(modelChartRef.value)
      }
      updateModelChart()
    } else if (length <= 1 && modelChart) {
      modelChart.dispose()
      modelChart = null
    }
  }
)

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
  modelChart = null
  serviceTypeChart = null
  dailyChart = null
  weeklyChart = null
  monthlyChart = null
  hourlyChart = null
})
</script>

<style scoped>
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.4;
}

.page-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--ja-ink-3);
  line-height: 1.5;
}

.filter-form {
  display: flex;
  justify-content: center;
  width: 100%;
}

/* 图表容器撑满 ChartCard 的固定高度 body，保证 echarts 初始化时尺寸已确定 */
.chart-container {
  width: 100%;
  height: 100%;
}

/* 单类数据：大数字 + 说明（替代无意义的单圈环形图） */
.chart-single {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--ja-space-2);
  height: 100%;
  min-width: 0;
  text-align: center;
}

.chart-single__value {
  color: var(--ja-ink);
  font-size: 34px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
  overflow-wrap: anywhere;
}

.chart-single__label {
  color: var(--ja-ink);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.chart-single__hint {
  max-width: 26em;
  color: var(--ja-ink-3);
  font-size: 12px;
  line-height: 1.5;
}

.chart-title {
  font-size: 16px;
  font-weight: bold;
}
</style>
