<template>
  <div class="rate-limiter-monitoring">
    <!-- 概览统计卡片 -->
    <el-row :gutter="16">
      <el-col :span="4">
        <StatCard :icon="DataBoard" :label="t('rateLimiter.monitoring.totalLimiters')" :value="summary.totalLimiters" tone="primary" />
      </el-col>
      <el-col :span="4">
        <StatCard :icon="Grid" :label="t('rateLimiter.monitoring.globalLimiters')" :value="summary.globalLimiters" tone="success" />
      </el-col>
      <el-col :span="4">
        <StatCard :icon="Service" :label="t('rateLimiter.monitoring.serviceLimiters')" :value="summary.serviceLimiters" tone="warning" />
      </el-col>
      <el-col :span="4">
        <StatCard :icon="Monitor" :label="t('rateLimiter.monitoring.instanceLimiters')" :value="summary.instanceLimiters" tone="info" />
      </el-col>
      <el-col :span="4">
        <StatCard :icon="TrendCharts" :label="t('rateLimiter.monitoring.averageUsage')" :value="summary.averageUsageRatio" unit="%" tone="primary" />
      </el-col>
      <el-col :span="4">
        <StatCard
          :icon="summary.highUsageLimiters > 0 ? WarningFilled : CircleCheckFilled"
          :label="t('rateLimiter.monitoring.highUsage')"
          :value="summary.highUsageLimiters"
          :tone="summary.highUsageLimiters > 0 ? 'danger' : 'success'"
        />
      </el-col>
    </el-row>

    <!-- 限流器详细指标 -->
    <el-card class="metrics-card" style="margin-top: 16px" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('rateLimiter.monitoring.metricsTitle') }}</span>
          <div class="control-buttons">
            <el-select v-model="filterScope" :placeholder="t('rateLimiter.monitoring.scopePlaceholder')" clearable style="width: 120px; margin-right: 8px">
              <el-option :label="t('rateLimiter.monitoring.scopeOptions.global')" value="global" />
              <el-option :label="t('rateLimiter.monitoring.scopeOptions.service')" value="service" />
              <el-option :label="t('rateLimiter.monitoring.scopeOptions.instance')" value="instance" />
            </el-select>
            <el-select v-model="filterAlgorithm" :placeholder="t('rateLimiter.monitoring.algorithmPlaceholder')" clearable style="width: 150px; margin-right: 8px">
              <el-option label="Token Bucket" value="TOKEN_BUCKET" />
              <el-option label="Leaky Bucket" value="LEAKY_BUCKET" />
              <el-option label="Sliding Window" value="SLIDING_WINDOW" />
              <el-option label="Warm Up" value="WARM_UP" />
            </el-select>
            <el-button type="primary" size="small" @click="loadMetrics" :loading="loading">
              {{ t('rateLimiter.monitoring.refresh') }}
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        :data="filteredMetrics"
        stripe
        v-loading="loading"
        class="metrics-table"
        :default-sort="{ prop: 'usageRatio', order: 'descending' }"
      >
        <el-table-column prop="service" :label="t('rateLimiter.monitoring.columns.service')" min-width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.service || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scope" :label="t('rateLimiter.monitoring.columns.scope')" width="90">
          <template #default="{ row }">
            <el-tag :type="getScopeTagType(row.scope)" size="small">
              {{ getScopeLabel(row.scope) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="identifier" :label="t('rateLimiter.monitoring.columns.identifier')" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip :content="row.identifier" placement="top">
              <span class="identifier-text">{{ getShortIdentifier(row.identifier) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="algorithm" :label="t('rateLimiter.monitoring.columns.algorithm')" width="130">
          <template #default="{ row }">
            <el-tag :type="getAlgorithmTagType(row.algorithm)" size="small" effect="plain">
              {{ getAlgorithmLabel(row.algorithm) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="capacity" :label="t('rateLimiter.monitoring.columns.capacity')" width="90" align="right">
          <template #default="{ row }">
            <span class="metric-value">{{ formatNumber(row.capacity) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="rate" :label="t('rateLimiter.monitoring.columns.rate')" width="90" align="right">
          <template #default="{ row }">
            <span class="metric-value">{{ formatNumber(row.rate) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remainingCapacity" :label="t('rateLimiter.monitoring.columns.remainingCapacity')" width="110" align="right">
          <template #default="{ row }">
            <span :class="['metric-value', { 'text-warning': row.remainingCapacity >= 0 && row.remainingCapacity < row.capacity * 0.2 }]">
              {{ row.remainingCapacity >= 0 ? formatNumber(row.remainingCapacity) : 'N/A' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="usageRatio" :label="t('rateLimiter.monitoring.columns.usage')" width="180" sortable>
          <template #default="{ row }">
            <div class="usage-cell">
              <el-progress
                :percentage="row.usageRatio"
                :color="getUsageColor(row.usageRatio)"
                :stroke-width="10"
              />
              <span class="usage-text">{{ row.usageRatio }}%</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Prometheus 指标信息 -->
    <el-card class="prometheus-card" style="margin-top: 16px" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('rateLimiter.monitoring.prometheusTitle') }}</span>
          <el-tag type="success" size="small">{{ t('rateLimiter.monitoring.collectionInterval', { interval: prometheusInfo.collectionInterval }) }}</el-tag>
        </div>
      </template>

      <el-descriptions :column="1" border>
        <el-descriptions-item :label="t('rateLimiter.monitoring.prometheus.metricsCount')">
          {{ prometheusInfo.metricsCount }}
        </el-descriptions-item>
        <el-descriptions-item :label="t('rateLimiter.monitoring.prometheus.availableMetrics')">
          <div class="metric-list">
            <el-tag
              v-for="metric in prometheusInfo.availableMetrics"
              :key="metric"
              type="info"
              size="small"
              class="metric-tag"
            >
              {{ metric }}
            </el-tag>
          </div>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  DataBoard,
  Grid,
  Monitor,
  TrendCharts,
  WarningFilled,
  CircleCheckFilled,
  Service
} from '@element-plus/icons-vue'
import request from '@/utils/request'
import StatCard from '@/components/StatCard.vue'
import { useChartTheme } from '@/composables/useChartTheme'

const { getChartTheme } = useChartTheme()
const { t } = useI18n()

interface RateLimiterMetrics {
  service: string
  scope: string
  identifier: string
  algorithm: string
  remainingCapacity: number
  usageRatio: number
  capacity: number
  rate: number
}

interface RateLimiterSummary {
  totalLimiters: number
  globalLimiters: number
  serviceLimiters: number
  instanceLimiters: number
  averageUsageRatio: number
  highUsageLimiters: number
}

interface PrometheusInfo {
  metricsCount: number
  availableMetrics: string[]
  collectionInterval: string
}

const metrics = ref<RateLimiterMetrics[]>([])
const summary = ref<RateLimiterSummary>({
  totalLimiters: 0,
  globalLimiters: 0,
  serviceLimiters: 0,
  instanceLimiters: 0,
  averageUsageRatio: 0,
  highUsageLimiters: 0
})
const prometheusInfo = ref<PrometheusInfo>({
  metricsCount: 0,
  availableMetrics: [],
  collectionInterval: '10s'
})

const loading = ref(false)
const filterScope = ref<string>('')
const filterAlgorithm = ref<string>('')
let refreshTimer: number | null = null

const filteredMetrics = computed(() => {
  let result = metrics.value
  if (filterScope.value) {
    result = result.filter(m => m.scope === filterScope.value)
  }
  if (filterAlgorithm.value) {
    result = result.filter(m => m.algorithm === filterAlgorithm.value)
  }
  return result
})

const loadMetrics = async () => {
  loading.value = true
  try {
    const [metricsRes, summaryRes, prometheusRes] = await Promise.all([
      request.get('/rate-limiter/metrics'),
      request.get('/rate-limiter/summary'),
      request.get('/rate-limiter/prometheus-info')
    ])

    metrics.value = metricsRes.data || []
    summary.value = summaryRes.data || summary.value
    prometheusInfo.value = prometheusRes.data || prometheusInfo.value
  } catch (error) {
    console.error('Failed to load rate limiter metrics:', error)
    ElMessage.error(t('rateLimiter.monitoring.messages.loadFailed'))
  } finally {
    loading.value = false
  }
}

const getScopeTagType = (scope: string) => {
  switch (scope) {
    case 'global':
      return 'primary'
    case 'service':
      return 'success'
    case 'instance':
      return 'warning'
    default:
      return 'info'
  }
}

const getScopeLabel = (scope: string) => {
  switch (scope) {
    case 'global':
      return t('rateLimiter.monitoring.scopeOptions.global')
    case 'service':
      return t('rateLimiter.monitoring.scopeOptions.service')
    case 'instance':
      return t('rateLimiter.monitoring.scopeOptions.instance')
    default:
      return scope
  }
}

const getAlgorithmTagType = (algorithm: string) => {
  switch (algorithm) {
    case 'TOKEN_BUCKET':
      return 'primary'
    case 'LEAKY_BUCKET':
      return 'success'
    case 'SLIDING_WINDOW':
      return 'warning'
    case 'WARM_UP':
      return 'danger'
    default:
      return 'info'
  }
}

const getAlgorithmLabel = (algorithm: string) => {
  switch (algorithm) {
    case 'TOKEN_BUCKET':
      return 'Token Bucket'
    case 'LEAKY_BUCKET':
      return 'Leaky Bucket'
    case 'SLIDING_WINDOW':
      return 'Sliding Window'
    case 'WARM_UP':
      return 'Warm Up'
    default:
      return algorithm
  }
}

const getShortIdentifier = (identifier: string) => {
  if (!identifier) return '-'
  if (identifier.length <= 30) return identifier
  return `${identifier.substring(0, 14)  }...${  identifier.substring(identifier.length - 13)}`
}

const formatNumber = (num: number) => {
  if (num < 0) return 'N/A'
  if (num >= 1000000) {
    return `${(num / 1000000).toFixed(1)  }M`
  }
  if (num >= 1000) {
    return `${(num / 1000).toFixed(1)  }K`
  }
  return num.toString()
}

const getUsageColor = (percentage: number) => {
  const theme = getChartTheme()
  if (percentage >= 90) return theme.danger
  if (percentage >= 80) return theme.warning
  if (percentage >= 50) return theme.primary
  return theme.success
}

onMounted(() => {
  loadMetrics()
  // 每 30 秒自动刷新
  refreshTimer = window.setInterval(loadMetrics, 30000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.rate-limiter-monitoring {
  padding: 24px;
  background: var(--ja-main-bg-gradient);
  min-height: calc(100vh - 80px);
}

.metrics-card,
.prometheus-card {
  box-shadow: var(--ja-shadow);
  border-radius: var(--ja-radius-lg);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ja-text-primary);
}

.control-buttons {
  display: flex;
  align-items: center;
}

.metrics-table {
  width: 100%;
}

.identifier-text {
  font-family: monospace;
  font-size: 12px;
  color: var(--ja-text-regular);
}

.metric-value {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 13px;
  color: var(--ja-text-primary);
}

.text-warning {
  color: var(--ja-warning);
}

.usage-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.usage-cell .el-progress {
  flex: 1;
}

.usage-text {
  font-size: 12px;
  font-weight: 500;
  color: var(--ja-text-regular);
  min-width: 45px;
  text-align: right;
}

.metric-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.metric-tag {
  font-family: monospace;
  font-size: 11px;
}
</style>
