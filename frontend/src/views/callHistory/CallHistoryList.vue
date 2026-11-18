<template>
  <PageSkeleton>
    <template #title>
      <h2 class="page-heading">{{ t('callHistory.list.pageTitle') }}</h2>
      <p class="page-subtitle">{{ t('callHistory.list.pageDescription') }}</p>
    </template>

    <template #stats>
      <!-- 统计条：只有「总记录数」是筛选后的全局量，其余三项只覆盖当前页，标签里写明范围 -->
      <MetricStrip :items="metricItems" :loading="loading" :columns="4" />
    </template>

    <template #toolbar>
      <!-- 工具栏顺序：时间范围 → 维度筛选（模型/服务类型/状态/状态码）→ 刷新 → 重置 -->
      <FilterBar class="toolbar-filter">
        <el-form :inline="true" :model="queryForm" class="filter-form">
          <el-form-item :label="t('callHistory.common.timeRange')">
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              :range-separator="t('callHistory.common.rangeSeparator')"
              :start-placeholder="t('callHistory.common.startTime')"
              :end-placeholder="t('callHistory.common.endTime')"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 360px"
            />
          </el-form-item>
          <el-form-item :label="t('callHistory.common.modelName')">
            <el-input
              v-model="queryForm.modelName"
              :placeholder="t('callHistory.list.modelNamePlaceholder')"
              clearable
              style="width: 180px"
            />
          </el-form-item>
          <el-form-item :label="t('callHistory.common.serviceType')">
            <el-select v-model="queryForm.serviceType" :placeholder="t('callHistory.list.allPlaceholder')" clearable style="width: 120px">
              <el-option :label="t('serviceTypes.chat')" value="chat" />
              <el-option :label="t('serviceTypes.embedding')" value="embedding" />
              <el-option :label="t('serviceTypes.rerank')" value="rerank" />
              <el-option :label="t('serviceTypes.tts')" value="tts" />
              <el-option :label="t('serviceTypes.stt')" value="stt" />
              <el-option :label="t('serviceTypes.imgGen')" value="imgGen" />
              <el-option :label="t('serviceTypes.imgEdit')" value="imgEdit" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('callHistory.common.status')">
            <el-select v-model="queryForm.isSuccess" :placeholder="t('callHistory.list.allPlaceholder')" clearable style="width: 100px">
              <el-option :label="t('callHistory.common.success')" :value="true" />
              <el-option :label="t('callHistory.common.failed')" :value="false" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('callHistory.common.httpStatusCode')">
            <el-input
              v-model.number="queryForm.httpStatusCode"
              :placeholder="t('callHistory.list.httpStatusCodePlaceholder')"
              clearable
              style="width: 120px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Refresh" @click="handleSearch">{{ t('callHistory.common.refresh') }}</el-button>
            <el-button @click="handleReset">{{ t('callHistory.common.reset') }}</el-button>
          </el-form-item>
        </el-form>
      </FilterBar>
    </template>

    <!-- 记录级别与内容治理已在「记录治理」页配置；本页只呈现记录，不再内嵌配置 -->
    <el-alert type="info" :closable="false" show-icon class="governance-hint">
      <template #title>
        {{ t('callHistory.list.governanceHint') }}
        <el-link type="primary" class="governance-link" @click="goRecordGovernance">
          {{ t('callHistory.list.governanceLink') }}
        </el-link>
      </template>
    </el-alert>

    <!-- 数据表格 -->
    <div class="table-header">
      <span class="chart-title">{{ t('callHistory.list.listTitle') }}</span>
      <span class="total-count">{{ t('callHistory.list.totalRecords', { count: totalCount }) }}</span>
    </div>

    <el-table
      v-if="!isNarrow"
      :key="isCompact ? 'compact' : 'wide'"
      v-loading="loading"
      :data="tableData"
      border
      stripe
      :max-height="600"
      :class="{ 'compact-table': isCompact }"
      @sort-change="handleSortChange"
    >
      <el-table-column
        :label="t('callHistory.common.time')"
        prop="createdAt"
        :width="isCompact ? 120 : 180"
        sortable="custom"
      >
        <template #default="scope">
          <TimeText :value="scope.row.createdAt" />
        </template>
      </el-table-column>
      <el-table-column
        label="Trace ID"
        prop="traceId"
        :width="isCompact ? 85 : 140"
        show-overflow-tooltip
      >
        <template #default="scope">
          <el-link type="primary" @click="handleTraceIdClick(scope.row.traceId)">
            {{ scope.row.traceId?.substring(0, 8) }}...
          </el-link>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('callHistory.common.modelName')"
        prop="modelName"
        :min-width="isCompact ? 85 : 160"
        show-overflow-tooltip
      />
      <el-table-column
        v-if="!isCompact"
        :label="t('callHistory.common.serviceType')"
        prop="serviceType"
        width="110"
      >
        <template #default="scope">
          <el-link
            v-if="scope.row.serviceType"
            type="primary"
            :underline="false"
            @click.stop="router.push({ name: 'instance-management', query: { serviceType: scope.row.serviceType } })"
          >
            <el-tag size="small">{{ getServiceTypeLabel(scope.row.serviceType) }}</el-tag>
          </el-link>
          <el-tag v-else size="small">{{ getServiceTypeLabel(scope.row.serviceType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="!isCompact" :label="t('callHistory.common.provider')" prop="provider" width="100" show-overflow-tooltip />
      <el-table-column
        :label="t('callHistory.common.httpStatus')"
        prop="httpStatusCode"
        :width="isCompact ? 65 : 100"
        align="center"
        sortable="custom"
      >
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.httpStatusCode)" size="small">
            {{ scope.row.httpStatusCode || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        v-if="!isCompact"
        :label="t('callHistory.common.token')"
        prop="totalTokens"
        width="100"
        align="right"
        sortable="custom"
      >
        <template #default="scope">
          {{ formatNumber(scope.row.totalTokens) }}
        </template>
      </el-table-column>
      <el-table-column
        v-if="!isCompact"
        :label="t('callHistory.common.responseTimeMs')"
        prop="responseTimeMs"
        width="120"
        align="right"
        sortable="custom"
      >
        <template #default="scope">
          <el-tag :type="getResponseTimeType(scope.row.responseTimeMs)" size="small">
            {{ scope.row.responseTimeMs?.toFixed(0) || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('callHistory.common.status')" :width="isCompact ? 60 : 80" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.isSuccess ? 'info' : 'danger'" size="small">
            {{ scope.row.isSuccess ? t('callHistory.common.success') : t('callHistory.common.failed') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('callHistory.common.action')" :width="isCompact ? 60 : 80" fixed="right" align="center">
        <template #default="scope">
          <el-button type="primary" link size="small" @click="handleDetail(scope.row)">
            {{ t('callHistory.common.details') }}
          </el-button>
        </template>
      </el-table-column>
      <!-- 空态：区分「还没有数据」与「筛选无结果」，筛选态给一个复位动作（只读页没有新建主操作） -->
      <template #empty>
        <EmptyState v-if="!loading" :title="emptyTitle" :description="emptyDescription">
          <template v-if="hasActiveFilter" #actions>
            <el-button icon="Refresh" @click="handleReset">
              {{ t('callHistory.common.reset') }}
            </el-button>
          </template>
        </EmptyState>
      </template>
    </el-table>

    <TableCards
      v-else
      :rows="tableData"
      row-key="requestId"
      :aria-label="t('callHistory.list.cards.ariaLabel')"
      :empty-title="emptyTitle"
      :empty-description="emptyDescription"
    >
      <template #default="{ row }">
        <div class="record-card">
          <div class="record-card__head">
            <el-link type="primary" class="record-card__title" @click="handleTraceIdClick(row.traceId)">
              {{ row.traceId?.substring(0, 8) }}...
            </el-link>
            <el-tag :type="row.isSuccess ? 'info' : 'danger'" size="small">
              {{ row.isSuccess ? t('callHistory.common.success') : t('callHistory.common.failed') }}
            </el-tag>
          </div>
          <div class="record-card__meta">
            <div class="record-card__field">
              <span class="record-card__label">{{ t('callHistory.common.time') }}</span>
              <span class="record-card__value"><TimeText :value="row.createdAt" /></span>
            </div>
            <div class="record-card__field">
              <span class="record-card__label">{{ t('callHistory.common.modelName') }}</span>
              <span class="record-card__value">{{ row.modelName || '-' }}</span>
            </div>
            <div class="record-card__field">
              <span class="record-card__label">{{ t('callHistory.common.serviceType') }}</span>
              <span class="record-card__value">{{ getServiceTypeLabel(row.serviceType) }}</span>
            </div>
            <div class="record-card__field">
              <span class="record-card__label">{{ t('callHistory.common.provider') }}</span>
              <span class="record-card__value">{{ row.provider || '-' }}</span>
            </div>
            <div class="record-card__field">
              <span class="record-card__label">{{ t('callHistory.common.httpStatus') }}</span>
              <span class="record-card__value">
                <el-tag :type="getStatusType(row.httpStatusCode)" size="small">
                  {{ row.httpStatusCode || '-' }}
                </el-tag>
              </span>
            </div>
          </div>
          <div class="record-card__actions">
            <el-button type="primary" link size="small" @click="handleDetail(row)">
              {{ t('callHistory.common.details') }}
            </el-button>
          </div>
        </div>
      </template>
    </TableCards>

    <template #footer>
      <el-pagination
        v-if="totalCount > 0"
        :current-page="(queryForm.page || 0) + 1"
        :page-sizes="[20, 50, 100]"
        :page-size="queryForm.size || 20"
        :total="totalCount"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </template>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="t('callHistory.list.detailsTitle')"
      size="600px"
    >
      <template v-if="selectedRecord">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="Trace ID" :span="2">
            <el-tag>{{ selectedRecord.traceId }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.list.requestId')" :span="2">
            {{ selectedRecord.requestId }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.common.time')" :span="2">
            {{ formatTime(selectedRecord.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.list.requestMethod')">
            {{ selectedRecord.requestMethod }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.list.requestPath')" :span="2">
            {{ selectedRecord.requestPath }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.common.modelName')">
            {{ selectedRecord.modelName }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.common.serviceType')">
            <el-link
              v-if="selectedRecord.serviceType"
              type="primary"
              :underline="false"
              @click="router.push({ name: 'service-management', query: { serviceType: selectedRecord.serviceType } })"
            >
              {{ getServiceTypeLabel(selectedRecord.serviceType) }}
            </el-link>
            <span v-else>{{ getServiceTypeLabel(selectedRecord.serviceType) }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.common.provider')">
            {{ selectedRecord.provider || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.list.instanceName')">
            <el-link
              v-if="selectedRecord.instanceName && selectedRecord.serviceType"
              type="primary"
              :underline="false"
              @click="router.push({ name: 'instance-management', query: { serviceType: selectedRecord.serviceType } })"
            >
              {{ selectedRecord.instanceName }}
            </el-link>
            <span v-else>{{ selectedRecord.instanceName || '-' }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.common.httpStatusCode')">
            <el-tag :type="getStatusType(selectedRecord.httpStatusCode)">
              {{ selectedRecord.httpStatusCode || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.list.responseTime')">
            {{ selectedRecord.responseTimeMs?.toFixed(0) || '-' }} ms
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.common.callStatus')">
            <el-tag :type="selectedRecord.isSuccess ? 'info' : 'danger'">
              {{ selectedRecord.isSuccess ? t('callHistory.common.success') : t('callHistory.common.failed') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.list.errorCode')">
            {{ selectedRecord.errorCode || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.common.errorMessage')" :span="2">
            <span v-if="selectedRecord.errorMessage" class="error-message">
              {{ selectedRecord.errorMessage }}
            </span>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.list.tokenStatsTitle')" :span="2">
            <el-tag type="info" size="small">{{ t('callHistory.list.tokenStats.input', { value: selectedRecord.promptTokens }) }}</el-tag>
            <el-tag type="info" size="small">{{ t('callHistory.list.tokenStats.output', { value: selectedRecord.completionTokens }) }}</el-tag>
            <el-tag type="info" size="small">{{ t('callHistory.list.tokenStats.total', { value: selectedRecord.totalTokens }) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.list.rateLimitedStatus')">
            <el-tag :type="selectedRecord.rateLimited ? 'danger' : 'info'" size="small">
              {{ selectedRecord.rateLimited ? t('callHistory.list.rateLimited') : t('callHistory.common.normal') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.list.circuitBreakStatus')">
            <el-tag :type="selectedRecord.circuitBroken ? 'danger' : 'info'" size="small">
              {{ selectedRecord.circuitBroken ? t('callHistory.list.circuitBroken') : t('callHistory.common.normal') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('callHistory.list.clientIp')">
            {{ selectedRecord.clientIp || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="User Agent">
            {{ selectedRecord.userAgent || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="API Key" :span="2">
            {{ selectedRecord.apiKeyId || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 请求/响应摘要 -->
        <el-card v-if="selectedRecord.requestBodySummary || selectedRecord.responseBodySummary"
                 shadow="never" style="margin-top: 16px;">
          <template #header>
            <span>{{ t('callHistory.list.bodySummaryTitle') }}</span>
          </template>
          <div v-if="selectedRecord.requestBodySummary" class="body-summary">
            <div class="summary-label">{{ t('callHistory.list.requestBodySummary') }}</div>
            <pre class="summary-content">{{ selectedRecord.requestBodySummary }}</pre>
          </div>
          <div v-if="selectedRecord.responseBodySummary" class="body-summary">
            <div class="summary-label">{{ t('callHistory.list.responseBodySummary') }}</div>
            <pre class="summary-content">{{ selectedRecord.responseBodySummary }}</pre>
          </div>
        </el-card>
      </template>
    </el-drawer>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getErrorMessage } from '@/utils/error'
import { queryCallHistory } from '@/api/callHistory'
import type { ApiCallHistoryRecord, CallHistoryQuery } from '@/types/callHistory'
import PageSkeleton from '@/components/PageSkeleton.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import EmptyState from '@/components/EmptyState.vue'
import FilterBar from '@/components/FilterBar.vue'
import TimeText from '@/components/TimeText.vue'
import TableCards from '@/components/TableCards.vue'
import { useViewport } from '@/composables/useViewport'
import { formatNumber as formatNumberBase } from '@/utils/format'

const router = useRouter()

// 记录级别的配置入口在「记录治理」页，本页只负责呈现
const goRecordGovernance = () => {
  void router.push('/security/record-governance')
}
const { t } = useI18n()
const { isNarrow, isCompact } = useViewport()

// 查询表单
const queryForm = reactive<CallHistoryQuery>({
  startTime: undefined,
  endTime: undefined,
  modelName: undefined,
  serviceType: undefined,
  isSuccess: undefined,
  httpStatusCode: undefined,
  page: 0,
  size: 20,
  sortField: 'createdAt',
  sortDirection: 'desc'
})

// 日期范围
const dateRange = ref<[string, string] | null>(null)

// 表格数据
const tableData = ref<ApiCallHistoryRecord[]>([])
const loading = ref(true)
const totalCount = ref(0)

// 详情抽屉
const drawerVisible = ref(false)
const selectedRecord = ref<ApiCallHistoryRecord | null>(null)

// 格式化数字（千分位，委托共享 format.ts，与其它调用历史页一致）
const formatNumber = (num?: number): string => {
  if (!num && num !== 0) return '0'
  return formatNumberBase(num)
}

// 空态文案：区分「无数据」与「筛选无结果」——任一筛选条件生效即视为筛选
const hasActiveFilter = computed(
  () =>
    !!dateRange.value ||
    !!queryForm.modelName ||
    !!queryForm.serviceType ||
    typeof queryForm.isSuccess === 'boolean' ||
    !!queryForm.httpStatusCode
)
const emptyTitle = computed(() =>
  hasActiveFilter.value ? t('callHistory.common.emptyFilteredTitle') : t('callHistory.common.emptyNoDataTitle')
)
const emptyDescription = computed(() =>
  hasActiveFilter.value
    ? t('callHistory.common.emptyFilteredDescription')
    : t('callHistory.common.emptyNoDataDescription')
)

// 统计条：只有「总记录数」是筛选后的全局量；其余三项只能覆盖当前页，标签里写明范围避免误读
const metricItems = computed<MetricStripItem[]>(() => {
  const pageRecords = tableData.value
  const successCount = pageRecords.filter((record) => record.isSuccess).length
  const failedCount = pageRecords.length - successCount
  // 只用有值的响应时间参与平均，避免把缺失值当 0 拉低均值
  const durations = pageRecords
    .map((record) => record.responseTimeMs)
    .filter((value): value is number => typeof value === 'number')
  const avgDuration = durations.length
    ? durations.reduce((sum, value) => sum + value, 0) / durations.length
    : 0
  return [
    {
      key: 'total',
      label: t('callHistory.list.stats.total'),
      value: formatNumber(totalCount.value)
    },
    {
      key: 'pageSuccess',
      label: t('callHistory.list.stats.pageSuccess'),
      value: successCount
    },
    {
      key: 'pageFailed',
      label: t('callHistory.list.stats.pageFailed'),
      value: failedCount,
      tone: failedCount > 0 ? 'danger' : 'default'
    },
    {
      key: 'pageAvgResponseTime',
      label: t('callHistory.list.stats.pageAvgResponseTime'),
      value: durations.length ? `${avgDuration.toFixed(0)} ms` : '-'
    }
  ]
})

// 格式化时间
const formatTime = (time?: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

// 服务类型 key -> i18n 键名映射（保持原有顺序）
const serviceTypeKeyMap: Record<string, string> = {
  chat: 'chat',
  embedding: 'embedding',
  rerank: 'rerank',
  tts: 'tts',
  stt: 'stt',
  imgGen: 'imgGen',
  imgEdit: 'imgEdit'
}

// 获取服务类型标签（v2.10.4: 文案收敛到顶层 serviceTypes.*，未知类型回退原值）
const getServiceTypeLabel = (type?: string) => {
  if (!type) return t('callHistory.common.unknown')
  return serviceTypeKeyMap[type] ? t(`serviceTypes.${serviceTypeKeyMap[type]}`) : type
}

// 获取 HTTP 状态码标签类型（只有 4xx/5xx 是真语义，2xx/3xx 保持中性）
const getStatusType = (code?: number) => {
  if (!code) return 'info'
  return code >= 400 ? 'danger' : 'info'
}

// 获取响应时间标签类型（≥1000ms 才算慢，其余中性，避免绿/橙/红三色并存）
const getResponseTimeType = (ms?: number) => {
  if (!ms) return 'info'
  return ms >= 1000 ? 'danger' : 'info'
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    // 处理日期范围
    if (dateRange.value && dateRange.value.length === 2) {
      queryForm.startTime = dateRange.value[0].replace(' ', 'T')
      queryForm.endTime = dateRange.value[1].replace(' ', 'T')
    } else {
      queryForm.startTime = undefined
      queryForm.endTime = undefined
    }

    const result = await queryCallHistory(queryForm)
    tableData.value = result.content || []
    totalCount.value = result.totalElements || 0
  } catch (error) {
    console.error('加载调用历史失败:', error)
    ElMessage.error(t('callHistory.list.loadFailed', { message: getErrorMessage(error) || t('callHistory.common.unknownError') }))
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  queryForm.page = 0
  loadData()
}

// 重置
const handleReset = () => {
  dateRange.value = null
  queryForm.startTime = undefined
  queryForm.endTime = undefined
  queryForm.modelName = undefined
  queryForm.serviceType = undefined
  queryForm.isSuccess = undefined
  queryForm.httpStatusCode = undefined
  queryForm.page = 0
  queryForm.size = 20
  queryForm.sortField = 'createdAt'
  queryForm.sortDirection = 'desc'
  loadData()
}

// 排序变化
const handleSortChange = ({ prop, order }: { prop: string; order: string }) => {
  if (prop) {
    queryForm.sortField = prop
    queryForm.sortDirection = order === 'ascending' ? 'asc' : 'desc'
  } else {
    queryForm.sortField = 'createdAt'
    queryForm.sortDirection = 'desc'
  }
  loadData()
}

// 分页大小变化
const handleSizeChange = (size: number) => {
  queryForm.size = size
  queryForm.page = 0
  loadData()
}

// 页码变化
const handleCurrentChange = (page: number) => {
  queryForm.page = page - 1
  loadData()
}

// Trace ID 点击
const handleTraceIdClick = async (traceId: string) => {
  if (!traceId) return
  try {
    // 这里可以通过 API 查询该 traceId 的所有调用
    // 暂时简单提示
    ElMessage.info(`Trace ID: ${traceId}`)
  } catch (error) {
    console.error('查询 Trace ID 失败:', error)
  }
}

// 查看详情
const handleDetail = (record: ApiCallHistoryRecord) => {
  selectedRecord.value = record
  drawerVisible.value = true
}

// 初始化
onMounted(() => {
  loadData()
})
</script>

<style scoped>
/* 与其它集合页同形：18px/600 的页标题（PageSkeleton 的 scoped 样式作用不到 #title 插槽内容） */
.page-heading {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--ja-text-primary);
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--ja-text-secondary);
  line-height: 1.6;
}

/* FilterBar 作为 #toolbar 的 flex 子项需要占满整行 */
.toolbar-filter {
  width: 100%;
}





.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  width: 100%;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-title {
  font-size: 16px;
  font-weight: bold;
}

.total-count {
  font-size: 14px;
  color: var(--ja-ink-3);
}

.error-message {
  color: var(--ja-danger);
  font-size: 12px;
  word-break: break-all;
}

.body-summary {
  margin-bottom: 12px;
}

.body-summary:last-child {
  margin-bottom: 0;
}

.summary-label {
  font-weight: bold;
  color: var(--ja-ink-2);
  margin-bottom: 4px;
}

.summary-content {
  background-color: var(--ja-surface-2);
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 12px;
  word-break: break-all;
  white-space: pre-wrap;
  max-height: 200px;
  overflow-y: auto;
  margin: 0;
}

/* compact：收窄单元格内边距与字号，保证 768px 下不出现横向滚动 */
.compact-table :deep(.cell) {
  padding: 0 4px;
  font-size: 12px;
}

/* 窄屏卡片（TableCards 内由本页插槽渲染） */
.record-card {
  display: flex;
  flex-direction: column;
  gap: var(--ja-space-2, 8px);
  min-width: 0;
}

.record-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--ja-space-2, 8px);
}

.record-card__title {
  font-size: 15px;
  font-weight: 600;
  word-break: break-all;
}

.record-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--ja-space-1, 4px) var(--ja-space-3, 12px);
}

.record-card__field {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.record-card__label {
  font-size: 12px;
  color: var(--ja-ink-3);
}

.record-card__value {
  font-size: 13px;
  color: var(--ja-ink);
  overflow-wrap: anywhere;
}

.record-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--ja-space-1, 4px);
  padding-top: var(--ja-space-2, 8px);
  border-top: 1px solid var(--ja-border-light);
}

@media (max-width: 420px) {
  .record-card__meta {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
