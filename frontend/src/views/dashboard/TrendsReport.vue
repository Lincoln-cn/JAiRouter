<template>
  <PageSkeleton
    :title="t('dashboardHome.trends.title')"
    :subtitle="t('dashboardHome.trends.subtitle')"
  >
    <template #actions>
      <el-button :icon="Refresh" :loading="loading" @click="reload">
        {{ t('dashboardHome.refresh') }}
      </el-button>
    </template>

    <template #stats>
      <MetricStrip :items="metrics" :loading="loading" />
    </template>

    <template #toolbar>
      <el-radio-group v-model="preset" size="small" @change="reload">
        <el-radio-button v-for="item in presets" :key="item" :value="item">
          {{ t(`dashboardHome.range.${item}`) }}
        </el-radio-button>
      </el-radio-group>
    </template>

    <p v-if="failed" class="load-failed">{{ t('dashboardHome.loadFailed') }}</p>

    <ChartCard
      :title="t('dashboardHome.trends.chartDaily')"
      :loading="loading"
      :empty="!hasData"
      :empty-title="t('dashboardHome.chartEmpty')"
    >
      <div ref="trendChartRef" class="chart" />
    </ChartCard>

    <ChartCard
      class="second-chart"
      :title="t('dashboardHome.trends.chartHourly')"
      :loading="loading"
      :empty="!hasData"
      :empty-title="t('dashboardHome.chartEmpty')"
    >
      <div ref="hourChartRef" class="chart" />
    </ChartCard>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import PageSkeleton from '@/components/PageSkeleton.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import ChartCard from '@/components/ChartCard.vue'
import { useChartSkin } from '@/composables/useChartSkin'
import { formatCount, formatMs, formatPercent, useStatsRange } from '@/composables/useStatsRange'

/**
 * 趋势报表（VIEWER 的落地页）。
 *
 * 聚合视角：只有走势与结构，没有单条明细 —— 观察者角色看的是「整体在变好还是变差」，
 * 定位具体某次调用是审计与运维的事。
 */
const { t } = useI18n()
const { skin } = useChartSkin()
const { preset, presets, loading, failed, stats, load } = useStatsRange('30d')

const trendChartRef = ref<HTMLDivElement | null>(null)
const hourChartRef = ref<HTMLDivElement | null>(null)
let trendChart: echarts.ECharts | null = null
let hourChart: echarts.ECharts | null = null

const hasData = computed(() => (stats.value?.totalRequests ?? 0) > 0)

const dayCount = computed(() => {
  const rows = stats.value?.byDay ?? []
  return Math.max(rows.length, 1)
})

const metrics = computed<MetricStripItem[]>(() => {
  const data = stats.value
  const total = data?.totalRequests ?? 0
  return [
    { key: 'requests', label: t('dashboardHome.trends.metricRequests'), value: formatCount(total) },
    {
      key: 'success',
      label: t('dashboardHome.trends.metricSuccessRate'),
      value: formatPercent(data?.successRate),
      tone: (data?.successRate ?? 100) < 95 ? 'warning' : 'success',
    },
    {
      key: 'dailyAvg',
      label: t('dashboardHome.trends.metricDailyAvg'),
      value: formatCount(Math.round(total / dayCount.value)),
    },
    { key: 'latency', label: t('dashboardHome.trends.metricLatency'), value: formatMs(data?.avgResponseTimeMs) },
  ]
})

const trendOption = (): echarts.EChartsOption => {
  const rows = stats.value?.byDay ?? []
  return {
    grid: skin.value.grid,
    tooltip: { ...skin.value.tooltip, trigger: 'axis' },
    legend: { ...skin.value.legend, top: 0 },
    xAxis: { ...skin.value.categoryAxis, type: 'category', data: rows.map((row) => row.date) },
    yAxis: [
      { ...skin.value.valueAxis, type: 'value' },
      { ...skin.value.valueAxis, type: 'value' },
    ],
    series: [
      {
        type: 'bar',
        name: t('dashboardHome.trends.seriesRequests'),
        data: rows.map((row) => row.requestCount),
        barMaxWidth: 22,
        itemStyle: { color: skin.value.palette[1], borderRadius: [3, 3, 0, 0] },
      },
      {
        type: 'line',
        name: t('dashboardHome.trends.seriesTokens'),
        yAxisIndex: 1,
        data: rows.map((row) => row.totalTokens ?? 0),
        smooth: true,
        symbolSize: 5,
        lineStyle: { color: skin.value.palette[0], width: 2 },
        itemStyle: { color: skin.value.palette[0] },
      },
    ],
  }
}

const hourOption = (): echarts.EChartsOption => {
  const rows = stats.value?.byHour ?? []
  const filled = Array.from({ length: 24 }, (_, hour) => {
    const hit = rows.find((row) => row.hour === hour)
    return hit?.requestCount ?? 0
  })
  return {
    grid: skin.value.grid,
    tooltip: { ...skin.value.tooltip, trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: {
      ...skin.value.categoryAxis,
      type: 'category',
      data: Array.from({ length: 24 }, (_, hour) => `${String(hour).padStart(2, '0')}:00`),
    },
    yAxis: { ...skin.value.valueAxis, type: 'value' },
    series: [
      {
        type: 'bar',
        name: t('dashboardHome.trends.seriesRequests'),
        data: filled,
        barMaxWidth: 18,
        itemStyle: { color: skin.value.palette[0], borderRadius: [2, 2, 0, 0] },
      },
    ],
  }
}

const render = (): void => {
  trendChart?.setOption(trendOption(), { notMerge: true })
  hourChart?.setOption(hourOption(), { notMerge: true })
}

const reload = async (): Promise<void> => {
  await load()
}

const handleResize = (): void => {
  trendChart?.resize()
  hourChart?.resize()
}

onMounted(async () => {
  if (trendChartRef.value) trendChart = echarts.init(trendChartRef.value)
  if (hourChartRef.value) hourChart = echarts.init(hourChartRef.value)
  window.addEventListener('resize', handleResize)
  await reload()
  render()
})

watch(
  [skin, () => t('dashboardHome.trends.seriesRequests'), () => t('dashboardHome.trends.seriesTokens')],
  render
)
watch(stats, render)

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  hourChart?.dispose()
  trendChart = null
  hourChart = null
})
</script>

<style scoped>
.chart {
  width: 100%;
  height: 100%;
}

.second-chart {
  margin-top: 16px;
}

.load-failed {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 6px;
  border: 1px solid var(--ja-border);
  background: var(--ja-surface-2);
  color: var(--ja-ink-2);
  font-size: 13px;
}
</style>
