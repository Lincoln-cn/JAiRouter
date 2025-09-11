<template>
  <div class="monitoring-overview">
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon success">
              <el-icon><SuccessFilled /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.successRate }}%</div>
              <div class="stat-label">成功率</div>
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
              <div class="stat-value">{{ stats.latency }}ms</div>
              <div class="stat-label">平均延迟</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon danger">
              <el-icon><CircleClose /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.errorCount }}</div>
              <div class="stat-label">错误数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon info">
              <el-icon><DataLine /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.qps }}</div>
              <div class="stat-label">QPS</div>
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
              <span>请求量趋势</span>
            </div>
          </template>
          <div ref="requestChart" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>错误率趋势</span>
            </div>
          </template>
          <div ref="errorChart" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>
    
    <el-row :gutter="20" class="services-row">
      <el-col :span="24">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>服务状态</span>
            </div>
          </template>
          <el-table :data="services" style="width: 100%">
            <el-table-column prop="name" label="服务名称" width="180" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="scope">
                <el-tag :type="scope.row.status === 'healthy' ? 'success' : 'danger'">
                  {{ scope.row.status === 'healthy' ? '健康' : '异常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="requests" label="请求数" width="120" />
            <el-table-column prop="errors" label="错误数" width="120" />
            <el-table-column prop="latency" label="平均延迟(ms)" width="150" />
            <el-table-column prop="lastCheck" label="最后检查时间" width="200" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { 
  SuccessFilled, 
  Warning, 
  CircleClose, 
  DataLine 
} from '@/icons'

// 统计数据
const stats = ref({
  successRate: 99.8,
  latency: 42,
  errorCount: 3,
  qps: 120
})

// 服务状态数据
const services = ref([
  { 
    name: 'Chat Service', 
    status: 'healthy', 
    requests: 1200, 
    errors: 2, 
    latency: 38, 
    lastCheck: '2023-10-01 10:00:00' 
  },
  { 
    name: 'Embedding Service', 
    status: 'healthy', 
    requests: 800, 
    errors: 1, 
    latency: 45, 
    lastCheck: '2023-10-01 10:00:00' 
  },
  { 
    name: 'Rerank Service', 
    status: 'unhealthy', 
    requests: 300, 
    errors: 15, 
    latency: 120, 
    lastCheck: '2023-10-01 09:55:00' 
  }
])

// 图表引用
const requestChart = ref<HTMLElement | null>(null)
const errorChart = ref<HTMLElement | null>(null)
let requestChartInstance: echarts.ECharts | null = null
let errorChartInstance: echarts.ECharts | null = null

// 初始化图表
const initCharts = () => {
  if (requestChart.value) {
    requestChartInstance = echarts.init(requestChart.value)
    requestChartInstance.setOption(getRequestChartOption())
  }
  
  if (errorChart.value) {
    errorChartInstance = echarts.init(errorChart.value)
    errorChartInstance.setOption(getErrorChartOption())
  }
}

// 请求量趋势图表配置
const getRequestChartOption = () => {
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
        data: [120, 200, 150, 80, 70, 110, 130],
        type: 'line',
        smooth: true
      }
    ]
  }
}

// 错误率趋势图表配置
const getErrorChartOption = () => {
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
        data: [2, 5, 3, 1, 2, 4, 3],
        type: 'line',
        smooth: true,
        itemStyle: {
          color: '#f56c6c'
        }
      }
    ]
  }
}

// 窗口大小变化时重置图表
const handleResize = () => {
  requestChartInstance?.resize()
  errorChartInstance?.resize()
}

// 组件挂载时初始化
onMounted(() => {
  initCharts()
  window.addEventListener('resize', handleResize)
})

// 组件卸载前清理
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  requestChartInstance?.dispose()
  errorChartInstance?.dispose()
})
</script>

<style scoped>
.monitoring-overview {
  padding: 20px;
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
}

.stat-icon.success {
  background-color: #f0f9ff;
  color: #409eff;
}

.stat-icon.warning {
  background-color: #fdf6ec;
  color: #e6a23c;
}

.stat-icon.danger {
  background-color: #fef0f0;
  color: #f56c6c;
}

.stat-icon.info {
  background-color: #f0f9ff;
  color: #409eff;
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

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-container {
  height: 300px;
}
</style>