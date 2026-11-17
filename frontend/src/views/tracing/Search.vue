<template>
  <ListPageLayout
    :loading="searching"
    :empty="!searching && traces.length === 0"
    :empty-title="t('tracing.search.emptyTitle')"
    :empty-description="t('tracing.search.emptyDescription')"
  >
    <template #header>
      <h2 class="page-title">{{ t('tracing.search.title') }}</h2>
      <p class="page-subtitle">{{ t('tracing.search.pageDescription') }}</p>
    </template>

    <template #actions>
      <!-- 列表页统一动作区：先刷新、再导出（查询按钮在筛选区末尾） -->
      <el-button @click="handleSearch" :loading="searching">
        <el-icon><Refresh /></el-icon>
        {{ t('tracing.search.refresh') }}
      </el-button>
      <el-button @click="handleExport" :disabled="traces.length === 0">{{ t('tracing.search.export') }}</el-button>
    </template>

    <!-- 搜索条件 -->
    <template #filters>
      <FilterBar>
        <el-form :model="searchForm" :inline="true" class="search-form">
          <el-form-item :label="t('tracing.search.timeRange')">
            <el-date-picker
              v-model="searchForm.timeRange"
              type="datetimerange"
              :range-separator="t('tracing.search.timeRangeTo')"
              :start-placeholder="t('tracing.search.startTimePlaceholder')"
              :end-placeholder="t('tracing.search.endTimePlaceholder')"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
              :shortcuts="timeShortcuts"
            />
          </el-form-item>

          <el-form-item :label="t('tracing.search.serviceName')">
            <el-select
              v-model="searchForm.serviceName"
              class="filter-select filter-select--service"
              :placeholder="t('tracing.search.serviceNamePlaceholder')"
              clearable
              filterable
            >
              <el-option
                v-for="service in availableServices"
                :key="service"
                :label="service"
                :value="service"
              />
            </el-select>
          </el-form-item>

          <el-form-item :label="t('tracing.search.traceId')">
            <el-input
              v-model="searchForm.traceId"
              class="filter-input"
              :placeholder="t('tracing.search.traceIdPlaceholder')"
              clearable
            />
          </el-form-item>

          <el-form-item :label="t('tracing.search.status')">
            <el-select
              v-model="searchForm.hasError"
              class="filter-select filter-select--status"
              :placeholder="t('tracing.search.statusPlaceholder')"
              clearable
            >
              <el-option :label="t('tracing.search.success')" :value="false" />
              <el-option :label="t('tracing.search.error')" :value="true" />
            </el-select>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" @click="handleSearch" :loading="searching">
              <el-icon><Search /></el-icon>
              {{ t('tracing.search.search') }}
            </el-button>
          </el-form-item>
        </el-form>
      </FilterBar>
    </template>

    <!-- 结果计数与批量动作 -->
    <template #toolbar>
      <div class="card-header">
        <span>{{ t('tracing.search.resultsTitle', { count: traces.length }) }}</span>
        <div class="header-actions">
          <el-checkbox v-model="includeArchived" class="archived-toggle">
            {{ t('tracing.search.includeArchived') }}
          </el-checkbox>
          <el-button @click="handleGetRecent">{{ t('tracing.search.getRecent') }}</el-button>
        </div>
      </div>
    </template>

    <!-- 搜索结果 -->
    <el-table
      :data="traces"
      v-loading="searching"
      style="width: 100%"
      @row-click="handleRowClick"
      row-class-name="trace-row"
    >
      <el-table-column prop="traceId" :label="t('tracing.search.traceId')" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" @click.stop="handleViewTrace(row)">
            {{ row.traceId.substring(0, 16) }}...
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="serviceName" :label="t('tracing.search.primaryService')" min-width="70" />
      <el-table-column prop="operationName" :label="t('tracing.search.operationName')" min-width="180" show-overflow-tooltip />
      <el-table-column prop="spanCount" :label="t('tracing.search.spanCount')" min-width="70">
        <template #default="{ row }">
          <el-tag size="small" :type="row.spanCount > 1 ? 'success' : 'info'">
            {{ row.spanCount }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="duration" :label="t('tracing.search.durationColumn')" min-width="70" sortable>
        <template #default="{ row }">
          <span :class="{ 'high-latency': row.duration > 1000 }">{{ formatMs(row.duration) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" :label="t('tracing.search.startTime')" min-width="140">
        <template #default="{ row }">
          <TimeText :value="row.startTime" />
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="t('tracing.search.status')" min-width="70">
        <template #default="{ row }">
          <el-tag :type="row.hasError ? 'danger' : 'success'" size="small">
            {{ row.hasError ? t('tracing.search.error') : t('tracing.search.success') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('tracing.search.action')" min-width="70" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="text" @click.stop="handleViewTrace(row)">
            {{ t('tracing.search.viewTrace') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <template #footer>
      <el-pagination
        v-if="traces.length > 0"
        :current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </template>

    <!-- 追踪详情对话框（drawer 透传槽，不受空态影响） -->
    <template #drawer>
      <el-dialog v-model="traceDialogVisible" :title="t('tracing.search.detailDialogTitle')" width="90%" top="3vh" destroy-on-close @close="disposeTraceChainChart">
        <div v-if="selectedTrace">
          <!-- 追踪概要信息 -->
          <el-card class="trace-summary">
            <el-descriptions :column="3" border>
              <el-descriptions-item :label="t('tracing.search.traceId')">
                <el-text type="primary" style="font-family: monospace;">{{ selectedTrace.traceId }}</el-text>
              </el-descriptions-item>
              <el-descriptions-item :label="t('tracing.search.primaryService')">{{ selectedTrace.serviceName }}</el-descriptions-item>
              <el-descriptions-item :label="t('tracing.search.totalDurationLabel')">
                <span :class="{ 'high-latency': (traceChain?.stats?.totalDuration || selectedTrace?.duration || 0) > 1000 }">
                  {{ formatDurationWithUnit(traceChain?.stats?.totalDuration ?? selectedTrace?.duration) }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item :label="t('tracing.search.spanCount')">
                <el-tag size="small" :type="(traceChain?.stats?.totalSpans || selectedTrace?.spanCount || 0) > 1 ? 'success' : 'info'">
                  {{ traceChain?.stats?.totalSpans || selectedTrace?.spanCount || 0 }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item :label="t('tracing.search.startTime')"><TimeText :value="selectedTrace?.startTime" /></el-descriptions-item>
              <el-descriptions-item :label="t('tracing.search.status')">
                <el-tag :type="selectedTrace?.hasError ? 'danger' : 'success'">
                  {{ selectedTrace?.hasError ? t('tracing.search.error') : t('tracing.search.success') }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item :label="t('tracing.search.errorCountLabel')" v-if="(traceChain?.stats?.errorCount ?? 0) > 0">
                <el-tag type="danger" size="small">{{ traceChain?.stats?.errorCount }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item :label="t('tracing.search.avgDurationLabel')">
                {{ formatDurationWithUnit(traceChain?.stats?.avgDuration) }}
              </el-descriptions-item>
              <el-descriptions-item :label="t('tracing.search.maxDurationLabel')">
                {{ formatDurationWithUnit(traceChain?.stats?.maxDuration) }}
              </el-descriptions-item>
              <el-descriptions-item :label="t('tracing.search.gatewayDurationLabel')">
                {{ formatDurationWithUnit(gatewayDuration) }}
              </el-descriptions-item>
              <el-descriptions-item :label="t('tracing.search.upstreamDurationLabel')">
                {{ formatDurationWithUnit(upstreamDuration) }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 追踪链路可视化 -->
          <div v-if="traceChain" class="trace-chain">
            <el-card>
              <template #header>
                <h4 style="margin: 0;">{{ t('tracing.search.chartTitle') }}</h4>
              </template>
              <div ref="traceChainChart" class="trace-chain-chart"></div>
            </el-card>

            <!-- Span详情表格 -->
            <el-card style="margin-top: 20px;">
              <template #header>
                <h4 style="margin: 0;">{{ t('tracing.search.spanDetailsTitle') }}</h4>
              </template>
              <el-table :data="sortedSpans" row-key="spanId" :tree-props="{ children: 'children' }" style="width: 100%" size="small" default-sort="{prop: 'startTime', order: 'ascending'}">
                <el-table-column type="index" label="#" min-width="50" />
                <el-table-column prop="spanId" label="Span ID" min-width="70" show-overflow-tooltip>
                  <template #default="{ row }">
                    <el-text style="font-family: monospace; font-size: 12px;">
                      {{ row.spanId.substring(0, 12) }}...
                    </el-text>
                  </template>
                </el-table-column>
                <el-table-column prop="operationName" :label="t('tracing.search.operation')" show-overflow-tooltip>
                  <template #default="{ row }">
                    <div>
                      <div style="font-weight: 500;">{{ row.operationName }}</div>
                      <div style="font-size: 12px; color: var(--ja-text-secondary);" v-if="row.attributes && row.attributes['service.type']">
                        {{ t('tracing.search.serviceTypePrefix', { value: row.attributes['service.type'] }) }}
                      </div>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="duration" :label="t('tracing.search.durationMsColumn')" min-width="70" sortable>
                  <template #default="{ row }">
                    <span :class="{ 'high-latency': row.duration > 500 }">
                      {{ formatMs(row.duration) }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="spanKind" :label="t('tracing.search.spanKindLabel')" min-width="80">
                  <template #default="{ row }">
                    <el-tag size="small" :type="getSpanKindTagType(row.spanKind)">
                      {{ getSpanKindText(row.spanKind) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="startTime" :label="t('tracing.search.startTime')" min-width="140" sortable>
                  <template #default="{ row }">
                    <TimeText :value="row.startTime" />
                  </template>
                </el-table-column>
                <el-table-column prop="error" :label="t('tracing.search.status')" min-width="70">
                  <template #default="{ row }">
                    <el-tag :type="row.error ? 'danger' : 'success'" size="small">
                      {{ row.error ? t('tracing.search.error') : t('tracing.search.success') }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="statusCode" :label="t('tracing.search.statusCode')" min-width="70">
                  <template #default="{ row }">
                    <el-tag
                      size="small"
                      :type="getStatusCodeType(row.statusCode)"
                    >
                      {{ row.statusCode }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column :label="t('tracing.search.action')" min-width="70" fixed="right">
                  <template #default="{ row }">
                    <el-button size="small" type="text" @click="showSpanDetails(row)">
                      {{ t('tracing.search.detail') }}
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>
          </div>
        </div>

        <template #footer>
          <span class="dialog-footer">
            <el-button @click="traceDialogVisible = false">{{ t('tracing.search.close') }}</el-button>
            <el-button type="primary" @click="handleExportTrace">{{ t('tracing.search.exportThisTrace') }}</el-button>
          </span>
        </template>
      </el-dialog>
    </template>
  </ListPageLayout>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  searchTraces,
  getRecentTraces,
  getArchivedRecentTraces,
  type ArchivedTraceRow,
  getTraceChain,
  getServiceStats,
  exportTraces
} from '@/api/tracing'
import type { TraceSearchParams } from '@/api/tracing'
import type {
  TraceChainResponse,
  TraceSearchResult,
  TraceSpan,
  TracingServiceListResult,
  TracingServiceStat
} from '@/types/tracing'
import ListPageLayout from '@/components/ListPageLayout.vue'
import FilterBar from '@/components/FilterBar.vue'
import TimeText from '@/components/TimeText.vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { useChartTheme } from '@/composables/useChartTheme'
import { useChartAutoRefresh } from '@/composables/useChartAutoRefresh'

const { getChartTheme } = useChartTheme()
const { t } = useI18n()
const route = useRoute()

// 搜索表单
const includeArchived = ref(false)

const searchForm = ref({
  timeRange: [],
  serviceName: '',
  traceId: '',
  hasError: null
})

// 时间快捷选项
const timeShortcuts = computed(() => [
  {
    text: t('tracing.search.shortcuts.last1h'),
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000)
      return [start, end]
    }
  },
  {
    text: t('tracing.search.shortcuts.last6h'),
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 6)
      return [start, end]
    }
  },
  {
    text: t('tracing.search.shortcuts.today'),
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setHours(0, 0, 0, 0)
      return [start, end]
    }
  },
  {
    text: t('tracing.search.shortcuts.yesterday'),
    value: () => {
      const end = new Date()
      const start = new Date()
      start.setTime(start.getTime() - 3600 * 1000 * 24)
      start.setHours(0, 0, 0, 0)
      end.setTime(start.getTime() + 3600 * 1000 * 24 - 1)
      return [start, end]
    }
  }
])

// 状态
const searching = ref(false)
const traces = ref<TraceSpan[]>([])
const availableServices = ref<string[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const totalPages = ref(0)

// 追踪详情
const traceDialogVisible = ref(false)
const selectedTrace = ref<TraceSpan | null>(null)
const traceChain = ref<TraceChainResponse | null>(null)
const traceChainChart = ref<HTMLElement | null>(null)
/** 上游调用耗时合计（spanKind === 'CLIENT'），#1153 */
const upstreamDuration = computed(() =>
  (traceChain.value?.spans || [])
    .filter(span => span.spanKind === 'CLIENT')
    .reduce((sum, span) => sum + (span.duration ?? 0), 0)
)
/** 网关自身耗时 = 总耗时 − 上游调用耗时 */
const gatewayDuration = computed(() => {
  const total = traceChain.value?.stats?.totalDuration || selectedTrace.value?.duration || 0
  return Math.max(0, total - upstreamDuration.value)
})
let traceChainChartInstance: echarts.ECharts | null = null

// 计算属性：排序后的 Span 列表
// Span 节点：后端已按 parentSpanId 建立父子关系（#1151），这里组装成 el-table 的树形数据。
type SpanNode = NonNullable<NonNullable<typeof traceChain.value>['spans']>[number] & { children: SpanNode[] }

const sortedSpans = computed<SpanNode[]>(() => {
  const spans: SpanNode[] = (traceChain.value?.spans || []).map(span => ({ ...span, children: [] }))
  if (spans.length === 0) return []

  const byTime = (a: SpanNode, b: SpanNode) =>
    new Date(a.startTime ?? 0).getTime() - new Date(b.startTime ?? 0).getTime()
  const byId = new Map(spans.map(span => [span.spanId, span]))
  const roots: SpanNode[] = []
  for (const span of spans) {
    const parent = span.parentSpanId ? byId.get(span.parentSpanId) : undefined
    if (parent && parent !== span) {
      parent.children.push(span)
    } else {
      roots.push(span)
    }
  }

  const sortTree = (list: SpanNode[]) => {
    list.sort(byTime)
    for (const item of list) sortTree(item.children)
  }
  sortTree(roots)
  return roots
})

// 搜索追踪
const handleSearch = async (resetPage = true) => {
  if (resetPage) {
    currentPage.value = 1
  }
  
  searching.value = true
  try {
    const params: TraceSearchParams = {
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

    // 处理响应数据
    const data = (response.data?.data || response.data) as TraceSearchResult | TraceSpan[] | undefined
    if (data && !Array.isArray(data) && data.traces) {
      traces.value = data.traces
      total.value = data.total || 0
      totalPages.value = data.totalPages || 0
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

    ElMessage.success(t('tracing.search.messages.found', { count: total.value }))
  } catch (error) {
    console.error('搜索追踪失败:', error)
    ElMessage.error(t('tracing.search.messages.searchFailed'))
    // 失败时展示空态，不填充占位数据（issue #255）
    traces.value = []
    total.value = 0
    totalPages.value = 0
  } finally {
    searching.value = false
  }
}

/**
 * 把持久化的异常链路合并进内存列表（#1155）。
 *
 * 内存里的链路更新，所以按 traceId 去重时保留内存那份；
 * 归档只补「内存已经淘汰掉」的那些。归档读取失败不影响主列表。
 */
const mergeArchived = async (memoryData: unknown): Promise<unknown[]> => {
  const memoryRows: Array<Record<string, unknown>> = Array.isArray(memoryData)
    ? (memoryData as Array<Record<string, unknown>>)
    : (((memoryData as TraceSearchResult | undefined)?.traces ?? []) as unknown as Array<
        Record<string, unknown>
      >)
  const seen = new Set<string>()
  for (const row of memoryRows) {
    if (row.traceId) seen.add(String(row.traceId))
  }

  try {
    const archived = await getArchivedRecentTraces(pageSize.value * 5)
    const archivedRows = ((archived.data?.data || archived.data) ?? []) as ArchivedTraceRow[]
    const extra = archivedRows
      .filter(row => row.traceId && !seen.has(row.traceId))
      .map(row => ({
        traceId: row.traceId,
        serviceName: row.serviceName,
        operationName: row.operationName,
        startTime: row.startTime,
        duration: row.duration ?? 0,
        hasError: row.hasError,
        spanCount: 0,
        archived: true,
      }))
    // 归档的行本身就是异常链路，排在前面——它们才是最需要看的
    return [...extra, ...memoryRows]
  } catch {
    return memoryRows
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

    let data: unknown = (response.data?.data || response.data) as unknown
    if (includeArchived.value) {
      data = await mergeArchived(data)
    }
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

    ElMessage.success(t('tracing.search.messages.fetchedRecent', { count: total.value }))
  } catch (error) {
    console.error('获取最近追踪失败:', error)
    ElMessage.error(t('tracing.search.messages.fetchRecentFailed'))
    // 失败时展示空态，不填充占位数据（issue #255）
    traces.value = []
    total.value = 0
    totalPages.value = 0
  } finally {
    searching.value = false
  }
}

// 查看追踪详情
const handleViewTrace = async (trace: TraceSpan) => {
  selectedTrace.value = trace
  traceDialogVisible.value = true

  try {
    const response = await getTraceChain(trace.traceId ?? '')

    const data = (response.data?.data || response.data) as TraceChainResponse | undefined
    if (data) {
      traceChain.value = data
      
      // 渲染追踪链路图表
      await nextTick()
      if (traceChainChart.value) {
        traceChainChartInstance?.dispose()
        traceChainChartInstance = echarts.init(traceChainChart.value)
        traceChainChartInstance.setOption(getTraceChainChartOption())
      }
    }
  } catch (error) {
    console.error('获取追踪链路失败:', error)
    ElMessage.error(t('tracing.search.messages.fetchChainFailed'))
    // 失败时置空链路，图表随之渲染为空，不填充占位数据（issue #255）
    traceChain.value = null
    
    await nextTick()
    if (traceChainChart.value) {
      traceChainChartInstance?.dispose()
      traceChainChartInstance = echarts.init(traceChainChart.value)
      traceChainChartInstance.setOption(getTraceChainChartOption())
    }
  }
}

// 行点击事件
const handleRowClick = (row: TraceSpan) => {
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

    await exportTraces(exportRequest)

    ElMessage.success(t('tracing.search.messages.exportSubmitted'))
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error(t('tracing.search.messages.exportFailed'))
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
  
  ElMessage.success(t('tracing.search.messages.traceDownloaded'))
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

  const spans = [...traceChain.value.spans].sort((a, b) => 
    new Date(a.startTime ?? 0).getTime() - new Date(b.startTime ?? 0).getTime()
  )
  
  // 找到最早的开始时间作为基准
  const baseTime = new Date(spans[0].startTime ?? 0).getTime()
  
  // 创建甘特图数据
  const theme = getChartTheme()

  const ganttData = spans.map((span, index: number) => {
    const startTime = new Date(span.startTime ?? 0).getTime()
    const duration = span.duration ?? 0
    
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
        // #1153：按 span 类型着色，区分「网关自身」与「上游调用」
        color: span.error ? theme.danger
          : span.spanKind === 'CLIENT' ? 'var(--ja-warning, #e6a23c)'
            : span.spanKind === 'SERVER' ? 'var(--ja-text-secondary, #909399)'
              : theme.primary,
        borderColor: 'var(--ja-bg-page, #fff)',
        borderWidth: 1
      }
    }
  })

  const categories = spans.map((span) => {
    const shortName = (span.operationName ?? '').length > 25 
      ? `${(span.operationName ?? '').substring(0, 25)}...` 
      : (span.operationName ?? '')
    return shortName
  })

  const maxDuration = Math.max(...ganttData.map(item => item.value[2] ?? 0))

  return {
    title: {
      text: t('tracing.search.chartTitleWithCount', { count: spans.length }),
      left: 'center',
      textStyle: {
        fontSize: 16,
        fontWeight: 'bold'
      }
    },
    tooltip: {
      formatter: (params: unknown) => {
        // echarts 入参类型很宽，这里收成甘特条自带的那几个字段（#522）
        const data = (params as { data: { name?: string; spanId?: string; value: number[] } }).data
        const duration = data.value[3]
        const startOffset = data.value[1]
        const endOffset = data.value[2]
        
        return `
          <div style="max-width: 300px;">
            <div style="font-weight: bold; margin-bottom: 8px;">${data.name}</div>
            <div style="margin-bottom: 4px;"><strong>${t('tracing.search.tooltip.spanId')}:</strong> ${(data.spanId ?? '').substring(0, 16)}...</div>
            <div style="margin-bottom: 4px;"><strong>${t('tracing.search.tooltip.startOffset')}:</strong> +${Math.round(startOffset)}ms</div>
            <div style="margin-bottom: 4px;"><strong>${t('tracing.search.tooltip.endOffset')}:</strong> +${Math.round(endOffset)}ms</div>
            <div><strong>${t('tracing.search.tooltip.duration')}:</strong> ${Math.round(duration)}ms</div>
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
      name: t('tracing.search.timeOffset'),
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
          color: 'var(--ja-border-lighter, #e0e6ed)',
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
        renderItem: (params: unknown, api: unknown) => {
          void params
          const a = api as {
            value: (index: number) => number
            coord: (point: number[]) => number[]
            size: (range: number[]) => number[]
            style: () => Record<string, unknown>
          }
          const categoryIndex = a.value(0)
          const start = a.coord([a.value(1), categoryIndex])
          const end = a.coord([a.value(2), categoryIndex])
          const height = a.size([0, 1])[1] * 0.7
          
          const rectShape = {
            x: start[0],
            y: start[1] - height / 2,
            width: Math.max(end[0] - start[0], 2), // 最小宽度为2px
            height
          }
          
          return {
            type: 'rect',
            shape: rectShape,
            style: {
              ...a.style(),
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
            color: 'var(--ja-danger, #ff4757)',
            type: 'dashed',
            width: 1
          },
          data: spans.length > 1 ? [
            {
              name: t('tracing.search.callStart'),
              xAxis: 0
            }
          ] : []
        }
      }
    ]
  }
}

// 加载可用服务列表
const loadAvailableServices = async () => {
  try {
    const response = await getServiceStats()
    const data = (response.data?.data || response.data) as TraceSearchResult | TraceSpan[] | undefined

    if (Array.isArray(data)) {
      availableServices.value = (data as TracingServiceStat[]).map((item) => item.serviceName || '')
    } else if (!Array.isArray(data) && (data as TracingServiceListResult).services) {
      availableServices.value = (data as TracingServiceListResult).services || []
    } else {
      availableServices.value = ['jairouter', 'model-adapter-service', 'load-balancer-service', 'rate-limiter-service']
    }
  } catch (error) {
    console.error('加载服务列表失败:', error)
    availableServices.value = ['jairouter-gateway', 'model-adapter-service', 'load-balancer-service', 'rate-limiter-service']
  }
}

// 毫秒一律取整：摘要区自带 ms 单位，表格列的单位已在表头声明
const formatMs = (value?: number | null): string => {
  const num = Number(value)
  return Number.isFinite(num) ? `${Math.round(num)}` : '-'
}

/** 摘要区需要单位（表格列的单位在表头，不重复） */
const formatDurationWithUnit = (value?: number | null): string => {
  const num = Number(value)
  return Number.isFinite(num) ? `${Math.round(num)}ms` : '-'
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

// span 类型展示（#1153）：SERVER 入口 / INTERNAL 网关内部 / CLIENT 上游调用
const getSpanKindText = (kind?: string) => {
  if (kind === 'SERVER') return t('tracing.search.spanKindServer')
  if (kind === 'CLIENT') return t('tracing.search.spanKindClient')
  return t('tracing.search.spanKindInternal')
}

const getSpanKindTagType = (kind?: string) => {
  if (kind === 'SERVER') return 'info'
  if (kind === 'CLIENT') return 'warning'
  return 'primary'
}

// 显示 Span 详情
const showSpanDetails = async (span: TraceSpan) => {
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
  
  // 此前这里只弹"已复制"提示、并没有真的写剪贴板（#494 附带发现）
  try {
    await navigator.clipboard.writeText(JSON.stringify(details, null, 2))
  } catch {
    // 非安全上下文下剪贴板不可用；不影响提示
  }

  ElMessage({
    message: t('tracing.search.messages.spanDetailsCopied'),
    type: 'info'
  })
}

// 语言 / 主题切换后重建追踪链路甘特图（纯重绘，无网络请求；未查看追踪时安全跳过）
const rebuildAll = () => {
  if (traceChainChartInstance && traceChain.value?.spans) {
    traceChainChartInstance.setOption(getTraceChainChartOption())
  }
}

const disposeTraceChainChart = () => {
  traceChainChartInstance?.dispose()
  traceChainChartInstance = null
}

// 组件挂载时初始化
onMounted(() => {
  useChartAutoRefresh(rebuildAll)
  loadAvailableServices()

  // 从「管理操作」页跳过来时带 traceId（#1154）：直接按该 ID 搜索，而不是显示最近列表
  const incomingTraceId = route.query.traceId
  if (typeof incomingTraceId === 'string' && incomingTraceId.trim().length > 0) {
    searchForm.value.traceId = incomingTraceId.trim()
    handleSearch()
  } else {
    handleGetRecent() // 默认加载最近的追踪
  }
})

onBeforeUnmount(() => {
  disposeTraceChainChart()
})
</script>

<style scoped>
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.4;
}

.page-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--ja-ink-3);
  line-height: 1.5;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.search-form {
  margin-bottom: 0;
}

.search-form :deep(.el-form-item) {
  margin-bottom: 15px;
}

/* 查询控件显式宽度：el-select 在 inline 表单里会按内容收缩，状态选择器因此只剩箭头（实测 44px） */
.search-form :deep(.filter-select--service) { width: 220px; }
.search-form :deep(.filter-select--status) { width: 160px; }
.search-form :deep(.filter-input) { width: 240px; }

@media (max-width: 767px) {
  .search-form {
    display: flex;
    flex-wrap: wrap;
    row-gap: 12px;
  }

  .search-form :deep(.el-form-item) {
    margin-right: 0;
    width: 100%;
    max-width: 100%;
  }

  .search-form :deep(.el-date-editor) {
    width: 100% !important;
    max-width: 100%;
  }

  .search-form :deep(.filter-select--service),
  .search-form :deep(.filter-select--status),
  .search-form :deep(.filter-input) { width: 100%; }
}

.high-latency {
  color: var(--ja-warning);
  font-weight: bold;
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
  background-color: var(--ja-primary-light-9, #f5f7fa);
}

:deep(.el-descriptions__label) {
  font-weight: 600;
}

:deep(.el-card__header) {
  padding: 15px 20px;
  border-bottom: 1px solid var(--ja-border-light);
}

/* 多 Span 追踪的特殊样式 */
.multi-span-indicator {
  background: linear-gradient(45deg, var(--ja-success), var(--ja-primary));
  color: white;
  font-weight: bold;
}

.single-span-indicator {
  background: var(--ja-info);
  color: white;
}

/* 追踪链路图表容器 */
.trace-chain-chart {
  border: 1px solid var(--ja-border-light);
  border-radius: 4px;
  background: var(--ja-bg-page);
}

/* Span 详情表格样式 */
:deep(.el-table .el-table__row:hover > td) {
  background-color: var(--ja-primary-light-9, #f0f9ff) !important;
}

/* 高延迟警告样式 */
.high-latency {
  color: var(--ja-warning);
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
  background-color: var(--ja-primary-light-9, #f0f9ff);
  border-color: var(--ja-primary);
  color: var(--ja-primary);
}

:deep(.el-tag--danger) {
  background-color: var(--el-color-danger-light-9, #fef0f0);
  border-color: var(--ja-danger);
  color: var(--ja-danger);
}

/* 追踪概要信息样式 */
.trace-summary :deep(.el-descriptions__label) {
  font-weight: 600;
  color: var(--ja-text-primary);
}

.trace-summary :deep(.el-descriptions__content) {
  color: var(--ja-text-regular);
}
</style>
