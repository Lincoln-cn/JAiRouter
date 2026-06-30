<template>
  <div class="trace-detail" v-loading="loading">
    <template v-if="traceChain">
      <!-- 追踪概要 -->
      <el-card class="summary-card" shadow="never">
        <el-descriptions :column="4" border>
          <el-descriptions-item label="Trace ID">
            <div class="trace-id-cell">
              <code>{{ traceChain.traceId }}</code>
              <el-button text type="primary" size="small" @click="copyTraceId">
                <el-icon><CopyDocument /></el-icon>
              </el-button>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="服务">{{ traceChain.serviceName }}</el-descriptions-item>
          <el-descriptions-item label="总耗时">
            <span :class="{ 'text-danger': traceChain.stats?.totalDuration > 1000 }">
              {{ Math.round(traceChain.stats?.totalDuration || 0) }}ms
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="Span数">{{ traceChain.stats?.totalSpans || 0 }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(traceChain.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="hasError ? 'danger' : 'success'">
              {{ hasError ? '错误' : '成功' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="错误数" v-if="traceChain.stats?.errorCount > 0">
            <el-tag type="danger">{{ traceChain.stats.errorCount }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="平均耗时">{{ Math.round(traceChain.stats?.avgDuration || 0) }}ms</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 追踪链路时序图 -->
      <el-card class="timeline-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>追踪链路时序图</span>
            <el-button-group>
              <el-button size="small" @click="exportTrace">
                <el-icon><Download /></el-icon>
                导出
              </el-button>
            </el-button-group>
          </div>
        </template>
        <div ref="ganttChart" class="gantt-chart"></div>
      </el-card>

      <!-- Span 详情列表 -->
      <el-card class="spans-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>Span 详情 ({{ sortedSpans.length }})</span>
            <el-input
              v-model="spanSearch"
              placeholder="搜索操作名称"
              clearable
              style="width: 200px"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </div>
        </template>
        <el-table :data="filteredSpans" style="width: 100%" size="small" row-key="spanId">
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="span-attributes" v-if="row.attributes">
                <h5>属性</h5>
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item
                    v-for="(value, key) in row.attributes"
                    :key="key"
                    :label="key"
                  >
                    {{ formatAttributeValue(value) }}
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="operationName" label="操作" min-width="200">
            <template #default="{ row }">
              <div class="operation-cell">
                <el-icon :size="14" :color="row.error ? '#f56c6c' : '#67c23a'">
                  <component :is="row.error ? CircleCloseFilled : CircleCheckFilled" />
                </el-icon>
                <span>{{ row.operationName }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="duration" label="耗时" width="100" sortable>
            <template #default="{ row }">
              <span :class="getDurationClass(row.duration)">
                {{ Math.round(row.duration) }}ms
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="startTime" label="开始时间" width="140">
            <template #default="{ row }">
              {{ formatTime(row.startTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="statusCode" label="状态码" width="80">
            <template #default="{ row }">
              <el-tag :type="getStatusCodeType(row.statusCode)" size="small">
                {{ row.statusCode || '-' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>

    <el-empty v-else-if="!loading" description="未找到追踪数据" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  CopyDocument, Download, Search, CircleCheckFilled, CircleCloseFilled
} from '@element-plus/icons-vue'
import { getTraceChain } from '@/api/tracing'

const props = defineProps<{
  traceId: string
}>()

const loading = ref(false)
const traceChain = ref<any>(null)
const spanSearch = ref('')
const ganttChart = ref<HTMLElement | null>(null)
let ganttChartInstance: echarts.ECharts | null = null

const hasError = computed(() => {
  return traceChain.value?.spans?.some((s: any) => s.error) || false
})

const sortedSpans = computed(() => {
  if (!traceChain.value?.spans) return []
  return [...traceChain.value.spans].sort((a: any, b: any) =>
    new Date(a.startTime).getTime() - new Date(b.startTime).getTime()
  )
})

const filteredSpans = computed(() => {
  if (!spanSearch.value) return sortedSpans.value
  const search = spanSearch.value.toLowerCase()
  return sortedSpans.value.filter((s: any) =>
    s.operationName?.toLowerCase().includes(search)
  )
})

const loadTraceChain = async () => {
  if (!props.traceId) return

  loading.value = true
  try {
    const response = await getTraceChain(props.traceId)
    const data = response.data?.data || response.data || response
    if (data) {
      traceChain.value = data
      await nextTick()
      renderGanttChart()
    }
  } catch (error) {
    console.error('加载追踪链路失败:', error)
    ElMessage.error('加载追踪链路失败')
  } finally {
    loading.value = false
  }
}

const renderGanttChart = () => {
  if (!ganttChart.value || !traceChain.value?.spans) return

  ganttChartInstance = echarts.init(ganttChart.value)

  const spans = sortedSpans.value
  if (spans.length === 0) return

  const baseTime = new Date(spans[0].startTime).getTime()
  const ganttData = spans.map((span: any, index: number) => {
    const startTime = new Date(span.startTime).getTime()
    const duration = span.duration
    const relativeStart = startTime - baseTime

    return {
      name: span.operationName,
      spanId: span.spanId,
      value: [index, relativeStart, relativeStart + duration, duration],
      itemStyle: {
        color: span.error ? '#f56c6c' : getSpanColor(span.operationName)
      }
    }
  })

  const categories = spans.map((span: any) => {
    const name = span.operationName || ''
    return name.length > 30 ? `${name.substring(0, 30)  }...` : name
  })

  const maxDuration = Math.max(...ganttData.map((d: any) => d.value[2]))

  const option = {
    tooltip: {
      formatter: (params: any) => {
        const data = params.data
        return `
          <div style="max-width: 300px;">
            <div style="font-weight: bold; margin-bottom: 8px;">${data.name}</div>
            <div>Span ID: ${data.spanId?.substring(0, 16)}...</div>
            <div>开始偏移: +${Math.round(data.value[1])}ms</div>
            <div>持续时间: ${Math.round(data.value[3])}ms</div>
          </div>
        `
      }
    },
    grid: {
      left: '25%',
      right: '10%',
      top: '10%',
      bottom: '10%'
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
      }
    },
    yAxis: {
      type: 'category',
      data: categories,
      axisLabel: {
        fontSize: 11,
        width: 150,
        overflow: 'truncate'
      },
      axisTick: { show: false },
      axisLine: { show: false }
    },
    series: [{
      type: 'custom',
      renderItem: (params: any, api: any) => {
        const categoryIndex = api.value(0)
        const start = api.coord([api.value(1), categoryIndex])
        const end = api.coord([api.value(2), categoryIndex])
        const height = api.size([0, 1])[1] * 0.6

        return {
          type: 'rect',
          shape: {
            x: start[0],
            y: start[1] - height / 2,
            width: Math.max(end[0] - start[0], 2),
            height
          },
          style: {
            ...api.style(),
            shadowBlur: 2,
            shadowColor: 'rgba(0, 0, 0, 0.1)'
          }
        }
      },
      data: ganttData
    }]
  }

  ganttChartInstance.setOption(option)
}

const getSpanColor = (operationName: string) => {
  if (operationName.includes('HTTP')) return '#409eff'
  if (operationName.includes('adapter')) return '#67c23a'
  if (operationName.includes('backend')) return '#e6a23c'
  if (operationName.includes('gateway')) return '#909399'
  return '#409eff'
}

const copyTraceId = async () => {
  try {
    await navigator.clipboard.writeText(props.traceId)
    ElMessage.success('Trace ID 已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

const exportTrace = () => {
  if (!traceChain.value) return

  const dataStr = JSON.stringify(traceChain.value, null, 2)
  const blob = new Blob([dataStr], { type: 'application/json' })
  const url = URL.createObjectURL(blob)

  const link = document.createElement('a')
  link.href = url
  link.download = `trace-${props.traceId}.json`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)

  ElMessage.success('追踪数据已导出')
}

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

const formatAttributeValue = (value: any) => {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

const getDurationClass = (duration: number) => {
  if (duration > 1000) return 'text-danger'
  if (duration > 500) return 'text-warning'
  return ''
}

const getStatusCodeType = (statusCode: string) => {
  if (!statusCode) return 'info'
  const code = parseInt(statusCode)
  if (code >= 200 && code < 300) return 'success'
  if (code >= 400 && code < 500) return 'warning'
  if (code >= 500) return 'danger'
  return 'info'
}

watch(() => props.traceId, () => {
  loadTraceChain()
}, { immediate: true })

onMounted(() => {
  loadTraceChain()
})
</script>

<style scoped>
.trace-detail {
  padding: 0;
}

.summary-card {
  margin-bottom: 16px;
}

.trace-id-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.trace-id-cell code {
  font-family: monospace;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
}

.timeline-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.gantt-chart {
  height: 300px;
}

.spans-card {
  margin-bottom: 16px;
}

.operation-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.span-attributes {
  padding: 12px;
  background: #f5f7fa;
}

.span-attributes h5 {
  margin: 0 0 12px 0;
  color: #606266;
}

.text-danger {
  color: #f56c6c;
  font-weight: 500;
}

.text-warning {
  color: #e6a23c;
}
</style>