<template>
  <PageSkeleton>
    <template #title>
      <h2>{{ t('tracing.dashboard.title') }}</h2>
      <el-tag :type="tracingEnabled ? 'info' : 'danger'" size="small">
        {{ tracingEnabled ? t('tracing.dashboard.enabledTag') : t('tracing.dashboard.disabledTag') }}
      </el-tag>
    </template>

    <template #actions>
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        :range-separator="t('tracing.dashboard.timeRangeTo')"
        :start-placeholder="t('tracing.dashboard.startTimePlaceholder')"
        :end-placeholder="t('tracing.dashboard.endTimePlaceholder')"
        format="YYYY-MM-DD HH:mm:ss"
        value-format="YYYY-MM-DD HH:mm:ss"
        :shortcuts="timeShortcuts"
        @change="handleTimeRangeChange"
      />
      <el-button type="primary" @click="handleRefresh" :loading="refreshing">
        <el-icon><Refresh /></el-icon>
        {{ t('tracing.dashboard.refresh') }}
      </el-button>
      <el-button @click="goToSearch">
        <el-icon><Search /></el-icon>
        {{ t('tracing.dashboard.searchTraces') }}
      </el-button>
      <el-button @click="goToManagement">
        <el-icon><Setting /></el-icon>
        {{ t('tracing.dashboard.config') }}
      </el-button>
    </template>

    <template #stats>
      <!-- 关键指标：4 张彩色渐变图标卡 → 一条中性 MetricStrip；
           只有「错误链路数 > 0」是真语义，其余保持 default -->
      <MetricStrip :items="kpiItems" :loading="loading" />
    </template>

    <!-- Tab 区域 -->
    <el-card class="tab-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane :label="t('tracing.dashboard.tabs.overview')" name="overview">
          <el-row :gutter="16">
            <el-col :xs="24" :lg="12">
              <ChartCard
                :title="t('tracing.dashboard.chart.traceVolume')"
                :loading="loading"
                :empty="traceTrendEmpty"
                :empty-title="t('common.chartEmpty')"
                :empty-description="t('common.chartEmptyHint')"
              >
                <template #actions>
                  <el-radio-group v-model="chartInterval" size="small" @change="updateTrendChart">
                    <el-radio-button label="5m">{{ t('tracing.dashboard.chart.intervals.5m') }}</el-radio-button>
                    <el-radio-button label="1h">{{ t('tracing.dashboard.chart.intervals.1h') }}</el-radio-button>
                    <el-radio-button label="1d">{{ t('tracing.dashboard.chart.intervals.1d') }}</el-radio-button>
                  </el-radio-group>
                </template>
                <div ref="traceTrendChart" class="chart-container"></div>
              </ChartCard>
            </el-col>
            <el-col :xs="24" :lg="12">
              <ChartCard
                :title="t('tracing.dashboard.chart.latencyDistribution')"
                :loading="loading"
                :empty="latencyDistEmpty"
                :empty-title="t('common.chartEmpty')"
                :empty-description="t('common.chartEmptyHint')"
              >
                <div ref="latencyDistributionChart" class="chart-container"></div>
              </ChartCard>
            </el-col>
          </el-row>

          <!-- 服务统计表格 -->
          <div class="service-stats-section">
            <div class="section-header">
              <h4>{{ t('tracing.dashboard.serviceStatistics') }}</h4>
              <el-button text type="primary" @click="goToSearch">{{ t('tracing.dashboard.viewAll') }}</el-button>
            </div>
            <el-table
              :data="serviceStats"
              v-loading="loading"
              style="width: 100%"
              :row-class-name="getRowClassName"
              @row-click="handleServiceRowClick"
            >
              <el-table-column prop="name" :label="t('tracing.dashboard.columns.serviceName')" min-width="150">
                <template #default="{ row }">
                  <div class="service-name">
                    <el-icon :size="16"><Monitor /></el-icon>
                    <span>{{ row.name }}</span>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="traces" :label="t('tracing.dashboard.columns.traces')" width="100" sortable>
                <template #default="{ row }">
                  <el-tag size="small">{{ formatNumber(row.traces) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="avgDuration" :label="t('tracing.dashboard.avgLatency')" width="120" sortable>
                <template #default="{ row }">
                  <span :class="getLatencyClass(row.avgDuration)">{{ row.avgDuration }}ms</span>
                </template>
              </el-table-column>
              <el-table-column prop="p95Duration" :label="t('tracing.dashboard.columns.p95Latency')" width="120" sortable>
                <template #default="{ row }">
                  <span :class="getLatencyClass(row.p95Duration)">{{ row.p95Duration }}ms</span>
                </template>
              </el-table-column>
              <el-table-column prop="errors" :label="t('tracing.dashboard.columns.errors')" width="100" sortable>
                <template #default="{ row }">
                  <span :class="{ 'text-danger': row.errors > 0 }">{{ row.errors }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="errorRate" :label="t('tracing.dashboard.columns.errorRate')" width="100" sortable>
                <template #default="{ row }">
                  <el-progress
                    :percentage="row.errorRate"
                    :status="row.errorRate > 5 ? 'exception' : ''"
                    :stroke-width="8"
                    :show-text="false"
                  />
                  <span class="error-rate-text" :class="{ 'text-danger': row.errorRate > 5 }">
                    {{ row.errorRate }}%
                  </span>
                </template>
              </el-table-column>
              <el-table-column :label="t('tracing.dashboard.columns.status')" width="80">
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

        <el-tab-pane :label="t('tracing.dashboard.tabs.latency')" name="latency">
          <el-row :gutter="16">
            <el-col :xs="24" :lg="12">
              <ChartCard
                :title="t('tracing.dashboard.chart.p95P99LatencyTrend')"
                :loading="loading"
                :empty="latencyTrendEmpty"
                :empty-title="t('common.chartEmpty')"
                :empty-description="t('common.chartEmptyHint')"
              >
                <div ref="latencyTrendChart" class="chart-container"></div>
              </ChartCard>
            </el-col>
            <el-col :xs="24" :lg="12">
              <div class="chart-wrapper">
                <div class="chart-header">
                  <h4>{{ t('tracing.dashboard.chart.slowestTraces') }}</h4>
                  <el-button text type="primary" @click="goToSearch">{{ t('tracing.dashboard.viewAll') }}</el-button>
                </div>
                <el-table :data="slowTraces" style="width: 100%" size="small" max-height="300">
                  <el-table-column prop="operationName" :label="t('tracing.dashboard.columns.operation')" show-overflow-tooltip />
                  <el-table-column prop="duration" :label="t('tracing.dashboard.columns.duration')" width="100">
                    <template #default="{ row }">
                      <span class="text-danger">{{ Math.round(row.duration) }}ms</span>
                    </template>
                  </el-table-column>
                  <el-table-column :label="t('tracing.dashboard.columns.action')" width="80">
                    <template #default="{ row }">
                      <el-button text type="primary" size="small" @click="viewTraceDetail(row)">
                        {{ t('tracing.dashboard.viewDetail') }}
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>

        <el-tab-pane :label="t('tracing.dashboard.tabs.error')" name="error">
          <el-row :gutter="16">
            <el-col :xs="24" :lg="12">
              <ChartCard
                :title="t('tracing.dashboard.chart.errorRateTrend')"
                :loading="loading"
                :empty="errorTrendEmpty"
                :empty-title="t('common.chartEmpty')"
                :empty-description="t('common.chartEmptyHint')"
              >
                <div ref="errorTrendChart" class="chart-container"></div>
              </ChartCard>
            </el-col>
            <el-col :xs="24" :lg="12">
              <div class="chart-wrapper">
                <div class="chart-header">
                  <h4>{{ t('tracing.dashboard.chart.commonErrors') }}</h4>
                </div>
                <el-table :data="commonErrors" style="width: 100%" size="small" max-height="300">
                  <el-table-column prop="errorType" :label="t('tracing.dashboard.columns.errorType')" width="150">
                    <template #default="{ row }">
                      <el-tag type="danger" size="small">{{ row.errorType || 'Unknown' }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="count" :label="t('tracing.dashboard.columns.count')" width="80">
                    <template #default="{ row }">
                      <span class="text-danger">{{ row.count }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="service" :label="t('tracing.dashboard.columns.service')" show-overflow-tooltip />
                </el-table>
              </div>
            </el-col>
          </el-row>
        </el-tab-pane>

        <el-tab-pane :label="t('tracing.dashboard.tabs.throughput')" name="throughput">
          <el-row :gutter="16">
            <el-col :xs="24" :lg="12">
              <ChartCard
                :title="t('tracing.dashboard.chart.requestVolume')"
                :loading="loading"
                :empty="throughputTrendEmpty"
                :empty-title="t('common.chartEmpty')"
                :empty-description="t('common.chartEmptyHint')"
              >
                <div ref="throughputChart" class="chart-container"></div>
              </ChartCard>
            </el-col>
            <el-col :xs="24" :lg="12">
              <!-- 后端尚未提供「按服务的吞吐分布」数据源，这里显式渲染空态而不是留一块空白画布 -->
              <ChartCard
                :title="t('tracing.dashboard.chart.serviceThroughput')"
                :empty="true"
                :empty-title="t('common.chartEmpty')"
                :empty-description="t('common.chartEmptyHint')"
              >
                <div class="chart-container"></div>
              </ChartCard>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </PageSkeleton>

  <!-- 追踪详情抽屉 -->
  <el-drawer
    v-model="traceDetailVisible"
    :title="t('tracing.dashboard.drawerTitle')"
    direction="rtl"
    size="60%"
  >
    <TraceDetail v-if="selectedTrace" :trace-id="selectedTrace.traceId ?? ''" />
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import { ElMessage } from 'element-plus'
import {
  Refresh, Search, Setting, Monitor,
  CircleCheckFilled, WarningFilled, CircleCloseFilled
} from '@element-plus/icons-vue'
import {
  getTracingOverview,
  getServiceStats,
  getTracingStatus,
  getLatencyAnalysis,
  getErrorAnalysis,
  getThroughputAnalysis,
} from '@/api/tracing'
import type { TraceSpan, TracingServiceStat, TracingStatistics, TracingStatus } from '@/types/tracing'
import type { LocationQueryRaw } from 'vue-router'
import type { CommonError, ErrorAnalysis, LatencyAnalysis, LatencyTrendData, ThroughputAnalysis, TimeSeriesData } from '@/types'
import TraceDetail from './components/TraceDetail.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import ChartCard from '@/components/ChartCard.vue'
import { useChartSkin } from '@/composables/useChartSkin'
import { useChartAutoRefresh } from '@/composables/useChartAutoRefresh'
import { formatNumber as formatNumberBase } from '@/utils/format'

const { t } = useI18n()

/** 图表统一皮肤：一次 computed 复用到全部 5 张图。 */
const { skin } = useChartSkin()

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

const serviceStats = ref<TracingServiceStat[]>([])
const slowTraces = ref<TraceSpan[]>([])
const commonErrors = ref<CommonError[]>([])

// 趋势图数据源（来自 /tracing/query/performance/*；缺失时渲染空图，不使用占位数据）
const latencyAnalysis = ref<LatencyAnalysis | null>(null)
const errorAnalysis = ref<ErrorAnalysis | null>(null)
const throughputAnalysis = ref<ThroughputAnalysis | null>(null)

// 图表引用
const traceTrendChart = ref<HTMLElement | null>(null)
const latencyDistributionChart = ref<HTMLElement | null>(null)
const latencyTrendChart = ref<HTMLElement | null>(null)
const errorTrendChart = ref<HTMLElement | null>(null)
const throughputChart = ref<HTMLElement | null>(null)

let traceTrendEcharts: ECharts | null = null
let latencyDistEcharts: ECharts | null = null
let latencyTrendEcharts: ECharts | null = null
let errorTrendEcharts: ECharts | null = null
let throughputEcharts: ECharts | null = null

// 追踪详情
const traceDetailVisible = ref(false)
const selectedTrace = ref<TraceSpan | null>(null)

// 数据空态：各图只看自己的数据源
// 追踪趋势图当前没有后端时序接口（issue #253），恒定空态，不用占位数据伪造曲线。
const traceTrendEmpty = true
const latencyDistEmpty = computed(() => serviceStats.value.length === 0)
const latencyTrendEmpty = computed(() => (latencyAnalysis.value?.trend?.timestamps || []).length === 0)
const errorTrendEmpty = computed(() => (errorAnalysis.value?.errorTrend || []).length === 0)
const throughputTrendEmpty = computed(() => (throughputAnalysis.value?.qpsTrend || []).length === 0)

/** 环比提示文案（有变化才显示，避免每格都挂一条 0%）。 */
const formatTrendHint = (value: number): string | undefined => {
  if (!value) return undefined
  return `${value > 0 ? '+' : ''}${value}% ${t('tracing.dashboard.vsPreviousPeriod')}`
}

/** 指标条：成功率/用量类数字一律中性，只有错误链路数是真语义。 */
const kpiItems = computed<MetricStripItem[]>(() => [
  {
    key: 'totalTraces',
    label: t('tracing.dashboard.totalTraces'),
    value: formatNumber(stats.value.totalTraces),
    hint: formatTrendHint(trendData.value.totalTracesTrend)
  },
  {
    key: 'errorTraces',
    label: t('tracing.dashboard.errorTraces'),
    value: formatNumber(stats.value.errorTraces),
    hint: formatTrendHint(trendData.value.errorTrend),
    tone: stats.value.errorTraces > 0 ? 'danger' : 'default'
  },
  {
    key: 'avgDuration',
    label: t('tracing.dashboard.avgLatency'),
    value: `${stats.value.avgDuration} ms`,
    hint: formatTrendHint(trendData.value.avgDurationTrend)
  },
  {
    key: 'samplingRate',
    label: t('tracing.dashboard.samplingRate'),
    value: `${stats.value.samplingRate}%`
  }
])

// 时间快捷选项
const timeShortcuts = computed(() => [
  {
    text: t('tracing.dashboard.shortcuts.last1h'),
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000)
      return [start, end]
    }
  },
  {
    text: t('tracing.dashboard.shortcuts.last6h'),
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 6)
      return [start, end]
    }
  },
  {
    text: t('tracing.dashboard.shortcuts.today'),
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setHours(0, 0, 0, 0)
      return [start, end]
    }
  }
])

// 加载数据
const loadAllData = async () => {
  loading.value = true
  try {
    await Promise.all([
      loadOverviewData(),
      loadServiceStats(),
      loadTracingStatus(),
      loadLatencyAnalysis(),
      loadErrorAnalysis(),
      loadThroughputAnalysis()
    ])
    await nextTick()
    initCharts()
    // 趋势图数据可能晚于页签切换到达，这里补一次重绘（未创建的图表会被跳过）
    rebuildAll()
  } catch (error) {
    console.error('加载数据失败:', error)
    ElMessage.warning(t('tracing.dashboard.messages.loadFailed'))
  } finally {
    loading.value = false
  }
}

const loadOverviewData = async () => {
  try {
    const response = await getTracingOverview()
    const data = (response.data?.data || response.data) as TracingStatistics | undefined
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
    const data = (response.data?.data || response.data) as TracingServiceStat[] | undefined
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
    const data = (response.data?.data || response.data) as TracingStatus | undefined
    if (data) {
      tracingEnabled.value = data.enabled !== false
    }
  } catch (error) {
    console.error('加载追踪状态失败:', error)
  }
}

/** 复用已创建的实例，避免重复 echarts.init 造成实例泄漏与警告。 */
const ensureChart = (element: HTMLElement | null, current: ECharts | null): ECharts | null => {
  if (current) return current
  return element ? echarts.init(element) : null
}

// 初始化图表
const initCharts = () => {
  // 追踪趋势图表
  traceTrendEcharts = ensureChart(traceTrendChart.value, traceTrendEcharts)
  traceTrendEcharts?.setOption(getTraceTrendOption(), { notMerge: true })

  // 延迟分布图表
  latencyDistEcharts = ensureChart(latencyDistributionChart.value, latencyDistEcharts)
  latencyDistEcharts?.setOption(getLatencyDistributionOption(), { notMerge: true })
}

// 图表配置
// 追踪趋势：当前后端 API 面没有「按时间桶的追踪数/错误数」时序接口 ——
// /tracing/query/statistics 与 /tracing/actuator/stats 只返回标量快照，
// /tracing/performance/metrics/dashboard 返回处理器与内存快照。故此处渲染空序列，
// 待后端提供时序接口后再接线；不得用随机数占位（issue #253）。
const getTraceTrendOption = () => {
  const chartSkin = skin.value
  return {
    tooltip: { ...chartSkin.tooltip, trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: {
      ...chartSkin.legend,
      data: [t('tracing.dashboard.columns.traces'), t('tracing.dashboard.columns.errors')],
      bottom: 0,
      top: 'auto',
      right: 'auto',
      left: 'center'
    },
    grid: { ...chartSkin.grid },
    xAxis: {
      ...chartSkin.categoryAxis,
      boundaryGap: false,
      data: generateTimeLabels()
    },
    yAxis: { ...chartSkin.valueAxis },
    series: [
      {
        name: t('tracing.dashboard.columns.traces'),
        type: 'line',
        smooth: true,
        showSymbol: false,
        areaStyle: { opacity: 0.12 },
        itemStyle: { color: chartSkin.palette[0] },
        data: []
      },
      {
        name: t('tracing.dashboard.columns.errors'),
        type: 'line',
        smooth: true,
        showSymbol: false,
        itemStyle: { color: chartSkin.emphasis },
        data: []
      }
    ]
  }
}

// 延迟趋势（P95/P99）——数据来自 /tracing/query/performance/latency
const getLatencyTrendOption = () => {
  const chartSkin = skin.value
  const trend: Partial<LatencyTrendData> = latencyAnalysis.value?.trend ?? {}
  return {
    tooltip: { ...chartSkin.tooltip, trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: {
      ...chartSkin.legend,
      data: [t('tracing.dashboard.columns.p95Latency'), t('tracing.dashboard.columns.p99Latency')],
      bottom: 0,
      top: 'auto',
      right: 'auto',
      left: 'center'
    },
    grid: { ...chartSkin.grid },
    xAxis: {
      ...chartSkin.categoryAxis,
      boundaryGap: false,
      data: trend.timestamps || []
    },
    yAxis: { ...chartSkin.valueAxis },
    series: [
      {
        name: t('tracing.dashboard.columns.p95Latency'),
        type: 'line',
        smooth: true,
        showSymbol: false,
        itemStyle: { color: chartSkin.palette[0] },
        data: trend.p95Latency || []
      },
      {
        name: t('tracing.dashboard.columns.p99Latency'),
        type: 'line',
        smooth: true,
        showSymbol: false,
        itemStyle: { color: chartSkin.palette[4] },
        data: trend.p99Latency || []
      }
    ]
  }
}

// 错误率趋势——数据来自 /tracing/query/performance/errors
const getErrorTrendOption = () => {
  const chartSkin = skin.value
  const points: TimeSeriesData[] = errorAnalysis.value?.errorTrend || []
  return {
    tooltip: { ...chartSkin.tooltip, trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: {
      ...chartSkin.legend,
      data: [t('tracing.dashboard.columns.errorRate')],
      bottom: 0,
      top: 'auto',
      right: 'auto',
      left: 'center'
    },
    grid: { ...chartSkin.grid },
    xAxis: {
      ...chartSkin.categoryAxis,
      boundaryGap: false,
      data: points.map(p => p.timestamp)
    },
    yAxis: { ...chartSkin.valueAxis },
    series: [
      {
        name: t('tracing.dashboard.columns.errorRate'),
        type: 'line',
        smooth: true,
        showSymbol: false,
        itemStyle: { color: chartSkin.emphasis },
        data: points.map(p => p.value)
      }
    ]
  }
}

// 请求量趋势——数据来自 /tracing/query/performance/throughput
const getThroughputTrendOption = () => {
  const chartSkin = skin.value
  const points: TimeSeriesData[] = throughputAnalysis.value?.qpsTrend || []
  return {
    tooltip: { ...chartSkin.tooltip, trigger: 'axis', axisPointer: { type: 'cross' } },
    legend: {
      ...chartSkin.legend,
      data: [t('tracing.dashboard.columns.requests')],
      bottom: 0,
      top: 'auto',
      right: 'auto',
      left: 'center'
    },
    grid: { ...chartSkin.grid },
    xAxis: {
      ...chartSkin.categoryAxis,
      boundaryGap: false,
      data: points.map(p => p.timestamp)
    },
    yAxis: { ...chartSkin.valueAxis },
    series: [
      {
        name: t('tracing.dashboard.columns.requests'),
        type: 'line',
        smooth: true,
        showSymbol: false,
        itemStyle: { color: chartSkin.palette[0] },
        data: points.map(p => p.value)
      }
    ]
  }
}

// 三个趋势图的数据加载（接口失败或返回空时保持空序列，不填充占位数据）
const loadLatencyAnalysis = async () => {
  try {
    const response = await getLatencyAnalysis()
    latencyAnalysis.value = (response.data?.data || response.data) as LatencyAnalysis | null
  } catch (error) {
    console.error('加载延迟分析失败:', error)
    latencyAnalysis.value = null
  }
}

const loadErrorAnalysis = async () => {
  try {
    const response = await getErrorAnalysis()
    errorAnalysis.value = (response.data?.data || response.data) as ErrorAnalysis | null
  } catch (error) {
    console.error('加载错误分析失败:', error)
    errorAnalysis.value = null
  }
}

const loadThroughputAnalysis = async () => {
  try {
    const response = await getThroughputAnalysis()
    throughputAnalysis.value = (response.data?.data || response.data) as ThroughputAnalysis | null
  } catch (error) {
    console.error('加载吞吐量分析失败:', error)
    throughputAnalysis.value = null
  }
}

const getLatencyDistributionOption = () => {
  const chartSkin = skin.value
  const services = serviceStats.value.map(s => s.name)
  const avgDurations = serviceStats.value.map(s => s.avgDuration || 0)
  const p95Durations = serviceStats.value.map(s => s.p95Duration || 0)

  return {
    tooltip: {
      ...chartSkin.tooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    legend: {
      ...chartSkin.legend,
      data: [t('tracing.dashboard.avgLatency'), t('tracing.dashboard.columns.p95Latency')],
      bottom: 0,
      top: 'auto',
      right: 'auto',
      left: 'center'
    },
    grid: { ...chartSkin.grid },
    xAxis: {
      ...chartSkin.categoryAxis,
      data: services.length > 0 ? services : [t('tracing.dashboard.chart.noData')],
      axisLabel: { color: chartSkin.textMuted, fontSize: 12, rotate: 30 }
    },
    yAxis: {
      ...chartSkin.valueAxis,
      name: t('tracing.dashboard.chart.latencyUnit'),
      nameTextStyle: { color: chartSkin.textMuted, fontSize: 12 }
    },
    series: [
      {
        name: t('tracing.dashboard.avgLatency'),
        type: 'bar',
        barMaxWidth: 28,
        itemStyle: { color: chartSkin.palette[0], borderRadius: [4, 4, 0, 0] },
        data: avgDurations
      },
      {
        name: t('tracing.dashboard.columns.p95Latency'),
        type: 'bar',
        barMaxWidth: 28,
        itemStyle: { color: chartSkin.palette[4], borderRadius: [4, 4, 0, 0] },
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

const formatNumber = (num: number) => {
  if (num >= 10000) {
    return `${(num / 10000).toFixed(1)  }w`
  }
  return formatNumberBase(num)
}

// 只有「慢到 1s 以上」才标红；原来 500ms 的 warning 档会带出第 4 个色相
const getLatencyClass = (duration: number) => {
  if (duration > 1000) return 'text-danger'
  return ''
}

const getRowClassName = ({ row }: { row: TracingServiceStat }) => {
  if ((row.errorRate ?? 0) > 5) return 'error-row'
  if ((row.avgDuration ?? 0) > 1000) return 'slow-row'
  return ''
}

// 服务状态图标：只有真错误上语义色，健康/偏慢用中性墨色（图标形状+tooltip 已能区分）
const getServiceStatusColor = (row: TracingServiceStat) => {
  return (row.errorRate ?? 0) > 5 ? skin.value.emphasis : skin.value.textMuted
}

const getServiceStatusIcon = (row: TracingServiceStat) => {
  if ((row.errorRate ?? 0) > 5) return CircleCloseFilled
  if ((row.avgDuration ?? 0) > 1000) return WarningFilled
  return CircleCheckFilled
}

const getServiceStatusTooltip = (row: TracingServiceStat) => {
  if ((row.errorRate ?? 0) > 5) return t('tracing.dashboard.statusTooltip.highErrorRate')
  if ((row.avgDuration ?? 0) > 1000) return t('tracing.dashboard.statusTooltip.highLatency')
  return t('tracing.dashboard.statusTooltip.healthy')
}

// 事件处理
const handleRefresh = async () => {
  refreshing.value = true
  try {
    await loadAllData()
    ElMessage.success(t('tracing.dashboard.messages.refreshed'))
  } finally {
    refreshing.value = false
  }
}

const handleTimeRangeChange = () => {
  loadAllData()
}

const handleTabChange = (tab: string) => {
  nextTick(() => {
    if (tab === 'latency') {
      latencyTrendEcharts = ensureChart(latencyTrendChart.value, latencyTrendEcharts)
      latencyTrendEcharts?.setOption(getLatencyTrendOption(), { notMerge: true })
    } else if (tab === 'error') {
      errorTrendEcharts = ensureChart(errorTrendChart.value, errorTrendEcharts)
      errorTrendEcharts?.setOption(getErrorTrendOption(), { notMerge: true })
    } else if (tab === 'throughput') {
      throughputEcharts = ensureChart(throughputChart.value, throughputEcharts)
      throughputEcharts?.setOption(getThroughputTrendOption(), { notMerge: true })
    }
  })
}

const goToSearch = (filter?: string) => {
  const query: LocationQueryRaw = {}
  if (filter === 'error') {
    query.hasError = 'true'
  }
  router.push({ name: 'tracing-search', query })
}

const goToManagement = () => {
  router.push({ name: 'tracing-management' })
}

const handleServiceRowClick = (row: TracingServiceStat) => {
  router.push({ name: 'tracing-search', query: { serviceName: String(row.name ?? row.serviceName ?? '') } })
}

const viewTraceDetail = (trace: TraceSpan) => {
  selectedTrace.value = trace
  traceDetailVisible.value = true
}

const updateTrendChart = () => {
  traceTrendEcharts?.setOption(getTraceTrendOption(), { notMerge: true })
}

// 语言 / 主题切换后重绘所有已创建图表（纯重绘，无网络请求；未创建/隐藏页签图表自动跳过）
const rebuildAll = () => {
  traceTrendEcharts?.setOption(getTraceTrendOption(), { notMerge: true })
  latencyDistEcharts?.setOption(getLatencyDistributionOption(), { notMerge: true })
  latencyTrendEcharts?.setOption(getLatencyTrendOption(), { notMerge: true })
  errorTrendEcharts?.setOption(getErrorTrendOption(), { notMerge: true })
  throughputEcharts?.setOption(getThroughputTrendOption(), { notMerge: true })
}

// 窗口大小变化处理
const handleResize = () => {
  traceTrendEcharts?.resize()
  latencyDistEcharts?.resize()
  latencyTrendEcharts?.resize()
  errorTrendEcharts?.resize()
  throughputEcharts?.resize()
}

// 生命周期
onMounted(() => {
  window.addEventListener('resize', handleResize)
  useChartAutoRefresh(rebuildAll)
  loadAllData()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  traceTrendEcharts?.dispose()
  latencyDistEcharts?.dispose()
  latencyTrendEcharts?.dispose()
  errorTrendEcharts?.dispose()
  throughputEcharts?.dispose()
  traceTrendEcharts = null
  latencyDistEcharts = null
  latencyTrendEcharts = null
  errorTrendEcharts = null
  throughputEcharts = null
})
</script>

<style scoped>
.tab-card {
  margin-bottom: var(--ja-space-4);
}

/* 表格类面板沿用同一套容器样式（图表面板由 ChartCard 自带） */
.chart-wrapper {
  padding: var(--ja-space-4);
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ja-space-3);
}

.chart-header h4 {
  margin: 0;
  font-size: 16px;
  color: var(--ja-ink);
}

/* 撑满 ChartCard 的固定高度 body，保证 echarts.init 时尺寸已确定 */
.chart-container {
  width: 100%;
  height: 100%;
}

.service-stats-section {
  margin-top: var(--ja-space-6);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ja-space-4);
}

.section-header h4 {
  margin: 0;
  font-size: 16px;
  color: var(--ja-ink);
}

.service-name {
  display: flex;
  align-items: center;
  gap: var(--ja-space-2);
}

.error-rate-text {
  font-size: 12px;
  margin-top: 4px;
  display: block;
  color: var(--ja-ink-3);
}

.text-danger {
  color: var(--ja-danger);
}

:deep(.error-row) {
  background-color: var(--ja-dashboard-error-row);
}

/* 偏慢行只做中性底纹（原来用橙色 light-9，属第 4 色相） */
:deep(.slow-row) {
  background-color: var(--ja-surface-2);
}
</style>
