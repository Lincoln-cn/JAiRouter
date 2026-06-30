<template>
  <div class="exception-statistics">
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

    <!-- 统计概览 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #F56C6C;">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.totalEvents }}</div>
              <div class="stat-label">异常总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #409EFF;">
              <el-icon><DataAnalysis /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.totalTypes }}</div>
              <div class="stat-label">异常类型数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #E6A23C;">
              <el-icon><TrendCharts /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ topErrorType?.count || 0 }}</div>
              <div class="stat-label">最多错误类型</div>
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
              <div class="stat-value">{{ successRate }}%</div>
              <div class="stat-label">成功率</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="20">
      <!-- 按类型统计 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">异常类型分布</span>
          </template>
          <div ref="typeChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 按分类统计 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">错误分类统计</span>
          </template>
          <div ref="categoryChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top: 20px;">
      <!-- 按 HTTP 状态码统计 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">HTTP 状态码分布</span>
          </template>
          <div ref="httpStatusChartRef" class="chart-container"></div>
        </el-card>
      </el-col>

      <!-- 小时分布 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <span class="chart-title">24 小时异常分布</span>
          </template>
          <div ref="hourlyChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Top 客户端 IP -->
    <el-card shadow="hover" style="margin-top: 20px;">
      <template #header>
        <span class="chart-title">Top 客户端 IP</span>
      </template>
      <el-table :data="topClientIps" border stripe :max-height="300">
        <el-table-column label="排名" type="index" width="60" :index="indexMethod" />
        <el-table-column label="IP 地址" prop="ip" min-width="200" />
        <el-table-column label="异常次数" prop="count" width="120" align="right">
          <template #default="scope">
            <el-tag type="info">{{ scope.row.count }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="占比" prop="percentage" width="120" align="right">
          <template #default="scope">
            <el-progress
              :percentage="calculatePercentage(scope.row.count)"
              :color="getProgressColor(calculatePercentage(scope.row.count))"
            />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 最近异常事件 -->
    <el-card shadow="hover" style="margin-top: 20px;">
      <template #header>
        <div class="card-header">
          <span class="chart-title">最近异常事件</span>
          <el-button link type="primary" @click="goToList">查看全部</el-button>
        </div>
      </template>
      <el-table :data="recentEvents" border stripe :max-height="300">
        <el-table-column label="事件 ID" prop="eventId" width="200" show-overflow-tooltip />
        <el-table-column label="异常类型" prop="exceptionType" min-width="180" show-overflow-tooltip />
        <el-table-column label="错误代码" prop="errorCode" width="100">
          <template #default="scope">
            <el-tag :type="getErrorTagType(scope.row.errorCode)" size="small">
              {{ scope.row.errorCode }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="错误分类" prop="errorCategory" width="120">
          <template #default="scope">
            <el-tag :type="getCategoryTagType(scope.row.errorCategory)" size="small">
              {{ formatCategory(scope.row.errorCategory) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发生时间" prop="occurredAt" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.occurredAt) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Warning,
  DataAnalysis,
  TrendCharts,
  CircleCheck,
  Search,
  Refresh
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import {
  getExceptionStatistics,
  getRecentExceptionEvents
} from '@/api/exception'
import type { ExceptionEvent, ExceptionStatistics } from '@/types/exception'

const router = useRouter()

// 时间范围
const dateRange = ref<[string, string] | null>(null)

// 统计数据
const statistics = ref<ExceptionStatistics>({
  startTime: '',
  endTime: '',
  totalEvents: 0,
  totalTypes: 0,
  eventsByType: {},
  eventsByCategory: {},
  eventsByOperation: {},
  eventsByHttpStatus: {},
  topClientIps: [],
  hourlyDistribution: []
})

// 最近异常事件
const recentEvents = ref<ExceptionEvent[]>([])

// 图表引用
const typeChartRef = ref<HTMLElement | null>(null)
const categoryChartRef = ref<HTMLElement | null>(null)
const httpStatusChartRef = ref<HTMLElement | null>(null)
const hourlyChartRef = ref<HTMLElement | null>(null)

// 图表实例
let typeChart: ECharts | null = null
let categoryChart: ECharts | null = null
let httpStatusChart: ECharts | null = null
let hourlyChart: ECharts | null = null

// 计算最多错误类型
const topErrorType = computed(() => {
  const entries = Object.entries(statistics.value.eventsByType)
  if (entries.length === 0) return { type: '-', count: 0 }
  const sorted = entries.sort((a, b) => b[1] - a[1])
  return { type: sorted[0][0], count: sorted[0][1] }
})

// 计算成功率
const successRate = computed(() => {
  const total = statistics.value.totalEvents
  if (total === 0) return 100
  // 这里假设非 5xx 错误都算成功
  const serverErrors = statistics.value.eventsByCategory?.['SERVER_ERROR'] || 0
  return ((total - serverErrors) / total * 100).toFixed(1)
})

// Top 客户端 IP
const topClientIps = computed(() => {
  return statistics.value.topClientIps || []
})

// 索引方法
const indexMethod = (index: number) => index + 1

// 格式化时间
const formatTime = (time?: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

// 获取错误标签类型
const getErrorTagType = (code?: string) => {
  if (!code) return 'info'
  const num = parseInt(code)
  if (num >= 500) return 'danger'
  if (num >= 400) return 'warning'
  return 'info'
}

// 获取分类标签类型
const getCategoryTagType = (category?: string) => {
  switch (category) {
    case 'SERVER_ERROR':
      return 'danger'
    case 'CLIENT_ERROR':
      return 'warning'
    case 'SECURITY_ERROR':
      return 'danger'
    default:
      return 'info'
  }
}

// 格式化分类
const formatCategory = (category?: string) => {
  if (!category) return '-'
  return category.replace(/_/g, '')
}

// 计算百分比
const calculatePercentage = (count: number) => {
  const total = statistics.value.totalEvents
  if (total === 0) return 0
  return Math.round((count / total) * 100)
}

// 获取进度条颜色
const getProgressColor = (percentage: number) => {
  if (percentage >= 50) return '#F56C6C'
  if (percentage >= 30) return '#E6A23C'
  return '#67C23A'
}

// 初始化图表
const initCharts = () => {
  nextTick(() => {
    // 类型分布图
    if (typeChartRef.value) {
      typeChart = echarts.init(typeChartRef.value)
      updateTypeChart()
    }

    // 分类统计图
    if (categoryChartRef.value) {
      categoryChart = echarts.init(categoryChartRef.value)
      updateCategoryChart()
    }

    // HTTP 状态码图
    if (httpStatusChartRef.value) {
      httpStatusChart = echarts.init(httpStatusChartRef.value)
      updateHttpStatusChart()
    }

    // 小时分布图
    if (hourlyChartRef.value) {
      hourlyChart = echarts.init(hourlyChartRef.value)
      updateHourlyChart()
    }
  })
}

// 更新类型分布图
const updateTypeChart = () => {
  if (!typeChart) return

  const data = Object.entries(statistics.value.eventsByType || {})
    .map(([type, count]) => ({ 
      name: type, 
      value: count,
      // 简化显示名称：只显示类名，去掉包名
      shortName: type.includes('.') ? type.substring(type.lastIndexOf('.') + 1) : type
    }))
    .sort((a, b) => b.value - a.value)
    .slice(0, 10) // 只显示前 10

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        return `<div style="max-width: 300px;">
          <div style="font-weight: bold; margin-bottom: 5px;">${params.data.shortName}</div>
          <div style="font-size: 12px; color: #999;">${params.name}</div>
          <div style="margin-top: 5px;">数量：<strong>${params.value}</strong> (${params.percent}%)</div>
        </div>`
      }
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      type: 'scroll',
      formatter: (name: string) => {
        const item = data.find(d => d.name === name)
        return item ? item.shortName : name
      },
      textStyle: {
        width: 150,
        overflow: 'truncate'
      }
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        left: '15%',
        data: data.map(d => ({ ...d, name: d.shortName })),
        label: {
          show: true,
          position: 'outside',
          formatter: (params: any) => {
            return `{name|${params.name}}\n{value|${params.value} (${params.percent}%)}`
          },
          rich: {
            name: {
              fontSize: 12,
              lineHeight: 18,
              width: 120,
              overflow: 'truncate'
            },
            value: {
              fontSize: 11,
              color: '#666',
              lineHeight: 16
            }
          }
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 10,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 0, 0, 0.5)'
          },
          label: {
            fontSize: 14,
            fontWeight: 'bold'
          }
        }
      }
    ]
  }

  typeChart.setOption(option)
}

// 更新分类统计图
const updateCategoryChart = () => {
  if (!categoryChart) return

  const data = Object.entries(statistics.value.eventsByCategory || {})
    .map(([category, count]) => ({ name: formatCategory(category), value: count }))

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

  categoryChart.setOption(option)
}

// 更新 HTTP 状态码图
const updateHttpStatusChart = () => {
  if (!httpStatusChart) return

  const data = Object.entries(statistics.value.eventsByHttpStatus || {})
    .map(([status, count]) => ({ name: status, value: count }))
    .sort((a, b) => parseInt(a.name) - parseInt(b.name))

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    xAxis: {
      type: 'category',
      data: data.map(item => item.name)
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        type: 'bar',
        data: data.map(item => item.value),
        itemStyle: {
          color: (params: any) => {
            const status = parseInt(params.name)
            if (status >= 500) return '#F56C6C'
            if (status >= 400) return '#E6A23C'
            return '#67C23A'
          }
        },
        label: {
          show: true,
          position: 'top'
        }
      }
    ]
  }

  httpStatusChart.setOption(option)
}

// 更新小时分布图
const updateHourlyChart = () => {
  if (!hourlyChart) return

  // 初始化 24 小时数据
  const hourlyData = new Array(24).fill(0)
  ;(statistics.value.hourlyDistribution || []).forEach(item => {
    const hour = parseInt(item.hour)
    if (!isNaN(hour) && hour >= 0 && hour < 24) {
      hourlyData[hour] = item.count
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
      data: hours,
      axisLabel: {
        interval: 1,
        rotate: 45
      }
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        type: 'line',
        data: hourlyData,
        smooth: true,
        areaStyle: {
          opacity: 0.3
        },
        itemStyle: {
          color: '#E6A23C'
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
      startTime = dateRange.value[0]
      endTime = dateRange.value[1]
    }

    // 加载统计数据
    const stats = await getExceptionStatistics(startTime, endTime)
    statistics.value = stats

    // 加载最近异常事件
    recentEvents.value = await getRecentExceptionEvents(10)

    // 更新图表
    initCharts()
  } catch (error: any) {
    console.error('加载统计数据失败:', error)
    ElMessage.error(`加载统计数据失败：${  error.message || '未知错误'}`)
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

// 跳转到列表页
const goToList = () => {
  router.push('/exceptions/list')
}

// 窗口大小变化时重新渲染图表
const handleResize = () => {
  typeChart?.resize()
  categoryChart?.resize()
  httpStatusChart?.resize()
  hourlyChart?.resize()
}

// 初始化
onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

// 组件卸载时清理
import { onBeforeUnmount } from 'vue'
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  typeChart?.dispose()
  categoryChart?.dispose()
  httpStatusChart?.dispose()
  hourlyChart?.dispose()
})
</script>

<style scoped>
.exception-statistics {
  padding: 20px;
}

.exception-statistics .filter-card {
  margin-bottom: 20px;
}

.exception-statistics .filter-card .filter-form {
  display: flex;
  justify-content: center;
}

.exception-statistics .stats-row {
  margin-bottom: 20px;
}

.exception-statistics .stats-row .stat-card .stat-content {
  display: flex;
  align-items: center;
}

.exception-statistics .stats-row .stat-card .stat-icon {
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

.exception-statistics .stats-row .stat-card .stat-info .stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

.exception-statistics .stats-row .stat-card .stat-info .stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 5px;
}

.exception-statistics .chart-container {
  height: 300px;
  width: 100%;
}

.exception-statistics .chart-title {
  font-size: 16px;
  font-weight: bold;
}

.exception-statistics .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
