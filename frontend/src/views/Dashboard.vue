<template>
  <PageSkeleton title="治理指挥台">
    <template #actions>
      <el-button size="small" type="primary" @click="fetchDashboardData" :loading="configLoading" plain>
        <el-icon><Refresh /></el-icon> 刷新全部
      </el-button>
    </template>

    <template #stats>
      <!-- 顶部统计卡片 -->
      <el-row class="stats-wrap" :gutter="18" justify="center">
        <el-col v-for="item in statCards" :key="item.key" :xs="12" :sm="8" :md="6" :lg="4">
          <StatCard
            :icon="item.icon"
            :label="item.label"
            :value="item.value"
            :tone="item.tone"
          />
        </el-col>
      </el-row>
    </template>

    <!-- 主体图表与监控详情 -->
    <el-row class="main-row" :gutter="20">
      <el-col :xs="24" :lg="16">
        <el-card class="card-panel" shadow="always">
          <template #header>
            <div class="card-title">系统概览</div>
          </template>
          <div ref="systemChart" class="chart-area" />
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8" :gutter="20">
        <el-card class="card-panel" shadow="always">
          <template #header>
            <div class="card-title">系统指标</div>
          </template>

          <div v-if="dashboardMetrics" class="monitoring-box">
            <!-- JVM 内存 -->
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="JVM 内存">
                <div class="row-inline">
                  <el-progress
                    :percentage="dashboardMetrics.jvm?.heapUsagePercent || 0"
                    :status="(dashboardMetrics.jvm?.heapUsagePercent || 0) > 80 ? 'exception' : 'success'"
                    stroke-width="12"
                    style="flex:1; margin-right:12px"
                  />
                  <el-tag size="small" type="info">
                    {{ dashboardMetrics.jvm?.heapUsedMB || 0 }} / {{ dashboardMetrics.jvm?.heapMaxMB || 0 }} MB
                  </el-tag>
                </div>
              </el-descriptions-item>

              <el-descriptions-item label="线程">
                <div class="row-inline">
                  <el-tag type="primary">{{ dashboardMetrics.jvm?.threadCount || 0 }} 个</el-tag>
                  <el-tag size="small" type="info" style="margin-left:8px">
                    峰值: {{ dashboardMetrics.jvm?.peakThreadCount || 0 }}
                  </el-tag>
                </div>
              </el-descriptions-item>

              <el-descriptions-item label="HTTP 请求">
                <div class="row-inline">
                  <el-tag type="primary">{{ dashboardMetrics.http?.totalRequests || 0 }} 次</el-tag>
                  <el-tag size="small" type="info" style="margin-left:8px">
                    平均: {{ (dashboardMetrics.http?.avgResponseTimeMs || 0).toFixed(1) }} ms
                  </el-tag>
                </div>
              </el-descriptions-item>

              <el-descriptions-item label="认证">
                <div class="row-inline">
                  <el-tag type="success">{{ dashboardMetrics.security?.authSuccesses || 0 }} 成功</el-tag>
                  <el-tag :type="(dashboardMetrics.security?.authFailures || 0) > 0 ? 'danger' : 'info'" style="margin-left:8px">
                    {{ dashboardMetrics.security?.authFailures || 0 }} 失败
                  </el-tag>
                  <el-tag size="small" type="warning" style="margin-left:8px">
                    活跃: {{ Math.round(dashboardMetrics.security?.activeUsers || 0) }}
                  </el-tag>
                </div>
              </el-descriptions-item>

              <el-descriptions-item label="安全缓存">
                <div class="row-inline">
                  <el-tag type="primary">命中: {{ Math.round(dashboardMetrics.security?.cacheHits || 0) }}</el-tag>
                  <el-tag size="small" type="info" style="margin-left:8px">
                    未命中: {{ Math.round(dashboardMetrics.security?.cacheMisses || 0) }}
                  </el-tag>
                  <el-tag size="small" type="warning" style="margin-left:8px">
                    大小: {{ Math.round(dashboardMetrics.security?.cacheSize || 0) }}
                  </el-tag>
                </div>
              </el-descriptions-item>

              <el-descriptions-item label="审计事件">
                <div class="row-inline">
                  <el-tag type="primary">{{ Math.round(dashboardMetrics.audit?.totalEvents || 0) }} 总数</el-tag>
                  <el-tag type="success" size="small" style="margin-left:8px">
                    {{ Math.round(dashboardMetrics.audit?.successEvents || 0) }} 成功
                  </el-tag>
                  <el-tag :type="(dashboardMetrics.audit?.failureEvents || 0) > 0 ? 'danger' : 'info'" size="small" style="margin-left:8px">
                    {{ Math.round(dashboardMetrics.audit?.failureEvents || 0) }} 失败
                  </el-tag>
                </div>
              </el-descriptions-item>

              <el-descriptions-item label="系统负载">
                <div class="row-inline">
                  <el-tag type="primary">{{ (dashboardMetrics.system?.systemLoadAverage || 0).toFixed(2) }}</el-tag>
                  <el-tag size="small" type="info" style="margin-left:8px">
                    CPU: {{ ((dashboardMetrics.system?.processCpuUsage || 0) * 100).toFixed(1) }}%
                  </el-tag>
                  <el-tag size="small" type="warning" style="margin-left:8px">
                    核心: {{ dashboardMetrics.system?.availableProcessors || 0 }}
                  </el-tag>
                </div>
              </el-descriptions-item>

              <el-descriptions-item label="运行时间">
                <el-tag type="primary">{{ formatUptime(dashboardMetrics.system?.uptimeSeconds) }}</el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <div v-else class="empty-placeholder">
            <el-icon><Loading /></el-icon>
            <div>正在加载指标数据...</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ════════════ 治理链路面板 ════════════ -->
    <el-row class="governance-row" :gutter="20">
      <el-col :xs="24">
        <el-card class="governance-card" shadow="hover">
          <template #header>
            <div class="governance-header">
              <span class="card-title">治理链路</span>
            </div>
          </template>

          <el-row :gutter="16">
            <!-- ① 规则命中 -->
            <el-col :xs="24" :sm="12" :lg="6">
              <div class="gov-section">
                <div class="gov-section-head">
                  <el-icon class="gov-icon gov-icon--primary"><SetUp /></el-icon>
                  <span class="gov-label">规则命中</span>
                  <el-button link type="primary" size="small" class="gov-jump" @click="router.push('/config/rules')">
                    查看 <el-icon><ArrowRight /></el-icon>
                  </el-button>
                </div>
                <div class="gov-metrics">
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">启用规则</span>
                    <span class="gov-metric-value">{{ ruleStats.enabledCount }}</span>
                  </div>
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">总命中次数</span>
                    <span class="gov-metric-value gov-metric-value--primary">{{ ruleStats.totalHits }}</span>
                  </div>
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">触发规则数</span>
                    <span class="gov-metric-value">{{ ruleStats.triggeredRuleCount }}</span>
                  </div>
                </div>
              </div>
            </el-col>

            <!-- ② 限流 -->
            <el-col :xs="24" :sm="12" :lg="6">
              <div class="gov-section">
                <div class="gov-section-head">
                  <el-icon class="gov-icon gov-icon--warning"><DataBoard /></el-icon>
                  <span class="gov-label">限流</span>
                  <el-button link type="primary" size="small" class="gov-jump" @click="router.push('/rate-limiters/monitoring')">
                    监控 <el-icon><ArrowRight /></el-icon>
                  </el-button>
                </div>
                <div class="gov-metrics">
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">平均使用率</span>
                    <span class="gov-metric-value">{{ rlSummary.averageUsageRatio }}%</span>
                  </div>
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">高使用率(>80%)</span>
                    <span :class="['gov-metric-value', rlSummary.highUsageLimiters > 0 ? 'gov-metric-value--danger' : '']">
                      {{ rlSummary.highUsageLimiters }}
                    </span>
                  </div>
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">服务 / 实例限流器</span>
                    <span class="gov-metric-value">{{ rlSummary.serviceLimiters }} / {{ rlSummary.instanceLimiters }}</span>
                  </div>
                </div>
              </div>
            </el-col>

            <!-- ③ 熔断 -->
            <el-col :xs="24" :sm="12" :lg="6">
              <div class="gov-section">
                <div class="gov-section-head">
                  <el-icon class="gov-icon gov-icon--danger"><Warning /></el-icon>
                  <span class="gov-label">熔断</span>
                  <el-button link type="primary" size="small" class="gov-jump" @click="router.push('/circuit-breakers/monitoring')">
                    监控 <el-icon><ArrowRight /></el-icon>
                  </el-button>
                </div>
                <div class="gov-metrics">
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">CLOSED</span>
                    <span class="gov-metric-value gov-metric-value--success">{{ cbStateCounts.CLOSED }}</span>
                  </div>
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">OPEN</span>
                    <span :class="['gov-metric-value', cbStateCounts.OPEN > 0 ? 'gov-metric-value--danger' : '']">
                      {{ cbStateCounts.OPEN }}
                    </span>
                  </div>
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">HALF_OPEN</span>
                    <span class="gov-metric-value gov-metric-value--warning">{{ cbStateCounts.HALF_OPEN }}</span>
                  </div>
                </div>
              </div>
            </el-col>

            <!-- ④ 负载均衡 -->
            <el-col :xs="24" :sm="12" :lg="6">
              <div class="gov-section">
                <div class="gov-section-head">
                  <el-icon class="gov-icon gov-icon--success"><Connection /></el-icon>
                  <span class="gov-label">负载均衡</span>
                  <el-button link type="primary" size="small" class="gov-jump" @click="router.push('/load-balancers/monitoring')">
                    监控 <el-icon><ArrowRight /></el-icon>
                  </el-button>
                </div>
                <div class="gov-metrics">
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">总实例</span>
                    <span class="gov-metric-value">{{ lbSummary.totalInstances }}</span>
                  </div>
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">健康实例</span>
                    <span class="gov-metric-value gov-metric-value--success">{{ lbSummary.healthyInstances }}</span>
                  </div>
                  <div class="gov-metric-row">
                    <span class="gov-metric-label">服务数</span>
                    <span class="gov-metric-value">{{ lbSummary.serviceCount }}</span>
                  </div>
                </div>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>

    <!-- ════════════ 异常/告警摘要 ════════════ -->
    <el-row class="alert-row" :gutter="20">
      <el-col :xs="24">
        <el-card class="alert-card" shadow="hover">
          <template #header>
            <div class="alert-header">
              <span class="card-title">
                <el-icon class="alert-title-icon"><WarningFilled /></el-icon>
                异常 / 告警摘要
              </span>
              <el-button link type="primary" size="small" @click="router.push('/exceptions/list')">
                全部异常 <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </template>

          <div v-if="recentExceptions.length > 0">
            <el-table :data="recentExceptions" stripe size="small" class="alert-table">
              <el-table-column prop="occurredAt" label="时间" width="170">
                <template #default="{ row }">
                  <span class="alert-time">{{ formatAlertTime(row.occurredAt) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="exceptionType" label="类型" width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-tag size="small" type="danger">{{ row.exceptionType }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="operation" label="操作" width="130">
                <template #default="{ row }">
                  <span>{{ row.operation || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="serviceName" label="服务" width="120">
                <template #default="{ row }">
                  <span>{{ row.serviceName || row.serviceType || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="sanitizedMessage" label="消息" min-width="200" show-overflow-tooltip />
              <el-table-column label="详情" width="80" align="center">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="router.push(`/exceptions/detail/${row.eventId}`)">
                    查看
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div v-else class="alert-empty">
            <el-icon class="alert-empty-icon"><CircleCheckFilled /></el-icon>
            <span>暂无异常，系统运行正常</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 服务配置详情 -->
    <el-row class="config-row" :gutter="20">
      <el-col :xs="24">
        <el-card class="config-card" shadow="hover">
          <template #header>
            <div class="config-header">
              <div class="config-title">服务配置速览</div>
              <div class="config-actions">
                <el-button size="small" type="primary" @click="fetchServiceConfig" :loading="configLoading" plain>
                  <el-icon><Refresh /></el-icon> 刷新配置
                </el-button>
              </div>
            </div>
          </template>

          <el-tabs v-model="activeServiceTab" type="border-card" style="margin-top: 12px;">
            <el-tab-pane label="全局配置" name="global">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item label="适配器">{{ serviceConfigData?.adapter || 'N/A' }}</el-descriptions-item>
                <el-descriptions-item label="负载均衡">{{ serviceConfigData?.loadBalance?.type || 'N/A' }}</el-descriptions-item>
                <el-descriptions-item label="哈希算法">{{ serviceConfigData?.loadBalance?.hashAlgorithm || 'N/A' }}</el-descriptions-item>

                <el-descriptions-item label="全局限流">
                  <el-tag :type="serviceConfigData?.rateLimit?.enabled ? 'success' : 'info'">
                    {{ serviceConfigData?.rateLimit?.enabled ? '启用' : '禁用' }}
                  </el-tag>
                </el-descriptions-item>

                <el-descriptions-item label="限流参数" v-if="serviceConfigData?.rateLimit?.enabled">
                  {{ serviceConfigData?.rateLimit?.algorithm || 'N/A' }} /
                  {{ serviceConfigData?.rateLimit?.rate || 'N/A' }} req/s
                </el-descriptions-item>

                <el-descriptions-item label="熔断器">
                  <el-tag :type="serviceConfigData?.circuitBreaker?.enabled ? 'success' : 'info'">
                    {{ serviceConfigData?.circuitBreaker?.enabled ? '启用' : '禁用' }}
                  </el-tag>
                </el-descriptions-item>

                <el-descriptions-item label="降级策略" v-if="serviceConfigData?.fallback?.enabled">
                  {{ serviceConfigData?.fallback?.strategy || 'N/A' }}
                </el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <!-- 每个服务类型单独Tab -->
            <el-tab-pane
              v-for="serviceName in orderedServiceNames"
              :key="serviceName"
              :name="serviceName"
            >
              <template #label>
                <router-link
                  class="service-tab-link"
                  :to="{ path: '/config/services', query: { serviceType: serviceName } }"
                  @click.stop
                >
                  {{ getServiceTypeName(serviceName) }}
                </router-link>
              </template>
              <el-table
                :data="serviceConfigData?.services?.[serviceName]?.instances || []"
                stripe
                size="small"
                style="width:100%"
                :row-class-name="(row: any) => row.row?.health ? '' : 'row-error'"
              >
                <el-table-column prop="name" label="实例名称" width="180">
                  <template #default="scope">
                    <router-link
                      class="instance-link"
                      :to="{ path: '/config/instances', query: { serviceType: serviceName } }"
                    >
                      {{ scope.row.name }}
                    </router-link>
                  </template>
                </el-table-column>
                <el-table-column prop="baseUrl" label="基础URL" min-width="220" />
                <el-table-column label="适配器" width="110">
                  <template #default="scope">{{ scope.row.adapter || serviceConfigData?.adapter || 'N/A' }}</template>
                </el-table-column>
                <el-table-column prop="path" label="路径" width="160" />
                <el-table-column prop="weight" label="权重" width="80" align="center" />
                <el-table-column label="健康" width="110" align="center">
                  <template #default="scope">
                    <el-tag :type="scope.row.health ? 'success' : 'danger'" size="small">
                      {{ scope.row.health ? '健康' : '异常' }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <!-- 其他服务单独一个Tab -->
            <el-tab-pane v-if="otherServiceNames.length > 0" label="其他" name="other">
              <el-table :data="otherServiceInstances" stripe size="small" style="width:100%">
                <el-table-column prop="serviceName" label="服务类型" width="140" />
                <el-table-column prop="name" label="实例名称" width="180">
                  <template #default="scope">
                    <router-link
                      class="instance-link"
                      :to="{ path: '/config/instances', query: { serviceType: scope.row.rawServiceType } }"
                    >
                      {{ scope.row.name }}
                    </router-link>
                  </template>
                </el-table-column>
                <el-table-column prop="baseUrl" label="基础URL" min-width="220" />
                <el-table-column label="适配器" width="110">
                  <template #default="scope">{{ scope.row.adapter || serviceConfigData?.adapter || 'N/A' }}</template>
                </el-table-column>
                <el-table-column prop="path" label="路径" width="160" />
                <el-table-column prop="weight" label="权重" width="80" align="center" />
                <el-table-column label="健康" width="110" align="center">
                  <template #default="scope">
                    <el-tag :type="scope.row.health ? 'success' : 'danger'" size="small">
                      {{ scope.row.health ? '健康' : '异常' }}
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
import { ref, onMounted, onBeforeUnmount, computed, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
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
import {
  Refresh,
  Loading,
  ArrowRight,
  SetUp,
  DataBoard,
  Warning,
  WarningFilled,
  CircleCheckFilled,
  Connection
} from '@element-plus/icons-vue'

// SSE helpers
import { connectSSE, disconnectSSE, addSSEListener, removeSSEListener } from '@/utils/sse'

// 组件
import PageSkeleton from '@/components/PageSkeleton.vue'
import StatCard from '@/components/StatCard.vue'

// 图表主题
import { useChartTheme } from '@/composables/useChartTheme'

const { getChartTheme } = useChartTheme()
const router = useRouter()

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

const monitoringOverview = ref<any>(null)
const dashboardMetrics = ref<any>(null)
const serviceConfigData = ref<any>(null)
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
let sseHandler: ((data: any) => void) | null = null

// 服务类型映射（保持原有）
const serviceTypeMap: Record<string, string> = {
  chat: '聊天服务',
  embedding: '嵌入服务',
  rerank: '重排序服务',
  tts: '文本转语音',
  stt: '语音转文本',
  imgGen: '图像生成',
  imgEdit: '图像编辑服务'
}

const getServiceTypeName = (type: string) => serviceTypeMap[type] || type

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
  const res: any[] = []
  otherServiceNames.value.forEach(name => {
    const svc = serviceConfigData.value.services[name]
    ;(svc.instances || []).forEach((ins: any) => {
      res.push({
        ...ins,
        serviceName: getServiceTypeName(name),
        rawServiceType: name,
        adapter: ins.adapter || svc.adapter || serviceConfigData.value?.adapter
      })
    })
  })
  return res
})

// 统计卡片数据计算
const statCards = computed(() => {
  const serviceCount = serviceConfigData.value?.services ? Object.keys(serviceConfigData.value.services).length : stats.value.serviceCount
  const instances = (() => {
    if (!serviceConfigData.value?.services) return stats.value.instanceCount
    let total = 0
    Object.values(serviceConfigData.value.services).forEach((s: any) => { total += (s.instances?.length || 0) })
    return total
  })()
  const healthy = (() => {
    if (!serviceConfigData.value?.services) return 0
    let c = 0
    Object.values(serviceConfigData.value.services).forEach((s: any) => {
      (s.instances || []).forEach((ins: any) => { if (ins.health) c++ })
    })
    return c
  })()
  const error = instances - healthy

  const healthyCount = healthy
  const errorCount = error

  return [
    { key: 'service', icon: 'Flag', label: '服务数量', value: serviceCount, tone: 'primary' as const },
    { key: 'instance', icon: 'Cpu', label: '实例数量', value: instances, tone: 'success' as const },
    { key: 'model', icon: 'Monitor', label: '模型数量', value: stats.value.totalModels || 0, tone: 'default' as const },
    { key: 'healthy', icon: 'Check', label: '健康实例', value: healthyCount, tone: 'success' as const },
    { key: 'error', icon: 'Warning', label: '异常实例', value: errorCount < 0 ? 0 : errorCount, tone: 'danger' as const },
    { key: 'user', icon: 'User', label: '账号数量', value: stats.value.userCount || 0, tone: 'info' as const }
  ]
})

// ════════════ 图表 ════════════
const systemChart = ref<HTMLElement | null>(null)
let systemChartInstance: echarts.ECharts | null = null

const getChartOption = () => {
  const { primary, success, warning, danger, textColor } = getChartTheme()

  if (!dashboardMetrics.value) {
    return {
      title: { text: '系统状态概览', left: 'center', textStyle: { fontSize: 14, color: textColor } },
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: ['内存', 'CPU', '认证', '请求'] },
      yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      series: [
        { name: '使用率', data: [0, 0, 0, 0], type: 'bar', barWidth: '40%' }
      ]
    }
  }

  const m = dashboardMetrics.value
  const jvm = m.jvm || {}
  const sys = m.system || {}
  const sec = m.security || {}
  const http = m.http || {}

  const memoryUsage = jvm.heapUsagePercent || 0
  const cpuUsage = (sys.processCpuUsage || 0) * 100
  const authSuccess = sec.authAttempts > 0 ? ((sec.authSuccesses || 0) / sec.authAttempts * 100) : 100
  const requestRate = Math.min((http.totalRequests || 0) / 1000 * 10, 100)

  return {
    title: { text: '系统资源概览', left: 'center', textStyle: { fontSize: 14, color: textColor } },
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const p = params[0]
        const labels = ['JVM 内存', 'CPU 使用率', '认证成功率', '请求量']
        const values = [
          `${memoryUsage}% (${jvm.heapUsedMB || 0}/${jvm.heapMaxMB || 0} MB)`,
          `${cpuUsage.toFixed(1)}%`,
          `${authSuccess.toFixed(1)}%`,
          `${http.totalRequests || 0} 次`
        ]
        return `<strong>${labels[p.dataIndex]}</strong><br/>${values[p.dataIndex]}`
      }
    },
    legend: { bottom: 0, data: ['资源使用'], textStyle: { color: textColor } },
    grid: { left: 30, right: 30, bottom: 50, top: 60 },
    xAxis: {
      type: 'category',
      data: ['JVM 内存', 'CPU 使用', '认证成功', '请求量'],
      axisLabel: { interval: 0, rotate: 0, fontSize: 11, color: textColor }
    },
    yAxis: {
      type: 'value',
      max: 100,
      axisLabel: {
        color: textColor,
        formatter: (v: number) => `${v}%`
      }
    },
    series: [
      {
        name: '资源使用',
        type: 'bar',
        barWidth: '50%',
        data: [
          {
            value: memoryUsage,
            itemStyle: { color: memoryUsage > 80 ? danger : memoryUsage > 60 ? warning : success },
            name: `${memoryUsage}%`
          },
          {
            value: cpuUsage,
            itemStyle: { color: cpuUsage > 80 ? danger : cpuUsage > 60 ? warning : primary },
            name: `${cpuUsage.toFixed(1)}%`
          },
          {
            value: authSuccess,
            itemStyle: { color: authSuccess < 50 ? danger : authSuccess < 80 ? warning : success },
            name: `${authSuccess.toFixed(1)}%`
          },
          {
            value: requestRate,
            itemStyle: { color: primary },
            name: `${http.totalRequests || 0}`
          }
        ],
        label: {
          show: true,
          position: 'top',
          formatter: (p: any) => {
            if (p.dataIndex === 3) return `${http.totalRequests || 0}`
            return `${p.value.toFixed(1)}%`
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

// SSE 辅助：规范化 baseUrl（去尾部斜杠）
const normalizeBase = (u?: string) => (u ? u.replace(/\/+$/, '') : '')

// 在主线程中更新数据的方法
const updateDataInMainThread = (updateFn: () => void) => {
  queueMicrotask(updateFn)
}

const handleHealthUpdate = (payload: any) => {
  if (!payload) return

  let dataObj: Record<string, any> | null = null

  if (payload.type === 'health-update' && payload.instanceHealth) {
    dataObj = payload.instanceHealth
  }
  else if (payload.type === 'health-update' && payload.data && payload.data.instanceHealth) {
    dataObj = payload.data.instanceHealth
  }
  else if (payload.instanceHealth) {
    dataObj = payload.instanceHealth
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
      if (svc && Array.isArray(svc.instances)) {
        let localChanged = false
        svc.instances.forEach((ins: any, idx: number) => {
          const insInstanceId = ins.instanceId || `${ins.name}@${ins.baseUrl}` || ''

          if (insInstanceId === instanceId) {
            if (ins.health !== isHealthy) {
              const newIns = { ...ins, health: isHealthy }
              svc.instances.splice(idx, 1, newIns)
              localChanged = true
            }
          } else if (!ins.instanceId && ins.name && ins.baseUrl) {
            const fallbackInstanceId = `${ins.name}@${ins.baseUrl}`;
            if (fallbackInstanceId === instanceId) {
              if (ins.health !== isHealthy) {
                const newIns = { ...ins, health: isHealthy }
                svc.instances.splice(idx, 1, newIns)
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
      serviceConfigData.value = res.data.data

      if (serviceConfigData.value?.services) {
        Object.keys(serviceConfigData.value.services).forEach(k => {
          const s = serviceConfigData.value.services[k]
          if (!s.adapter) s.adapter = serviceConfigData.value.adapter
        })
      }

      if (serviceConfigData.value?.models !== undefined) {
        if (Array.isArray(serviceConfigData.value.models)) {
          stats.value.totalModels = serviceConfigData.value.models.length
        }
      }

      ElMessage.success('服务配置加载成功')
      const ordered = orderedServiceNames.value
      if (ordered.length > 0) activeServiceTab.value = ordered[0]
    } else {
      ElMessage.error(`获取服务配置失败: ${  res.data?.message || '未知错误'}`)
    }
  } catch (e: any) {
    ElMessage.error(`获取服务配置异常: ${  e.message || '网络错误'}`)
  } finally {
    configLoading.value = false
  }
}

const fetchMonitoringOverview = async () => {
  try {
    const res = await getMonitoringOverview()
    if (res.data && res.data.success) {
      monitoringOverview.value = res.data.data
      nextTick(() => {
        systemChartInstance ? systemChartInstance.setOption(getChartOption(), { notMerge: true }) : initChart()
      })
    } else {
      ElMessage.error(`获取监控概览失败: ${  res.data?.message || '未知错误'}`)
    }
  } catch (e: any) {
    ElMessage.error(`获取监控概览异常: ${  e.message || '网络错误'}`)
  }
}

const fetchDashboardMetrics = async () => {
  try {
    const res = await getDashboardMetrics()
    if (res.data && res.data.success) {
      dashboardMetrics.value = res.data.data
      nextTick(() => {
        systemChartInstance ? systemChartInstance.setOption(getChartOption(), { notMerge: true }) : initChart()
      })
    } else {
      ElMessage.error(`获取指标数据失败: ${  res.data?.message || '未知错误'}`)
    }
  } catch (e: any) {
    ElMessage.error(`获取指标数据异常: ${  e.message || '网络错误'}`)
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
    const res = results[0].value as any
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

const formatUptime = (seconds: number | undefined) => {
  if (seconds === undefined || seconds === null) return 'N/A'
  const s = Math.round(seconds)
  const days = Math.floor(s / 86400)
  const hours = Math.floor((s % 86400) / 3600)
  const mins = Math.floor((s % 3600) / 60)
  if (days > 0) return `${days}天 ${hours}时`
  if (hours > 0) return `${hours}时 ${mins}分`
  return `${mins}分`
}

const fetchDashboardData = async () => {
  try {
    const statsRes = await getServiceStats()
    if (statsRes.data && statsRes.data.success) {
      const d: any = statsRes.data.data || {}
      stats.value.serviceCount = d.totalServices || 0
      stats.value.instanceCount = d.totalInstances || 0
      stats.value.totalModels = d.totalModels || 0
      stats.value.alertCount = d.alertCount || 0
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
  } catch (e: any) {
    ElMessage.error(`加载仪表板失败: ${  e.message || '网络错误'}`)
  }

  // 治理链路数据独立加载，不阻塞主流程
  fetchGovernanceData()
}

onMounted(() => {
  fetchDashboardData().then(() => {
    sseHandler = (data: any) => {
      handleHealthUpdate(data)
    }
    addSSEListener(sseHandler)
    connectSSE()
  }).catch(() => {
    sseHandler = (data: any) => handleHealthUpdate(data)
    addSSEListener(sseHandler)
    connectSSE()
  })

  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
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
/* 统计卡片 */
.stats-wrap { margin-bottom: 16px; }

/* 主体卡片 */
.card-panel { min-height: 330px; border-radius: var(--ja-radius-lg); }
.chart-area { width:100%; height:320px; }

/* 监控侧栏 */
.monitoring-box { padding:4px 0; }
.row-inline { display:flex; align-items:center; gap:8px; }

/* 配置区域 */
.config-row { margin-top:22px; }
.config-card { border-radius: var(--ja-radius-lg); padding-bottom: 6px; }
.config-header { display:flex; justify-content:space-between; align-items:center; gap:12px; }
.config-title { font-weight:700; color: var(--ja-dashboard-config-title); }
.config-actions { display:flex; align-items:center; }

/* 表格样式 */
:deep(.el-table .row-error) { background: var(--ja-dashboard-error-row) !important; }
:deep(.el-table th) { background: transparent; }

/* Empty */
.empty-placeholder { text-align:center; padding:24px 12px; color: var(--ja-dashboard-empty-color); }

/* 使主行的列高度一致，并让卡片伸展占满列 */
.main-row {
  align-items: stretch;
}

.main-row > .el-col {
  display: flex;
  flex-direction: column;
}

.card-panel {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 320px;
}

:deep(.card-panel .el-card__body) {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  padding: 12px 16px;
}

.chart-area {
  width: 100%;
  height: 100%;
  min-height: 220px;
  flex: 1 1 auto;
}

/* 卡片标题 */
.card-title {
  font-weight: 600;
  color: var(--ja-text-primary);
}

/* ════════════ 治理链路面板 ════════════ */
.governance-row {
  margin-top: 22px;
}

.governance-card {
  border-radius: var(--ja-radius-lg);
}

.governance-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.gov-section {
  padding: 8px 4px;
  min-height: 120px;
}

.gov-section-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--ja-border-light, #ebeef5);
}

.gov-icon {
  font-size: 18px;
}

.gov-icon--primary { color: var(--ja-primary); }
.gov-icon--warning { color: var(--ja-warning); }
.gov-icon--danger  { color: var(--ja-danger); }
.gov-icon--success { color: var(--ja-success); }

.gov-label {
  font-weight: 600;
  font-size: 14px;
  color: var(--ja-text-primary);
}

.gov-jump {
  margin-left: auto;
  font-size: 12px;
}

.gov-metrics {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.gov-metric-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2px 0;
}

.gov-metric-label {
  font-size: 12px;
  color: var(--ja-text-secondary, #909399);
}

.gov-metric-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--ja-text-primary);
  font-variant-numeric: tabular-nums;
}

.gov-metric-value--primary { color: var(--ja-primary); }
.gov-metric-value--success { color: var(--ja-success); }
.gov-metric-value--warning { color: var(--ja-warning); }
.gov-metric-value--danger  { color: var(--ja-danger); }

/* ════════════ 异常/告警摘要 ════════════ */
.alert-row {
  margin-top: 22px;
}

.alert-card {
  border-radius: var(--ja-radius-lg);
}

.alert-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.alert-title-icon {
  color: var(--ja-warning);
  margin-right: 4px;
  vertical-align: middle;
}

.alert-time {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--ja-text-secondary, #909399);
}

.alert-empty {
  text-align: center;
  padding: 32px 16px;
  color: var(--ja-success);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
}

.alert-empty-icon {
  font-size: 20px;
}

.alert-table {
  width: 100%;
}

/* ════════════ 服务配置链接 ════════════ */
.service-tab-link {
  text-decoration: none;
  color: inherit;
  cursor: pointer;
}

.service-tab-link:hover {
  color: var(--ja-primary);
}

.instance-link {
  text-decoration: none;
  color: var(--ja-primary);
  cursor: pointer;
  font-size: 13px;
}

.instance-link:hover {
  text-decoration: underline;
}

/* 响应式 */
@media (max-width: 992px) {
  .chart-area { height: 220px; }
  .card-panel { min-height: 220px; }
}
@media (max-width: 640px) {
  .chart-area { height: 200px; }
}
</style>
