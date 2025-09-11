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
          </div>
        </div>
      </template>
      
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
            <el-table :data="slowTraces" style="width: 100%">
              <el-table-column prop="traceId" label="追踪ID" width="200" />
              <el-table-column prop="service" label="服务" width="150" />
              <el-table-column prop="operation" label="操作" width="200" />
              <el-table-column prop="duration" label="耗时(ms)" width="120" />
              <el-table-column prop="startTime" label="开始时间" width="200" />
              <el-table-column label="操作" width="100">
                <template #default="scope">
                  <el-button size="small" @click="handleViewTrace(scope.row)">查看</el-button>
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
            <el-table :data="commonErrors" style="width: 100%">
              <el-table-column prop="errorType" label="错误类型" width="200" />
              <el-table-column prop="service" label="服务" width="150" />
              <el-table-column prop="operation" label="操作" width="200" />
              <el-table-column prop="count" label="次数" width="100" />
              <el-table-column prop="lastOccurrence" label="最后发生时间" width="200" />
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

// 时间范围
const timeRange = ref(['2023-10-01 00:00:00', '2023-10-01 23:59:59'])

// 激活的标签页
const activeTab = ref('latency')

// 最慢的追踪
const slowTraces = ref([
  { 
    traceId: 'trace-001', 
    service: 'Chat Service', 
    operation: 'chat/completions', 
    duration: 5200, 
    startTime: '2023-10-01 10:00:00' 
  },
  { 
    traceId: 'trace-002', 
    service: 'Embedding Service', 
    operation: 'embeddings', 
    duration: 4800, 
    startTime: '2023-10-01 09:30:00' 
  },
  { 
    traceId: 'trace-003', 
    service: 'Rerank Service', 
    operation: 'rerank', 
    duration: 3900, 
    startTime: '2023-10-01 08:45:00' 
  }
])

// 常见错误
const commonErrors = ref([
  { 
    errorType: 'TimeoutException', 
    service: 'Chat Service', 
    operation: 'chat/completions', 
    count: 25, 
    lastOccurrence: '2023-10-01 10:00:00' 
  },
  { 
    errorType: 'DownstreamServiceException', 
    service: 'Embedding Service', 
    operation: 'embeddings', 
    count: 18, 
    lastOccurrence: '2023-10-01 09:45:00' 
  },
  { 
    errorType: 'AuthenticationException', 
    service: 'API Gateway', 
    operation: 'auth/validate', 
    count: 12, 
    lastOccurrence: '2023-10-01 09:30:00' 
  }
])

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

// 初始化图表
const initCharts = () => {
  if (latencyDistributionChart.value) {
    latencyDistributionChartInstance = echarts.init(latencyDistributionChart.value)
    latencyDistributionChartInstance.setOption(getLatencyDistributionChartOption())
  }
  
  if (latencyTrendChart.value) {
    latencyTrendChartInstance = echarts.init(latencyTrendChart.value)
    latencyTrendChartInstance.setOption(getLatencyTrendChartOption())
  }
  
  if (errorRateChart.value) {
    errorRateChartInstance = echarts.init(errorRateChart.value)
    errorRateChartInstance.setOption(getErrorRateChartOption())
  }
  
  if (errorTrendChart.value) {
    errorTrendChartInstance = echarts.init(errorTrendChart.value)
    errorTrendChartInstance.setOption(getErrorTrendChartOption())
  }
  
  if (requestDistributionChart.value) {
    requestDistributionChartInstance = echarts.init(requestDistributionChart.value)
    requestDistributionChartInstance.setOption(getRequestDistributionChartOption())
  }
  
  if (qpsTrendChart.value) {
    qpsTrendChartInstance = echarts.init(qpsTrendChart.value)
    qpsTrendChartInstance.setOption(getQpsTrendChartOption())
  }
}

// 服务延迟分布图表配置
const getLatencyDistributionChartOption = () => {
  return {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: ['Chat Service', 'Embedding Service', 'Rerank Service', 'API Gateway']
    },
    yAxis: {
      type: 'value',
      name: '平均延迟(ms)'
    },
    series: [
      {
        name: '平均延迟',
        type: 'bar',
        data: [125, 180, 210, 85],
        itemStyle: {
          color: '#409eff'
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
const getErrorRateChartOption = () => {
  return {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: ['Chat Service', 'Embedding Service', 'Rerank Service', 'API Gateway']
    },
    yAxis: {
      type: 'value',
      name: '错误率(%)'
    },
    series: [
      {
        name: '错误率',
        type: 'bar',
        data: [0.5, 0.3, 1.2, 0.1],
        itemStyle: {
          color: '#f56c6c'
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
const getRequestDistributionChartOption = () => {
  return {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: ['Chat Service', 'Embedding Service', 'Rerank Service', 'API Gateway']
    },
    yAxis: {
      type: 'value',
      name: '请求数'
    },
    series: [
      {
        name: '请求数',
        type: 'bar',
        data: [1200, 800, 300, 2500],
        itemStyle: {
          color: '#67c23a'
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
  // 这里可以调用API获取指定时间范围的数据
  ElMessage.success('时间范围已更新')
}

// 刷新
const handleRefresh = () => {
  // 这里可以调用API获取最新数据
  ElMessage.success('数据已刷新')
}

// 查看追踪详情
const handleViewTrace = (row: any) => {
  traceDialogVisible.value = true
  // 延迟渲染图表
  setTimeout(() => {
    if (traceDetailChart.value) {
      traceDetailChartInstance = echarts.init(traceDetailChart.value)
      traceDetailChartInstance.setOption(getTraceDetailChartOption())
    }
  }, 100)
}

// 追踪详情图表配置
const getTraceDetailChartOption = () => {
  return {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: ['API Gateway', 'Chat Service', 'Downstream API']
    },
    yAxis: {
      type: 'value',
      name: '耗时(ms)'
    },
    series: [
      {
        name: '耗时',
        type: 'bar',
        data: [20, 5100, 80],
        itemStyle: {
          color: '#409eff'
        }
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

// 组件挂载时初始化
onMounted(() => {
  initCharts()
  window.addEventListener('resize', handleResize)
})

// 组件卸载前清理
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  latencyDistributionChartInstance?.dispose()
  latencyTrendChartInstance?.dispose()
  errorRateChartInstance?.dispose()
  errorTrendChartInstance?.dispose()
  requestDistributionChartInstance?.dispose()
  qpsTrendChartInstance?.dispose()
  traceDetailChartInstance?.dispose()
})
</script>

<style scoped>
.tracing-performance {
  padding: 20px;
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
</style>