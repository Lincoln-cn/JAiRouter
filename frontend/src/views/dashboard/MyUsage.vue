<template>
  <PageSkeleton
    :title="t('dashboardHome.mine.title')"
    :subtitle="t('dashboardHome.mine.subtitle')"
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
      :title="t('dashboardHome.mine.chartDaily')"
      :loading="loading"
      :empty="!hasData"
      :empty-title="t('dashboardHome.chartEmpty')"
      :empty-description="t('dashboardHome.mine.emptyHint')"
    >
      <div ref="dailyChartRef" class="chart" />
    </ChartCard>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">{{ t('dashboardHome.mine.modelTitle') }}</span>
          <span class="card-hint">{{ t('dashboardHome.mine.modelHint') }}</span>
        </div>
      </template>
      <el-table v-if="modelRows.length > 0" :data="modelRows" size="small">
        <el-table-column prop="modelName" :label="t('dashboardHome.mine.modelName')" min-width="220" />
        <el-table-column :label="t('dashboardHome.mine.modelRequests')" width="120" align="right">
          <template #default="{ row }">{{ formatCount(row.requestCount) }}</template>
        </el-table-column>
        <el-table-column :label="t('dashboardHome.mine.modelTokens')" width="140" align="right">
          <template #default="{ row }">{{ formatCount(row.totalTokens) }}</template>
        </el-table-column>
        <el-table-column :label="t('dashboardHome.mine.modelLatency')" width="140" align="right">
          <template #default="{ row }">{{ formatMs(row.avgResponseTimeMs) }}</template>
        </el-table-column>
      </el-table>
      <EmptyState
        v-else
        :title="t('dashboardHome.chartEmpty')"
        :description="t('dashboardHome.mine.emptyHint')"
      />
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
 * 我的用量（USER 的落地页）。
 *
 * 数据范围由服务端按「本人」过滤（持 `callhistory:self:read` 时只看自己的会话与本人创建的密钥），
 * 前端不传 userId —— 传了也不会生效，服务端只信认证主体。
 */
const { t } = useI18n()
const { skin } = useChartSkin()
const { preset, presets, loading, failed, stats, load } = useStatsRange('30d')

const dailyChartRef = ref<HTMLDivElement | null>(null)
let dailyChart: echarts.ECharts | null = null

const hasData = computed(() => (stats.value?.totalRequests ?? 0) > 0)

const metrics = computed<MetricStripItem[]>(() => {
  const data = stats.value
  return [
    { key: 'requests', label: t('dashboardHome.mine.metricRequests'), value: formatCount(data?.totalRequests) },
    { key: 'tokens', label: t('dashboardHome.mine.metricTokens'), value: formatCount(data?.totalTokens) },
    {
      key: 'success',
      label: t('dashboardHome.mine.metricSuccessRate'),
      value: formatPercent(data?.successRate),
      tone: (data?.successRate ?? 100) < 95 ? 'warning' : 'success',
    },
    { key: 'latency', label: t('dashboardHome.mine.metricLatency'), value: formatMs(data?.avgResponseTimeMs) },
  ]
})

const modelRows = computed(() =>
  [...(stats.value?.byModel ?? [])].sort((a, b) => b.requestCount - a.requestCount).slice(0, 12)
)

const dailyOption = (): echarts.EChartsOption => {
  const rows = stats.value?.byDay ?? []
  return {
    grid: skin.value.grid,
    tooltip: { ...skin.value.tooltip, trigger: 'axis' },
    xAxis: { ...skin.value.categoryAxis, type: 'category', data: rows.map((row) => row.date) },
    yAxis: { ...skin.value.valueAxis, type: 'value' },
    series: [
      {
        type: 'line',
        name: t('dashboardHome.mine.metricRequests'),
        data: rows.map((row) => row.requestCount),
        smooth: true,
        symbolSize: 6,
        lineStyle: { color: skin.value.palette[0], width: 2 },
        itemStyle: { color: skin.value.palette[0] },
        areaStyle: { color: skin.value.palette[3], opacity: 0.35 },
      },
    ],
  }
}

const render = (): void => {
  dailyChart?.setOption(dailyOption(), { notMerge: true })
}

const reload = async (): Promise<void> => {
  await load()
}

const handleResize = (): void => {
  dailyChart?.resize()
}

onMounted(async () => {
  if (dailyChartRef.value) dailyChart = echarts.init(dailyChartRef.value)
  window.addEventListener('resize', handleResize)
  await reload()
  render()
})

watch([skin, () => t('dashboardHome.mine.metricRequests')], render)
watch(stats, render)

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  dailyChart?.dispose()
  dailyChart = null
})
</script>

<style scoped>
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
