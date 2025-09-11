<template>
  <div class="dashboard">
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon success">
              <el-icon><SuccessFilled /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.serviceCount }}</div>
              <div class="stat-label">服务数量</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon info">
              <el-icon><Cpu /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.instanceCount }}</div>
              <div class="stat-label">实例数量</div>
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
              <div class="stat-value">{{ stats.alertCount }}</div>
              <div class="stat-label">告警数量</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon danger">
              <el-icon><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.userCount }}</div>
              <div class="stat-label">用户数量</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="charts-row">
      <el-col :span="16">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>系统概览</span>
            </div>
          </template>
          <div ref="systemChart" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>最近活动</span>
            </div>
          </template>
          <el-timeline>
            <el-timeline-item
              v-for="(activity, index) in recentActivities"
              :key="index"
              :timestamp="activity.timestamp"
              :type="activity.type"
            >
              {{ activity.content }}
            </el-timeline-item>
          </el-timeline>
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
            <el-table-column prop="type" label="类型" width="120" />
            <el-table-column prop="instances" label="实例数" width="100" />
            <el-table-column prop="status" label="状态" width="120">
              <template #default="scope">
                <el-tag :type="scope.row.status === 'healthy' ? 'success' : 'danger'">
                  {{ scope.row.status === 'healthy' ? '健康' : '异常' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastUpdate" label="最后更新" width="200" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'

// 统计数据
const stats = ref({
  serviceCount: 8,
  instanceCount: 24,
  alertCount: 3,
  userCount: 12
})

// 最近活动
const recentActivities = ref([
  {
    content: '添加了新的聊天服务实例',
    timestamp: '2023-10-01 10:30:00',
    type: 'primary'
  },
  {
    content: '更新了嵌入服务配置',
    timestamp: '2023-10-01 09:45:00',
    type: 'success'
  },
  {
    content: 'Rerank服务出现异常',
    timestamp: '2023-10-01 09:15:00',
    type: 'warning'
  },
  {
    content: '创建了新的API密钥',
    timestamp: '2023-10-01 08:30:00',
    type: 'info'
  }
])

// 服务状态
const services = ref([
  {
    name: 'Chat Service',
    type: 'chat',
    instances: 3,
    status: 'healthy',
    lastUpdate: '2023-10-01 10:30:00'
  },
  {
    name: 'Embedding Service',
    type: 'embedding',
    instances: 2,
    status: 'healthy',
    lastUpdate: '2023-10-01 09:45:00'
  },
  {
    name: 'Rerank Service',
    type: 'rerank',
    instances: 1,
    status: 'unhealthy',
    lastUpdate: '2023-10-01 09:15:00'
  },
  {
    name: 'TTS Service',
    type: 'tts',
    instances: 2,
    status: 'healthy',
    lastUpdate: '2023-10-01 08:00:00'
  }
])

// 图表引用
const systemChart = ref<HTMLElement | null>(null)
let systemChartInstance: echarts.ECharts | null = null

// 初始化图表
const initChart = () => {
  if (systemChart.value) {
    systemChartInstance = echarts.init(systemChart.value)
    systemChartInstance.setOption(getChartOption())
  }
}

// 图表配置
const getChartOption = () => {
  return {
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: ['请求数', '错误数']
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
        name: '请求数',
        data: [120, 200, 150, 80, 70, 110, 130],
        type: 'line',
        smooth: true
      },
      {
        name: '错误数',
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
  systemChartInstance?.resize()
}

// 组件挂载时初始化
onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

// 组件卸载前清理
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  systemChartInstance?.dispose()
})
</script>

<style scoped>
.dashboard {
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
  color: #67c23a;
}

.stat-icon.info {
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