<template>
  <div class="token-usage-statistics">
    <!-- 时间筛选 -->
    <el-card class="filter-card" shadow="hover">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 400px"
            @change="handleDateChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计概览卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #409EFF;">
              <el-icon><DataAnalysis /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(statistics.totalTokens) }}</div>
              <div class="stat-label">总 Token 使用量</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #67C23A;">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(statistics.totalPromptTokens) }}</div>
              <div class="stat-label">输入 Token</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #E6A23C;">
              <el-icon><ChatDotSquare /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(statistics.totalCompletionTokens) }}</div>
              <div class="stat-label">输出 Token</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #909399;">
              <el-icon><Clock /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.avgResponseTimeMs?.toFixed(0) || 0 }}</div>
              <div class="stat-label">平均响应时间 (ms)</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #F56C6C;">
              <el-icon><Tickets /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(statistics.totalRequests) }}</div>
              <div class="stat-label">总请求数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #67C23A;">
              <el-icon><CircleCheck /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.successRate?.toFixed(2) || 0 }}%</div>
              <div class="stat-label">成功率</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #409EFF;">
              <el-icon><Grid /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.byModel?.length || 0 }}</div>
              <div class="stat-label">使用模型数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #E6A23C;">
              <el-icon><Service /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.byServiceType?.length || 0 }}</div>
              <div class="stat-label">服务类型数</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20">
      <!-- 按模型统计 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">模型 Token 使用量 Top 10</span>
          </template>
          <div ref="modelChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 按服务类型统计 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">服务类型 Token 使用量</span>
          </template>
          <div ref="serviceTypeChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 按日统计 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">每日 Token 使用趋势</span>
          </template>
          <div ref="dailyChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 按周统计 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">每周 Token 使用趋势</span>
          </template>
          <div ref="weeklyChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 按月统计 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">每月 Token 使用趋势</span>
          </template>
          <div ref="monthlyChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 小时分布 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">24 小时 Token 使用分布</span>
          </template>
          <div ref="hourlyChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近使用记录 -->
    <el-card shadow="hover" style="margin-top: 20px;">
      <template #header>
        <span class="chart-title">最近 Token 使用记录</span>
      </template>
      <el-table :data="recentUsage" border stripe :max-height="400">
        <el-table-column label="时间" prop="occurredAt" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.occurredAt) }}
          </template>
        </el-table-column>
        <el-table-column label="模型名称" prop="modelName" min-width="180" show-overflow-tooltip />
        <el-table-column label="服务类型" prop="serviceType" width="120">
          <template #default="scope">
            <el-tag size="small">{{ getServiceTypeLabel(scope.row.serviceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提供商" prop="provider" width="120" />
        <el-table-column label="输入 Token" prop="promptTokens" width="100" align="right">
          <template #default="scope">
            {{ formatNumber(scope.row.promptTokens) }}
          </template>
        </el-table-column>
        <el-table-column label="输出 Token" prop="completionTokens" width="100" align="right">
          <template #default="scope">
            {{ formatNumber(scope.row.completionTokens) }}
          </template>
        </el-table-column>
        <el-table-column label="总 Token" prop="totalTokens" width="100" align="right">
          <template #default="scope">
            <el-tag type="success" size="small">{{ formatNumber(scope.row.totalTokens) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="响应时间 (ms)" prop="responseTimeMs" width="110" align="right">
          <template #default="scope">
            {{ scope.row.responseTimeMs?.toFixed(0) || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.isSuccess ? 'success' : 'danger'" size="small">
              {{ scope.row.isSuccess ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  DataAnalysis,
  Document,
  ChatDotSquare,
  Clock,
  Tickets,
  CircleCheck,
  Grid,
  Service,
  Search,
  Refresh
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import {
  getTokenUsageStatistics,
  getRecentUsage,
  getTopModels,
  getTopServiceTypes
} from '@/api/tokenUsage'
import type { TokenUsageStatistics, TokenUsageRecord } from '@/types/tokenUsage'

// 时间范围
const dateRange = ref<[string, string] | null>(null)

// 统计数据
const statistics = ref<TokenUsageStatistics>({
  startTime: '',
  endTime: '',
  totalRequests: 0,
  successfulRequests: 0,
  failedRequests: 0,
  totalTokens: 0,
  totalPromptTokens: 0,
  totalCompletionTokens: 0,
  avgResponseTimeMs: 0,
  successRate: 0,
  byModel: [],
  byServiceType: [],
  byProvider: [],
  byDay: [],
  byWeek: [],
  byMonth: [],
  byHour: [],
  byApiKey: [],
  byUser: []
})

// 最近使用记录
const recentUsage = ref<TokenUsageRecord[]>([])

// 图表引用
const modelChartRef = ref<HTMLElement | null>(null)
const serviceTypeChartRef = ref<HTMLElement | null>(null)
const dailyChartRef = ref<HTMLElement | null>(null)
const weeklyChartRef = ref<HTMLElement | null>(null)
const monthlyChartRef = ref<HTMLElement | null>(null)
const hourlyChartRef = ref<HTMLElement | null>(null)

// 图表实例
let modelChart: ECharts | null = null
let serviceTypeChart: ECharts | null = null
let dailyChart: ECharts | null = null
let weeklyChart: ECharts | null = null
let monthlyChart: ECharts | null = null
let hourlyChart: ECharts | null = null

// 格式化数字（千分位）
const formatNumber = (num?: number): string => {
  if (!num && num !== 0) return '0'
  return num.toLocaleString()
}

// 格式化时间
const formatTime = (time?: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

// 获取服务类型标签
const getServiceTypeLabel = (type?: string) => {
  if (!type) return '未知'
  const labels: Record<string, string> = {
    chat: '聊天',
    embedding: '嵌入',
    rerank: '重排序',
    tts: '语音合成',
    stt: '语音识别',
    imgGen: '图像生成',
    imgEdit: '图像编辑'
  }
  return labels[type] || type
}

// 初始化图表
const initCharts = () => {
  nextTick(() => {
    if (modelChartRef.value) {
      modelChart = echarts.init(modelChartRef.value)
      updateModelChart()
    }
    if (serviceTypeChartRef.value) {
      serviceTypeChart = echarts.init(serviceTypeChartRef.value)
      updateServiceTypeChart()
    }
    if (dailyChartRef.value) {
      dailyChart = echarts.init(dailyChartRef.value)
      updateDailyChart()
    }
    if (weeklyChartRef.value) {
      weeklyChart = echarts.init(weeklyChartRef.value)
      updateWeeklyChart()
    }
    if (monthlyChartRef.value) {
      monthlyChart = echarts.init(monthlyChartRef.value)
      updateMonthlyChart()
    }
    if (hourlyChartRef.value) {
      hourlyChart = echarts.init(hourlyChartRef.value)
      updateHourlyChart()
    }
  })
}

// 更新模型图表
const updateModelChart = () => {
  if (!modelChart) return

  const data = (statistics.value.byModel || [])
    .slice(0, 10)
    .map(item => ({
      name: item.modelName,
      value: item.totalTokens
    }))

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      type: 'scroll'
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        left: '15%',
        data: data,
        label: {
          show: true,
          formatter: '{b}: {c}'
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          }
        }
      }
    ]
  }

  modelChart.setOption(option)
}

// 更新服务类型图表
const updateServiceTypeChart = () => {
  if (!serviceTypeChart) return

  const data = (statistics.value.byServiceType || []).map(item => ({
    name: getServiceTypeLabel(item.serviceType),
    value: item.totalTokens
  }))

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      type: 'category',
      data: data.map(item => item.name),
      axisLabel: {
        interval: 0,
        rotate: 0
      }
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        type: 'bar',
        data: data.map(item => item.value),
        itemStyle: {
          color: '#409EFF'
        },
        label: {
          show: true,
          position: 'top'
        }
      }
    ]
  }

  serviceTypeChart.setOption(option)
}

// 更新每日趋势图表
const updateDailyChart = () => {
  if (!dailyChart) return

  const data = (statistics.value.byDay || []).map(item => ({
    date: item.date,
    totalTokens: item.totalTokens,
    promptTokens: item.promptTokens,
    completionTokens: item.completionTokens
  }))

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: ['输入 Token', '输出 Token', '总 Token']
    },
    xAxis: {
      type: 'category',
      data: data.map(item => item.date),
      boundaryGap: false
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '输入 Token',
        type: 'line',
        stack: 'Total',
        areaStyle: { opacity: 0.3 },
        data: data.map(item => item.promptTokens),
        itemStyle: { color: '#67C23A' }
      },
      {
        name: '输出 Token',
        type: 'line',
        stack: 'Total',
        areaStyle: { opacity: 0.3 },
        data: data.map(item => item.completionTokens),
        itemStyle: { color: '#E6A23C' }
      },
      {
        name: '总 Token',
        type: 'line',
        data: data.map(item => item.totalTokens),
        itemStyle: { color: '#409EFF' },
        lineStyle: { width: 3 }
      }
    ]
  }

  dailyChart.setOption(option)
}

// 更新每周趋势图表
const updateWeeklyChart = () => {
  if (!weeklyChart) return

  const data = (statistics.value.byWeek || []).map(item => ({
    week: item.weekLabel,
    totalTokens: item.totalTokens
  }))

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: data.map(item => item.week),
      boundaryGap: false
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '总 Token',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.3 },
        data: data.map(item => item.totalTokens),
        itemStyle: { color: '#409EFF' }
      }
    ]
  }

  weeklyChart.setOption(option)
}

// 更新每月趋势图表
const updateMonthlyChart = () => {
  if (!monthlyChart) return

  const data = (statistics.value.byMonth || []).map(item => ({
    month: item.monthLabel,
    totalTokens: item.totalTokens
  }))

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    xAxis: {
      type: 'category',
      data: data.map(item => item.month),
      boundaryGap: false
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '总 Token',
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.3 },
        data: data.map(item => item.totalTokens),
        itemStyle: { color: '#E6A23C' }
      }
    ]
  }

  monthlyChart.setOption(option)
}

// 更新小时分布图表
const updateHourlyChart = () => {
  if (!hourlyChart) return

  // 初始化 24 小时数据
  const hourlyData = new Array(24).fill(0)
  ;(statistics.value.byHour || []).forEach(item => {
    if (item.hour >= 0 && item.hour < 24) {
      hourlyData[item.hour] = item.totalTokens
    }
  })

  const hours = Array.from({ length: 24 }, (_, i) => `${i.toString().padStart(2, '0')}:00`)

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      type: 'category',
      data: hours
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: 'Token 使用量',
        type: 'bar',
        data: hourlyData,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#83bff6' },
            { offset: 0.5, color: '#188df0' },
            { offset: 1, color: '#188df0' }
          ])
        },
        label: {
          show: false
        }
      }
    ]
  }

  hourlyChart.setOption(option)
}

// 加载数据
const loadData = async () => {
  try {
    let startTime: string | undefined
    let endTime: string | undefined

    if (dateRange.value && dateRange.value.length === 2) {
      // 转换为 ISO-8601 格式 (YYYY-MM-DDTHH:mm:ss)
      startTime = dateRange.value[0].replace(' ', 'T')
      endTime = dateRange.value[1].replace(' ', 'T')
    }

    // 加载统计数据
    statistics.value = await getTokenUsageStatistics(startTime, endTime)

    // 加载最近使用记录
    recentUsage.value = await getRecentUsage(50)

    // 更新图表
    initCharts()
  } catch (error: any) {
    console.error('加载统计数据失败:', error)
    ElMessage.error('加载统计数据失败：' + (error.message || '未知错误'))
  }
}

// 日期变化处理
const handleDateChange = () => {
  loadData()
}

// 重置
const handleReset = () => {
  dateRange.value = null
  loadData()
}

// 窗口大小变化时重新渲染图表
const handleResize = () => {
  modelChart?.resize()
  serviceTypeChart?.resize()
  dailyChart?.resize()
  weeklyChart?.resize()
  monthlyChart?.resize()
  hourlyChart?.resize()
}

// 初始化
onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

// 组件卸载时清理
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  modelChart?.dispose()
  serviceTypeChart?.dispose()
  dailyChart?.dispose()
  weeklyChart?.dispose()
  monthlyChart?.dispose()
  hourlyChart?.dispose()
})
</script>

<style scoped>
.token-usage-statistics {
  padding: 20px;
}

.token-usage-statistics .filter-card {
  margin-bottom: 20px;
}

.token-usage-statistics .filter-card .filter-form {
  display: flex;
  justify-content: center;
}

.token-usage-statistics .stats-row {
  margin-bottom: 20px;
}

.token-usage-statistics .stats-row .stat-card .stat-content {
  display: flex;
  align-items: center;
}

.token-usage-statistics .stats-row .stat-card .stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 15px;
  color: white;
  font-size: 28px;
}

.token-usage-statistics .stats-row .stat-card .stat-info .stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

.token-usage-statistics .stats-row .stat-card .stat-info .stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 5px;
}

.token-usage-statistics .chart-container {
  height: 300px;
  width: 100%;
}

.token-usage-statistics .chart-title {
  font-size: 16px;
  font-weight: bold;
}
</style>
