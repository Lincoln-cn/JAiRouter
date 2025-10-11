<template>
  <div class="tracing-performance">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>性能分析</span>
          <div class="header-actions">
            <el-date-picker
              v-model="timeRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
              @change="handleTimeRangeChange"
            />
            <el-button type="primary" @click="handleRefresh">刷新</el-button>
            <el-dropdown @command="handleOptimizationAction">
              <el-button type="warning">
                性能优化<el-icon class="el-icon--right"><arrow-down /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="optimize">触发优化</el-dropdown-item>
                  <el-dropdown-item command="gc">垃圾回收</el-dropdown-item>
                  <el-dropdown-item command="flush">刷新缓冲区</el-dropdown-item>
                  <el-dropdown-item command="memory-check">内存检查</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </template>
      
      <!-- 性能状态指示器 -->
      <el-row :gutter="20" class="performance-indicators">
        <el-col :span="6">
          <el-card class="indicator-card">
            <div class="indicator">
              <div class="indicator-value" :class="{ 'warning': memoryPressure === 'HIGH', 'danger': memoryPressure === 'CRITICAL' }">
                {{ memoryUsage }}%
              </div>
              <div class="indicator-label">内存使用率</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="indicator-card">
            <div class="indicator">
              <div class="indicator-value" :class="{ 'warning': processingQueueSize > 100, 'danger': processingQueueSize > 500 }">
                {{ processingQueueSize }}
              </div>
              <div class="indicator-label">处理队列大小</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="indicator-card">
            <div class="indicator">
              <div class="indicator-value" :class="{ 'warning': successRate < 95, 'danger': successRate < 90 }">
                {{ successRate }}%
              </div>
              <div class="indicator-label">成功率</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="indicator-card">
            <div class="indicator">
              <div class="indicator-value" :class="{ 'warning': activeBottlenecks > 0, 'danger': activeBottlenecks > 3 }">
                {{ activeBottlenecks }}
              </div>
              <div class="indicator-label">性能瓶颈</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="延迟分析" name="latency">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-card>
                <template #header>
                  <div class="card-header">
                    <span>服务延迟分布</span>
                  </div>
                </template>
                <div ref="latencyDistributionChart" class="chart-container"></div>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card>
                <template #header>
                  <div class="card-header">
                    <span>延迟趋势</span>
                  </div>
                </template>
                <div ref="latencyTrendChart" class="chart-container"></div>
              </el-card>
            </el-col>
          </el-row>
          
          <el-card class="top-slow-card">
            <template #header>
              <div class="card-header">
                <span>最慢的追踪</span>
              </div>
            </template>
            <el-table :data="slowTraces" style="width: 100%" empty-text="暂无慢追踪数据">
              <el-table-column prop="traceId" label="追踪ID" width="180" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-text style="font-family: monospace; font-size: 12px;">
                    {{ row.traceId.substring(0, 16) }}...
                  </el-text>
                </template>
              </el-table-column>
              <el-table-column prop="service" label="服务" width="120" />
              <el-table-column prop="operation" label="操作" show-overflow-tooltip />
              <el-table-column prop="duration" label="耗时(ms)" width="100" sortable>
                <template #default="{ row }">
                  <span :class="{ 'high-latency': row.duration > 1000 }">
                    {{ row.duration }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="hasError" label="状态" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.hasError ? 'danger' : 'success'" size="small">
                    {{ row.hasError ? '错误' : '成功' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="startTime" label="开始时间" width="140" />
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="scope">
                  <el-button size="small" type="primary" @click="handleViewTrace(scope.row)">
                    查看链路
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-tab-pane>
        
        <el-tab-pane label="错误分析" name="error">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-card>
                <template #header>
                  <div class="card-header">
                    <span>错误率分布</span>
                  </div>
                </template>
                <div ref="errorRateChart" class="chart-container"></div>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card>
                <template #header>
                  <div class="card-header">
                    <span>错误趋势</span>
                  </div>
                </template>
                <div ref="errorTrendChart" class="chart-container"></div>
              </el-card>
            </el-col>
          </el-row>
          
          <el-card class="top-errors-card">
            <template #header>
              <div class="card-header">
                <span>最常见的错误</span>
              </div>
            </template>
            <el-table :data="commonErrors" style="width: 100%" empty-text="暂无错误数据">
              <el-table-column prop="errorType" label="错误类型" width="150">
                <template #default="{ row }">
                  <el-tag type="danger" size="small">{{ row.errorType }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="service" label="服务" width="120" />
              <el-table-column prop="operation" label="操作" show-overflow-tooltip />
              <el-table-column prop="count" label="次数" width="80" sortable>
                <template #default="{ row }">
                  <el-text type="danger" style="font-weight: bold;">{{ row.count }}</el-text>
                </template>
              </el-table-column>
              <el-table-column prop="lastOccurrence" label="最后发生时间" width="140" />
            </el-table>
          </el-card>
        </el-tab-pane>
        
        <el-tab-pane label="吞吐量分析" name="throughput">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-card>
                <template #header>
                  <div class="card-header">
                    <span>请求量分布</span>
                  </div>
                </template>
                <div ref="requestDistributionChart" class="chart-container"></div>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card>
                <template #header>
                  <div class="card-header">
                    <span>QPS趋势</span>
                  </div>
                </template>
                <div ref="qpsTrendChart" class="chart-container"></div>
              </el-card>
            </el-col>
          </el-row>
        </el-tab-pane>
      </el-tabs>
    </el-card>
    
    <!-- 追踪详情对话框 -->
    <el-dialog v-model="traceDialogVisible" title="追踪详情" width="800px">
      <div ref="traceDetailChart" class="trace-detail-chart"></div>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="traceDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import {
  getTracingStats,
  getServiceStats,
  getRecentTraces,
  getTraceChain,
  getPerformanceStats,
  getDashboardMetrics,
  getProcessingStats,
  getMemoryStats,
  detectBottlenecks,
  getOptimizationSuggestions,
  triggerOptimization,
  triggerGarbageCollection,
  flushProcessingBuffer,
  performMemoryCheck
} from '@/api/tracing'

// 时间格式化函数
const formatTime = (timeStr: string) => {
  try {
    const date = new Date(timeStr)
    return date.toLocaleString('zh-CN', {
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  } catch {
    return timeStr
  }
}

// 时间范围
const timeRange = ref(['2023-10-01 00:00:00', '2023-10-01 23:59:59'])

// 激活的标签页
const activeTab = ref('latency')

// 最慢的追踪
const slowTraces = ref<any[]>([])

// 常见错误
const commonErrors = ref<any[]>([])

// 图表引用
const latencyDistributionChart = ref<HTMLElement | null>(null)
const latencyTrendChart = ref<HTMLElement | null>(null)
const errorRateChart = ref<HTMLElement | null>(null)
const errorTrendChart = ref<HTMLElement | null>(null)
const requestDistributionChart = ref<HTMLElement | null>(null)
const qpsTrendChart = ref<HTMLElement | null>(null)
const traceDetailChart = ref<HTMLElement | null>(null)

let latencyDistributionChartInstance: echarts.ECharts | null = null
let latencyTrendChartInstance: echarts.ECharts | null = null
let errorRateChartInstance: echarts.ECharts | null = null
let errorTrendChartInstance: echarts.ECharts | null = null
let requestDistributionChartInstance: echarts.ECharts | null = null
let qpsTrendChartInstance: echarts.ECharts | null = null
let traceDetailChartInstance: echarts.ECharts | null = null

// 追踪详情对话框
const traceDialogVisible = ref(false)

// 性能指标
const memoryUsage = ref(0)
const memoryPressure = ref('LOW')
const processingQueueSize = ref(0)
const successRate = ref(100)
const activeBottlenecks = ref(0)

// 初始化图表
const initCharts = () => {
  console.log('初始化图表...')
  
  // 延迟分布图表
  if (latencyDistributionChart.value) {
    console.log('初始化延迟分布图表')
    latencyDistributionChartInstance = echarts.init(latencyDistributionChart.value)
    latencyDistributionChartInstance.setOption(getLatencyDistributionChartOption())
  }
  
  // 延迟趋势图表
  if (latencyTrendChart.value) {
    console.log('初始化延迟趋势图表')
    latencyTrendChartInstance = echarts.init(latencyTrendChart.value)
    latencyTrendChartInstance.setOption(getLatencyTrendChartOption())
  }
  
  // 错误率分布图表
  if (errorRateChart.value) {
    console.log('初始化错误率分布图表')
    errorRateChartInstance = echarts.init(errorRateChart.value)
    errorRateChartInstance.setOption(getErrorRateChartOption())
  }
  
  // 错误趋势图表
  if (errorTrendChart.value) {
    console.log('初始化错误趋势图表')
    errorTrendChartInstance = echarts.init(errorTrendChart.value)
    errorTrendChartInstance.setOption(getErrorTrendChartOption())
  }
  
  // 请求分布图表
  if (requestDistributionChart.value) {
    console.log('初始化请求分布图表')
    requestDistributionChartInstance = echarts.init(requestDistributionChart.value)
    requestDistributionChartInstance.setOption(getRequestDistributionChartOption())
  }
  
  // QPS趋势图表
  if (qpsTrendChart.value) {
    console.log('初始化QPS趋势图表')
    qpsTrendChartInstance = echarts.init(qpsTrendChart.value)
    qpsTrendChartInstance.setOption(getQpsTrendChartOption())
  }
  
  console.log('图表初始化完成')
}

// 服务延迟分布图表配置
const getLatencyDistributionChartOption = (services?: any[]) => {
  const defaultServices = ['jairouter', 'chat-service', 'embedding-service', 'rerank-service']
  const defaultData = [125, 180, 210, 85]
  
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const data = params[0]
        return `${data.name}<br/>平均延迟: ${data.value}ms`
      }
    },
    xAxis: {
      type: 'category',
      data: services ? services.map(s => s.name) : defaultServices,
      axisLabel: {
        rotate: 45,
        fontSize: 12
      }
    },
    yAxis: {
      type: 'value',
      name: '平均延迟(ms)',
      nameTextStyle: {
        fontSize: 12
      }
    },
    series: [
      {
        name: '平均延迟',
        type: 'bar',
        data: services ? services.map(s => Math.round(s.avgLatency)) : defaultData,
        itemStyle: {
          color: '#409eff'
        },
        label: {
          show: true,
          position: 'top',
          formatter: '{c}ms'
        }
      }
    ]
  }
}

// 延迟趋势图表配置
const getLatencyTrendChartOption = () => {
  return {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00', '24:00']
    },
    yAxis: {
      type: 'value',
      name: '延迟(ms)'
    },
    series: [
      {
        name: 'P50',
        data: [80, 90, 85, 75, 80, 95, 85],
        type: 'line',
        smooth: true
      },
      {
        name: 'P95',
        data: [200, 220, 210, 180, 190, 240, 210],
        type: 'line',
        smooth: true
      },
      {
        name: 'P99',
        data: [350, 380, 360, 320, 340, 420, 360],
        type: 'line',
        smooth: true
      }
    ]
  }
}

// 错误率分布图表配置
const getErrorRateChartOption = (services?: any[]) => {
  const defaultServices = ['jairouter', 'chat-service', 'embedding-service', 'rerank-service']
  const defaultData = [2.5, 1.8, 3.2, 0.5]
  
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const data = params[0]
        return `${data.name}<br/>错误率: ${data.value}%`
      }
    },
    xAxis: {
      type: 'category',
      data: services ? services.map(s => s.name) : defaultServices,
      axisLabel: {
        rotate: 45,
        fontSize: 12
      }
    },
    yAxis: {
      type: 'value',
      name: '错误率(%)',
      nameTextStyle: {
        fontSize: 12
      }
    },
    series: [
      {
        name: '错误率',
        type: 'bar',
        data: services ? services.map(s => Math.round(s.errorRate * 100) / 100) : defaultData,
        itemStyle: {
          color: '#f56c6c'
        },
        label: {
          show: true,
          position: 'top',
          formatter: '{c}%'
        }
      }
    ]
  }
}

// 错误趋势图表配置
const getErrorTrendChartOption = () => {
  return {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00', '24:00']
    },
    yAxis: {
      type: 'value',
      name: '错误数'
    },
    series: [
      {
        name: '错误数',
        data: [5, 8, 6, 3, 4, 12, 7],
        type: 'line',
        smooth: true,
        itemStyle: {
          color: '#f56c6c'
        }
      }
    ]
  }
}

// 请求量分布图表配置
const getRequestDistributionChartOption = (services?: any[]) => {
  const defaultServices = ['jairouter', 'chat-service', 'embedding-service', 'rerank-service']
  const defaultData = [1200, 800, 300, 150]
  
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const data = params[0]
        return `${data.name}<br/>请求数: ${data.value}`
      }
    },
    xAxis: {
      type: 'category',
      data: services ? services.map(s => s.name) : defaultServices,
      axisLabel: {
        rotate: 45,
        fontSize: 12
      }
    },
    yAxis: {
      type: 'value',
      name: '请求数',
      nameTextStyle: {
        fontSize: 12
      }
    },
    series: [
      {
        name: '请求数',
        type: 'bar',
        data: services ? services.map(s => s.requestCount) : defaultData,
        itemStyle: {
          color: '#67c23a'
        },
        label: {
          show: true,
          position: 'top',
          formatter: '{c}'
        }
      }
    ]
  }
}

// QPS趋势图表配置
const getQpsTrendChartOption = () => {
  return {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00', '24:00']
    },
    yAxis: {
      type: 'value',
      name: 'QPS'
    },
    series: [
      {
        name: 'QPS',
        data: [15, 22, 18, 12, 14, 28, 20],
        type: 'line',
        smooth: true,
        itemStyle: {
          color: '#e6a23c'
        }
      }
    ]
  }
}

// 时间范围变更
const handleTimeRangeChange = () => {
  loadPerformanceData()
  ElMessage.success('时间范围已更新')
}

// 刷新
const handleRefresh = () => {
  loadPerformanceData()
  loadRealTimeMetrics()
  ElMessage.success('数据已刷新')
}

// 处理性能优化操作
const handleOptimizationAction = async (command: string) => {
  try {
    let response
    let message = ''
    
    switch (command) {
      case 'optimize':
        response = await triggerOptimization()
        message = '性能优化已触发'
        break
      case 'gc':
        response = await triggerGarbageCollection()
        message = '垃圾回收已执行'
        break
      case 'flush':
        response = await flushProcessingBuffer()
        message = '缓冲区已刷新'
        break
      case 'memory-check':
        response = await performMemoryCheck()
        message = '内存检查已完成'
        break
      default:
        return
    }
    
    console.log('优化操作结果:', response)
    ElMessage.success(message)
    
    // 延迟刷新数据以查看优化效果
    setTimeout(() => {
      loadRealTimeMetrics()
    }, 2000)
    
  } catch (error) {
    console.error('执行优化操作失败:', error)
    ElMessage.error('执行优化操作失败')
  }
}

// 加载性能数据
const loadPerformanceData = async () => {
  try {
    // 1. 加载服务统计数据
    const serviceResponse = await getServiceStats()
    const serviceData = serviceResponse.data?.data || serviceResponse.data || serviceResponse
    console.log('服务统计数据:', serviceData)

    let services: any[] = []
    if (Array.isArray(serviceData)) {
      services = serviceData.map((service: any) => ({
        name: service.name || 'Unknown Service',
        avgLatency: service.avgDuration || 0,
        p95Latency: service.p95Duration || 0,
        p99Latency: service.p99Duration || 0,
        errorRate: service.errorRate || 0,
        requestCount: service.traces || 0,
        errorCount: service.errors || 0
      }))
    }

    // 2. 加载最近的追踪数据用于分析
    const tracesResponse = await getRecentTraces(100) // 获取更多数据用于分析
    const tracesData = tracesResponse.data?.data || tracesResponse.data || tracesResponse
    console.log('最近追踪数据:', tracesData)

    let timeSeriesData: any[] = []
    
    if (Array.isArray(tracesData) && tracesData.length > 0) {
      // 分析最慢的追踪
      const sortedByDuration = [...tracesData]
        .filter(trace => trace.duration > 0)
        .sort((a, b) => b.duration - a.duration)
        .slice(0, 10)

      slowTraces.value = sortedByDuration.map((trace: any) => ({
        traceId: trace.traceId,
        service: trace.serviceName,
        operation: trace.operationName,
        duration: Math.round(trace.duration),
        startTime: formatTime(trace.startTime),
        hasError: trace.hasError
      }))

      // 分析错误数据
      const errorTraces = tracesData.filter((trace: any) => trace.hasError)
      const errorStats = analyzeErrors(errorTraces)
      commonErrors.value = errorStats

      // 生成时间序列数据
      timeSeriesData = generateTimeSeriesFromTraces(tracesData)
      
      ElMessage.success(`已加载 ${tracesData.length} 条追踪数据`)
    } else {
      // 如果没有真实数据，使用模拟数据
      console.log('没有真实追踪数据，使用模拟数据')
      const mockData = generateMockData()
      services = mockData.services
      timeSeriesData = mockData.timeSeriesData
      slowTraces.value = mockData.slowTraces
      commonErrors.value = mockData.commonErrors
      
      ElMessage.info('使用模拟数据进行展示')
    }

    // 更新图表数据
    updateChartsWithRealData(services, timeSeriesData)

    // 3. 加载性能统计数据
    try {
      const statsResponse = await getPerformanceStats()
      const statsData = statsResponse.data?.data || statsResponse.data || statsResponse
      console.log('性能统计数据:', statsData)
    } catch (statsError) {
      console.log('性能统计数据加载失败，跳过')
    }

    // 4. 加载仪表板指标
    try {
      const dashboardResponse = await getDashboardMetrics()
      const dashboardData = dashboardResponse.data?.data || dashboardResponse.data || dashboardResponse
      console.log('仪表板数据:', dashboardData)
    } catch (dashboardError) {
      console.log('仪表板数据加载失败，跳过')
    }

  } catch (error) {
    console.error('加载性能数据失败:', error)
    ElMessage.error('加载性能数据失败，使用模拟数据')
    
    // 使用模拟数据作为后备
    loadMockData()
  }
}

// 分析错误数据
const analyzeErrors = (errorTraces: any[]) => {
  const errorMap = new Map()
  
  errorTraces.forEach(trace => {
    const key = `${trace.serviceName}-${trace.operationName}`
    if (!errorMap.has(key)) {
      errorMap.set(key, {
        errorType: 'ServiceError', // 简化的错误类型
        service: trace.serviceName,
        operation: trace.operationName,
        count: 0,
        lastOccurrence: trace.startTime
      })
    }
    
    const error = errorMap.get(key)
    error.count++
    if (new Date(trace.startTime) > new Date(error.lastOccurrence)) {
      error.lastOccurrence = trace.startTime
    }
  })
  
  return Array.from(errorMap.values())
    .sort((a: any, b: any) => b.count - a.count)
    .slice(0, 10)
    .map(error => ({
      ...error,
      lastOccurrence: formatTime(error.lastOccurrence)
    }))
}

// 从追踪数据生成时间序列
const generateTimeSeriesFromTraces = (traces: any[]) => {
  // 按小时分组统计
  const hourlyStats = new Map()
  
  traces.forEach(trace => {
    const hour = new Date(trace.startTime).getHours()
    const hourKey = `${hour.toString().padStart(2, '0')}:00`
    
    if (!hourlyStats.has(hourKey)) {
      hourlyStats.set(hourKey, {
        hour: hourKey,
        totalRequests: 0,
        totalErrors: 0,
        totalDuration: 0,
        durations: []
      })
    }
    
    const stats = hourlyStats.get(hourKey)
    stats.totalRequests++
    if (trace.hasError) stats.totalErrors++
    stats.totalDuration += trace.duration
    stats.durations.push(trace.duration)
  })
  
  // 计算统计指标
  const timeSeriesData = Array.from(hourlyStats.values()).map(stats => {
    const sortedDurations = stats.durations.sort((a: number, b: number) => a - b)
    const p50Index = Math.floor(sortedDurations.length * 0.5)
    const p95Index = Math.floor(sortedDurations.length * 0.95)
    const p99Index = Math.floor(sortedDurations.length * 0.99)
    
    return {
      hour: stats.hour,
      qps: Math.round(stats.totalRequests / 3600 * 1000), // 简化的QPS计算
      errorCount: stats.totalErrors,
      avgDuration: stats.totalRequests > 0 ? stats.totalDuration / stats.totalRequests : 0,
      p50Duration: sortedDurations[p50Index] || 0,
      p95Duration: sortedDurations[p95Index] || 0,
      p99Duration: sortedDurations[p99Index] || 0
    }
  }).sort((a, b) => a.hour.localeCompare(b.hour))
  
  return timeSeriesData
}

// 使用真实数据更新图表
const updateChartsWithRealData = (services: any[], timeSeriesData: any[]) => {
  console.log('更新图表数据:', { services, timeSeriesData })
  
  // 更新延迟分布图表
  if (latencyDistributionChartInstance) {
    latencyDistributionChartInstance.setOption(getLatencyDistributionChartOption(services), true)
  }

  // 更新延迟趋势图表
  if (latencyTrendChartInstance && timeSeriesData.length > 0) {
    const hours = timeSeriesData.map(d => d.hour)
    const p50Data = timeSeriesData.map(d => Math.round(d.p50Duration))
    const p95Data = timeSeriesData.map(d => Math.round(d.p95Duration))
    const p99Data = timeSeriesData.map(d => Math.round(d.p99Duration))
    
    latencyTrendChartInstance.setOption({
      xAxis: {
        data: hours
      },
      series: [
        { name: 'P50', data: p50Data },
        { name: 'P95', data: p95Data },
        { name: 'P99', data: p99Data }
      ]
    }, true)
  }

  // 更新错误率图表
  if (errorRateChartInstance) {
    errorRateChartInstance.setOption(getErrorRateChartOption(services), true)
  }

  // 更新错误趋势图表
  if (errorTrendChartInstance && timeSeriesData.length > 0) {
    const hours = timeSeriesData.map(d => d.hour)
    const errorCounts = timeSeriesData.map(d => d.errorCount)
    
    errorTrendChartInstance.setOption({
      xAxis: {
        data: hours
      },
      series: [{
        data: errorCounts
      }]
    }, true)
  }

  // 更新请求分布图表
  if (requestDistributionChartInstance) {
    requestDistributionChartInstance.setOption(getRequestDistributionChartOption(services), true)
  }

  // 更新QPS趋势图表
  if (qpsTrendChartInstance && timeSeriesData.length > 0) {
    const hours = timeSeriesData.map(d => d.hour)
    const qpsData = timeSeriesData.map(d => d.qps)
    
    qpsTrendChartInstance.setOption({
      xAxis: {
        data: hours
      },
      series: [{
        data: qpsData
      }]
    }, true)
  }
}

// 生成模拟数据
const generateMockData = () => {
  const services = [
    { name: 'jairouter', avgLatency: 125, p95Latency: 250, errorRate: 2.5, requestCount: 1200, errorCount: 30 },
    { name: 'chat-service', avgLatency: 180, p95Latency: 350, errorRate: 1.8, requestCount: 800, errorCount: 14 },
    { name: 'embedding-service', avgLatency: 210, p95Latency: 420, errorRate: 3.2, requestCount: 300, errorCount: 10 },
    { name: 'rerank-service', avgLatency: 85, p95Latency: 180, errorRate: 0.5, requestCount: 150, errorCount: 1 }
  ]
  
  const timeSeriesData = Array.from({ length: 24 }, (_, i) => ({
    hour: `${i.toString().padStart(2, '0')}:00`,
    qps: Math.floor(Math.random() * 30) + 10,
    errorCount: Math.floor(Math.random() * 10),
    p50Duration: Math.floor(Math.random() * 100) + 50,
    p95Duration: Math.floor(Math.random() * 200) + 150,
    p99Duration: Math.floor(Math.random() * 300) + 250
  }))

  const slowTraces = [
    { 
      traceId: 'trace-001-mock', 
      service: 'chat-service', 
      operation: 'POST /v1/chat/completions', 
      duration: 5200, 
      startTime: formatTime(new Date(Date.now() - 3600000).toISOString()),
      hasError: false
    },
    { 
      traceId: 'trace-002-mock', 
      service: 'embedding-service', 
      operation: 'POST /v1/embeddings', 
      duration: 4800, 
      startTime: formatTime(new Date(Date.now() - 7200000).toISOString()),
      hasError: true
    },
    { 
      traceId: 'trace-003-mock', 
      service: 'rerank-service', 
      operation: 'POST /v1/rerank', 
      duration: 3900, 
      startTime: formatTime(new Date(Date.now() - 10800000).toISOString()),
      hasError: false
    }
  ]

  const commonErrors = [
    { 
      errorType: 'TimeoutException', 
      service: 'chat-service', 
      operation: 'POST /v1/chat/completions', 
      count: 25, 
      lastOccurrence: formatTime(new Date(Date.now() - 1800000).toISOString())
    },
    { 
      errorType: 'DownstreamServiceException', 
      service: 'embedding-service', 
      operation: 'POST /v1/embeddings', 
      count: 18, 
      lastOccurrence: formatTime(new Date(Date.now() - 3600000).toISOString())
    },
    { 
      errorType: 'AuthenticationException', 
      service: 'jairouter', 
      operation: 'auth/validate', 
      count: 12, 
      lastOccurrence: formatTime(new Date(Date.now() - 5400000).toISOString())
    }
  ]

  return { services, timeSeriesData, slowTraces, commonErrors }
}

// 加载模拟数据作为后备
const loadMockData = () => {
  const mockData = generateMockData()
  
  // 设置模拟数据
  slowTraces.value = mockData.slowTraces
  commonErrors.value = mockData.commonErrors
  
  // 更新图表
  updateChartsWithRealData(mockData.services, mockData.timeSeriesData)
}

// 查看追踪详情
const handleViewTrace = async (row: any) => {
  traceDialogVisible.value = true
  
  try {
    console.log('查看追踪详情:', row.traceId)
    
    // 获取追踪链路详情
    const response = await getTraceChain(row.traceId)
    const traceData = response.data?.data || response.data || response
    
    console.log('追踪链路数据:', traceData)
    
    // 延迟渲染图表
    setTimeout(() => {
      if (traceDetailChart.value) {
        if (traceDetailChartInstance) {
          traceDetailChartInstance.dispose()
        }
        traceDetailChartInstance = echarts.init(traceDetailChart.value)
        traceDetailChartInstance.setOption(getTraceDetailChartOption(traceData))
      }
    }, 100)
  } catch (error) {
    console.error('获取追踪详情失败:', error)
    ElMessage.error('获取追踪详情失败')
    
    // 使用默认图表
    setTimeout(() => {
      if (traceDetailChart.value) {
        if (traceDetailChartInstance) {
          traceDetailChartInstance.dispose()
        }
        traceDetailChartInstance = echarts.init(traceDetailChart.value)
        traceDetailChartInstance.setOption(getTraceDetailChartOption())
      }
    }, 100)
  }
}

// 加载实时性能监控数据
const loadRealTimeMetrics = async () => {
  try {
    // 获取处理统计
    const processingResponse = await getProcessingStats()
    const processingData = processingResponse.data?.data || processingResponse.data || processingResponse
    console.log('处理统计:', processingData)

    // 获取内存统计
    const memoryResponse = await getMemoryStats()
    const memoryData = memoryResponse.data?.data || memoryResponse.data || memoryResponse
    console.log('内存统计:', memoryData)

    // 检测性能瓶颈
    const bottlenecksResponse = await detectBottlenecks()
    const bottlenecksData = bottlenecksResponse.data?.data || bottlenecksResponse.data || bottlenecksResponse
    console.log('性能瓶颈:', bottlenecksData)

    // 获取优化建议
    const suggestionsResponse = await getOptimizationSuggestions()
    const suggestionsData = suggestionsResponse.data?.data || suggestionsResponse.data || suggestionsResponse
    console.log('优化建议:', suggestionsData)

    // 更新界面显示
    updatePerformanceIndicators(processingData, memoryData, bottlenecksData, suggestionsData)

  } catch (error) {
    console.error('加载实时监控数据失败:', error)
  }
}

// 更新性能指标显示
const updatePerformanceIndicators = (processing: any, memory: any, bottlenecks: any[], suggestions: any[]) => {
  // 更新内存指标
  if (memory) {
    memoryUsage.value = Math.round(memory.heapUsageRatio * 100) || 0
    memoryPressure.value = memory.pressureLevel || 'LOW'
  }

  // 更新处理指标
  if (processing) {
    processingQueueSize.value = processing.queueSize || 0
    successRate.value = Math.round(processing.successRate * 100) || 100
  }

  // 更新瓶颈指标
  if (bottlenecks) {
    activeBottlenecks.value = bottlenecks.length || 0
    
    const criticalBottlenecks = bottlenecks.filter(b => 
      b.severity === 'CRITICAL' || b.severity === 'HIGH'
    )
    
    if (criticalBottlenecks.length > 0) {
      ElMessage.warning(`检测到 ${criticalBottlenecks.length} 个严重性能瓶颈`)
    }
  }

  // 内存压力告警
  if (memory && memory.pressureLevel === 'HIGH') {
    ElMessage.warning('内存使用率较高，建议进行垃圾回收')
  } else if (memory && memory.pressureLevel === 'CRITICAL') {
    ElMessage.error('内存使用率过高，请立即进行垃圾回收')
  }

  // 处理队列告警
  if (processing && processing.queueSize > 500) {
    ElMessage.warning('处理队列积压严重，建议刷新缓冲区')
  }
}

// 追踪详情图表配置
const getTraceDetailChartOption = (traceData?: any) => {
  if (!traceData || !traceData.spans || traceData.spans.length === 0) {
    // 默认数据
    return {
      title: {
        text: '追踪链路时序图',
        left: 'center'
      },
      tooltip: {
        formatter: '暂无追踪数据'
      },
      xAxis: {
        type: 'value',
        name: '时间 (ms)'
      },
      yAxis: {
        type: 'category',
        data: ['暂无数据']
      },
      series: []
    }
  }

  // 按开始时间排序 Span
  const sortedSpans = [...traceData.spans].sort((a: any, b: any) => 
    new Date(a.startTime).getTime() - new Date(b.startTime).getTime()
  )

  // 计算基准时间
  const baseTime = new Date(sortedSpans[0].startTime).getTime()

  // 创建甘特图数据
  const ganttData = sortedSpans.map((span: any, index: number) => {
    const startTime = new Date(span.startTime).getTime()
    const relativeStart = startTime - baseTime
    const duration = span.duration
    
    return {
      name: span.operationName,
      spanId: span.spanId,
      value: [
        index,
        relativeStart,
        relativeStart + duration,
        duration
      ],
      itemStyle: {
        color: span.error ? '#f56c6c' : (span.operationName.includes('process') ? '#67c23a' : '#409eff'),
        borderColor: '#fff',
        borderWidth: 1
      }
    }
  })

  const categories = sortedSpans.map((span: any) => {
    const shortName = span.operationName.length > 30 
      ? span.operationName.substring(0, 30) + '...' 
      : span.operationName
    return shortName
  })

  const maxDuration = Math.max(...ganttData.map(item => item.value[2]))

  return {
    title: {
      text: `追踪链路时序图 (${sortedSpans.length} 个 Span)`,
      left: 'center',
      textStyle: {
        fontSize: 16,
        fontWeight: 'bold'
      }
    },
    tooltip: {
      formatter: (params: any) => {
        const data = params.data
        const duration = data.value[3]
        const startOffset = data.value[1]
        const endOffset = data.value[2]
        
        return `
          <div style="max-width: 300px;">
            <div style="font-weight: bold; margin-bottom: 8px;">${data.name}</div>
            <div style="margin-bottom: 4px;"><strong>Span ID:</strong> ${data.spanId.substring(0, 16)}...</div>
            <div style="margin-bottom: 4px;"><strong>开始偏移:</strong> +${Math.round(startOffset)}ms</div>
            <div style="margin-bottom: 4px;"><strong>结束偏移:</strong> +${Math.round(endOffset)}ms</div>
            <div><strong>持续时间:</strong> ${Math.round(duration)}ms</div>
          </div>
        `
      }
    },
    grid: {
      left: '25%',
      right: '10%',
      top: '15%',
      bottom: '15%'
    },
    xAxis: {
      type: 'value',
      name: '时间偏移 (ms)',
      nameLocation: 'middle',
      nameGap: 30,
      min: 0,
      max: maxDuration * 1.1,
      axisLabel: {
        formatter: (value: number) => `+${Math.round(value)}ms`
      },
      splitLine: {
        show: true,
        lineStyle: {
          color: '#e0e6ed',
          type: 'dashed'
        }
      }
    },
    yAxis: {
      type: 'category',
      data: categories,
      axisLabel: {
        fontSize: 11,
        width: 200,
        overflow: 'truncate'
      },
      axisTick: {
        show: false
      },
      axisLine: {
        show: false
      }
    },
    series: [
      {
        type: 'custom',
        renderItem: (params: any, api: any) => {
          const categoryIndex = api.value(0)
          const start = api.coord([api.value(1), categoryIndex])
          const end = api.coord([api.value(2), categoryIndex])
          const height = api.size([0, 1])[1] * 0.7
          
          const rectShape = {
            x: start[0],
            y: start[1] - height / 2,
            width: Math.max(end[0] - start[0], 2),
            height: height
          }
          
          return {
            type: 'rect',
            shape: rectShape,
            style: {
              ...api.style(),
              shadowBlur: 3,
              shadowColor: 'rgba(0, 0, 0, 0.2)',
              shadowOffsetY: 2
            }
          }
        },
        data: ganttData
      }
    ]
  }
}

// 窗口大小变化时重置图表
const handleResize = () => {
  latencyDistributionChartInstance?.resize()
  latencyTrendChartInstance?.resize()
  errorRateChartInstance?.resize()
  errorTrendChartInstance?.resize()
  requestDistributionChartInstance?.resize()
  qpsTrendChartInstance?.resize()
  traceDetailChartInstance?.resize()
}

// 实时监控定时器
let realTimeMonitoringInterval: NodeJS.Timeout | null = null

// 组件挂载时初始化
onMounted(async () => {
  // 等待DOM渲染完成
  await new Promise(resolve => setTimeout(resolve, 100))
  
  initCharts()
  window.addEventListener('resize', handleResize)
  
  // 加载数据
  await loadPerformanceData()
  
  // 启动实时监控（每30秒更新一次）
  realTimeMonitoringInterval = setInterval(() => {
    loadRealTimeMetrics()
  }, 30000)
  
  // 立即执行一次实时监控
  loadRealTimeMetrics()
})

// 组件卸载前清理
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  
  // 清理实时监控定时器
  if (realTimeMonitoringInterval) {
    clearInterval(realTimeMonitoringInterval)
    realTimeMonitoringInterval = null
  }
  
  // 安全地清理图表实例
  try {
    const instances = [
      latencyDistributionChartInstance,
      latencyTrendChartInstance,
      errorRateChartInstance,
      errorTrendChartInstance,
      requestDistributionChartInstance,
      qpsTrendChartInstance,
      traceDetailChartInstance
    ]
    
    instances.forEach(instance => {
      if (instance && !instance.isDisposed()) {
        instance.dispose()
      }
    })
  } catch (error) {
    console.warn('清理图表实例时出错:', error)
  }
  
  // 重置实例引用
  latencyDistributionChartInstance = null
  latencyTrendChartInstance = null
  errorRateChartInstance = null
  errorTrendChartInstance = null
  requestDistributionChartInstance = null
  qpsTrendChartInstance = null
  traceDetailChartInstance = null
})
</script>

<style scoped>
.tracing-performance {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.chart-container {
  height: 300px;
}

.trace-detail-chart {
  height: 400px;
}

.top-slow-card, .top-errors-card {
  margin-top: 20px;
}

/* 高延迟警告样式 */
.high-latency {
  color: #e6a23c;
  font-weight: bold;
}

/* 图表容器边框 */
.chart-container {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
}

/* 表格样式优化 */
:deep(.el-table .el-table__row:hover > td) {
  background-color: #f0f9ff !important;
}

/* 标签页样式 */
:deep(.el-tabs__item) {
  font-weight: 500;
}

:deep(.el-tabs__item.is-active) {
  color: #409eff;
  font-weight: 600;
}

/* 卡片头部样式 */
:deep(.el-card__header) {
  background-color: #f8f9fa;
  border-bottom: 1px solid #ebeef5;
}

/* 对话框样式 */
:deep(.el-dialog__header) {
  background-color: #f8f9fa;
  border-bottom: 1px solid #ebeef5;
}

/* 性能指标样式 */
.performance-indicators {
  margin-bottom: 20px;
}

.indicator-card {
  text-align: center;
  border: 1px solid #ebeef5;
  transition: all 0.3s ease;
}

.indicator-card:hover {
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.indicator {
  padding: 10px;
}

.indicator-value {
  font-size: 28px;
  font-weight: bold;
  color: #67c23a;
  margin-bottom: 5px;
  transition: color 0.3s ease;
}

.indicator-value.warning {
  color: #e6a23c;
}

.indicator-value.danger {
  color: #f56c6c;
}

.indicator-label {
  font-size: 14px;
  color: #909399;
  font-weight: 500;
}

/* 指标卡片动画 */
.indicator-card :deep(.el-card__body) {
  padding: 15px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .performance-indicators .el-col {
    margin-bottom: 10px;
  }
  
  .indicator-value {
    font-size: 24px;
  }
}
</style>