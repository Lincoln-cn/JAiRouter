<template>
  <div class="tracing-performance">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>性能分析</span>
          <div class="header-actions">
            <el-date-picker v-model="timeRange" type="datetimerange" range-separator="至" start-placeholder="开始时间"
              end-placeholder="结束时间" format="YYYY-MM-DD HH:mm:ss" value-format="YYYY-MM-DD HH:mm:ss"
              @change="handleTimeRangeChange" />
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
              <div class="indicator-value"
                :class="{ 'warning': memoryPressure === 'HIGH', 'danger': memoryPressure === 'CRITICAL' }">
                {{ memoryUsage }}%
              </div>
              <div class="indicator-label">内存使用率</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="indicator-card">
            <div class="indicator">
              <div class="indicator-value"
                :class="{ 'warning': processingQueueSize > 100, 'danger': processingQueueSize > 500 }">
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
          <el-card class="indicator-card" @click="showBottleneckDetails">
            <div class="indicator">
              <div class="indicator-value"
                :class="{ 'warning': activeBottlenecks > 0, 'danger': activeBottlenecks > 3 }">
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
            <el-table :data="commonErrors" style="width: 100%" empty-text="暂无错误数据 - 所有服务运行正常 🎉">
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
                    <span>追踪数量分布</span>
                    <el-tooltip content="显示各服务的追踪记录数量" placement="top">
                      <el-icon>
                        <QuestionFilled />
                      </el-icon>
                    </el-tooltip>
                  </div>
                </template>
                <div ref="requestDistributionChart" class="chart-container"></div>
              </el-card>
            </el-col>
            <el-col :span="12">
              <el-card>
                <template #header>
                  <div class="card-header">
                    <span>服务活跃度</span>
                    <el-tooltip content="基于追踪数据计算的服务活跃度趋势" placement="top">
                      <el-icon>
                        <QuestionFilled />
                      </el-icon>
                    </el-tooltip>
                  </div>
                </template>
                <div ref="qpsTrendChart" class="chart-container"></div>
              </el-card>
            </el-col>
          </el-row>

          <!-- 添加服务统计表格 -->
          <el-card class="service-stats-card" style="margin-top: 20px;">
            <template #header>
              <div class="card-header">
                <span>服务统计详情</span>
              </div>
            </template>
            <el-table :data="serviceStatsTable" style="width: 100%" empty-text="暂无服务统计数据">
              <el-table-column prop="name" label="服务名称" width="150" />
              <el-table-column prop="requestCount" label="追踪数量" width="100" sortable>
                <template #default="{ row }">
                  <el-tag :type="row.requestCount > 0 ? 'success' : 'info'" size="small">
                    {{ row.requestCount }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="avgLatency" label="平均延迟(ms)" width="120" sortable>
                <template #default="{ row }">
                  <span :class="{ 'high-latency': row.avgLatency > 1000 }">
                    {{ Math.round(row.avgLatency) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="errorCount" label="错误数量" width="100" sortable>
                <template #default="{ row }">
                  <el-tag :type="row.errorCount > 0 ? 'danger' : 'success'" size="small">
                    {{ row.errorCount }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="errorRate" label="错误率(%)" width="100" sortable>
                <template #default="{ row }">
                  <span :class="{ 'high-error-rate': row.errorRate > 5 }">
                    {{ row.errorRate }}%
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="p95Latency" label="P95延迟(ms)" width="120" sortable />
              <el-table-column prop="p99Latency" label="P99延迟(ms)" width="120" sortable />
            </el-table>
          </el-card>
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
    
    <!-- 性能瓶颈详情对话框 -->
    <el-dialog v-model="bottleneckDialogVisible" title="性能瓶颈详情" width="800px">
      <el-table :data="bottleneckDetails" style="width: 100%" empty-text="暂无性能瓶颈数据">
        <el-table-column prop="type" label="瓶颈类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getBottleneckTypeTag(row.type)">
              {{ getBottleneckTypeLabel(row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column prop="severity" label="严重程度" width="100">
          <template #default="{ row }">
            <el-tag :type="getSeverityTag(row.severity)">
              {{ getSeverityLabel(row.severity) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优化建议" width="100">
          <template #default="{ row }">
            <el-button size="small" @click="showOptimizationSuggestions(row)">查看建议</el-button>
          </template>
        </el-table-column>
      </el-table>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="bottleneckDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
    
    <!-- 优化建议对话框 -->
    <el-dialog v-model="suggestionsDialogVisible" title="优化建议" width="600px">
      <el-card v-for="(suggestion, index) in currentSuggestions" :key="index" class="suggestion-card">
        <div class="suggestion-content">
          <div class="suggestion-title">{{ suggestion.action }}</div>
          <div class="suggestion-description">{{ suggestion.description }}</div>
          <el-tag :type="getPriorityTag(suggestion.priority)" size="small" class="suggestion-priority">
            {{ getPriorityLabel(suggestion.priority) }}
          </el-tag>
        </div>
      </el-card>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="suggestionsDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { ArrowDown, QuestionFilled } from '@element-plus/icons-vue'
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
  performMemoryCheck,
  getLatencyAnalysis,
  getErrorAnalysis,
  getThroughputAnalysis
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

// 服务统计表格数据
const serviceStatsTable = ref<any[]>([])

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

// 性能瓶颈详情对话框
const bottleneckDialogVisible = ref(false)
const bottleneckDetails = ref<any[]>([])

// 优化建议对话框
const suggestionsDialogVisible = ref(false)
const currentSuggestions = ref<any[]>([])

// 性能指标
const memoryUsage = ref(0)
const memoryPressure = ref('LOW')
const processingQueueSize = ref(0)
const successRate = ref(100)
const activeBottlenecks = ref(0)

watch(activeTab, (tab) => {
  setTimeout(() => {
    handleResize()
    refreshChartForTab(tab)
  }, 100)
})

function refreshChartForTab(tab: string) {
  if (tab === 'latency') {
    latencyDistributionChartInstance?.resize()
    latencyTrendChartInstance?.resize()
  } else if (tab === 'error') {
    errorRateChartInstance?.resize()
    errorTrendChartInstance?.resize()
    if (errorRateChartInstance && serviceStatsTable.value?.length) {
      errorRateChartInstance.setOption(getErrorRateChartOption(serviceStatsTable.value), true)
    }
    if (errorTrendChartInstance) {
      errorTrendChartInstance.setOption(getErrorTrendChartOption(), true)
    }
  } else if (tab === 'throughput') {
    requestDistributionChartInstance?.resize()
    qpsTrendChartInstance?.resize()
    if (requestDistributionChartInstance && serviceStatsTable.value?.length) {
      requestDistributionChartInstance.setOption(getRequestDistributionChartOption(serviceStatsTable.value), true)
    }
    if (qpsTrendChartInstance) {
      qpsTrendChartInstance.setOption(getQpsTrendChartOption([]), true)
    }
  }
}

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
  if (!services || services.length === 0) {
    return {
      title: {
        text: '暂无服务数据',
        left: 'center',
        top: 'middle',
        textStyle: {
          color: '#999',
          fontSize: 14
        }
      },
      xAxis: {
        type: 'category',
        data: []
      },
      yAxis: {
        type: 'value',
        name: '平均延迟(ms)'
      },
      series: []
    }
  }

  const latencies = services.map(s => Math.round(s.avgLatency || 0))
  const hasData = latencies.some(latency => latency > 0)

  return {
    title: hasData ? null : {
      text: '暂无延迟数据\n请发送一些请求以生成数据',
      left: 'center',
      top: 'middle',
      textStyle: {
        color: '#999',
        fontSize: 14
      }
    },
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        if (!params || params.length === 0) return ''
        const data = params[0]
        return `${data.name}<br/>平均延迟: ${data.value}ms`
      }
    },
    xAxis: {
      type: 'category',
      data: services.map(s => s.name),
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
      },
      min: 0
    },
    series: [
      {
        name: '平均延迟',
        type: 'bar',
        data: latencies,
        itemStyle: {
          color: hasData ? '#409eff' : '#e0e0e0'
        },
        label: {
          show: hasData,
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
    title: {
      text: '暂无时序数据',
      left: 'center',
      top: 'middle',
      textStyle: {
        color: '#999',
        fontSize: 14
      }
    },
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: []
    },
    yAxis: {
      type: 'value',
      name: '延迟(ms)'
    },
    series: []
  }
}

// 错误率分布图表配置
const getErrorRateChartOption = (services?: any[]) => {
  if (!services || services.length === 0) {
    return {
      title: {
        text: '暂无服务数据',
        left: 'center',
        top: 'middle',
        textStyle: {
          color: '#999',
          fontSize: 14
        }
      },
      xAxis: {
        type: 'category',
        data: []
      },
      yAxis: {
        type: 'value',
        name: '错误率(%)'
      },
      series: []
    }
  }

  const errorRates = services.map(s => s.errorRate || 0) // 后端已经是百分比格式
  const hasErrors = services.some(s => (s.errorCount || 0) > 0 || (s.errorRate || 0) > 0)

  return {
    title: hasErrors ? null : {
      text: '暂无错误数据\n所有服务运行正常 🎉',
      left: 'center',
      top: 'middle',
      textStyle: {
        color: '#67c23a',
        fontSize: 14
      }
    },
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        if (!params || params.length === 0) return ''
        const data = params[0]
        return `${data.name}<br/>错误率: ${data.value}%`
      }
    },
    xAxis: {
      type: 'category',
      data: services.map(s => s.name),
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
      },
      min: 0,
      max: hasErrors ? undefined : 5
    },
    series: [
      {
        name: '错误率',
        type: 'bar',
        data: errorRates,
        itemStyle: {
          color: hasErrors ? '#f56c6c' : '#67c23a'
        },
        label: {
          show: hasErrors,
          position: 'top',
          formatter: '{c}%'
        }
      }
    ]
  }
}

// 错误趋势图表配置
const getErrorTrendChartOption = (timeSeriesData?: any[]) => {
  let xData: string[] = []
  let yData: number[] = []
  if (timeSeriesData && timeSeriesData.length > 0) {
    xData = timeSeriesData.map(d => d.hour)
    yData = timeSeriesData.map(d => d.errorCount)
  } else {
    // 没有数据，给当前小时和0
    const nowHour = `${new Date().getHours().toString().padStart(2, '0')  }:00`
    xData = [nowHour]
    yData = [0]
  }
  return {
    title: null,
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: xData },
    yAxis: { type: 'value', name: '错误数' },
    series: [{
      name: '错误数',
      data: yData,
      type: 'line',
      smooth: true,
      itemStyle: { color: '#f56c6c' }
    }]
  }
}

// 请求量分布图表配置
const getRequestDistributionChartOption = (services?: any[]) => {
  if (!services || services.length === 0) {
    return {
      title: {
        text: '暂无服务数据',
        left: 'center',
        top: 'middle',
        textStyle: {
          color: '#999',
          fontSize: 14
        }
      },
      xAxis: {
        type: 'category',
        data: []
      },
      yAxis: {
        type: 'value',
        name: '追踪数量'
      },
      series: []
    }
  }

  const requestCounts = services.map(s => s.requestCount || 0)
  const hasData = requestCounts.some(count => count > 0)

  return {
    title: hasData ? null : {
      text: '暂无追踪数据\n请发送一些请求以生成数据',
      left: 'center',
      top: 'middle',
      textStyle: {
        color: '#999',
        fontSize: 14
      }
    },
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        if (!params || params.length === 0) return ''
        const data = params[0]
        return `${data.name}<br/>追踪数量: ${data.value}`
      }
    },
    xAxis: {
      type: 'category',
      data: services.map(s => s.name),
      axisLabel: {
        rotate: 45,
        fontSize: 12
      }
    },
    yAxis: {
      type: 'value',
      name: '追踪数量',
      nameTextStyle: {
        fontSize: 12
      },
      min: 0,
      max: hasData ? undefined : 10
    },
    series: [
      {
        name: '追踪数量',
        type: 'bar',
        data: requestCounts,
        itemStyle: {
          color: hasData ? '#67c23a' : '#e0e0e0'
        },
        label: {
          show: hasData,
          position: 'top',
          formatter: '{c}'
        }
      }
    ]
  }
}

// 服务活跃度图表配置
const getQpsTrendChartOption = (timeSeriesData?: any[]) => {
  let xData: string[] = []
  let yData: number[] = []
  if (timeSeriesData && timeSeriesData.length > 0) {
    xData = timeSeriesData.map(d => d.hour)
    yData = timeSeriesData.map(d => d.totalRequests || d.qps || 0)
  } else {
    // 没有数据，给当前小时和0
    const nowHour = `${new Date().getHours().toString().padStart(2, '0')  }:00`
    xData = [nowHour]
    yData = [0]
  }
  return {
    title: null,
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: xData },
    yAxis: { type: 'value', name: '活跃度' },
    series: [{
      name: '服务活跃度',
      data: yData,
      type: 'line',
      smooth: true,
      itemStyle: { color: '#e6a23c' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(230, 162, 60, 0.3)' },
            { offset: 1, color: 'rgba(230, 162, 60, 0.1)' }
          ]
        }
      }
    }]
  }
}

// 时间范围变更
const handleTimeRangeChange = () => {
  loadPerformanceData()
  ElMessage.success('时间范围已更新')
}

// 刷新
const handleRefresh = async () => {
  try {
    await loadPerformanceData()
    await loadRealTimeMetrics()
    ElMessage.success('数据已刷新')
  } catch (error) {
    console.error('刷新数据失败:', error)
    ElMessage.error('刷新数据失败')
  }
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
      console.log('原始后端服务数据:', serviceData)
      services = serviceData.map((service: any) => ({
        name: service.name || 'Unknown Service',
        avgLatency: service.avgDuration || 0,           // 平均延迟
        p95Latency: service.p95Duration || 0,           // P95延迟
        p99Latency: service.p99Duration || 0,           // P99延迟
        errorRate: service.errorRate || 0,              // 错误率(后端已转换为百分比)
        requestCount: service.traces || 0,              // 追踪数量(用作请求数量)
        errorCount: service.errors || 0,                // 错误数量
        // 添加原始数据用于调试
        _raw: service
      }))
      console.log('适配后的服务数据:', services)

      // 更新服务统计表格数据
      serviceStatsTable.value = services

      // 验证数据有效性
      services.forEach(service => {
        console.log(`服务 ${service.name}:`, {
          追踪数量: service.requestCount,
          平均延迟: `${service.avgLatency  }ms`,
          错误率: `${service.errorRate  }%`,
          错误数量: service.errorCount
        })
      })
    } else {
      console.log('服务数据不是数组格式:', serviceData)
    }

    // 2. 加载最近的追踪数据用于分析
    const tracesResponse = await getRecentTraces(100) // 获取更多数据用于分析
    const tracesData = tracesResponse.data?.data || tracesResponse.data || tracesResponse
    console.log('最近追踪数据:', tracesData)

    // 提取错误追踪数据，供后续使用
    const errorTraces = Array.isArray(tracesData) ? tracesData.filter((trace: any) => trace.hasError) : []
    console.log('总追踪数据:', Array.isArray(tracesData) ? tracesData.length : 0, '错误追踪数据:', errorTraces.length)

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
      const errorStats = analyzeErrors(errorTraces)
      console.log('分析后的错误统计:', errorStats)
      commonErrors.value = errorStats

      // 如果有追踪数据但没有错误，显示成功信息
      if (tracesData.length > 0 && errorTraces.length === 0) {
        console.log('所有追踪都成功，没有错误数据')
        // 可以在这里添加一些模拟错误数据用于测试（仅开发环境）
        if (process.env.NODE_ENV === 'development' && tracesData.length > 5) {
          console.log('开发环境：添加一些模拟错误数据用于测试')
          const mockErrors = [
            {
              errorType: 'TimeoutException',
              service: 'jairouter',
              operation: 'POST /v1/chat/completions',
              count: 2,
              lastOccurrence: formatTime(new Date().toISOString())
            }
          ]
          commonErrors.value = mockErrors
        }
      }

      // 生成时间序列数据
      timeSeriesData = generateTimeSeriesFromTraces(tracesData)

      ElMessage.success(`已加载 ${tracesData.length} 条追踪数据`)
    } else {
      // 没有数据时清空显示
      console.log('没有追踪数据')
      slowTraces.value = []
      commonErrors.value = []
      timeSeriesData = []

      ElMessage.info('暂无追踪数据，请通过API发送一些请求以生成追踪数据')
    }

    // 3. 加载性能分析数据
    try {
      // 加载延迟分析数据
      const latencyResponse = await getLatencyAnalysis()
      // 正确处理响应数据结构
      const latencyRawData = latencyResponse.data?.data || latencyResponse.data
      // 安全检查数据结构
      const latencyData = latencyRawData && typeof latencyRawData === 'object' && 'distribution' in latencyRawData 
        ? latencyRawData as unknown as { distribution: any[]; trend: any[] } 
        : undefined
      console.log('延迟分析数据:', latencyData)
      if (latencyData && latencyData.distribution) {
        // 更新延迟分布图表
        if (latencyDistributionChartInstance) {
          const chartOption = getLatencyDistributionChartOption(latencyData.distribution.map((item: any) => ({
            name: item.service,
            avgLatency: item.avgLatency || 0,
            p95Latency: item.p95Latency || 0,
            p99Latency: item.p99Latency || 0
          })))
          latencyDistributionChartInstance.setOption(chartOption, true)
        }
      }

      // 加载错误分析数据
      const errorResponse = await getErrorAnalysis()
      // 正确处理响应数据结构
      const errorRawData = errorResponse.data?.data || errorResponse.data
      // 安全检查数据结构并适配前端期望的字段名
      const errorData = errorRawData && typeof errorRawData === 'object' && 'errorRateDistribution' in errorRawData 
        ? errorRawData as unknown as { errorRateDistribution: any[]; errorTrend: any[]; commonErrors: any[] } 
        : (errorRawData && typeof errorRawData === 'object' && 'distribution' in errorRawData 
           ? { 
               errorRateDistribution: (errorRawData as any).distribution, 
               errorTrend: (errorRawData as any).trend,
               commonErrors: []
             } 
           : undefined)
      console.log('错误分析数据:', errorData)
      if (errorData && errorData.errorRateDistribution) {
        // 更新错误率图表
        if (errorRateChartInstance) {
          const servicesForErrorChart = errorData.errorRateDistribution.map((item: any) => ({
            name: item.service,
            errorRate: item.errorRate || 0,
            errorCount: item.errorCount || 0,
            requestCount: item.totalRequests || 0
          }))
          const chartOption = getErrorRateChartOption(servicesForErrorChart)
          errorRateChartInstance.setOption(chartOption, true)
        }
        
        // 更新常见错误表格
        if (errorData.commonErrors && errorData.commonErrors.length > 0) {
          commonErrors.value = errorData.commonErrors
        } else {
          // 如果没有常见错误数据，使用追踪数据中的错误信息
          if (errorTraces.length > 0) {
            commonErrors.value = analyzeErrors(errorTraces)
          }
        }
      } else {
        // 如果没有错误分析数据，但有服务数据，尝试从服务统计数据中提取错误信息
        if (services.length > 0) {
          const servicesForErrorChart = services.map((service: any) => ({
            name: service.name,
            errorRate: service.errorRate || 0,
            errorCount: service.errorCount || 0,
            requestCount: service.requestCount || 0
          }))
          const chartOption = getErrorRateChartOption(servicesForErrorChart)
          errorRateChartInstance?.setOption(chartOption, true)
        }
      }

      // 加载吞吐量分析数据
      const throughputResponse = await getThroughputAnalysis()
      // 正确处理响应数据结构
      const throughputRawData = throughputResponse.data?.data || throughputResponse.data
      // 安全检查数据结构并适配前端期望的字段名
      const throughputData = throughputRawData && typeof throughputRawData === 'object' && 'requestDistribution' in throughputRawData 
        ? throughputRawData as unknown as { requestDistribution: any[]; qpsTrend: any[] } 
        : (throughputRawData && typeof throughputRawData === 'object' && 'distribution' in throughputRawData 
           ? { 
               requestDistribution: (throughputRawData as any).distribution, 
               qpsTrend: (throughputRawData as any).trend
             } 
           : undefined)
      console.log('吞吐量分析数据:', throughputData)
      if (throughputData && throughputData.requestDistribution) {
        // 更新请求分布图表
        if (requestDistributionChartInstance) {
          const servicesForThroughputChart = throughputData.requestDistribution.map((item: any) => ({
            name: item.service,
            requestCount: item.totalRequests || item.requestsPerSecond || 0
          }))
          const chartOption = getRequestDistributionChartOption(servicesForThroughputChart)
          requestDistributionChartInstance.setOption(chartOption, true)
        }
      } else {
        // 如果没有吞吐量分析数据，但有服务数据，使用服务统计数据
        if (services.length > 0) {
          const chartOption = getRequestDistributionChartOption(services)
          requestDistributionChartInstance?.setOption(chartOption, true)
        }
      }
    } catch (analysisError) {
      console.log('性能分析数据加载失败:', analysisError)
    }

    // 更新图表数据
    console.log('准备更新图表，服务数量:', services.length, '时序数据长度:', timeSeriesData.length)
    updateChartsWithRealData(services, timeSeriesData)

    // 强制重新渲染图表
    setTimeout(() => {
      handleResize()
    }, 200)

    // 4. 加载性能统计数据
    try {
      const statsResponse = await getPerformanceStats()
      const statsData = statsResponse.data?.data || statsResponse.data || statsResponse
      console.log('性能统计数据:', statsData)
    } catch (statsError) {
      console.log('性能统计数据加载失败，跳过')
    }

    // 5. 加载仪表板指标
    try {
      const dashboardResponse = await getDashboardMetrics()
      const dashboardData = dashboardResponse.data?.data || dashboardResponse.data || dashboardResponse
      console.log('仪表板数据:', dashboardData)
    } catch (dashboardError) {
      console.log('仪表板数据加载失败，跳过')
    }

  } catch (error) {
    console.error('加载性能数据失败:', error)
    ElMessage.error('加载性能数据失败')

    // 清空数据显示
    slowTraces.value = []
    commonErrors.value = []
    serviceStatsTable.value = []

    // 初始化空图表，传入空的services数组
    const emptyServices: any[] = []
    const emptyTimeSeriesData: any[] = []
    updateChartsWithRealData(emptyServices, emptyTimeSeriesData)
  }
}

// 分析错误数据
const analyzeErrors = (errorTraces: any[]) => {
  console.log('开始分析错误数据，错误追踪数量:', errorTraces.length)

  if (errorTraces.length === 0) {
    console.log('没有错误追踪数据')
    return []
  }

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

  const result = Array.from(errorMap.values())
    .sort((a: any, b: any) => b.count - a.count)
    .slice(0, 10)
    .map(error => ({
      ...error,
      lastOccurrence: formatTime(error.lastOccurrence)
    }))

  console.log('错误分析结果:', result)
  return result
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
  console.log('更新图表数据:', {
    servicesCount: services.length,
    timeSeriesCount: timeSeriesData.length,
    services: services.map(s => ({ name: s.name, requestCount: s.requestCount, avgLatency: s.avgLatency, errorRate: s.errorRate }))
  })

  // 更新延迟分布图表
  if (latencyDistributionChartInstance) {
    console.log('更新延迟分布图表')
    const chartOption = getLatencyDistributionChartOption(services)
    console.log('延迟分布图表配置:', chartOption)
    latencyDistributionChartInstance.setOption(chartOption, true)
  }

  // 更新延迟趋势图表
  if (latencyTrendChartInstance) {
    if (timeSeriesData.length > 0) {
      const hours = timeSeriesData.map(d => d.hour)
      const p50Data = timeSeriesData.map(d => Math.round(d.p50Duration))
      const p95Data = timeSeriesData.map(d => Math.round(d.p95Duration))
      const p99Data = timeSeriesData.map(d => Math.round(d.p99Duration))

      latencyTrendChartInstance.setOption({
        title: null, // 清除标题
        tooltip: {
          trigger: 'axis'
        },
        xAxis: {
          type: 'category',
          data: hours
        },
        yAxis: {
          type: 'value',
          name: '延迟(ms)'
        },
        series: [
          {
            name: 'P50',
            data: p50Data,
            type: 'line',
            smooth: true
          },
          {
            name: 'P95',
            data: p95Data,
            type: 'line',
            smooth: true
          },
          {
            name: 'P99',
            data: p99Data,
            type: 'line',
            smooth: true
          }
        ]
      }, true)
    } else {
      // 没有数据时显示默认图表
      latencyTrendChartInstance.setOption(getLatencyTrendChartOption(), true)
    }
  }

  // 更新错误率图表
  if (errorRateChartInstance) {
    console.log('更新错误率图表，服务数据:', services)
    if (services && services.length > 0) {
      console.log('服务错误率:', services.map(s => ({
        name: s.name,
        errorRate: s.errorRate,
        errorCount: s.errorCount
      })))
    }
    const chartOption = getErrorRateChartOption(services)
    console.log('错误率图表配置:', chartOption)
    errorRateChartInstance.setOption(chartOption, true)
  }

  // 更新错误趋势图表
  if (errorTrendChartInstance) {
    console.log('更新错误趋势图表，时序数据:', timeSeriesData)
    if (timeSeriesData.length > 0) {
      const hours = timeSeriesData.map(d => d.hour)
      const errorCounts = timeSeriesData.map(d => d.errorCount)
      console.log('错误趋势数据:', { hours, errorCounts })

      errorTrendChartInstance.setOption({
        title: null, // 清除标题
        tooltip: {
          trigger: 'axis'
        },
        xAxis: {
          type: 'category',
          data: hours
        },
        yAxis: {
          type: 'value',
          name: '错误数'
        },
        series: [{
          name: '错误数',
          data: errorCounts,
          type: 'line',
          smooth: true,
          itemStyle: {
            color: '#f56c6c'
          }
        }]
      }, true)
    } else {
      // 没有数据时显示默认图表
      errorTrendChartInstance.setOption(getErrorTrendChartOption(), true)
    }
  }

  // 更新请求分布图表
  if (requestDistributionChartInstance) {
    console.log('更新请求分布图表，服务数据:', services)
    if (services && services.length > 0) {
      console.log('服务请求数量:', services.map(s => ({ name: s.name, requestCount: s.requestCount })))
    }
    const chartOption = getRequestDistributionChartOption(services)
    console.log('请求分布图表配置:', chartOption)
    requestDistributionChartInstance.setOption(chartOption, true)

    // 强制重新渲染
    setTimeout(() => {
      if (requestDistributionChartInstance && !requestDistributionChartInstance.isDisposed()) {
        requestDistributionChartInstance.resize()
      }
    }, 100)
  }

  // 更新服务活跃度图表
  if (qpsTrendChartInstance) {
    console.log('更新服务活跃度图表，时序数据:', timeSeriesData)
    const chartOption = getQpsTrendChartOption(timeSeriesData)
    console.log('服务活跃度图表配置:', chartOption)
    qpsTrendChartInstance.setOption(chartOption, true)
  }
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

// 显示性能瓶颈详情
const showBottleneckDetails = async () => {
  try {
    // 获取性能瓶颈详情
    const response = await detectBottlenecks()
    const bottlenecksData = response.data?.data || response.data || response
    console.log('性能瓶颈详情:', bottlenecksData)
    
    if (bottlenecksData && Array.isArray(bottlenecksData)) {
      bottleneckDetails.value = bottlenecksData
      bottleneckDialogVisible.value = true
    } else {
      ElMessage.info('暂无性能瓶颈信息')
    }
  } catch (error) {
    console.error('获取性能瓶颈详情失败:', error)
    ElMessage.error('获取性能瓶颈详情失败')
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

    // 只有当存在严重瓶颈（CRITICAL级别）时才显示警告
    const criticalBottlenecks = bottlenecks.filter(b =>
      b.severity === 'CRITICAL'
    )

    if (criticalBottlenecks.length > 0) {
      ElMessage.warning(`检测到 ${criticalBottlenecks.length} 个严重性能瓶颈`)
    }
  }

  // 内存压力告警 - 只在严重情况下提示
  if (memory && memory.pressureLevel === 'CRITICAL') {
    ElMessage.error('内存使用率过高，请立即进行垃圾回收')
  }

  // 处理队列告警 - 提高阈值
  if (processing && processing.queueSize > 1000) {
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
      ? `${span.operationName.substring(0, 30)  }...`
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
            height
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

// 获取瓶颈类型标签
const getBottleneckTypeLabel = (type: string) => {
  const typeLabels: Record<string, string> = {
    'MEMORY': '内存',
    'PROCESSING': '处理器',
    'OPERATION': '操作',
    'SYSTEM': '系统'
  }
  return typeLabels[type] || type
}

// 获取瓶颈类型标签样式
const getBottleneckTypeTag = (type: string) => {
  const typeTags: Record<string, string> = {
    'MEMORY': 'danger',
    'PROCESSING': 'warning',
    'OPERATION': 'info',
    'SYSTEM': 'primary'
  }
  return typeTags[type] || 'info'
}

// 获取严重程度标签
const getSeverityLabel = (severity: string) => {
  const severityLabels: Record<string, string> = {
    'LOW': '低',
    'MEDIUM': '中',
    'HIGH': '高',
    'CRITICAL': '严重'
  }
  return severityLabels[severity] || severity
}

// 获取严重程度标签样式
const getSeverityTag = (severity: string) => {
  const severityTags: Record<string, string> = {
    'LOW': 'info',
    'MEDIUM': 'warning',
    'HIGH': 'danger',
    'CRITICAL': 'danger'
  }
  return severityTags[severity] || 'info'
}

// 获取优先级标签
const getPriorityLabel = (priority: string) => {
  const priorityLabels: Record<string, string> = {
    'LOW': '低',
    'MEDIUM': '中',
    'HIGH': '高',
    'CRITICAL': '严重'
  }
  return priorityLabels[priority] || priority
}

// 获取优先级标签样式
const getPriorityTag = (priority: string) => {
  const priorityTags: Record<string, string> = {
    'LOW': 'info',
    'MEDIUM': 'warning',
    'HIGH': 'danger',
    'CRITICAL': 'danger'
  }
  return priorityTags[priority] || 'info'
}

// 显示优化建议
const showOptimizationSuggestions = (bottleneck: any) => {
  if (bottleneck.suggestions && bottleneck.suggestions.length > 0) {
    currentSuggestions.value = bottleneck.suggestions
    suggestionsDialogVisible.value = true
  } else {
    ElMessage.info('暂无优化建议')
  }
}

// 实时监控定时器
let realTimeMonitoringInterval: NodeJS.Timeout | null = null

// 验证和适配后端数据结构
const validateAndAdaptBackendData = () => {
  console.log('=== 后端数据结构验证 ===')

  // 模拟后端返回的服务统计数据
  const mockBackendServiceData = [
    {
      name: "jairouter",
      traces: 15,           // 追踪数量
      errors: 2,            // 错误数量
      avgDuration: 125,     // 平均延迟(ms)
      p95Duration: 250,     // P95延迟(ms)
      p99Duration: 400,     // P99延迟(ms)
      errorRate: 13.33      // 错误率(已转换为百分比)
    }
  ]

  // 模拟后端返回的追踪数据
  const mockBackendTraceData = [
    {
      traceId: "trace-001",
      serviceName: "jairouter",
      operationName: "POST /v1/chat/completions",
      duration: 1250.5,
      spanCount: 3,
      hasError: false,
      startTime: "2024-01-15T10:30:00Z"
    },
    {
      traceId: "trace-002",
      serviceName: "jairouter",
      operationName: "POST /v1/embeddings",
      duration: 850.2,
      spanCount: 2,
      hasError: true,
      startTime: "2024-01-15T10:31:00Z"
    }
  ]

  console.log('后端服务数据示例:', mockBackendServiceData)
  console.log('后端追踪数据示例:', mockBackendTraceData)

  // 验证前端数据映射
  const adaptedServices = mockBackendServiceData.map((service: any) => ({
    name: service.name,
    avgLatency: service.avgDuration || 0,
    p95Latency: service.p95Duration || 0,
    p99Latency: service.p99Duration || 0,
    errorRate: service.errorRate || 0,        // 后端已转换为百分比
    requestCount: service.traces || 0,        // 使用traces字段作为请求数量
    errorCount: service.errors || 0
  }))

  console.log('适配后的服务数据:', adaptedServices)

  return { adaptedServices, mockBackendTraceData }
}

onMounted(async () => {
  await new Promise(resolve => setTimeout(resolve, 100))
  validateAndAdaptBackendData()
  initCharts()
  window.addEventListener('resize', handleResize)
  await loadPerformanceData()
  realTimeMonitoringInterval = setInterval(() => {
    loadRealTimeMetrics()
  }, 60000)
  loadRealTimeMetrics()
  refreshChartForTab(activeTab.value)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (realTimeMonitoringInterval) {
    clearInterval(realTimeMonitoringInterval)
    realTimeMonitoringInterval = null
  }
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

.top-slow-card,
.top-errors-card {
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

/* 服务统计表格样式 */
.service-stats-card {
  margin-top: 20px;
}

.high-error-rate {
  color: #f56c6c;
  font-weight: bold;
}

/* 建议卡片样式 */
.suggestion-card {
  margin-bottom: 15px;
}

.suggestion-content {
  padding: 15px;
}

.suggestion-title {
  font-weight: bold;
  margin-bottom: 8px;
  font-size: 16px;
}

.suggestion-description {
  color: #606266;
  margin-bottom: 10px;
  line-height: 1.5;
}

.suggestion-priority {
  margin-top: 5px;
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