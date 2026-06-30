<template>
  <div class="rate-limiter-monitoring">
    <!-- 概览统计卡片 -->
    <el-row :gutter="16">
      <el-col :span="4">
        <el-card shadow="hover" class="summary-card summary-total">
          <div class="summary-content">
            <div class="summary-icon">
              <el-icon :size="32"><DataBoard /></el-icon>
            </div>
            <div class="summary-info">
              <div class="summary-value">{{ summary.totalLimiters }}</div>
              <div class="summary-label">限流器总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="summary-card summary-global">
          <div class="summary-content">
            <div class="summary-icon">
              <el-icon :size="32"><Grid /></el-icon>
            </div>
            <div class="summary-info">
              <div class="summary-value">{{ summary.globalLimiters }}</div>
              <div class="summary-label">全局限流</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="summary-card summary-service">
          <div class="summary-content">
            <div class="summary-icon">
              <el-icon :size="32"><Service /></el-icon>
            </div>
            <div class="summary-info">
              <div class="summary-value">{{ summary.serviceLimiters }}</div>
              <div class="summary-label">服务限流</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="summary-card summary-instance">
          <div class="summary-content">
            <div class="summary-icon">
              <el-icon :size="32"><Monitor /></el-icon>
            </div>
            <div class="summary-info">
              <div class="summary-value">{{ summary.instanceLimiters }}</div>
              <div class="summary-label">实例限流</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="summary-card summary-usage">
          <div class="summary-content">
            <div class="summary-icon">
              <el-icon :size="32"><TrendCharts /></el-icon>
            </div>
            <div class="summary-info">
              <div class="summary-value">{{ summary.averageUsageRatio }}%</div>
              <div class="summary-label">平均使用率</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="summary-card" :class="summary.highUsageLimiters > 0 ? 'summary-warning' : 'summary-ok'">
          <div class="summary-content">
            <div class="summary-icon">
              <el-icon :size="32"><WarningFilled v-if="summary.highUsageLimiters > 0" /><CircleCheckFilled v-else /></el-icon>
            </div>
            <div class="summary-info">
              <div class="summary-value">{{ summary.highUsageLimiters }}</div>
              <div class="summary-label">高使用率(>80%)</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 限流器详细指标 -->
    <el-card class="metrics-card" style="margin-top: 16px" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">限流器详细指标</span>
          <div class="control-buttons">
            <el-select v-model="filterScope" placeholder="作用域" clearable style="width: 120px; margin-right: 8px">
              <el-option label="全局" value="global" />
              <el-option label="服务" value="service" />
              <el-option label="实例" value="instance" />
            </el-select>
            <el-select v-model="filterAlgorithm" placeholder="算法" clearable style="width: 150px; margin-right: 8px">
              <el-option label="Token Bucket" value="TOKEN_BUCKET" />
              <el-option label="Leaky Bucket" value="LEAKY_BUCKET" />
              <el-option label="Sliding Window" value="SLIDING_WINDOW" />
              <el-option label="Warm Up" value="WARM_UP" />
            </el-select>
            <el-button type="primary" size="small" @click="loadMetrics" :loading="loading">
              刷新
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
        <el-table-column prop="service" label="服务" min-width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.service || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scope" label="作用域" width="90">
          <template #default="{ row }">
            <el-tag :type="getScopeTagType(row.scope)" size="small">
              {{ getScopeLabel(row.scope) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="identifier" label="标识符" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tooltip :content="row.identifier" placement="top">
              <span class="identifier-text">{{ getShortIdentifier(row.identifier) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="algorithm" label="算法" width="130">
          <template #default="{ row }">
            <el-tag :type="getAlgorithmTagType(row.algorithm)" size="small" effect="plain">
              {{ getAlgorithmLabel(row.algorithm) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="capacity" label="容量" width="90" align="right">
          <template #default="{ row }">
            <span class="metric-value">{{ formatNumber(row.capacity) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="rate" label="速率(/s)" width="90" align="right">
          <template #default="{ row }">
            <span class="metric-value">{{ formatNumber(row.rate) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="remainingCapacity" label="剩余容量" width="110" align="right">
          <template #default="{ row }">
            <span :class="['metric-value', { 'text-warning': row.remainingCapacity >= 0 && row.remainingCapacity < row.capacity * 0.2 }]">
              {{ row.remainingCapacity >= 0 ? formatNumber(row.remainingCapacity) : 'N/A' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="usageRatio" label="使用率" width="180" sortable>
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
          <span class="card-title">Prometheus 指标</span>
          <el-tag type="success" size="small">采集间隔: {{ prometheusInfo.collectionInterval }}</el-tag>
        </div>
      </template>

      <el-descriptions :column="1" border>
        <el-descriptions-item label="指标数量">
          {{ prometheusInfo.metricsCount }}
        </el-descriptions-item>
        <el-descriptions-item label="可用指标">
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
    ElMessage.error('加载限流器指标失败')
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
      return '全局'
    case 'service':
      return '服务'
    case 'instance':
      return '实例'
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
  if (percentage >= 90) return '#F56C6C'
  if (percentage >= 80) return '#E6A23C'
  if (percentage >= 50) return '#409EFF'
  return '#67C23A'
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
  background: linear-gradient(180deg, #f7f9fc 0%, #ffffff 100%);
  min-height: calc(100vh - 80px);
}

.summary-card {
  border-radius: 12px;
  transition: transform 0.2s, box-shadow 0.2s;
}

.summary-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.summary-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.summary-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(64, 158, 255, 0.1);
  color: #409EFF;
}

.summary-total .summary-icon {
  background: rgba(64, 158, 255, 0.1);
  color: #409EFF;
}

.summary-global .summary-icon {
  background: rgba(103, 194, 58, 0.1);
  color: #67C23A;
}

.summary-service .summary-icon {
  background: rgba(230, 162, 60, 0.1);
  color: #E6A23C;
}

.summary-instance .summary-icon {
  background: rgba(144, 147, 153, 0.1);
  color: #909399;
}

.summary-usage .summary-icon {
  background: rgba(64, 158, 255, 0.1);
  color: #409EFF;
}

.summary-warning .summary-icon {
  background: rgba(245, 108, 108, 0.1);
  color: #F56C6C;
}

.summary-ok .summary-icon {
  background: rgba(103, 194, 58, 0.1);
  color: #67C23A;
}

.summary-info {
  flex: 1;
}

.summary-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
  line-height: 1.2;
}

.summary-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.metrics-card,
.prometheus-card {
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.06);
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
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
  color: #606266;
}

.metric-value {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 13px;
  color: #303133;
}

.text-warning {
  color: #E6A23C;
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
  color: #606266;
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
