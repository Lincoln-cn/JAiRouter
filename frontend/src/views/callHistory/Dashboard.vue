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
      <!-- 概览指标：8 张彩色图标 KPI 卡 → 一条中性 MetricStrip；
           只有「失败请求数 > 0」这种真语义才给 tone -->
      <MetricStrip :items="kpiItems" :loading="loading" />
    </template>

    <!-- 图表区域：每张图一张 ChartCard（默认槽始终挂载，空态为覆盖层） -->
    <el-row :gutter="20" class="chart-row">
      <!-- 请求趋势 -->
      <el-col :span="12">
        <ChartCard
          :title="t('callHistory.dashboard.chart.dailyTrendTitle')"
          :loading="loading"
          :empty="dailyEmpty"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
        >
          <div ref="dailyChartRef" class="chart-container"></div>
        </ChartCard>
      </el-col>

      <!-- 模型分布 -->
      <el-col :span="12">
        <ChartCard
          :title="t('callHistory.dashboard.chart.modelDistributionTitle')"
          :loading="loading"
          :empty="modelEmpty"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
        >
          <!-- 单类数据：饼图只剩一整圈（无对比意义）⇒ 「大数字 + 说明」 -->
          <div v-if="modelSingle" class="chart-single">
            <div class="chart-single__value">{{ formatNumber(modelSingleItem?.requestCount) }}</div>
            <div class="chart-single__label">{{ modelSingleItem?.modelName || t('callHistory.common.unknown') }}</div>
            <div class="chart-single__hint">{{ t('callHistory.dashboard.chart.singleCategoryHint') }}</div>
          </div>
          <div v-else ref="modelChartRef" class="chart-container"></div>
        </ChartCard>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="chart-row">
      <!-- 服务类型分布 -->
      <el-col :span="12">
        <ChartCard
          :title="t('callHistory.dashboard.chart.serviceTypeDistributionTitle')"
          :loading="loading"
          :empty="serviceTypeEmpty"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
        >
          <div v-if="serviceTypeSingle" class="chart-single">
            <div class="chart-single__value">{{ formatNumber(serviceTypeSingleItem?.requestCount) }}</div>
            <div class="chart-single__label">{{ getServiceTypeLabel(serviceTypeSingleItem?.serviceType) }}</div>
            <div class="chart-single__hint">{{ t('callHistory.dashboard.chart.singleCategoryHint') }}</div>
          </div>
          <div v-else ref="serviceTypeChartRef" class="chart-container"></div>
        </ChartCard>
      </el-col>

      <!-- 小时分布 -->
      <el-col :span="12">
        <ChartCard
          :title="t('callHistory.dashboard.chart.hourlyDistributionTitle')"
          :loading="loading"
          :empty="hourlyEmpty"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
        >
          <div ref="hourlyChartRef" class="chart-container"></div>
        </ChartCard>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="chart-row">
      <!-- 状态码分布 -->
      <el-col :span="12">
        <ChartCard
          :title="t('callHistory.dashboard.chart.statusCodeDistributionTitle')"
          :loading="loading"
          :empty="statusCodeEmpty"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
        >
          <div v-if="statusCodeSingle" class="chart-single">
            <div class="chart-single__value">{{ formatNumber(statusCodeSingleItem?.count) }}</div>
            <div class="chart-single__label">{{ statusCodeSingleItem?.statusCode }}</div>
            <div class="chart-single__hint">{{ t('callHistory.dashboard.chart.singleCategoryHint') }}</div>
          </div>
          <div v-else ref="statusCodeChartRef" class="chart-container"></div>
        </ChartCard>
      </el-col>

      <!-- 错误码分布 -->
      <el-col :span="12">
        <ChartCard
          :title="t('callHistory.dashboard.chart.errorCodeDistributionTitle')"
          :loading="loading"
          :empty="errorCodeEmpty"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
        >
          <div ref="errorCodeChartRef" class="chart-container"></div>
        </ChartCard>
      </el-col>
    </el-row>

    <!-- 最近调用记录 -->
    <el-card shadow="hover" class="recent-card">
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
            <el-tag :type="scope.row.isSuccess ? 'info' : 'danger'" size="small">
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
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getErrorMessage } from '@/utils/error'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import {
  getCallHistoryDashboard,
  getCallHistoryStatistics
} from '@/api/callHistory'
import type {
  CallHistoryStatistics,
  ApiCallHistoryRecord,
  ModelCallStats,
  ServiceTypeCallStats,
  StatusCodeStats,
} from '@/types/callHistory'
import PageSkeleton from '@/components/PageSkeleton.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import ChartCard from '@/components/ChartCard.vue'
import { useChartSkin } from '@/composables/useChartSkin'
import { useChartAutoRefresh } from '@/composables/useChartAutoRefresh'
import { formatDateTime as formatDateTimeBase, formatNumber as formatNumberBase } from '@/utils/format'

const { t } = useI18n()

/** 图表统一皮肤：分类色板只含主色相 + 中性阶，语义色只给失败系列。 */
const { skin } = useChartSkin()

// 时间范围
const dateRange = ref<[string, string] | null>(null)

// 加载中（驱动 MetricStrip 骨架与 ChartCard 覆盖层）
const loading = ref(false)

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

/**
 * 指标条数据。
 *
 * 8 项都是「计数 / 用量」，没有状态语义；唯一例外是失败请求数 > 0 时给 danger。
 */
const kpiItems = computed<MetricStripItem[]>(() => [
  {
    key: 'totalRequests',
    label: t('callHistory.dashboard.stats.totalRequests'),
    value: formatNumber(statistics.value.totalRequests)
  },
  {
    key: 'successRate',
    label: t('callHistory.dashboard.stats.successRate'),
    value: `${(statistics.value.successRate || 0).toFixed(2)}%`
  },
  {
    key: 'avgResponseTime',
    label: t('callHistory.dashboard.stats.avgResponseTime'),
    value: `${(statistics.value.avgResponseTimeMs || 0).toFixed(0)} ms`
  },
  {
    key: 'totalTokens',
    label: t('callHistory.dashboard.stats.totalTokenConsumed'),
    value: formatNumber(statistics.value.totalTokens)
  },
  {
    key: 'failedRequests',
    label: t('callHistory.dashboard.stats.failedRequests'),
    value: formatNumber(statistics.value.failedRequests),
    tone: (statistics.value.failedRequests || 0) > 0 ? 'danger' : 'default'
  },
  {
    key: 'modelCount',
    label: t('callHistory.dashboard.stats.modelCount'),
    value: statistics.value.byModel?.length || 0
  },
  {
    key: 'serviceTypeCount',
    label: t('callHistory.dashboard.stats.serviceTypeCount'),
    value: statistics.value.byServiceType?.length || 0
  },
  {
    key: 'avgTokensPerRequest',
    label: t('callHistory.dashboard.stats.avgTokensPerRequest'),
    value: formatNumber(statistics.value.avgTokensPerRequest)
  }
])

// 每张图的空态判定：只看各自的原始数据源是否为空
const dailyEmpty = computed(() => (statistics.value.byDay || []).length === 0)
const modelEmpty = computed(() => (statistics.value.byModel || []).length === 0)
const serviceTypeEmpty = computed(() => (statistics.value.byServiceType || []).length === 0)
const hourlyEmpty = computed(() => (statistics.value.byHour || []).length === 0)
const statusCodeEmpty = computed(() => (statistics.value.byStatusCode || []).length === 0)
const errorCodeEmpty = computed(() => (statistics.value.byErrorCode || []).length === 0)

// 单类情形：饼图/柱图只剩一项，没有对比意义 ⇒ 改渲染「大数字 + 说明」
const modelSingle = computed(() => (statistics.value.byModel || []).length === 1)
const modelSingleItem = computed<ModelCallStats | null>(() =>
  modelSingle.value ? statistics.value.byModel[0] || null : null
)
const serviceTypeSingle = computed(() => (statistics.value.byServiceType || []).length === 1)
const serviceTypeSingleItem = computed<ServiceTypeCallStats | null>(() =>
  serviceTypeSingle.value ? statistics.value.byServiceType[0] || null : null
)
const statusCodeSingle = computed(() => (statistics.value.byStatusCode || []).length === 1)
const statusCodeSingleItem = computed<StatusCodeStats | null>(() =>
  statusCodeSingle.value ? statistics.value.byStatusCode[0] || null : null
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

// 获取 HTTP 状态码标签类型（成功/失败两态：4xx 与 5xx 都是失败，其余中性）
const getStatusType = (code?: number) => {
  if (!code) return 'info'
  return code >= 400 ? 'danger' : 'info'
}

// 获取响应时间标签类型（只有超过 1s 才算慢，避免引入第 4 个色相）
const getResponseTimeType = (ms?: number) => {
  if (!ms) return 'info'
  return ms >= 1000 ? 'danger' : 'info'
}

// 初始化图表（容器已初始化时复用实例，避免重复 init 警告）
const initCharts = () => {
  nextTick(() => {
    if (!dailyChart && dailyChartRef.value) {
      dailyChart = echarts.init(dailyChartRef.value)
    }
    if (!modelChart && !modelSingle.value && modelChartRef.value) {
      modelChart = echarts.init(modelChartRef.value)
    }
    if (!serviceTypeChart && !serviceTypeSingle.value && serviceTypeChartRef.value) {
      serviceTypeChart = echarts.init(serviceTypeChartRef.value)
    }
    if (!hourlyChart && hourlyChartRef.value) {
      hourlyChart = echarts.init(hourlyChartRef.value)
    }
    if (!statusCodeChart && !statusCodeSingle.value && statusCodeChartRef.value) {
      statusCodeChart = echarts.init(statusCodeChartRef.value)
    }
    if (!errorCodeChart && errorCodeChartRef.value) {
      errorCodeChart = echarts.init(errorCodeChartRef.value)
    }
    rebuildAll()
  })
}

// 更新每日趋势图表（请求数柱 + Token 折线，双轴）
const updateDailyChart = () => {
  if (!dailyChart) return

  const chartSkin = skin.value
  const data = (statistics.value.byDay || []).map(item => ({
    date: item.date,
    requestCount: item.requestCount,
    totalTokens: item.totalTokens
  }))
  const requestLabel = t('callHistory.common.requestCount')
  const tokenLabel = t('callHistory.common.totalToken')

  const option = {
    tooltip: { ...chartSkin.tooltip, trigger: 'axis' },
    legend: { ...chartSkin.legend, data: [requestLabel, tokenLabel] },
    grid: { ...chartSkin.grid },
    xAxis: {
      ...chartSkin.categoryAxis,
      data: data.map(item => item.date)
    },
    yAxis: [
      {
        ...chartSkin.valueAxis,
        name: requestLabel,
        nameTextStyle: { color: chartSkin.textMuted, fontSize: 12 },
        position: 'left'
      },
      {
        ...chartSkin.valueAxis,
        name: 'Token',
        nameTextStyle: { color: chartSkin.textMuted, fontSize: 12 },
        position: 'right',
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: requestLabel,
        type: 'bar',
        data: data.map(item => item.requestCount),
        barMaxWidth: 24,
        itemStyle: { color: chartSkin.palette[0], borderRadius: [4, 4, 0, 0] }
      },
      {
        name: tokenLabel,
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        showSymbol: false,
        data: data.map(item => item.totalTokens),
        lineStyle: { color: chartSkin.palette[4] },
        itemStyle: { color: chartSkin.palette[4] }
      }
    ]
  }

  dailyChart.setOption(option, { notMerge: true })
}

// 更新模型图表（占比图：单类情形不渲染本图，见模板）
const updateModelChart = () => {
  if (!modelChart) return

  const chartSkin = skin.value
  const data = (statistics.value.byModel || [])
    .slice(0, 10)
    .map(item => ({
      name: item.modelName || t('callHistory.common.unknown'),
      value: item.requestCount
    }))

  const option = {
    // 饼图各扇区没有系列级颜色可设，必须给 option 级 `color`，
    // 否则 ECharts 会回落到内置调色板（红绿蓝），与本页统一皮肤冲突。
    color: chartSkin.palette,
    tooltip: {
      ...chartSkin.tooltip,
      trigger: 'item',
      formatter: `{b}: {c} ${t('callHistory.dashboard.chart.countUnit')} ({d}%)`
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

// 更新服务类型图表（纵向柱图 + 基线；单类情形不渲染本图）
const updateServiceTypeChart = () => {
  if (!serviceTypeChart) return

  const chartSkin = skin.value
  const data = (statistics.value.byServiceType || []).map(item => ({
    name: getServiceTypeLabel(item.serviceType),
    value: item.requestCount
  }))

  const option = {
    tooltip: {
      ...chartSkin.tooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: { ...chartSkin.grid },
    xAxis: {
      ...chartSkin.categoryAxis,
      data: data.map(item => item.name),
      axisLabel: { color: chartSkin.textMuted, fontSize: 12, interval: 0, rotate: 0 }
    },
    yAxis: { ...chartSkin.valueAxis },
    series: [
      {
        type: 'bar',
        data: data.map(item => item.value),
        barMaxWidth: 32,
        itemStyle: { color: chartSkin.palette[0], borderRadius: [4, 4, 0, 0] },
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

// 更新小时分布图表（实心柱，不再使用渐变）
const updateHourlyChart = () => {
  if (!hourlyChart) return

  const chartSkin = skin.value
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
      ...chartSkin.tooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: { ...chartSkin.grid },
    xAxis: {
      ...chartSkin.categoryAxis,
      data: hours
    },
    yAxis: { ...chartSkin.valueAxis },
    series: [
      {
        name: t('callHistory.common.requestCount'),
        type: 'bar',
        data: hourlyData,
        barMaxWidth: 16,
        itemStyle: { color: chartSkin.palette[0], borderRadius: [3, 3, 0, 0] }
      }
    ]
  }

  hourlyChart.setOption(option, { notMerge: true })
}

// 更新状态码图表（占比图；2xx 主色 / 3xx 中性 / 4xx·5xx 语义色）
const updateStatusCodeChart = () => {
  if (!statusCodeChart) return

  const chartSkin = skin.value
  const data = (statistics.value.byStatusCode || []).map(item => ({
    name: `${item.statusCode}`,
    value: item.count
  }))

  const statusColor = (code: string) => {
    const num = parseInt(code, 10)
    if (num >= 200 && num < 300) return chartSkin.palette[0]
    if (num >= 300 && num < 400) return chartSkin.palette[4]
    return chartSkin.emphasis
  }

  const option = {
    color: chartSkin.palette,
    tooltip: {
      ...chartSkin.tooltip,
      trigger: 'item',
      formatter: `{b}: {c} ${t('callHistory.dashboard.chart.countUnit')} ({d}%)`
    },
    legend: {
      ...chartSkin.legend,
      bottom: 0,
      top: 'auto',
      right: 'auto',
      left: 'center'
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '66%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        data: data.map(item => ({
          name: item.name,
          value: item.value,
          itemStyle: { color: statusColor(item.name) }
        })),
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

  statusCodeChart.setOption(option, { notMerge: true })
}

// 更新错误码图表（错误本身即语义 ⇒ 统一语义色）
const updateErrorCodeChart = () => {
  if (!errorCodeChart) return

  const chartSkin = skin.value
  const data = (statistics.value.byErrorCode || [])
    .filter(item => item.errorCode)
    .slice(0, 10)
    .map(item => ({
      name: item.errorCode,
      value: item.count
    }))

  const option = {
    tooltip: {
      ...chartSkin.tooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: { ...chartSkin.grid },
    xAxis: {
      ...chartSkin.categoryAxis,
      data: data.map(item => item.name),
      axisLabel: { color: chartSkin.textMuted, fontSize: 11, interval: 0, rotate: 30 }
    },
    yAxis: { ...chartSkin.valueAxis },
    series: [
      {
        type: 'bar',
        data: data.map(item => item.value),
        barMaxWidth: 32,
        itemStyle: { color: chartSkin.emphasis, borderRadius: [4, 4, 0, 0] },
        label: {
          show: true,
          position: 'top',
          color: chartSkin.textMuted,
          fontSize: 12
        }
      }
    ]
  }

  errorCodeChart.setOption(option, { notMerge: true })
}

// 单类 ⇄ 多类切换时维护 echarts 实例（容器被 v-if 卸载/重建）
const syncSingleModeChart = async (
  isSingle: boolean,
  wasSingle: boolean,
  element: HTMLElement | null,
  current: ECharts | null
): Promise<ECharts | null> => {
  if (isSingle) {
    current?.dispose()
    return null
  }
  if (wasSingle) {
    await nextTick()
    return element ? echarts.init(element) : null
  }
  return current
}

watch(
  () => (statistics.value.byModel || []).length,
  async (length, previous) => {
    modelChart = await syncSingleModeChart(
      length === 1,
      (previous ?? 0) === 1,
      modelChartRef.value,
      modelChart
    )
    updateModelChart()
  }
)

watch(
  () => (statistics.value.byServiceType || []).length,
  async (length, previous) => {
    serviceTypeChart = await syncSingleModeChart(
      length === 1,
      (previous ?? 0) === 1,
      serviceTypeChartRef.value,
      serviceTypeChart
    )
    updateServiceTypeChart()
  }
)

watch(
  () => (statistics.value.byStatusCode || []).length,
  async (length, previous) => {
    statusCodeChart = await syncSingleModeChart(
      length === 1,
      (previous ?? 0) === 1,
      statusCodeChartRef.value,
      statusCodeChart
    )
    updateStatusCodeChart()
  }
)

// 加载数据
const loadData = async () => {
  loading.value = true
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
  } catch (error) {
    console.error('加载调用历史数据失败:', error)
    ElMessage.error(t('callHistory.dashboard.loadFailed', { message: getErrorMessage(error) || t('callHistory.common.unknownError') }))
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
  dailyChart = null
  modelChart = null
  serviceTypeChart = null
  hourlyChart = null
  statusCodeChart = null
  errorCodeChart = null
})
</script>

<style scoped>
.filter-form {
  display: flex;
  justify-content: center;
}

.chart-row {
  margin-top: var(--ja-space-5);
}

.recent-card {
  margin-top: var(--ja-space-5);
}

/* 图表容器撑满 ChartCard 的固定高度 body，保证 echarts 初始化时尺寸已确定 */
.chart-container {
  width: 100%;
  height: 100%;
}

/* 单类数据：大数字 + 说明（替代无对比意义的单圈饼图） */
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
  color: var(--ja-ink-3);
  font-size: 12px;
  line-height: 1.5;
  max-width: 26em;
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
  color: var(--ja-ink-3);
}
</style>
