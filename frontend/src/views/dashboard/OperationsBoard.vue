<template>
  <PageSkeleton
    :title="t('dashboardHome.ops.title')"
    :subtitle="t('dashboardHome.ops.subtitle')"
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

    <div class="chart-grid">
      <ChartCard
        :title="t('dashboardHome.ops.chartService')"
        :loading="loading"
        :empty="!hasData"
        :empty-title="t('dashboardHome.chartEmpty')"
      >
        <div ref="serviceChartRef" class="chart" />
      </ChartCard>

      <ChartCard
        :title="t('dashboardHome.ops.chartStatus')"
        :loading="loading"
        :empty="!hasData"
        :empty-title="t('dashboardHome.chartEmpty')"
      >
        <div ref="statusChartRef" class="chart" />
      </ChartCard>
    </div>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">{{ t('dashboardHome.ops.errorTitle') }}</span>
          <span class="card-hint">{{ t('dashboardHome.ops.errorHint') }}</span>
        </div>
      </template>
      <el-table v-if="errorRows.length > 0" :data="errorRows" size="small">
        <el-table-column prop="errorCode" :label="t('dashboardHome.ops.errorCode')" min-width="200" />
        <el-table-column prop="count" :label="t('dashboardHome.ops.errorCount')" width="120" align="right" />
      </el-table>
      <EmptyState v-else :title="t('dashboardHome.ops.noErrors')" />
    </el-card>
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
import EmptyState from '@/components/EmptyState.vue'
import { useChartSkin } from '@/composables/useChartSkin'
import { formatCount, formatMs, formatPercent, useStatsRange } from '@/composables/useStatsRange'

/**
 * 运营看板（OPERATOR 的落地页）。
 *
 * 只看运行结果：流量、成功率、失败来源。配置入口不在这里 —— 运维改配置要去
 * 「流量治理 / 模型与实例」，本页只回答「现在跑得怎么样」。
 */
const { t } = useI18n()
const { skin } = useChartSkin()
const { preset, presets, loading, failed, stats, load } = useStatsRange('7d')

const serviceChartRef = ref<HTMLDivElement | null>(null)
const statusChartRef = ref<HTMLDivElement | null>(null)
let serviceChart: echarts.ECharts | null = null
let statusChart: echarts.ECharts | null = null

const hasData = computed(() => (stats.value?.totalRequests ?? 0) > 0)

const metrics = computed<MetricStripItem[]>(() => {
  const data = stats.value
  const failedCount = data?.failedRequests ?? 0
  return [
    { key: 'requests', label: t('dashboardHome.ops.metricRequests'), value: formatCount(data?.totalRequests) },
    {
      key: 'success',
      label: t('dashboardHome.ops.metricSuccessRate'),
      value: formatPercent(data?.successRate),
      tone: (data?.successRate ?? 100) < 95 ? 'warning' : 'success',
    },
    {
      key: 'failed',
      label: t('dashboardHome.ops.metricFailed'),
      value: formatCount(failedCount),
      tone: failedCount > 0 ? 'danger' : 'default',
    },
    { key: 'latency', label: t('dashboardHome.ops.metricLatency'), value: formatMs(data?.avgResponseTimeMs) },
  ]
})

const errorRows = computed(() =>
  (stats.value?.byErrorCode ?? []).filter((row) => (row.count ?? 0) > 0).slice(0, 10)
)

const serviceOption = (): echarts.EChartsOption => {
  const rows = [...(stats.value?.byServiceType ?? [])].sort((a, b) => b.requestCount - a.requestCount).slice(0, 8)
  return {
    grid: skin.value.grid,
    tooltip: { ...skin.value.tooltip, trigger: 'axis', axisPointer: { type: 'shadow' } },
    xAxis: { ...skin.value.valueAxis, type: 'value' },
    yAxis: { ...skin.value.categoryAxis, type: 'category', data: rows.map((row) => row.serviceType) },
    series: [
      {
        type: 'bar',
        name: t('dashboardHome.ops.metricRequests'),
        data: rows.map((row) => row.requestCount),
        barMaxWidth: 18,
        itemStyle: { color: skin.value.palette[0], borderRadius: [0, 3, 3, 0] },
      },
    ],
  }
}

const statusOption = (): echarts.EChartsOption => {
  const rows = (stats.value?.byStatusCode ?? []).filter((row) => (row.count ?? 0) > 0)
  const failed = new Set(['400', '401', '403', '404', '429', '500', '502', '503', '504'])
  return {
    tooltip: skin.value.tooltip,
    legend: { ...skin.value.legend, bottom: 0 },
    series: [
      {
        type: 'pie',
        radius: ['46%', '68%'],
        avoidLabelOverlap: true,
        label: { color: skin.value.text, formatter: '{b} {d}%' },
        data: rows.map((row) => ({
          name: String(row.statusCode),
          value: row.count,
          itemStyle: { color: failed.has(String(row.statusCode)) ? skin.value.emphasis : skin.value.palette[0] },
        })),
      },
    ],
  }
}

const render = (): void => {
  serviceChart?.setOption(serviceOption(), { notMerge: true })
  statusChart?.setOption(statusOption(), { notMerge: true })
}

const reload = async (): Promise<void> => {
  await load()
}

onMounted(async () => {
  if (serviceChartRef.value) serviceChart = echarts.init(serviceChartRef.value)
  if (statusChartRef.value) statusChart = echarts.init(statusChartRef.value)
  window.addEventListener('resize', handleResize)
  await reload()
  render()
})

const handleResize = (): void => {
  serviceChart?.resize()
  statusChart?.resize()
}

// 主题或语言变化后按新皮肤重绘（不含网络请求）
watch([skin, () => t('dashboardHome.ops.chartService')], render)

// 数据到位后重绘
watch(stats, render, { deep: false })

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  serviceChart?.dispose()
  statusChart?.dispose()
  serviceChart = null
  statusChart = null
})
</script>

<style scoped>
.chart-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(380px, 1fr));
  gap: 16px;
}

.chart {
  width: 100%;
  height: 100%;
}

.table-card {
  margin-top: 16px;
}

.card-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ja-ink);
}

.card-hint {
  font-size: 12px;
  color: var(--ja-ink-3);
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
