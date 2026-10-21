<template>
  <PageSkeleton :title="t('dashboard.pageTitle')">
    <template #actions>
      <el-button size="small" type="primary" @click="fetchDashboardData" :loading="configLoading" plain>
        <el-icon><Refresh /></el-icon> {{ t('dashboard.refreshAll') }}
      </el-button>
    </template>

    <template #stats>
      <!-- 概览指标：一条中性 MetricStrip，只有「异常实例」带语义色 -->
      <div class="stats-wrap">
        <MetricStrip :items="metricItems" :columns="6" />
      </div>
    </template>

    <!-- 主体图表与监控详情 -->
    <el-row class="main-row" :gutter="20">
      <el-col :xs="24" :lg="16">
        <ChartCard
          :title="t('dashboard.systemOverview')"
          :empty="!dashboardMetrics"
          :empty-title="t('common.chartEmpty')"
          :empty-description="t('common.chartEmptyHint')"
          :height="320"
        >
          <!-- 默认槽常驻：ChartCard 的 empty/loading 是覆盖层，echarts 容器不能因 v-if 消失 -->
          <div ref="systemChart" class="chart-area" />
        </ChartCard>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card class="card-panel metrics-panel" shadow="never">
          <template #header>
            <div class="card-title">{{ t('dashboard.systemMetrics') }}</div>
          </template>

          <SystemMetricsPanel :metrics="dashboardMetrics" />
        </el-card>
      </el-col>
    </el-row>

    <!-- ════════════ 治理链路面板 ════════════ -->
    <el-row class="governance-row" :gutter="20">
      <el-col :xs="24">
        <el-card class="governance-card" shadow="never">
          <template #header>
            <div class="governance-header">
              <span class="card-title">{{ t('dashboard.governanceChain') }}</span>
            </div>
          </template>

          <el-row :gutter="16">
            <!-- ① 规则命中 -->
            <el-col :xs="24" :sm="12" :lg="6">
              <div class="gov-section">
                <div class="gov-section-head">
                  <el-icon class="gov-icon"><SetUp /></el-icon>
                  <span class="gov-label">{{ t('dashboard.ruleHits') }}</span>
                  <el-button link type="primary" size="small" class="gov-jump" @click="router.push('/config/rules')">
                    {{ t('dashboard.jumpViewRules') }} <el-icon><ArrowRight /></el-icon>
                  </el-button>
                </div>
                <div class="gov-metrics">
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">{{ t('dashboard.enabledRules') }}</span>
                    <span class="gov-metric-value">{{ ruleStats.enabledCount }}</span>
                  </div>
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">{{ t('dashboard.totalHitsCount') }}</span>
                    <span class="gov-metric-value">{{ ruleStats.totalHits }}</span>
                  </div>
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">{{ t('dashboard.triggeredRuleCount') }}</span>
                    <span class="gov-metric-value">{{ ruleStats.triggeredRuleCount }}</span>
                  </div>
                </div>
              </div>
            </el-col>

            <!-- ② 限流 -->
            <el-col :xs="24" :sm="12" :lg="6">
              <div class="gov-section">
                <div class="gov-section-head">
                  <el-icon class="gov-icon"><DataBoard /></el-icon>
                  <span class="gov-label">{{ t('dashboard.rateLimit') }}</span>
                  <el-button link type="primary" size="small" class="gov-jump" @click="router.push('/rate-limiters/monitoring')">
                    {{ t('dashboard.jumpMonitorRateLimit') }} <el-icon><ArrowRight /></el-icon>
                  </el-button>
                </div>
                <div class="gov-metrics">
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">{{ t('dashboard.avgUsageRatio') }}</span>
                    <span class="gov-metric-value">{{ rlSummary.averageUsageRatio }}%</span>
                  </div>
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">{{ t('dashboard.highUsageRatio') }}</span>
                    <span :class="['gov-metric-value', rlSummary.highUsageLimiters > 0 ? 'gov-metric-value--danger' : '']">
                      {{ rlSummary.highUsageLimiters }}
                    </span>
                  </div>
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">{{ t('dashboard.serviceInstanceLimiters') }}</span>
                    <span class="gov-metric-value">{{ rlSummary.serviceLimiters }} / {{ rlSummary.instanceLimiters }}</span>
                  </div>
                </div>
              </div>
            </el-col>

            <!-- ③ 熔断 -->
            <el-col :xs="24" :sm="12" :lg="6">
              <div class="gov-section">
                <div class="gov-section-head">
                  <el-icon class="gov-icon"><Warning /></el-icon>
                  <span class="gov-label">{{ t('dashboard.circuitBreaker') }}</span>
                  <el-button link type="primary" size="small" class="gov-jump" @click="router.push('/circuit-breakers/monitoring')">
                    {{ t('dashboard.jumpMonitorCircuitBreaker') }} <el-icon><ArrowRight /></el-icon>
                  </el-button>
                </div>
                <div class="gov-metrics">
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">CLOSED</span>
                    <span class="gov-metric-value">{{ cbStateCounts.CLOSED }}</span>
                  </div>
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">OPEN</span>
                    <span :class="['gov-metric-value', cbStateCounts.OPEN > 0 ? 'gov-metric-value--danger' : '']">
                      {{ cbStateCounts.OPEN }}
                    </span>
                  </div>
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">HALF_OPEN</span>
                    <span :class="['gov-metric-value', cbStateCounts.HALF_OPEN > 0 ? 'gov-metric-value--warning' : '']">
                      {{ cbStateCounts.HALF_OPEN }}
                    </span>
                  </div>
                </div>
              </div>
            </el-col>

            <!-- ④ 负载均衡 -->
            <el-col :xs="24" :sm="12" :lg="6">
              <div class="gov-section">
                <div class="gov-section-head">
                  <el-icon class="gov-icon"><Connection /></el-icon>
                  <span class="gov-label">{{ t('dashboard.loadBalancing') }}</span>
                  <el-button link type="primary" size="small" class="gov-jump" @click="router.push('/load-balancers/monitoring')">
                    {{ t('dashboard.jumpMonitorLoadBalancer') }} <el-icon><ArrowRight /></el-icon>
                  </el-button>
                </div>
                <div class="gov-metrics">
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">{{ t('dashboard.totalInstanceCount') }}</span>
                    <span class="gov-metric-value">{{ lbSummary.totalInstances }}</span>
                  </div>
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">{{ t('dashboard.healthyInstanceCount') }}</span>
                    <span class="gov-metric-value">{{ lbSummary.healthyInstances }}</span>
                  </div>
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">{{ t('dashboard.serviceCount') }}</span>
                    <span class="gov-metric-value">{{ lbSummary.serviceCount }}</span>
                  </div>
                </div>
              </div>
            </el-col>
          </el-row>

          <!-- ════════════ 能力入口卡（纯导航） ════════════ -->
          <el-row :gutter="16" class="gov-entry-row">
            <el-col :xs="24" :sm="12" :lg="6">
              <router-link class="gov-entry-card" :to="{ name: 'response-cache-management' }">
                <div class="gov-entry-inner">
                  <el-icon class="gov-icon"><Coin /></el-icon>
                  <span class="gov-entry-label">{{ t('dashboard.governance.responseCache') }}</span>
                  <span class="gov-entry-desc">{{ t('dashboard.governance.responseCacheDesc') }}</span>
                </div>
                <el-icon class="gov-entry-arrow"><ArrowRight /></el-icon>
              </router-link>
            </el-col>

            <el-col :xs="24" :sm="12" :lg="6">
              <router-link class="gov-entry-card" :to="{ name: 'slow-query-analysis' }">
                <div class="gov-entry-inner">
                  <el-icon class="gov-icon"><Timer /></el-icon>
                  <span class="gov-entry-label">{{ t('dashboard.governance.slowQuery') }}</span>
                  <span class="gov-entry-desc">{{ t('dashboard.governance.slowQueryDesc') }}</span>
                </div>
                <el-icon class="gov-entry-arrow"><ArrowRight /></el-icon>
              </router-link>
            </el-col>

            <el-col :xs="24" :sm="12" :lg="6">
              <router-link class="gov-entry-card" :to="{ name: 'call-history-token-usage' }">
                <div class="gov-entry-inner">
                  <el-icon class="gov-icon"><DataLine /></el-icon>
                  <span class="gov-entry-label">{{ t('dashboard.governance.tokenUsage') }}</span>
                  <span class="gov-entry-desc">{{ t('dashboard.governance.tokenUsageDesc') }}</span>
                </div>
                <el-icon class="gov-entry-arrow"><ArrowRight /></el-icon>
              </router-link>
            </el-col>

            <el-col :xs="24" :sm="12" :lg="6">
              <router-link class="gov-entry-card" :to="{ name: 'tracing-dashboard' }">
                <div class="gov-entry-inner">
                  <el-icon class="gov-icon"><TrendCharts /></el-icon>
                  <span class="gov-entry-label">{{ t('dashboard.governance.tracing') }}</span>
                  <span class="gov-entry-desc">{{ t('dashboard.governance.tracingDesc') }}</span>
                </div>
                <el-icon class="gov-entry-arrow"><ArrowRight /></el-icon>
              </router-link>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>

    <!-- ════════════ 异常/告警摘要 ════════════ -->
    <el-row class="alert-row" :gutter="20">
      <el-col :xs="24">
        <el-card class="alert-card" shadow="never">
          <template #header>
            <div class="alert-header">
              <span class="card-title">
                <el-icon :class="['alert-title-icon', { 'alert-title-icon--danger': stats.alertCount > 0 }]"><WarningFilled /></el-icon>
                {{ t('dashboard.exceptionSummary') }}
              </span>
              <span class="alert-count-tag">{{ t('dashboard.securityAlertsLast24h', { count: stats.alertCount }) }}</span>
              <el-button link type="primary" size="small" @click="router.push('/exceptions/list')">
                {{ t('dashboard.allExceptions') }} <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </template>

          <div v-if="recentExceptions.length > 0">
            <el-table :data="recentExceptions" stripe size="small" class="alert-table">
              <el-table-column prop="occurredAt" :label="t('dashboard.time')" width="170">
                <template #default="{ row }">
                  <span class="alert-time">{{ formatAlertTime(row.occurredAt) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="exceptionType" :label="t('dashboard.type')" width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-tag size="small" type="danger">{{ row.exceptionType }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="operation" :label="t('dashboard.operation')" width="130">
                <template #default="{ row }">
                  <span>{{ row.operation || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="serviceName" :label="t('dashboard.service')" width="120">
                <template #default="{ row }">
                  <span>{{ row.serviceName || row.serviceType || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="sanitizedMessage" :label="t('dashboard.message')" min-width="200" show-overflow-tooltip />
              <el-table-column :label="t('dashboard.details')" width="80" align="center">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="router.push(`/exceptions/detail/${row.eventId}`)">
                    {{ t('dashboard.view') }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div v-else class="alert-empty">
            <el-icon class="alert-empty-icon"><CircleCheckFilled /></el-icon>
            <span>{{ t('dashboard.noException') }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 服务配置详情 -->
    <el-row class="config-row" :gutter="20">
      <el-col :xs="24">
        <el-card class="config-card" shadow="never">
          <template #header>
            <div class="config-header">
              <div class="config-title">{{ t('dashboard.serviceOverview') }}</div>
              <div class="config-actions">
                <el-button size="small" type="primary" @click="fetchServiceConfig" :loading="configLoading" plain>
                  <el-icon><Refresh /></el-icon> {{ t('dashboard.refreshConfig') }}
                </el-button>
              </div>
            </div>
          </template>

          <el-tabs v-model="activeServiceTab" type="border-card" style="margin-top: 12px;">
            <el-tab-pane :label="t('dashboard.globalConfig')" name="global">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item :label="t('dashboard.adapter')">{{ serviceConfigData?.adapter || 'N/A' }}</el-descriptions-item>
                <el-descriptions-item :label="t('dashboard.loadBalancing')">{{ serviceConfigData?.loadBalance?.type || 'N/A' }}</el-descriptions-item>
                <el-descriptions-item :label="t('dashboard.hashAlgorithm')">{{ serviceConfigData?.loadBalance?.hashAlgorithm || 'N/A' }}</el-descriptions-item>

                <el-descriptions-item :label="t('dashboard.globalRateLimit')">
                  <el-tag type="info">
                    {{ serviceConfigData?.rateLimit?.enabled ? t('dashboard.enabledStatus') : t('dashboard.disabledStatus') }}
                  </el-tag>
                </el-descriptions-item>

                <el-descriptions-item :label="t('dashboard.rateLimitParams')" v-if="serviceConfigData?.rateLimit?.enabled">
                  {{ serviceConfigData?.rateLimit?.algorithm || 'N/A' }} /
                  {{ serviceConfigData?.rateLimit?.rate || 'N/A' }} req/s
                </el-descriptions-item>

                <el-descriptions-item :label="t('dashboard.circuitBreakerConfig')">
                  <el-tag type="info">
                    {{ serviceConfigData?.circuitBreaker?.enabled ? t('dashboard.enabledStatus') : t('dashboard.disabledStatus') }}
                  </el-tag>
                </el-descriptions-item>

                <el-descriptions-item :label="t('dashboard.fallbackStrategy')" v-if="serviceConfigData?.fallback?.enabled">
                  {{ serviceConfigData?.fallback?.strategy || 'N/A' }}
                </el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <!-- 每个服务类型单独 Tab（label 用纯文本：做成 router-link 会让 tab 点不动，直接跳走） -->
            <el-tab-pane
              v-for="serviceName in orderedServiceNames"
              :key="serviceName"
              :name="serviceName"
              :label="getServiceTypeName(serviceName)"
            >
              <div class="service-pane-bar">
                <span class="service-pane-count">
                  {{ t('dashboard.instanceTotal', { count: (serviceConfigData?.services?.[serviceName]?.instances || []).length }) }}
                </span>
                <router-link
                  class="instance-link"
                  :to="{ path: '/config/instances', query: { serviceType: serviceName } }"
                >
                  {{ t('dashboard.viewInstances') }} →
                </router-link>
              </div>
              <el-table
                :data="serviceConfigData?.services?.[serviceName]?.instances || []"
                stripe
                size="small"
                style="width:100%"
                :row-class-name="(scope: { row?: InstanceRow }) => scope.row?.health ? '' : 'row-error'"
              >
                <el-table-column prop="name" :label="t('dashboard.instanceName')" width="180">
                  <template #default="scope">
                    <router-link
                      class="instance-link"
                      :to="{ path: '/config/instances', query: { serviceType: serviceName } }"
                    >
                      {{ scope.row.name }}
                    </router-link>
                  </template>
                </el-table-column>
                <el-table-column prop="baseUrl" :label="t('dashboard.baseUrl')" min-width="220" />
                <el-table-column :label="t('dashboard.adapter')" width="110">
                  <template #default="scope">{{ scope.row.adapter || serviceConfigData?.adapter || 'N/A' }}</template>
                </el-table-column>
                <el-table-column prop="path" :label="t('dashboard.path')" width="160" />
                <el-table-column prop="weight" :label="t('dashboard.weight')" width="80" align="center" />
                <el-table-column :label="t('dashboard.health')" width="110" align="center">
                  <template #default="scope">
                    <el-tag :type="scope.row.health ? 'info' : 'danger'" size="small">
                      {{ scope.row.health ? t('dashboard.health') : t('dashboard.unhealthyStatus') }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 其他服务单独一个Tab -->
            <el-tab-pane v-if="otherServiceNames.length > 0" :label="t('dashboard.others')" name="other">
              <el-table :data="otherServiceInstances" stripe size="small" style="width:100%">
                <el-table-column prop="serviceName" :label="t('dashboard.serviceType')" width="140" />
                <el-table-column prop="name" :label="t('dashboard.instanceName')" width="180">
                  <template #default="scope">
                    <router-link
                      class="instance-link"
                      :to="{ path: '/config/instances', query: { serviceType: scope.row.rawServiceType } }"
                    >
                      {{ scope.row.name }}
                    </router-link>
                  </template>
                </el-table-column>
                <el-table-column prop="baseUrl" :label="t('dashboard.baseUrl')" min-width="220" />
                <el-table-column :label="t('dashboard.adapter')" width="110">
                  <template #default="scope">{{ scope.row.adapter || serviceConfigData?.adapter || 'N/A' }}</template>
                </el-table-column>
                <el-table-column prop="path" :label="t('dashboard.path')" width="160" />
                <el-table-column prop="weight" :label="t('dashboard.weight')" width="80" align="center" />
                <el-table-column :label="t('dashboard.health')" width="110" align="center">
                  <template #default="scope">
                    <el-tag :type="scope.row.health ? 'info' : 'danger'" size="small">
                      {{ scope.row.health ? t('dashboard.health') : t('dashboard.unhealthyStatus') }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </PageSkeleton>
</template>

<script setup lang="ts">
import type { DashboardMetrics, ServiceStatsPayload } from '@/types'
import type { ServiceConfigPayload, ServiceInstance } from '@/types/common'

/** 首页实例行：服务配置里的实例 + 本页补的服务名/原始类型（#526） */
interface InstanceRow extends ServiceInstance {
  serviceName?: string
  rawServiceType?: string
}

import { ref, onMounted, onBeforeUnmount, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import * as echarts from 'echarts'
import {
  getServiceStats,
  getAllServiceConfig,
  getMonitoringOverview,
  getDashboardMetrics
} from '@/api/dashboard'
import { getJwtAccounts } from '@/api/account'
import { getRuleStats, type RuleStat } from '@/api/rules'
import { getRecentExceptionEvents } from '@/api/exception'
import type { ExceptionEvent } from '@/types/exception'
import {
  getRateLimiterSummary,
  getCircuitBreakerStates,
  getRoutingMonitorStats,
  type RateLimiterSummary,
  type CircuitBreakerState,
  type ServiceRoutingStats
} from '@/api/limitMetrics'
import { ElMessage } from 'element-plus'
import { getErrorMessage } from '@/utils/error'
import {
  Refresh,
  ArrowRight,
  SetUp,
  DataBoard,
  Warning,
  WarningFilled,
  CircleCheckFilled,
  Connection,
  Coin,
  Timer,
  DataLine,
  TrendCharts
} from '@element-plus/icons-vue'

// SSE helpers
import { connectSSE, disconnectSSE, addSSEListener, removeSSEListener } from '@/utils/sse'

// 组件
import PageSkeleton from '@/components/PageSkeleton.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import ChartCard from '@/components/ChartCard.vue'
import SystemMetricsPanel from './dashboard/SystemMetricsPanel.vue'

// 图表皮肤：颜色/字号/网格/提示框统一从令牌派生，页面不再持有色值
import { useChartSkin } from '@/composables/useChartSkin'
import { useChartAutoRefresh } from '@/composables/useChartAutoRefresh'

const { skin } = useChartSkin()
const router = useRouter()
const { t } = useI18n()

// ════════════ 状态数据 ════════════
const stats = ref({
  serviceCount: 0,
  instanceCount: 0,
  totalModels: 0,
  alertCount: 0,
  userCount: 0,
  healthyInstanceCount: 0,
  errorInstanceCount: 0
})

const dashboardMetrics = ref<DashboardMetrics | null>(null)
const serviceConfigData = ref<ServiceConfigPayload | null>(null)
const configLoading = ref(false)
const activeServiceTab = ref<string>('global')

// 治理链路状态
const ruleStatsRaw = ref<RuleStat[]>([])
const rlSummary = ref<RateLimiterSummary>({
  totalLimiters: 0,
  globalLimiters: 0,
  serviceLimiters: 0,
  instanceLimiters: 0,
  averageUsageRatio: 0,
  highUsageLimiters: 0,
})
const cbStates = ref<CircuitBreakerState[]>([])
const lbRoutingStats = ref<Record<string, ServiceRoutingStats>>({})
const recentExceptions = ref<ExceptionEvent[]>([])

// SSE 回调引用，方便移除
let sseHandler: ((data: unknown) => void) | null = null
// 卸载标志：fetchDashboardData 的 then/catch 可能在卸载后才执行
let isUnmounted = false

// 服务类型映射（保持原有顺序，type -> i18n 键名；v2.10.4: 文案收敛到顶层 serviceTypes.*）
const serviceTypeMap: Record<string, string> = {
  chat: 'chat',
  embedding: 'embedding',
  rerank: 'rerank',
  tts: 'tts',
  stt: 'stt',
  imgGen: 'imgGen',
  imgEdit: 'imgEdit'
}

const getServiceTypeName = (type: string) =>
  serviceTypeMap[type] ? t(`serviceTypes.${serviceTypeMap[type]}`) : type

// ════════════ 治理链路计算属性 ════════════

/** 规则命中摘要 */
const ruleStats = computed(() => {
  const list = ruleStatsRaw.value
  if (!list || list.length === 0) {
    return { enabledCount: 0, totalHits: 0, triggeredRuleCount: 0 }
  }
  const totalHits = list.reduce((sum, r) => sum + (r.hits || 0), 0)
  const triggeredRuleCount = list.filter(r => (r.hits || 0) > 0).length
  return {
    enabledCount: list.length,
    totalHits,
    triggeredRuleCount,
  }
})

/** 熔断器状态计数 */
const cbStateCounts = computed(() => {
  const counts: Record<string, number> = { CLOSED: 0, OPEN: 0, HALF_OPEN: 0 }
  cbStates.value.forEach(s => {
    const st = s.state || 'CLOSED'
    if (counts[st] !== undefined) {
      counts[st]++
    } else {
      counts[st] = 1
    }
  })
  return counts
})

/** 负载均衡摘要 */
const lbSummary = computed(() => {
  const statsMap = lbRoutingStats.value
  const serviceKeys = Object.keys(statsMap)
  let totalInstances = 0
  let healthyInstances = 0
  serviceKeys.forEach(key => {
    const svc = statsMap[key]
    const instanceCounts = svc.instanceCounts || {}
    const instKeys = Object.keys(instanceCounts)
    totalInstances += instKeys.length
    // instanceCounts 记录每个实例的路由次数，只要计数 > 0 就认为是活跃/健康
    instKeys.forEach(ik => {
      if (instanceCounts[ik] > 0) healthyInstances++
    })
  })
  return {
    totalInstances,
    healthyInstances,
    serviceCount: serviceKeys.length,
  }
})

// 保留原有计算属性逻辑
const orderedServiceNames = computed(() => {
  if (!serviceConfigData.value?.services) return []
  const names = Object.keys(serviceConfigData.value.services)
  const ordered: string[] = []
  Object.keys(serviceTypeMap).forEach(k => {
    if (names.includes(k)) ordered.push(k)
  })
  return ordered
})

const otherServiceNames = computed(() => {
  if (!serviceConfigData.value?.services) return []
  return Object.keys(serviceConfigData.value.services).filter(n => !Object.keys(serviceTypeMap).includes(n))
})

const otherServiceInstances = computed(() => {
  if (!serviceConfigData.value?.services || otherServiceNames.value.length === 0) return []
  const res: InstanceRow[] = []
  const services = serviceConfigData.value.services
  otherServiceNames.value.forEach(name => {
    const svc = services[name]
    ;(svc.instances ?? []).forEach((ins) => {
      res.push({
        ...ins,
        serviceName: getServiceTypeName(name),
        rawServiceType: name,
        adapter: ins.adapter || svc.adapter || serviceConfigData.value?.adapter || ''
      })
    })
  })
  return res
})

// 概览指标（顶部 MetricStrip）：六项取值与原统计卡完全一致
const metricItems = computed<MetricStripItem[]>(() => {
  const serviceCount = serviceConfigData.value?.services ? Object.keys(serviceConfigData.value.services).length : stats.value.serviceCount
  const instances = (() => {
    if (!serviceConfigData.value?.services) return stats.value.instanceCount
    let total = 0
    Object.values(serviceConfigData.value.services).forEach((s) => { total += (s.instances?.length || 0) })
    return total
  })()
  const healthy = (() => {
    if (!serviceConfigData.value?.services) return 0
    let c = 0
    Object.values(serviceConfigData.value.services).forEach((s) => {
      (s.instances || []).forEach((ins) => { if (ins.health) c++ })
    })
    return c
  })()
  const errorDiff = instances - healthy
  const errorCount = errorDiff < 0 ? 0 : errorDiff

  return [
    { key: 'service', label: t('dashboard.serviceQuantity'), value: serviceCount },
    { key: 'instance', label: t('dashboard.instanceQuantity'), value: instances },
    { key: 'model', label: t('dashboard.modelQuantity'), value: stats.value.totalModels || 0 },
    { key: 'healthy', label: t('dashboard.healthyInstanceCount'), value: healthy },
    // 唯一允许着色的指标：有异常实例时才升级为 danger
    {
      key: 'error',
      label: t('dashboard.errorInstanceCount'),
      value: errorCount,
      tone: errorCount > 0 ? 'danger' : 'default'
    },
    { key: 'user', label: t('dashboard.accountQuantity'), value: stats.value.userCount || 0 }
  ]
})

// ════════════ 图表 ════════════
const systemChart = ref<HTMLElement | null>(null)
let systemChartInstance: echarts.ECharts | null = null

/** 从皮肤里取轴标签样式（类型上是 Record<string, unknown>，取值需断言） */
const axisLabelStyle = (axis: Record<string, unknown>) =>
  (axis.axisLabel ?? {}) as { color?: string; fontSize?: number }

const getChartOption = () => {
  const chartSkin = skin.value
  const m = dashboardMetrics.value
  const jvm = m?.jvm || {}
  const sys = m?.system || {}
  const sec = m?.security || {}
  const http = m?.http || {}

  const memoryUsage = jvm.heapUsagePercent || 0
  const cpuUsage = (sys.processCpuUsage || 0) * 100
  const attempts = sec.authAttempts ?? 0
  const authSuccess = attempts > 0 ? ((sec.authSuccesses || 0) / attempts * 100) : 100
  const requestRate = Math.min((http.totalRequests || 0) / 1000 * 10, 100)

  const categories = [
    t('dashboard.metrics.jvmMemory'),
    t('dashboard.chart.cpuUsage'),
    t('dashboard.chart.authSuccess'),
    t('dashboard.chart.requestVolume')
  ]
  const labels = [
    t('dashboard.metrics.jvmMemory'),
    t('dashboard.chart.cpuUsageRate'),
    t('dashboard.chart.authSuccessRate'),
    t('dashboard.chart.requestVolume')
  ]
  const values = [memoryUsage, cpuUsage, authSuccess, requestRate]
  // 只有越过阈值的指标才升级为语义色，其余一律主色（全屏 ≤ 3 个色相）
  const breached = [memoryUsage > 80, cpuUsage > 80, authSuccess < 80, false]

  const categoryLabel = axisLabelStyle(chartSkin.categoryAxis)
  const valueLabel = axisLabelStyle(chartSkin.valueAxis)

  return {
    backgroundColor: 'transparent',
    textStyle: { color: chartSkin.text, fontSize: valueLabel.fontSize },
    tooltip: {
      ...chartSkin.tooltip,
      formatter: (params: unknown) => {
        const p = (params as Array<{ dataIndex: number }>)[0]
        const detail = [
          `${memoryUsage}% (${jvm.heapUsedMB || 0}/${jvm.heapMaxMB || 0} MB)`,
          `${cpuUsage.toFixed(1)}%`,
          `${authSuccess.toFixed(1)}%`,
          t('dashboard.metrics.timesUnit', { count: http.totalRequests || 0 })
        ]
        return `<strong>${labels[p.dataIndex]}</strong><br/>${detail[p.dataIndex]}`
      }
    },
    grid: chartSkin.grid,
    xAxis: {
      ...chartSkin.categoryAxis,
      data: categories,
      axisLabel: {
        ...categoryLabel,
        interval: 0,
        // 窄屏（容器 < 420px）四个中文类目名会互相压住，斜置 35° 换可读性；
        // resize 时会重算 option，故旋转角随容器宽度自适应。
        rotate: (systemChart.value?.clientWidth ?? 0) > 0 && (systemChart.value?.clientWidth ?? 0) < 420 ? 35 : 0
      }
    },
    yAxis: {
      ...chartSkin.valueAxis,
      max: 100,
      axisLabel: { ...valueLabel, formatter: (v: number) => `${v}%` }
    },
    series: [
      {
        name: t('dashboard.chart.resourceUsage'),
        type: 'bar',
        barWidth: '50%',
        data: values.map((value, index) => ({
          value,
          name: labels[index],
          itemStyle: { color: breached[index] ? chartSkin.emphasis : chartSkin.palette[0] }
        })),
        label: {
          show: true,
          position: 'top',
          color: chartSkin.textMuted,
          fontSize: valueLabel.fontSize,
          formatter: (p: unknown) => {
            const item = p as { dataIndex: number; value: number }
            if (item.dataIndex === 3) return `${http.totalRequests || 0}`
            return `${item.value.toFixed(1)}%`
          }
        }
      }
    ]
  }
}

const initChart = () => {
  if (!systemChart.value) return
  systemChartInstance = echarts.init(systemChart.value)
  systemChartInstance.setOption(getChartOption())
}

const resizeChart = () => {
  systemChartInstance?.resize()
  systemChartInstance?.setOption(getChartOption(), { notMerge: true })
}

// 语言 / 主题切换后按当前数据与最新文案重建图表（纯重绘，无网络请求）
const rebuildAll = () => {
  systemChartInstance?.setOption(getChartOption(), { notMerge: true })
}

// SSE 辅助：规范化 baseUrl（去尾部斜杠）

// 在主线程中更新数据的方法
const updateDataInMainThread = (updateFn: () => void) => {
  queueMicrotask(updateFn)
}

const handleHealthUpdate = (payload: unknown) => {
    const p = payload as {
      type?: string
      instanceHealth?: Record<string, unknown>
      data?: { instanceHealth?: Record<string, unknown> }
    }
  if (!payload) return

  let dataObj: Record<string, unknown> | null = null

  if (p.type === 'health-update' && p.instanceHealth) {
    dataObj = p.instanceHealth
  }
  else if (p.type === 'health-update' && p.data && p.data.instanceHealth) {
    dataObj = p.data.instanceHealth
  }
  else if (p.instanceHealth) {
    dataObj = p.instanceHealth
  }

  if (!dataObj || typeof dataObj !== 'object') {
    return
  }

  updateDataInMainThread(() => {
    const changedServices = new Set<string>()

    Object.entries(dataObj || {}).forEach(([key, val]) => {
      const isHealthy = (typeof val === 'boolean') ? val : String(val).toLowerCase() === 'true'

      const firstColon = key.indexOf(':')
      if (firstColon === -1) return
      const svcType = key.slice(0, firstColon)
      const instanceId = key.slice(firstColon + 1)

      const svc = serviceConfigData.value?.services?.[svcType]
      const instances = svc?.instances
      if (svc && Array.isArray(instances)) {
        let localChanged = false
        instances.forEach((ins, idx: number) => {
          const insInstanceId = ins.instanceId || `${ins.name}@${ins.baseUrl}` || ''

          if (insInstanceId === instanceId) {
            if (ins.health !== isHealthy) {
              const newIns = { ...ins, health: isHealthy }
              instances.splice(idx, 1, newIns)
              localChanged = true
            }
          } else if (!ins.instanceId && ins.name && ins.baseUrl) {
            const fallbackInstanceId = `${ins.name}@${ins.baseUrl}`;
            if (fallbackInstanceId === instanceId) {
              if (ins.health !== isHealthy) {
                const newIns = { ...ins, health: isHealthy }
                instances.splice(idx, 1, newIns)
                localChanged = true
              }
            }
          }
        })
        if (localChanged) changedServices.add(svcType)
      }
    })

    changedServices.forEach(svcType => {
      const svc = serviceConfigData.value?.services?.[svcType]
      if (svc && Array.isArray(svc.instances)) {
        svc.instances = svc.instances.slice()
      }
    })

    nextTick(() => {
      if (systemChartInstance) {
        try {
          systemChartInstance.setOption(getChartOption(), { notMerge: true })
        } catch (e) {
          console.error('[SSE] update chart failed', e)
        }
      }
    })
  })
}

const fetchServiceConfig = async () => {
  try {
    configLoading.value = true
    const res = await getAllServiceConfig()
    if (res.data && res.data.success) {
      serviceConfigData.value = (res.data?.data ?? null) as ServiceConfigPayload | null

      if (serviceConfigData.value?.services) {
        const services = serviceConfigData.value.services
        const fallbackAdapter = serviceConfigData.value.adapter
        Object.keys(services).forEach(k => {
          const s = services[k]
          if (!s.adapter) s.adapter = fallbackAdapter
        })
      }

      if (serviceConfigData.value?.models !== undefined) {
        if (Array.isArray(serviceConfigData.value.models)) {
          stats.value.totalModels = serviceConfigData.value.models.length
        }
      }

      ElMessage.success(t('dashboard.serviceConfigLoaded'))
      const ordered = orderedServiceNames.value
      if (ordered.length > 0) activeServiceTab.value = ordered[0]
    } else {
      ElMessage.error(t('dashboard.fetchConfigFailed', { message: res.data?.message || t('dashboard.unknownError') }))
    }
  } catch (e) {
    ElMessage.error(t('dashboard.fetchConfigError', { message: getErrorMessage(e) || t('dashboard.networkError') }))
  } finally {
    configLoading.value = false
  }
}

const fetchMonitoringOverview = async () => {
  try {
    const res = await getMonitoringOverview()
    if (res.data && res.data.success) {
      // 安全告警数来自监控概览（#291）：此前从 /models/stats 读一个不存在的字段，值也无处显示。
      // 这里的 `res.data.data` 现在受真实 DashboardOverview 类型约束（issue #297）——
      // 原先它被赋给一个 ref<any> 且再没人读，等于让类型检查失效。
      stats.value.alertCount = res.data.data?.alertsLast24Hours ?? 0
      nextTick(() => {
        if (systemChartInstance) {
          systemChartInstance.setOption(getChartOption(), { notMerge: true })
        } else {
          initChart()
        }
      })
    } else {
      ElMessage.error(t('dashboard.fetchOverviewFailed', { message: res.data?.message || t('dashboard.unknownError') }))
    }
  } catch (e) {
    ElMessage.error(t('dashboard.fetchOverviewError', { message: getErrorMessage(e) || t('dashboard.networkError') }))
  }
}

const fetchDashboardMetrics = async () => {
  try {
    const res = await getDashboardMetrics()
    if (res.data && res.data.success) {
      dashboardMetrics.value = (res.data?.data ?? null) as DashboardMetrics | null
      nextTick(() => {
        if (systemChartInstance) {
          systemChartInstance.setOption(getChartOption(), { notMerge: true })
        } else {
          initChart()
        }
      })
    } else {
      ElMessage.error(t('dashboard.fetchMetricsFailed', { message: res.data?.message || t('dashboard.unknownError') }))
    }
  } catch (e) {
    ElMessage.error(t('dashboard.fetchMetricsError', { message: getErrorMessage(e) || t('dashboard.networkError') }))
  }
}

// ════════════ 治理链路数据加载（静默，不弹错误） ════════════

const fetchGovernanceData = async () => {
  // 并行加载四组治理数据 + 异常摘要，任一失败不影响其它
  const results = await Promise.allSettled([
    getRuleStats(),
    getRateLimiterSummary(),
    getCircuitBreakerStates(),
    getRoutingMonitorStats(),
    getRecentExceptionEvents(5),
  ])

  // ① 规则命中
  if (results[0].status === 'fulfilled') {
    const res = results[0].value as { data?: { data?: RuleStat[] } }
    ruleStatsRaw.value = (res.data?.data as RuleStat[]) ?? []
  }

  // ② 限流摘要
  if (results[1].status === 'fulfilled') {
    rlSummary.value = results[1].value as RateLimiterSummary
  }

  // ③ 熔断器状态
  if (results[2].status === 'fulfilled') {
    cbStates.value = results[2].value as CircuitBreakerState[]
  }

  // ④ 负载均衡路由统计
  if (results[3].status === 'fulfilled') {
    lbRoutingStats.value = results[3].value as Record<string, ServiceRoutingStats>
  }

  // ⑤ 异常摘要
  if (results[4].status === 'fulfilled') {
    recentExceptions.value = results[4].value as ExceptionEvent[]
  }
}

const formatAlertTime = (iso: string | undefined) => {
  if (!iso) return '-'
  try {
    const d = new Date(iso)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  } catch {
    return iso
  }
}

const fetchDashboardData = async () => {
  try {
    const statsRes = await getServiceStats()
    if (statsRes.data && statsRes.data.success) {
      const d: ServiceStatsPayload = statsRes.data.data || {}
      stats.value.serviceCount = d.totalServices || 0
      stats.value.instanceCount = d.totalInstances || 0
      stats.value.totalModels = d.totalModels || 0
      stats.value.userCount = d.userCount || 0
    }

    try {
      const accounts = await getJwtAccounts()
      stats.value.userCount = accounts.length
    } catch {
      // ignore
    }

    await fetchServiceConfig()
    await fetchMonitoringOverview()
    await fetchDashboardMetrics()

    nextTick(() => {
      initChart()
    })
  } catch (e) {
    ElMessage.error(t('dashboard.loadDashboardFailed', { message: getErrorMessage(e) || t('dashboard.networkError') }))
  }

  // 治理链路数据独立加载，不阻塞主流程
  fetchGovernanceData()
}

onMounted(() => {
  useChartAutoRefresh(rebuildAll)

  fetchDashboardData().then(() => {
    if (isUnmounted) return
    sseHandler = (data: unknown) => {
      handleHealthUpdate(data)
    }
    addSSEListener(sseHandler)
    connectSSE()
  }).catch(() => {
    if (isUnmounted) return
    sseHandler = (data: unknown) => handleHealthUpdate(data)
    addSSEListener(sseHandler)
    connectSSE()
  })

  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  isUnmounted = true
  window.removeEventListener('resize', resizeChart)
  systemChartInstance?.dispose()

  if (sseHandler) {
    removeSSEListener(sseHandler)
    sseHandler = null
  }
  disconnectSSE()
})
</script>

<style scoped>
/* ════════════ 概览指标 ════════════ */
.stats-wrap { min-width: 0; }

/* ════════════ 主体两栏 ════════════ */
.main-row { align-items: stretch; }
.main-row > .el-col { display: flex; flex-direction: column; }

/* 与 ChartCard 同一套外壳：纸面 + 1px 分隔线，不使用投影 */
.card-panel {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 320px;
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius-lg);
  background: var(--ja-paper);
}

:deep(.el-card__header) {
  padding: var(--ja-space-3) var(--ja-space-4);
  border-bottom: 1px solid var(--ja-border);
  background: var(--ja-paper);
}

:deep(.el-card__body) { padding: var(--ja-space-4); }

.card-title {
  color: var(--ja-ink);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
}

.chart-area { width: 100%; height: 100%; min-height: 240px; }

/* ════════════ 系统指标（明细见 ./dashboard/SystemMetricsPanel.vue） ════════════ */
.metrics-panel :deep(.el-card__body) { flex: 1 1 auto; }

/* ════════════ 治理链路 ════════════ */
.governance-row { margin-top: var(--ja-space-5); }

/* 三块内容卡与 ChartCard 同壳：纸面 + 1px 边界，不投影 */
.governance-card,
.alert-card,
.config-card {
  background: var(--ja-paper);
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius-lg);
}

.governance-header { display: flex; align-items: center; gap: var(--ja-space-2); }

.gov-section {
  display: flex;
  flex-direction: column;
  min-height: 132px;
  padding: var(--ja-space-2) 0;
}

.gov-section-head {
  display: flex;
  align-items: center;
  gap: var(--ja-space-2);
  padding-bottom: var(--ja-space-2);
  margin-bottom: var(--ja-space-3);
  border-bottom: 1px solid var(--ja-border);
}

/* 四类小节的图标底一律中性：identity 由文案承担，颜色只留给异常数值 */
.gov-icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  padding: var(--ja-space-1);
  font-size: 16px;
  color: var(--ja-ink-3);
  background: var(--ja-surface-2);
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius-sm);
}

.gov-label { color: var(--ja-ink); font-size: 14px; font-weight: 600; }
.gov-jump { margin-left: auto; font-size: 12px; }
.gov-jump:focus-visible { outline: 2px solid var(--ja-primary); outline-offset: 2px; }

.gov-metrics { display: flex; flex-direction: column; gap: var(--ja-space-1); }

.gov-metric-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ja-space-2);
  padding: 2px 0;
}

.gov-metric-label { color: var(--ja-ink-3); font-size: 12px; }
.gov-metric-value { color: var(--ja-ink); font-size: 14px; font-weight: 600; font-variant-numeric: tabular-nums; }
.gov-metric-value--danger { color: var(--ja-danger); }
.gov-metric-value--warning { color: var(--ja-warning); }

/* 能力入口卡：纯导航，hover 才出现主色 */
.gov-entry-row { margin-top: var(--ja-space-3); }

.gov-entry-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ja-space-2);
  min-height: 88px;
  padding: var(--ja-space-3) var(--ja-space-4);
  color: inherit;
  text-decoration: none;
  background: var(--ja-paper);
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius);
  transition: border-color 0.2s, box-shadow 0.2s, background-color 0.2s;
}

.gov-entry-card:hover {
  background: var(--ja-surface-2);
  border-color: var(--ja-primary);
  box-shadow: var(--ja-shadow-sm);
}

.gov-entry-card:focus-visible { outline: 2px solid var(--ja-primary); outline-offset: 2px; }

.gov-entry-inner { display: flex; flex-direction: column; gap: var(--ja-space-1); min-width: 0; }
.gov-entry-label { color: var(--ja-ink); font-size: 14px; font-weight: 600; }
.gov-entry-desc { color: var(--ja-ink-3); font-size: 12px; line-height: 1.5; }

/* 入口卡的图标只是行首标记，不再套用上面小节的底/边框 */
.gov-entry-inner .gov-icon {
  padding: 0;
  background: transparent;
  border: 0;
  font-size: 15px;
}

.gov-entry-arrow {
  flex-shrink: 0;
  margin-left: var(--ja-space-2);
  font-size: 16px;
  color: var(--ja-ink-3);
  transition: color 0.2s, transform 0.2s;
}

.gov-entry-card:hover .gov-entry-arrow { color: var(--ja-primary); transform: translateX(2px); }

/* ════════════ 异常/告警摘要 ════════════ */
.alert-row { margin-top: var(--ja-space-5); }

.alert-header { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--ja-space-2); }
.alert-title-icon { margin-right: var(--ja-space-1); color: var(--ja-ink-3); vertical-align: middle; }
.alert-title-icon--danger { color: var(--ja-danger); }
.alert-count-tag { color: var(--ja-ink-3); font-size: 12px; font-variant-numeric: tabular-nums; }
.alert-time { color: var(--ja-ink-3); font-size: 12px; font-variant-numeric: tabular-nums; }

.alert-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--ja-space-2);
  padding: var(--ja-space-7) var(--ja-space-4);
  color: var(--ja-ink-3);
  font-size: 14px;
}

.alert-empty-icon { font-size: 20px; }
.alert-table { width: 100%; }

/* ════════════ 服务配置 ════════════ */
.config-row { margin-top: var(--ja-space-5); }
.config-header { display: flex; align-items: center; justify-content: space-between; gap: var(--ja-space-3); }
.config-title { color: var(--ja-ink); font-size: 14px; font-weight: 600; }
.config-actions { display: flex; align-items: center; }

/* 失败/异常行才用中性底强调，正常行保持纸面 */
:deep(.el-table .row-error td) { background: var(--ja-surface-2) !important; }
:deep(.el-table th) { background: transparent; }

.service-pane-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ja-space-2);
  margin-bottom: var(--ja-space-2);
}
.service-pane-count { color: var(--ja-ink-3); font-size: 13px; }
.instance-link { color: var(--ja-primary); font-size: 13px; text-decoration: none; cursor: pointer; }
.instance-link:hover { text-decoration: underline; }

/* el-row 只处理横向 gutter：窄屏堆叠时补纵向间距 */
@media (max-width: 1199px) {
  .main-row > .el-col + .el-col { margin-top: var(--ja-space-4); }
  .gov-section { margin-bottom: var(--ja-space-3); }
  .gov-entry-row > .el-col { margin-bottom: var(--ja-space-3); }
}

@media (max-width: 992px) {
  .card-panel { min-height: 220px; }
  .chart-area { min-height: 200px; }
  .gov-section { min-height: 0; }
}
</style>
