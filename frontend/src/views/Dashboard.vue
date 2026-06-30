<template>
  <div class="dashboard">
    <!-- 顶部统计卡片 -->
    <el-row class="stats-wrap" :gutter="18" justify="center">
      <el-col v-for="item in statCards" :key="item.key" :xs="12" :sm="8" :md="6" :lg="4">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-inner">
            <div class="icon-box" :style="{ background: item.bg }">
              <el-icon class="icon">
                <component :is="item.icon" />
              </el-icon>
            </div>
            <div class="text-box">
              <div class="value">{{ item.value }}</div>
              <div class="label">{{ item.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

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

    <!-- 服务配置详情 -->
    <el-row class="config-row" :gutter="20">
      <el-col :xs="24">
        <el-card class="config-card" shadow="hover">
          <template #header>
            <div class="config-header">
              <div class="config-title">服务配置详情</div>
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

            <!-- 每个服务类型单独Tab（恢复之前喜欢的交互） -->
            <el-tab-pane
              v-for="serviceName in orderedServiceNames"
              :key="serviceName"
              :label="getServiceTypeName(serviceName)"
              :name="serviceName"
            >
              <el-table
                :data="serviceConfigData?.services?.[serviceName]?.instances || []"
                stripe
                size="small"
                style="width:100%"
                :row-class-name="(row: any) => row.row?.health ? '' : 'row-error'"
              >
                <el-table-column prop="name" label="实例名称" width="180" />
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
                <el-table-column prop="name" label="实例名称" width="180" />
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed, nextTick } from 'vue'
import * as echarts from 'echarts'
import {
  getServiceStats,
  getAllServiceConfig,
  getMonitoringOverview,
  getDashboardMetrics
} from '@/api/dashboard'
import { getJwtAccounts } from '@/api/account'
import { ElMessage } from 'element-plus'

// SSE helpers
import { connectSSE, disconnectSSE, addSSEListener, removeSSEListener } from '@/utils/sse'

// 状态数据
const stats = ref({
  serviceCount: 0,
  instanceCount: 0,
  totalModels: 0,
  alertCount: 0,
  userCount: 0,
  // 保留这些字段作为初始值，但实际值将通过计算属性获取
  healthyInstanceCount: 0,
  errorInstanceCount: 0
})

const monitoringOverview = ref<any>(null)
const dashboardMetrics = ref<any>(null)
const serviceConfigData = ref<any>(null)
const configLoading = ref(false)
const activeServiceTab = ref<string>('global')

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

const categoryMap: Record<string, string> = {
  system: '系统',
  business: '业务',
  infrastructure: '基础设施'
}

const getServiceTypeName = (type: string) => serviceTypeMap[type] || type

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
      res.push({ ...ins, serviceName: getServiceTypeName(name), adapter: ins.adapter || svc.adapter || serviceConfigData.value?.adapter })
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
    if (!serviceConfigData.value?.services) return 0 // 默认返回0
    let c = 0
    Object.values(serviceConfigData.value.services).forEach((s: any) => {
      (s.instances || []).forEach((ins: any) => { if (ins.health) c++ })
    })
    return c
  })()
  const error = instances - healthy

  // 始终基于服务配置详情中的实例状态计算
  const healthyCount = healthy
  const errorCount = error

  console.log('[Dashboard] 计算统计卡片数据:', {
    serviceCount,
    instances,
    healthy,
    error,
    healthyCount,
    errorCount
  })

  return [
    { key: 'service', icon: 'Flag', label: '服务数量', value: serviceCount, bg: 'linear-gradient(135deg,#e6f7ff,#fff0f6)' },
    { key: 'instance', icon: 'Cpu', label: '实例数量', value: instances, bg: 'linear-gradient(135deg,#fff7e6,#f0fff4)' },
    { key: 'model', icon: 'Monitor', label: '模型数量', value: stats.value.totalModels || 0, bg: 'linear-gradient(135deg,#f0f5ff,#fff)' },
    { key: 'healthy', icon: 'Check', label: '健康实例', value: healthyCount, bg: 'linear-gradient(135deg,#e6fffb,#f0f9ff)' },
    { key: 'error', icon: 'Warning', label: '异常实例', value: errorCount < 0 ? 0 : errorCount, bg: 'linear-gradient(135deg,#fff0f0,#fff7e6)' },
    { key: 'user', icon: 'User', label: '账号数量', value: stats.value.userCount || 0, bg: 'linear-gradient(135deg,#f8eaff,#eef7ff)' }
  ]
})

// 图表
const systemChart = ref<HTMLElement | null>(null)
let systemChartInstance: echarts.ECharts | null = null

const getChartOption = () => {
  // 使用真实业务指标数据
  if (!dashboardMetrics.value) {
    return {
      title: { text: '系统状态概览', left: 'center', textStyle: { fontSize: 14 } },
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

  // 构建真实业务指标图表
  const memoryUsage = jvm.heapUsagePercent || 0
  const cpuUsage = (sys.processCpuUsage || 0) * 100
  const authSuccess = sec.authAttempts > 0 ? ((sec.authSuccesses || 0) / sec.authAttempts * 100) : 100
  const requestRate = Math.min((http.totalRequests || 0) / 1000 * 10, 100) // 假设 10000 请求 = 100%

  return {
    title: { text: '系统资源概览', left: 'center', textStyle: { fontSize: 14 } },
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
    legend: { bottom: 0, data: ['资源使用'] },
    grid: { left: 30, right: 30, bottom: 50, top: 60 },
    xAxis: {
      type: 'category',
      data: ['JVM 内存', 'CPU 使用', '认证成功', '请求量'],
      axisLabel: { interval: 0, rotate: 0, fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      max: 100,
      axisLabel: {
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
            itemStyle: { color: memoryUsage > 80 ? '#f56c6c' : memoryUsage > 60 ? '#e6a23c' : '#67c23a' },
            name: `${memoryUsage}%`
          },
          { 
            value: cpuUsage, 
            itemStyle: { color: cpuUsage > 80 ? '#f56c6c' : cpuUsage > 60 ? '#e6a23c' : '#409eff' },
            name: `${cpuUsage.toFixed(1)}%`
          },
          { 
            value: authSuccess, 
            itemStyle: { color: authSuccess < 50 ? '#f56c6c' : authSuccess < 80 ? '#e6a23c' : '#67c23a' },
            name: `${authSuccess.toFixed(1)}%`
          },
          { 
            value: requestRate, 
            itemStyle: { color: '#409eff' },
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

// 辅助显示函数
const formatPercent = (v?: number) => `${((v ?? 0) * 100).toFixed(0)  }%`
const degradationText = (level?: string) => (level === 'NONE' ? '无降级' : level === 'PARTIAL' ? '部分降级' : level === 'FULL' ? '全部降级' : 'N/A')
const degradationTagType = (level?: string) => (level === 'NONE' ? 'success' : level === 'PARTIAL' ? 'warning' : 'danger')
const circuitText = (s?: string) => (s === 'CLOSED' ? '关闭' : s === 'OPEN' ? '开启' : s === 'HALF_OPEN' ? '半开' : 'N/A')
const circuitTagType = (s?: string) => (s === 'CLOSED' ? 'success' : s === 'OPEN' ? 'danger' : 'warning')

// SSE 辅助：规范化 baseUrl（去尾部斜杠）
const normalizeBase = (u?: string) => (u ? u.replace(/\/+$/, '') : '')

// 在主线程中更新数据的方法
const updateDataInMainThread = (updateFn: () => void) => {
  // 使用 queueMicrotask 确保在主线程中更新数据
  queueMicrotask(updateFn)
}

// 替换 Dashboard.vue 中原有的 handleHealthUpdate 为下面实现
const handleHealthUpdate = (payload: any) => {
  if (!payload) return
  
  // 处理新的SSE数据格式: {"instanceHealth":{...},"type":"health-update","timestamp":"..."}
  let dataObj: Record<string, any> | null = null;
  
  // 检查是否是新的数据格式
  if (payload.type === 'health-update' && payload.instanceHealth) {
    dataObj = payload.instanceHealth;
  } 
  // 兼容旧的数据格式
  else if (payload.type === 'health-update' && payload.data && payload.data.instanceHealth) {
    dataObj = payload.data.instanceHealth;
  }
  // 兼容直接传递instanceHealth对象的格式
  else if (payload.instanceHealth) {
    dataObj = payload.instanceHealth;
  }
  
  if (!dataObj || typeof dataObj !== 'object') {
    console.log('[SSE] 无效的数据格式:', payload);
    return;
  }

  console.log('[SSE] handleHealthUpdate payload:', dataObj);

  // 在主线程中更新数据
  updateDataInMainThread(() => {
    // track which services changed so we can reassign arrays to trigger reactivity
    const changedServices = new Set<string>()

    Object.entries(dataObj || {}).forEach(([key, val]) => {
      // 更严格地判断健康状态：兼容 boolean 与字符串 'true'/'false'（不再使用 Boolean(val)）
      const isHealthy = (typeof val === 'boolean') ? val : String(val).toLowerCase() === 'true'

      // key 格式示例: "chat:550e8400-e29b-41d4-a716-446655440000" (serviceType:instanceId)
      const firstColon = key.indexOf(':')
      if (firstColon === -1) return
      const svcType = key.slice(0, firstColon)
      const instanceId = key.slice(firstColon + 1)

      console.log(`[SSE] 解析实例信息: key=${key}, svcType=${svcType}, instanceId=${instanceId}`)

      const svc = serviceConfigData.value?.services?.[svcType]
      if (svc && Array.isArray(svc.instances)) {
        let localChanged = false
        svc.instances.forEach((ins: any, idx: number) => {
          const insInstanceId = ins.instanceId || `${ins.name}@${ins.baseUrl}` || ''
          // 匹配实例：优先 instanceId 精确匹配
          console.log(`[SSE] 尝试匹配: insInstanceId=${insInstanceId} vs instanceId=${instanceId}`)
          
          if (insInstanceId === instanceId) {
            // 添加调试日志
            console.log(`[SSE] 精确匹配到实例: ${key} -> ${svcType}.${ins.name} (${ins.baseUrl}), 健康状态: ${ins.health} -> ${isHealthy}`)
            
            // 仅在值变化时替换对象以确保 Vue 能检测到变化
            if (ins.health !== isHealthy) {
              const newIns = { ...ins, health: isHealthy }
              // 使用 splice 直接替换当前索引，保持数组引用但替换元素引用
              svc.instances.splice(idx, 1, newIns)
              localChanged = true
              console.log(`[SSE] 更新实例健康状态: ${key}, ${ins.health} -> ${isHealthy}`)
            } else {
              console.log(`[SSE] 实例健康状态未变化: ${key}, 仍然是 ${isHealthy}`)
            }
          } else if (!ins.instanceId && ins.name && ins.baseUrl) {
            // 如果实例没有instanceId，尝试通过name@baseUrl格式匹配（向后兼容）
            const fallbackInstanceId = `${ins.name}@${ins.baseUrl}`;
            if (fallbackInstanceId === instanceId) {
              console.log(`[SSE] 向后兼容匹配到实例: ${key} -> ${svcType}.${ins.name} (${ins.baseUrl}), 健康状态: ${ins.health} -> ${isHealthy}`)
              
              if (ins.health !== isHealthy) {
                const newIns = { ...ins, health: isHealthy }
                svc.instances.splice(idx, 1, newIns)
                localChanged = true
                console.log(`[SSE] 更新实例健康状态(向后兼容): ${key}, ${ins.health} -> ${isHealthy}`)
              } else {
                console.log(`[SSE] 实例健康状态未变化(向后兼容): ${key}, 仍然是 ${isHealthy}`)
              }
            }
          }
        })
        if (localChanged) changedServices.add(svcType)
      } else {
        // 添加调试日志
        console.log(`[SSE] 未找到服务类型或实例: ${svcType}`, svc)
      }
    })

    // 强制刷新变更过的服务实例数组以保证视图更新
    changedServices.forEach(svcType => {
      const svc = serviceConfigData.value?.services?.[svcType]
      if (svc && Array.isArray(svc.instances)) {
        svc.instances = svc.instances.slice()
      }
    })

    // 不再直接更新健康实例和异常实例的数量，而是通过计算属性自动计算
    console.log('[SSE] updated instance health status, changed services:', Array.from(changedServices))

    // 触发视图/图表刷新（若需要）
    nextTick(() => {
      if (systemChartInstance) {
        try {
          systemChartInstance.setOption(getChartOption(), { notMerge: true })
        } catch (e) {
          console.warn('[SSE] update chart failed', e)
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

      // 如果服务中未包含adapter则使用全局adapter
      if (serviceConfigData.value?.services) {
        Object.keys(serviceConfigData.value.services).forEach(k => {
          const s = serviceConfigData.value.services[k]
          if (!s.adapter) s.adapter = serviceConfigData.value.adapter
        })
      }

      // 仅在服务配置中明确包含 models 字段时更新统计值，避免覆盖之前正确的 totalModels
      if (serviceConfigData.value?.models !== undefined) {
        if (Array.isArray(serviceConfigData.value.models)) {
          stats.value.totalModels = serviceConfigData.value.models.length
        } else {
          // 如果 models 存在但不是数组，不覆盖已有值（保守处理）
          // 可以考虑记录或上报异常格式
        }
      }

      ElMessage.success('服务配置加载成功')
      // 设置默认激活服务tab
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
      // 更新图表
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

// 获取真实业务指标
const fetchDashboardMetrics = async () => {
  try {
    const res = await getDashboardMetrics()
    if (res.data && res.data.success) {
      dashboardMetrics.value = res.data.data
      // 更新图表
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

// 格式化运行时间
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
    // 服务统计（保留）
    const statsRes = await getServiceStats()
    if (statsRes.data && statsRes.data.success) {
      const d: any = statsRes.data.data || {}
      stats.value.serviceCount = d.totalServices || 0
      stats.value.instanceCount = d.totalInstances || 0
      stats.value.totalModels = d.totalModels || 0
      stats.value.alertCount = d.alertCount || 0
      stats.value.userCount = d.userCount || 0
    }

    // 账号数（保留）
    try {
      const accounts = await getJwtAccounts()
      stats.value.userCount = accounts.length
    } catch {
      // ignore
    }

    // 监控和配置
    await fetchServiceConfig()
    await fetchMonitoringOverview()
    await fetchDashboardMetrics()

    // 初始化或刷新图表
    nextTick(() => {
      initChart()
    })
  } catch (e: any) {
    ElMessage.error(`加载仪表板失败: ${  e.message || '网络错误'}`)
  }
}

onMounted(() => {
  // 加载初始数据后再建立 SSE 连接并注册回调，保证 serviceConfigData 已经尽可能就绪
  fetchDashboardData().then(() => {
    // 注册 SSE 回调
    sseHandler = (data: any) => {
      // sse.ts 会传入解析后的 JSON 对象
      // 有时 payload 可能被嵌套或直接携带 instanceHealth 字段，handleHealthUpdate 会处理兼容
      handleHealthUpdate(data)
    }
    addSSEListener(sseHandler)
    connectSSE()
  }).catch(() => {
    // 即便初始加载失败，也尝试建立 SSE 连接以便后续实时更新
    sseHandler = (data: any) => handleHealthUpdate(data)
    addSSEListener(sseHandler)
    connectSSE()
  })

  window.addEventListener('resize', resizeChart)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeChart)
  systemChartInstance?.dispose()

  // 移除 SSE 相关资源
  if (sseHandler) {
    removeSSEListener(sseHandler)
    sseHandler = null
  }
  disconnectSSE()
})
</script>

<style scoped>
.dashboard {
  padding: 18px;
  background: linear-gradient(180deg, #f6f9ff 0%, #fbfdff 100%);
  min-height: 100vh;
}

/* 统计卡片 */
.stats-wrap { margin-bottom: 16px; }
.stat-card {
  border-radius: 10px;
  overflow: hidden;
  padding: 12px;
  min-height: 88px;
  display: flex;
  align-items: center;
  background: #fff;
  border: 1px solid rgba(20,40,80,0.04);
}
.stat-inner { display:flex; align-items:center; width:100% }
.icon-box {
  width:56px;
  height:56px;
  border-radius: 10px;
  display:flex;
  align-items:center;
  justify-content:center;
  margin-right:12px;
  box-shadow: 0 6px 14px rgba(64,158,255,0.06);
}
.icon { font-size:22px; color:#2b3a67; }
.text-box { flex:1; min-width:0; }
.value { font-size:20px; font-weight:700; color:#222b45; }
.label { font-size:12px; color:#6b7280; margin-top:4px; }

/* 主体卡片 */
.card-panel { min-height: 330px; border-radius:10px; }
.chart-area { width:100%; height:320px; }

/* 监控侧栏 */
.monitoring-box { padding:4px 0; }
.row-inline { display:flex; align-items:center; gap:8px; }
.cache-row { display:flex; align-items:center; gap:8px; }

/* 配置区域 */
.config-row { margin-top:22px; }
.config-card { border-radius:10px; padding-bottom: 6px; }
.config-header { display:flex; justify-content:space-between; align-items:center; gap:12px; }
.config-title { font-weight:700; color:#283044; }
.config-actions { display:flex; align-items:center; }

/* 表格样式 */
.el-table .row-error { background: #fff7f6 !important; }
.el-table th { background: transparent; }

/* Empty */
.empty-placeholder { text-align:center; padding:24px 12px; color:#909399; }
/* 使主行的列高度一致，并让卡片伸展占满列 */
.main-row {
  align-items: stretch; /* 让行内列高度拉伸一致 */
}

/* 让列成为弹性容器，这样内部 card 可以撑满高度 */
.main-row > .el-col {
  display: flex;
  flex-direction: column;
}

/* 卡片本身垂直布局，卡片占满列 */
.card-panel {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-height: 320px; /* 保留一个最小高度（可按需调整） */
}

/* 让 element-plus 卡片 body 占满卡片剩余空间（使用深度选择器以保证命中内部类） */
::v-deep .card-panel .el-card__body {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  padding: 12px 16px; /* 保持原有内边距感 */
}

/* 将图表区域改为弹性高度以充满卡片 body */
.chart-area {
  width: 100%;
  height: 100%;
  min-height: 220px; /* 在窄屏或高度受限时保底高度 */
  flex: 1 1 auto;
}

/* 响应式 */
@media (max-width: 992px) {
  .chart-area { height: 220px; }
  .card-panel { min-height: 220px; }
}
@media (max-width: 640px) {
  .stat-card { min-height: 78px; padding:10px; }
  .icon-box { width:48px; height:48px; }
  .chart-area { height: 200px; }
}
</style>