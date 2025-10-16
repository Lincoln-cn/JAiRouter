<template>
  <div class="tracing-overview">
    <el-card v-loading="loading" element-loading-text="加载中...">
      <template #header>
        <div class="card-header">
          <span>追踪概览</span>
          <el-button type="primary" @click="handleRefresh" :loading="refreshing">刷新</el-button>
        </div>
      </template>

      <el-row :gutter="20" class="stats-row">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon info">
                <el-icon>
                  <DataLine />
                </el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.totalTraces }}</div>
                <div class="stat-label">总追踪数</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon warning">
                <el-icon>
                  <Warning />
                </el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.errorTraces }}</div>
                <div class="stat-label">错误追踪数</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon success">
                <el-icon>
                  <SuccessFilled />
                </el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.avgDuration }}ms</div>
                <div class="stat-label">平均耗时</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon">
                <el-icon>
                  <ScaleToOriginal />
                </el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.samplingRate }}%</div>
                <div class="stat-label">采样率</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20" class="charts-row">
        <el-col :span="12">
          <el-card>
            <template #header>
              <div class="card-header">
                <span>追踪量趋势</span>
              </div>
            </template>
            <div ref="traceChart" class="chart-container" v-show="tracingStats.traceVolumeTrend.length > 0"></div>
            <div v-show="tracingStats.traceVolumeTrend.length === 0" class="empty-chart">
              <el-empty description="暂无追踪趋势数据">
                <template #image>
                  <el-icon size="60" color="#c0c4cc">
                    <DataLine />
                  </el-icon>
                </template>
              </el-empty>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card>
            <template #header>
              <div class="card-header">
                <span>服务延迟分布</span>
              </div>
            </template>
            <div ref="latencyChart" class="chart-container" v-show="services.length > 0"></div>
            <div v-show="services.length === 0" class="empty-chart">
              <el-empty description="暂无服务延迟数据">
                <template #image>
                  <el-icon size="60" color="#c0c4cc">
                    <ScaleToOriginal />
                  </el-icon>
                </template>
              </el-empty>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20" class="services-row">
        <el-col :span="24">
          <el-card>
            <template #header>
              <div class="card-header">
                <span>服务追踪统计</span>
              </div>
            </template>
            <el-table :data="services" style="width: 100%" empty-text="暂无服务数据，请先发送一些请求以生成追踪数据">
              <el-table-column prop="name" label="服务名称" width="180" />
              <el-table-column prop="traces" label="追踪数" width="120" />
              <el-table-column prop="errors" label="错误数" width="120">
                <template #default="{ row }">
                  <span :class="{ 'high-error': row.errorRate > 5 }">{{ row.errors }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="avgDuration" label="平均耗时(ms)" width="150">
                <template #default="{ row }">
                  <span :class="{ 'high-latency': row.avgDuration > 200 }">{{ row.avgDuration }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="p95Duration" label="P95耗时(ms)" width="150">
                <template #default="{ row }">
                  <span :class="{ 'high-latency': row.p95Duration > 500 }">{{ row.p95Duration }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="p99Duration" label="P99耗时(ms)" width="150">
                <template #default="{ row }">
                  <span :class="{ 'high-latency': row.p99Duration > 800 }">{{ row.p99Duration }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="errorRate" label="错误率(%)" width="120">
                <template #default="{ row }">
                  <span :class="{ 'high-error': row.errorRate > 5 }">{{ row.errorRate }}%</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  getTracingOverview,
  getTracingStats,
  getServiceStats,
  refreshTracingData
} from '@/api/tracing'
import type { TracingOverview, TracingStats, ServiceStats } from '@/types'

// 图表更新定时器
let chartUpdateTimer: number | null = null

// 加载状态
const loading = ref(false)
const refreshing = ref(false)

// 统计数据
const stats = ref<TracingOverview>({
  totalTraces: 0,
  errorTraces: 0,
  avgDuration: 0,
  samplingRate: 0
})

// 趋势数据
const tracingStats = ref<TracingStats>({
  traceVolumeTrend: [],
  errorTrend: []
})

// 服务统计数据
const services = ref<ServiceStats[]>([])

// 图表引用
const traceChart = ref<HTMLElement | null>(null)
const latencyChart = ref<HTMLElement | null>(null)
let traceChartInstance: echarts.ECharts | null = null
let latencyChartInstance: echarts.ECharts | null = null

// 监听数据变化并更新图表
watch([tracingStats, services], () => {
  nextTick(() => {
    updateTraceChart()
    updateLatencyChart()
  })
}, { deep: true })

// 初始化图表
const initCharts = () => {
  // 清理已存在的图表实例
  if (traceChartInstance) {
    traceChartInstance.dispose()
    traceChartInstance = null
  }
  
  if (latencyChartInstance) {
    latencyChartInstance.dispose()
    latencyChartInstance = null
  }
  
  if (traceChart.value) {
    try {
      traceChartInstance = echarts.init(traceChart.value)
      traceChartInstance.setOption(getTraceChartOption())
    } catch (error) {
      console.error('初始化追踪趋势图表失败:', error)
    }
  }

  if (latencyChart.value) {
    try {
      latencyChartInstance = echarts.init(latencyChart.value)
      const option = getLatencyChartOption()
      latencyChartInstance.setOption(option)
    } catch (error) {
      console.error('初始化延迟分布图表失败:', error)
    }
  }
  
  // 立即尝试更新图表，确保即使在初始化后数据到达也能正确显示
  updateTraceChart()
  updateLatencyChart()
}

// 追踪量趋势图表配置
const getTraceChartOption = () => {
  // 提取时间戳并转换为可读的时间格式
  const timestamps = tracingStats.value.traceVolumeTrend.map(item => {
    const timestamp = typeof item.timestamp === 'string' ? parseInt(item.timestamp) : item.timestamp
    return new Date(timestamp).toLocaleTimeString('zh-CN', { 
      hour: '2-digit', 
      minute: '2-digit',
      hour12: false
    })
  })
  
  // 提取追踪量和错误量数据
  const traceVolumes = tracingStats.value.traceVolumeTrend.map(item => item.value)
  const errorVolumes = tracingStats.value.errorTrend.map(item => item.value)

  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any[]) => {
        let tooltipText = params[0].axisValueLabel + '<br/>'
        params.forEach((param: any) => {
          tooltipText += `${param.marker} ${param.seriesName}: ${param.value}<br/>`
        })
        return tooltipText
      }
    },
    legend: {
      data: ['总追踪数', '错误追踪数']
    },
    xAxis: {
      type: 'category',
      data: timestamps.length > 0 ? timestamps : ['暂无数据'],
      axisLabel: {
        interval: 0,
        rotate: 45
      }
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        formatter: '{value}'
      }
    },
    series: [
      {
        name: '总追踪数',
        data: traceVolumes.length > 0 ? traceVolumes : [0],
        type: 'line',
        smooth: true,
        itemStyle: {
          color: '#409eff'
        },
        areaStyle: {
          opacity: 0.1
        }
      },
      {
        name: '错误追踪数',
        data: errorVolumes.length > 0 ? errorVolumes : [0],
        type: 'line',
        smooth: true,
        itemStyle: {
          color: '#f56c6c'
        },
        areaStyle: {
          opacity: 0.1
        }
      }
    ],
    grid: {
      left: '10%',
      right: '10%',
      bottom: '20%',
      containLabel: true
    }
  }
}

// 更新追踪趋势图表
const updateTraceChart = () => {
  if (traceChartInstance) {
    traceChartInstance.setOption(getTraceChartOption(), true) // 第二个参数为true表示不合并，完全替换
    // 强制重新渲染
    traceChartInstance.resize()
  } else if (traceChart.value) {
    // 如果图表实例不存在，重新创建
    try {
      traceChartInstance = echarts.init(traceChart.value)
      traceChartInstance.setOption(getTraceChartOption())
      // 强制重新渲染
      traceChartInstance.resize()
    } catch (error) {
      console.error('创建追踪趋势图表实例失败:', error)
    }
  }
}

// 服务延迟分布图表配置
const getLatencyChartOption = () => {
  const serviceNames = services.value.map(service => service.name)
  const avgDurations = services.value.map(service => service.avgDuration)
  const p95Durations = services.value.map(service => service.p95Duration)

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: ['平均耗时', 'P95耗时']
    },
    xAxis: {
      type: 'category',
      data: serviceNames.length > 0 ? serviceNames : ['暂无数据'],
      axisLabel: {
        rotate: 45
      }
    },
    yAxis: {
      type: 'value',
      name: '耗时(ms)'
    },
    series: [
      {
        name: '平均耗时',
        type: 'bar',
        data: avgDurations.length > 0 ? avgDurations : [0],
        itemStyle: {
          color: '#409eff'
        }
      },
      {
        name: 'P95耗时',
        type: 'bar',
        data: p95Durations.length > 0 ? p95Durations : [0],
        itemStyle: {
          color: '#e6a23c'
        }
      }
    ]
  }
  
  return option
}

// 更新延迟分布图表
const updateLatencyChart = () => {
  const option = getLatencyChartOption()
  
  if (latencyChartInstance) {
    latencyChartInstance.setOption(option, true) // 第二个参数为true表示不合并，完全替换
    // 强制重新渲染
    latencyChartInstance.resize()
  } else if (latencyChart.value) {
    // 如果图表实例不存在，重新创建
    try {
      latencyChartInstance = echarts.init(latencyChart.value)
      latencyChartInstance.setOption(option)
      // 强制重新渲染
      latencyChartInstance.resize()
    } catch (error) {
      console.error('创建图表实例失败:', error)
    }
  }
}

// 窗口大小变化时重置图表
const handleResize = () => {
  setTimeout(() => {
    traceChartInstance?.resize()
    latencyChartInstance?.resize()
  }, 100) // 延迟一点时间确保DOM更新完成
}

// 加载追踪概览数据
const loadTracingOverview = async () => {
  try {
    const response = await getTracingOverview()

    const data = (response.data.data || response) as any
    
    if (data) {
      // 直接使用返回的数据结构，而不是尝试访问 data.data
      stats.value = {
        totalTraces: data.totalTraces || 0,
        errorTraces: data.errorTraces !== undefined ? data.errorTraces : (data.totalTraces - data.successfulTraces) || 0,
        avgDuration: Math.round(data.avgDuration || 0),
        samplingRate: data.samplingRate || 0,
        successfulTraces: data.successfulTraces,
        totalSpans: data.totalSpans,
        maxDuration: data.maxDuration,
        minDuration: data.minDuration
      }
    } else {
      // 数据为空时设置默认值
      stats.value = {
        totalTraces: 0,
        errorTraces: 0,
        avgDuration: 0,
        samplingRate: 0
      }
    }
  } catch (error) {
    console.error('加载追踪概览数据失败:', error)
    ElMessage.warning('暂无追踪数据，请先发送一些请求以生成追踪数据')
    // 设置默认值
    stats.value = {
      totalTraces: 0,
      errorTraces: 0,
      avgDuration: 0,
      samplingRate: 0
    }
  }
}
// 加载追踪统计数据
const loadTracingStats = async () => {
  try {
    const response = await getTracingStats()

    const data = response.data || response

    if (data) {
      // 正确处理返回的数据结构
      tracingStats.value = {
        traceVolumeTrend: (data as any).traceVolumeTrend || [],
        errorTrend: (data as any).errorTrend || [],
        processing: (data as any).processing,
        configInfo: (data as any).configInfo,
        memory: (data as any).memory,
        timestamp: (data as any).timestamp
      }

      // 正确设置采样率
      const configInfo = (data as any).configInfo;
      if (configInfo && typeof configInfo.globalSamplingRatio === 'number') {
        stats.value.samplingRate = Math.round(configInfo.globalSamplingRatio * 100);
      } else if (configInfo && typeof configInfo.samplingRate === 'number') {
        stats.value.samplingRate = configInfo.samplingRate;
      }
    } else {
      // 数据为空时设置默认值
      tracingStats.value = {
        traceVolumeTrend: [],
        errorTrend: []
      }
    }
  } catch (error) {
    console.error('加载追踪统计数据失败:', error)
    // 设置默认值，不显示错误消息
    tracingStats.value = {
      traceVolumeTrend: [],
      errorTrend: []
    }
  }
}
    
// 加载服务统计数据
const loadServiceStats = async () => {
  try {
    const response = await getServiceStats()
    const data = response.data?.data || response.data

    if (data) {
      services.value = data
    } else {
      services.value = []
    }
  } catch (error) {
    console.error('加载服务统计数据失败:', error)
    // 设置空数组，不显示错误消息
    services.value = []
  }
}
    
// 加载所有数据
const loadAllData = async () => {
  loading.value = true
  try {
    await Promise.all([
      loadTracingOverview(),
      loadTracingStats(),
      loadServiceStats()
    ])
  } catch (error) {
    console.error('加载数据失败:', error)
  } finally {
    loading.value = false
  }
}

// 刷新数据
const handleRefresh = async () => {
  refreshing.value = true
  try {
    const response = await refreshTracingData()
    if (response.data?.success !== false) {
      ElMessage.success('数据刷新成功')
    }
    await loadAllData()
  } catch (error) {
    console.error('刷新数据失败:', error)
    ElMessage.warning('刷新完成，如果没有数据请先发送一些请求')
    await loadAllData()
  } finally {
    refreshing.value = false
  }
}

// 组件挂载时初始化
onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await loadAllData()
  // 数据加载完成后再初始化图表
  await nextTick()
  initCharts()
})

// 组件卸载前清理
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  
  // 安全地清理图表实例
  try {
    if (traceChartInstance && !traceChartInstance.isDisposed()) {
      traceChartInstance.dispose()
    }
    if (latencyChartInstance && !latencyChartInstance.isDisposed()) {
      latencyChartInstance.dispose()
    }
  } catch (error) {
    console.warn('清理图表实例时出错:', error)
  }
  
  // 重置实例引用
  traceChartInstance = null
  latencyChartInstance = null
})
</script>

<style scoped>
.tracing-overview {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  height: 120px;
}

.stat-content {
  display: flex;
  align-items: center;
  height: 100%;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20px;
  font-size: 24px;
  background-color: #f0f9ff;
  color: #409eff;
}

.stat-icon.warning {
  background-color: #fdf6ec;
  color: #e6a23c;
}

.stat-icon.success {
  background-color: #f0f9ff;
  color: #67c23a;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 5px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.chart-container {
  height: 300px;
}

.high-error {
  color: #f56c6c;
  font-weight: bold;
}

.high-latency {
  color: #e6a23c;
  font-weight: bold;
}

.empty-chart {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
