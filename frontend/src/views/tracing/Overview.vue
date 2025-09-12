<template>
  <div class="tracing-overview">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>追踪概览</span>
          <el-button type="primary" @click="handleRefresh">刷新</el-button>
        </div>
      </template>
      
      <el-row :gutter="20" class="stats-row">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-content">
              <div class="stat-icon info">
                <el-icon><DataLine /></el-icon>
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
                <el-icon><Warning /></el-icon>
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
                <el-icon><SuccessFilled /></el-icon>
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
                <el-icon><ScaleToOriginal /></el-icon>
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
            <div ref="traceChart" class="chart-container"></div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card>
            <template #header>
              <div class="card-header">
                <span>服务延迟分布</span>
              </div>
            </template>
            <div ref="latencyChart" class="chart-container"></div>
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
            <el-table :data="services" style="width: 100%">
              <el-table-column prop="name" label="服务名称" width="180" />
              <el-table-column prop="traces" label="追踪数" width="120" />
              <el-table-column prop="errors" label="错误数" width="120" />
              <el-table-column prop="avgDuration" label="平均耗时(ms)" width="150" />
              <el-table-column prop="p95Duration" label="P95耗时(ms)" width="150" />
              <el-table-column prop="p99Duration" label="P99耗时(ms)" width="150" />
            </el-table>
          </el-card>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'

// 统计数据
const stats = ref({
  totalTraces: 12345,
  errorTraces: 67,
  avgDuration: 142,
  samplingRate: 10
})

// 服务统计数据
const services = ref([
  { 
    name: 'Chat Service', 
    traces: 5678, 
    errors: 12, 
    avgDuration: 125, 
    p95Duration: 210, 
    p99Duration: 320 
  },
  { 
    name: 'Embedding Service', 
    traces: 3456, 
    errors: 8, 
    avgDuration: 180, 
    p95Duration: 280, 
    p99Duration: 420 
  },
  { 
    name: 'Rerank Service', 
    traces: 1234, 
    errors: 32, 
    avgDuration: 210, 
    p95Duration: 350, 
    p99Duration: 580 
  },
  { 
    name: 'API Gateway', 
    traces: 1977, 
    errors: 15, 
    avgDuration: 85, 
    p95Duration: 150, 
    p99Duration: 240 
  }
])

// 图表引用
const traceChart = ref<HTMLElement | null>(null)
const latencyChart = ref<HTMLElement | null>(null)
let traceChartInstance: echarts.ECharts | null = null
let latencyChartInstance: echarts.ECharts | null = null

// 初始化图表
const initCharts = () => {
  if (traceChart.value) {
    traceChartInstance = echarts.init(traceChart.value)
    traceChartInstance.setOption(getTraceChartOption())
  }
  
  if (latencyChart.value) {
    latencyChartInstance = echarts.init(latencyChart.value)
    latencyChartInstance.setOption(getLatencyChartOption())
  }
}

// 追踪量趋势图表配置
const getTraceChartOption = () => {
  return {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: ['00:00', '04:00', '08:00', '12:00', '16:00', '20:00', '24:00']
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '总追踪数',
        data: [1200, 2000, 1500, 800, 700, 1100, 1300],
        type: 'line',
        smooth: true
      },
      {
        name: '错误追踪数',
        data: [12, 20, 15, 8, 7, 11, 13],
        type: 'line',
        smooth: true,
        itemStyle: {
          color: '#f56c6c'
        }
      }
    ]
  }
}

// 服务延迟分布图表配置
const getLatencyChartOption = () => {
  return {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: ['Chat Service', 'Embedding Service', 'Rerank Service', 'API Gateway']
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '平均耗时',
        type: 'bar',
        data: [125, 180, 210, 85],
        itemStyle: {
          color: '#409eff'
        }
      }
    ]
  }
}

// 窗口大小变化时重置图表
const handleResize = () => {
  traceChartInstance?.resize()
  latencyChartInstance?.resize()
}

// 刷新
const handleRefresh = () => {
  // 这里可以调用API获取最新数据
  ElMessage.success('数据已刷新')
}

// 组件挂载时初始化
onMounted(() => {
  initCharts()
  window.addEventListener('resize', handleResize)
})

// 组件卸载前清理
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  traceChartInstance?.dispose()
  latencyChartInstance?.dispose()
})
</script>

<style scoped>
.tracing-overview {
  padding: 20px;
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
</style>