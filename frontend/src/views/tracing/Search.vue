<template>
  <div class="tracing-search">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>追踪搜索</span>
          <el-button type="primary" @click="handleSearch" :loading="searching">搜索</el-button>
        </div>
      </template>

      <!-- 搜索条件 -->
      <el-form :model="searchForm" :inline="true" class="search-form">
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="searchForm.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DD HH:mm:ss"
            :shortcuts="timeShortcuts"
          />
        </el-form-item>
        
        <el-form-item label="服务名称">
          <el-select v-model="searchForm.serviceName" placeholder="请选择服务" clearable filterable>
            <el-option
              v-for="service in availableServices"
              :key="service"
              :label="service"
              :value="service"
            />
          </el-select>
        </el-form-item>
        
        <el-form-item label="追踪ID">
          <el-input v-model="searchForm.traceId" placeholder="请输入追踪ID" clearable />
        </el-form-item>
        
        <el-form-item label="状态">
          <el-select v-model="searchForm.hasError" placeholder="请选择状态" clearable>
            <el-option label="成功" :value="false" />
            <el-option label="错误" :value="true" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 搜索结果 -->
    <el-card class="results-card">
      <template #header>
        <div class="card-header">
          <span>搜索结果 ({{ traces.length }})</span>
          <div class="header-actions">
            <el-button @click="handleExport" :disabled="traces.length === 0">导出</el-button>
            <el-button @click="handleGetRecent">获取最近追踪</el-button>
          </div>
        </div>
      </template>

      <el-table 
        :data="traces" 
        v-loading="searching"
        style="width: 100%" 
        empty-text="暂无追踪数据，请点击'获取最近追踪'或使用搜索条件"
        @row-click="handleRowClick"
        row-class-name="trace-row"
      >
        <el-table-column prop="traceId" label="追踪ID" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" @click.stop="handleViewTrace(row)">
              {{ row.traceId.substring(0, 16) }}...
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="serviceName" label="主服务" width="120" />
        <el-table-column prop="operationName" label="主要操作" width="200" show-overflow-tooltip />
        <el-table-column prop="spanCount" label="Span数量" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.spanCount > 1 ? 'success' : 'info'">
              {{ row.spanCount }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="总耗时(ms)" width="120" sortable>
          <template #default="{ row }">
            <span :class="{ 'high-latency': row.duration > 1000 }">{{ Math.round(row.duration) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.hasError ? 'danger' : 'success'" size="small">
              {{ row.hasError ? '错误' : '成功' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="text" @click.stop="handleViewTrace(row)">
              查看链路
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-if="traces.length > 0"
        class="pagination"
        :current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </el-card>

    <!-- 追踪详情对话框 -->
    <el-dialog v-model="traceDialogVisible" title="追踪链路详情" width="90%" top="3vh">
      <div v-if="selectedTrace">
        <!-- 追踪概要信息 -->
        <el-card class="trace-summary">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="追踪ID">
              <el-text type="primary" style="font-family: monospace;">{{ selectedTrace.traceId }}</el-text>
            </el-descriptions-item>
            <el-descriptions-item label="主服务">{{ selectedTrace.serviceName }}</el-descriptions-item>
            <el-descriptions-item label="总耗时">
              <span :class="{ 'high-latency': (traceChain?.stats?.totalDuration || selectedTrace.duration) > 1000 }">
                {{ Math.round(traceChain?.stats?.totalDuration || selectedTrace.duration) }}ms
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="Span数量">
              <el-tag size="small" :type="(traceChain?.stats?.totalSpans || selectedTrace.spanCount) > 1 ? 'success' : 'info'">
                {{ traceChain?.stats?.totalSpans || selectedTrace.spanCount }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ formatTime(selectedTrace.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="selectedTrace.hasError ? 'danger' : 'success'">
                {{ selectedTrace.hasError ? '错误' : '成功' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="错误数量" v-if="traceChain?.stats?.errorCount > 0">
              <el-tag type="danger" size="small">{{ traceChain.stats.errorCount }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="平均耗时">
              {{ Math.round(traceChain?.stats?.avgDuration || 0) }}ms
            </el-descriptions-item>
            <el-descriptions-item label="最大耗时">
              {{ Math.round(traceChain?.stats?.maxDuration || 0) }}ms
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 追踪链路可视化 -->
        <div v-if="traceChain" class="trace-chain">
          <el-card>
            <template #header>
              <h4 style="margin: 0;">追踪链路时序图</h4>
            </template>
            <div ref="traceChainChart" class="trace-chain-chart"></div>
          </el-card>
          
          <!-- Span详情表格 -->
          <el-card style="margin-top: 20px;">
            <template #header>
              <h4 style="margin: 0;">Span详情列表</h4>
            </template>
            <el-table :data="sortedSpans" style="width: 100%" size="small" default-sort="{prop: 'startTime', order: 'ascending'}">
              <el-table-column type="index" label="#" width="50" />
              <el-table-column prop="spanId" label="Span ID" width="120" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-text style="font-family: monospace; font-size: 12px;">
                    {{ row.spanId.substring(0, 12) }}...
                  </el-text>
                </template>
              </el-table-column>
              <el-table-column prop="operationName" label="操作" show-overflow-tooltip>
                <template #default="{ row }">
                  <div>
                    <div style="font-weight: 500;">{{ row.operationName }}</div>
                    <div style="font-size: 12px; color: #909399;" v-if="row.attributes && row.attributes['service.type']">
                      服务类型: {{ row.attributes['service.type'] }}
                    </div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="duration" label="耗时(ms)" width="100" sortable>
                <template #default="{ row }">
                  <span :class="{ 'high-latency': row.duration > 500 }">
                    {{ Math.round(row.duration) }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="startTime" label="开始时间" width="140" sortable>
                <template #default="{ row }">
                  {{ formatTime(row.startTime) }}
                </template>
              </el-table-column>
              <el-table-column prop="error" label="状态" width="80">
                <template #default="{ row }">
                  <el-tag :type="row.error ? 'danger' : 'success'" size="small">
                    {{ row.error ? '错误' : '成功' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="statusCode" label="状态码" width="80">
                <template #default="{ row }">
                  <el-tag 
                    size="small" 
                    :type="getStatusCodeType(row.statusCode)"
                  >
                    {{ row.statusCode }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="text" @click="showSpanDetails(row)">
                    详情
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </div>
      </div>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="traceDialogVisible = false">关闭</el-button>
          <el-button type="primary" @click="handleExportTrace">导出此追踪</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, computed } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  searchTraces,
  getRecentTraces,
  getTraceChain,
  getServiceStats,
  exportTraces
} from '@/api/tracing'

// 搜索表单
const searchForm = ref({
  timeRange: [],
  serviceName: '',
  traceId: '',
  hasError: null
})

// 时间快捷选项
const timeShortcuts = [
  {
    text: '最近1小时',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000)
      return [start, end]
    }
  },
  {
    text: '最近6小时',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 6)
      return [start, end]
    }
  },
  {
    text: '今天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setHours(0, 0, 0, 0)
      return [start, end]
    }
  },
  {
    text: '昨天',
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24)
      start.setHours(0, 0, 0, 0)
      end.setTime(start.getTime() + 3600 * 1000 * 24 - 1)
      return [start, end]
    }
  }
]

// 状态
const searching = ref(false)
const traces = ref<any[]>([])
const availableServices = ref<string[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const totalPages = ref(0)

// 追踪详情
const traceDialogVisible = ref(false)
const selectedTrace = ref<any>(null)
const traceChain = ref<any>(null)
const traceChainChart = ref<HTMLElement | null>(null)
let traceChainChartInstance: echarts.ECharts | null = null

// 计算属性：排序后的 Span 列表
const sortedSpans = computed(() => {
  if (!traceChain.value || !traceChain.value.spans) {
    return []
  }
  
  return [...traceChain.value.spans].sort((a: any, b: any) => 
    new Date(a.startTime).getTime() - new Date(b.startTime).getTime()
  )
})

// 搜索追踪
const handleSearch = async (resetPage = true) => {
  if (resetPage) {
    currentPage.value = 1
  }
  
  searching.value = true
  try {
    const params: any = {
      page: currentPage.value,
      size: pageSize.value
    }

    if (searchForm.value.timeRange && searchForm.value.timeRange.length === 2) {
      params.startTime = searchForm.value.timeRange[0]
      params.endTime = searchForm.value.timeRange[1]
    }

    if (searchForm.value.serviceName) {
      params.serviceName = searchForm.value.serviceName
    }

    if (searchForm.value.traceId) {
      params.traceId = searchForm.value.traceId
    }

    if (searchForm.value.hasError !== null) {
      params.hasError = searchForm.value.hasError
    }

    const response = await searchTraces(params)
    console.log('搜索结果:', response)

    // 处理响应数据
    const data = response.data?.data || response.data || response
    if (data && (data as any).traces) {
      traces.value = (data as any).traces
      total.value = (data as any).total || 0
      totalPages.value = (data as any).totalPages || 0
    } else if (Array.isArray(data)) {
      // 兼容旧的数组格式
      traces.value = data
      total.value = data.length
      totalPages.value = Math.ceil(data.length / pageSize.value)
    } else {
      traces.value = []
      total.value = 0
      totalPages.value = 0
    }

    ElMessage.success(`找到 ${total.value} 条追踪记录`)
  } catch (error) {
    console.error('搜索追踪失败:', error)
    ElMessage.error('搜索追踪失败')
    
    // 使用模拟数据
    const mockData = generateMockTraces()
    traces.value = mockData.slice(0, pageSize.value)
    total.value = mockData.length
    totalPages.value = Math.ceil(mockData.length / pageSize.value)
  } finally {
    searching.value = false
  }
}

// 获取最近追踪
const handleGetRecent = async () => {
  // 清空搜索条件
  searchForm.value = {
    timeRange: [],
    serviceName: '',
    traceId: '',
    hasError: null
  }
  
  currentPage.value = 1
  searching.value = true
  
  try {
    const response = await getRecentTraces(pageSize.value * 5) // 获取更多数据用于分页
    console.log('最近追踪:', response)

    const data = response.data?.data || response.data || response
    if (Array.isArray(data)) {
      // 模拟分页
      traces.value = data.slice(0, pageSize.value)
      total.value = data.length
      totalPages.value = Math.ceil(data.length / pageSize.value)
    } else {
      traces.value = []
      total.value = 0
      totalPages.value = 0
    }

    ElMessage.success(`获取到 ${total.value} 条最近追踪`)
  } catch (error) {
    console.error('获取最近追踪失败:', error)
    ElMessage.error('获取最近追踪失败')
    
    // 使用模拟数据
    const mockData = generateMockTraces()
    traces.value = mockData.slice(0, pageSize.value)
    total.value = mockData.length
    totalPages.value = Math.ceil(mockData.length / pageSize.value)
  } finally {
    searching.value = false
  }
}

// 查看追踪详情
const handleViewTrace = async (trace: any) => {
  selectedTrace.value = trace
  traceDialogVisible.value = true

  try {
    const response = await getTraceChain(trace.traceId)
    console.log('追踪链路:', response)

    const data = response.data?.data || response.data || response
    if (data) {
      traceChain.value = data
      
      // 渲染追踪链路图表
      await nextTick()
      if (traceChainChart.value) {
        traceChainChartInstance = echarts.init(traceChainChart.value)
        traceChainChartInstance.setOption(getTraceChainChartOption())
      }
    }
  } catch (error) {
    console.error('获取追踪链路失败:', error)
    ElMessage.error('获取追踪链路失败')
    
    // 使用模拟数据
    traceChain.value = generateMockTraceChain(trace.traceId)
    
    await nextTick()
    if (traceChainChart.value) {
      traceChainChartInstance = echarts.init(traceChainChart.value)
      traceChainChartInstance.setOption(getTraceChainChartOption())
    }
  }
}

// 行点击事件
const handleRowClick = (row: any) => {
  handleViewTrace(row)
}

// 导出追踪数据
const handleExport = async () => {
  try {
    const exportRequest = {
      startTime: searchForm.value.timeRange?.[0] ? new Date(searchForm.value.timeRange[0]).toISOString() : null,
      endTime: searchForm.value.timeRange?.[1] ? new Date(searchForm.value.timeRange[1]).toISOString() : null,
      format: 'json',
      maxRecords: total.value
    }

    const response = await exportTraces(exportRequest)
    console.log('导出结果:', response)

    ElMessage.success('导出请求已提交')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  }
}

// 导出单个追踪
const handleExportTrace = () => {
  if (!selectedTrace.value) return
  
  const traceData = {
    trace: selectedTrace.value,
    chain: traceChain.value
  }
  
  const dataStr = JSON.stringify(traceData, null, 2)
  const dataBlob = new Blob([dataStr], { type: 'application/json' })
  const url = URL.createObjectURL(dataBlob)
  
  const link = document.createElement('a')
  link.href = url
  link.download = `trace-${selectedTrace.value.traceId}.json`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  
  ElMessage.success('追踪数据已下载')
}

// 分页处理
const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  handleSearch(false) // 不重置页码
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  handleSearch(false) // 不重置页码
}

// 追踪链路图表配置
const getTraceChainChartOption = () => {
  if (!traceChain.value || !traceChain.value.spans) {
    return {}
  }

  const spans = [...traceChain.value.spans].sort((a: any, b: any) => 
    new Date(a.startTime).getTime() - new Date(b.startTime).getTime()
  )
  
  // 找到最早的开始时间作为基准
  const baseTime = new Date(spans[0].startTime).getTime()
  
  // 创建甘特图数据
  const ganttData = spans.map((span: any, index: number) => {
    const startTime = new Date(span.startTime).getTime()
    const endTime = new Date(span.endTime).getTime()
    const duration = span.duration
    
    // 计算相对于基准时间的偏移
    const relativeStart = startTime - baseTime
    const relativeEnd = relativeStart + duration
    
    return {
      name: span.operationName,
      spanId: span.spanId,
      value: [
        index,
        relativeStart,
        relativeEnd,
        duration
      ],
      itemStyle: {
        color: span.error ? '#f56c6c' : (span.operationName.includes('process') ? '#67c23a' : '#409eff'),
        borderColor: '#fff',
        borderWidth: 1
      }
    }
  })

  const categories = spans.map((span: any) => {
    const shortName = span.operationName.length > 25 
      ? span.operationName.substring(0, 25) + '...' 
      : span.operationName
    return shortName
  })

  const maxDuration = Math.max(...ganttData.map(item => item.value[2]))

  return {
    title: {
      text: `追踪链路时序图 (${spans.length} 个 Span)`,
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
      left: '20%',
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
        width: 150,
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
            width: Math.max(end[0] - start[0], 2), // 最小宽度为2px
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
        data: ganttData,
        markLine: {
          silent: true,
          lineStyle: {
            color: '#ff4757',
            type: 'dashed',
            width: 1
          },
          data: spans.length > 1 ? [
            {
              name: '调用开始',
              xAxis: 0
            }
          ] : []
        }
      }
    ]
  }
}

// 生成模拟追踪数据
const generateMockTraces = () => {
  const mockServices = ['jairouter']
  const mockOperations = ['POST /v1/chat/completions', 'POST /v1/embeddings', 'GET /v1/models', 'POST /v1/rerank']
  
  return Array.from({ length: 20 }, (_, i) => {
    const hasError = Math.random() < 0.15
    const spanCount = Math.random() < 0.7 ? 2 : 1 // 70% 概率有2个span，30% 概率只有1个
    
    return {
      traceId: `trace-${Date.now()}-${i}`,
      serviceName: mockServices[0],
      operationName: mockOperations[Math.floor(Math.random() * mockOperations.length)],
      duration: Math.floor(Math.random() * 2000) + 50,
      spanCount,
      startTime: new Date(Date.now() - Math.random() * 86400000).toISOString(),
      hasError
    }
  })
}

// 生成模拟追踪链路数据
const generateMockTraceChain = (traceId: string) => {
  const baseTime = new Date()
  const rootSpanStart = baseTime.toISOString()
  const childSpanStart = new Date(baseTime.getTime() + 10).toISOString()
  const rootSpanEnd = new Date(baseTime.getTime() + 1250).toISOString()
  const childSpanEnd = new Date(baseTime.getTime() + 1200).toISOString()
  
  return {
    traceId,
    serviceName: 'jairouter',
    spans: [
      {
        spanId: 'root-span-001',
        traceId,
        operationName: 'POST /v1/chat/completions',
        startTime: rootSpanStart,
        endTime: rootSpanEnd,
        duration: 1250,
        error: false,
        statusCode: '200',
        attributes: {
          'http.method': 'POST',
          'http.url': 'http://localhost:8080/v1/chat/completions',
          'http.status_code': 200,
          'trace.trace_id': traceId,
          'trace.span_id': 'root-span-001'
        }
      },
      {
        spanId: 'child-span-001',
        traceId,
        operationName: 'chat.process',
        startTime: childSpanStart,
        endTime: childSpanEnd,
        duration: 1190,
        error: false,
        statusCode: '200',
        attributes: {
          'service.type': 'chat',
          'model.name': 'gpt-3.5-turbo',
          'trace.trace_id': traceId,
          'trace.span_id': 'child-span-001'
        }
      }
    ],
    stats: {
      totalSpans: 2,
      totalDuration: 1250,
      avgDuration: 1220,
      maxDuration: 1250,
      errorCount: 0,
      depth: 2
    },
    startTime: rootSpanStart
  }
}

// 加载可用服务列表
const loadAvailableServices = async () => {
  try {
    const response = await getServiceStats()
    const data = response.data?.data || response.data || response

    if (Array.isArray(data)) {
      availableServices.value = data.map((item: any) => item.name || item)
    } else if ((data as any).services && Array.isArray((data as any).services)) {
      availableServices.value = (data as any).services
    } else {
      availableServices.value = ['jairouter', 'model-adapter-service', 'load-balancer-service', 'rate-limiter-service']
    }
  } catch (error) {
    console.error('加载服务列表失败:', error)
    availableServices.value = ['jairouter-gateway', 'model-adapter-service', 'load-balancer-service', 'rate-limiter-service']
  }
}

// 时间格式化
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

// 状态码类型判断
const getStatusCodeType = (statusCode: string) => {
  if (!statusCode) return 'info'
  const code = parseInt(statusCode)
  if (code >= 200 && code < 300) return 'success'
  if (code >= 300 && code < 400) return 'warning'
  if (code >= 400) return 'danger'
  return 'info'
}

// 显示 Span 详情
const showSpanDetails = (span: any) => {
  const details = {
    spanId: span.spanId,
    traceId: span.traceId,
    operationName: span.operationName,
    duration: span.duration,
    startTime: span.startTime,
    endTime: span.endTime,
    error: span.error,
    statusCode: span.statusCode,
    attributes: span.attributes
  }
  
  ElMessage({
    message: `Span详情已复制到控制台`,
    type: 'info'
  })
  
  console.log('Span详情:', details)
}

// 组件挂载时初始化
onMounted(() => {
  loadAvailableServices()
  handleGetRecent() // 默认加载最近的追踪
})
</script>

<style scoped>
.tracing-search {
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

.search-form {
  margin-bottom: 20px;
}

.results-card {
  margin-top: 20px;
}

.high-latency {
  color: #e6a23c;
  font-weight: bold;
}

.pagination {
  margin-top: 20px;
  text-align: right;
}

.trace-chain {
  margin-top: 20px;
}

.trace-chain-chart {
  height: 300px;
  margin: 20px 0;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.trace-summary {
  margin-bottom: 20px;
}

.trace-row {
  cursor: pointer;
}

.trace-row:hover {
  background-color: #f5f7fa;
}

:deep(.el-descriptions__label) {
  font-weight: 600;
}

:deep(.el-card__header) {
  padding: 15px 20px;
  border-bottom: 1px solid #ebeef5;
}

.search-form :deep(.el-form-item) {
  margin-bottom: 15px;
}

/* 多 Span 追踪的特殊样式 */
.multi-span-indicator {
  background: linear-gradient(45deg, #67c23a, #409eff);
  color: white;
  font-weight: bold;
}

.single-span-indicator {
  background: #909399;
  color: white;
}

/* 追踪链路图表容器 */
.trace-chain-chart {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
}

/* Span 详情表格样式 */
:deep(.el-table .el-table__row:hover > td) {
  background-color: #f0f9ff !important;
}

/* 高延迟警告样式 */
.high-latency {
  color: #e6a23c;
  font-weight: bold;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0% { opacity: 1; }
  50% { opacity: 0.7; }
  100% { opacity: 1; }
}

/* 状态标签样式优化 */
:deep(.el-tag--success) {
  background-color: #f0f9ff;
  border-color: #409eff;
  color: #409eff;
}

:deep(.el-tag--danger) {
  background-color: #fef0f0;
  border-color: #f56c6c;
  color: #f56c6c;
}

/* 追踪概要信息样式 */
.trace-summary :deep(.el-descriptions__label) {
  font-weight: 600;
  color: #303133;
}

.trace-summary :deep(.el-descriptions__content) {
  color: #606266;
}
</style>