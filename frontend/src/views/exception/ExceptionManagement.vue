<template>
  <PageSkeleton>
    <template #title>
      <h2 class="page-title">{{ t('exception.list.title') }}</h2>
      <p class="page-subtitle">{{ t('exception.list.pageDescription') }}</p>
    </template>

    <template #actions>
      <!-- 主操作只保留一个：统计分析（跳转）；刷新与清理过期进工具栏 -->
      <el-button icon="DataAnalysis" type="primary" @click="goToStatistics">{{ t('exception.list.goStatistics') }}</el-button>
    </template>

    <!-- 筛选条件 + 工具栏（顺序：筛选 → 刷新 → 次要动作） -->
    <template #toolbar>
      <FilterBar class="toolbar-filter">
        <el-form :inline="true" :model="queryParams" class="filter-form">
          <el-form-item :label="t('exception.list.exceptionType')">
            <el-input
              v-model="queryParams.exceptionType"
              :placeholder="t('exception.list.exceptionTypePlaceholder')"
              clearable
              style="width: 200px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item :label="t('exception.list.errorCode')">
            <el-select v-model="queryParams.errorCode" :placeholder="t('exception.list.errorCodePlaceholder')" clearable style="width: 120px">
              <el-option label="400" value="400" />
              <el-option label="401" value="401" />
              <el-option label="403" value="403" />
              <el-option label="404" value="404" />
              <el-option label="429" value="429" />
              <el-option label="500" value="500" />
              <el-option label="502" value="502" />
              <el-option label="503" value="503" />
              <el-option label="504" value="504" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('exception.list.errorCategory')">
            <el-select v-model="queryParams.errorCategory" :placeholder="t('exception.list.errorCategoryPlaceholder')" clearable style="width: 150px">
              <el-option :label="t('exception.list.errorCategories.CLIENT_ERROR')" value="CLIENT_ERROR" />
              <el-option :label="t('exception.list.errorCategories.SERVER_ERROR')" value="SERVER_ERROR" />
              <el-option :label="t('exception.list.errorCategories.NETWORK_ERROR')" value="NETWORK_ERROR" />
              <el-option :label="t('exception.list.errorCategories.TIMEOUT_ERROR')" value="TIMEOUT_ERROR" />
              <el-option :label="t('exception.list.errorCategories.VALIDATION_ERROR')" value="VALIDATION_ERROR" />
              <el-option :label="t('exception.list.errorCategories.SECURITY_ERROR')" value="SECURITY_ERROR" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('exception.list.clientIp')">
            <el-input
              v-model="queryParams.clientIp"
              :placeholder="t('exception.list.clientIpPlaceholder')"
              clearable
              style="width: 150px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item :label="t('exception.list.serviceType')">
            <el-select v-model="queryParams.serviceType" :placeholder="t('exception.list.serviceTypePlaceholder')" clearable style="width: 130px">
              <el-option :label="t('serviceTypes.chat')" value="CHAT" />
              <el-option :label="t('serviceTypes.embedding')" value="EMBEDDING" />
              <el-option :label="t('serviceTypes.rerank')" value="RERANK" />
              <el-option :label="t('serviceTypes.tts')" value="TTS" />
              <el-option :label="t('serviceTypes.stt')" value="STT" />
              <el-option :label="t('serviceTypes.imgGen')" value="IMG_GENERATE" />
              <el-option :label="t('serviceTypes.imgEdit')" value="IMG_EDIT" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('exception.list.modelName')">
            <el-input
              v-model="queryParams.modelName"
              :placeholder="t('exception.list.modelNamePlaceholder')"
              clearable
              style="width: 160px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item :label="t('exception.list.timeRange')">
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              :range-separator="t('exception.list.dateRangeSeparator')"
              :start-placeholder="t('exception.list.startTime')"
              :end-placeholder="t('exception.list.endTime')"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 400px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">{{ t('exception.list.query') }}</el-button>
            <el-button icon="RefreshLeft" @click="handleReset">{{ t('exception.list.reset') }}</el-button>
            <!-- 刷新跟在筛选之后 -->
            <el-button icon="Refresh" :loading="loading" @click="handleRefresh">{{ t('exception.list.refresh') }}</el-button>
            <!-- 清理过期：先打开确认抽屉，真正的删除由抽屉里的 ConfirmAction 提交 -->
            <el-button icon="Delete" type="danger" plain v-permission="'monitoring:exceptions:write'" @click="showCleanupDialog">{{ t('exception.list.cleanupExpired') }}</el-button>
          </el-form-item>
        </el-form>
      </FilterBar>
    </template>

    <template #stats>
      <!-- 统计指标条：中性为主，「异常总数 > 0」才上语义色 -->
      <MetricStrip :items="metricItems" :columns="4" />
    </template>

    <!-- 表格（窄屏不渲染，改用下方卡片列表） -->
    <div v-if="!isNarrow" class="table-wrapper">
      <el-table
        :key="isCompact ? 'compact' : 'wide'"
        v-loading="loading"
        :data="exceptionList"
        border
        stripe
        :max-height="500"
        :class="{ 'compact-table': isCompact }"
        @row-click="handleRowClick"
      >
        <!-- 空态区分「还没有数据」与「筛选无结果」；两种情况都给出下一步动作 -->
        <template #empty>
          <EmptyState
            icon="Warning"
            :title="hasActiveFilter ? t('exception.list.emptyTitle') : t('exception.list.emptyNoDataTitle')"
            :description="hasActiveFilter ? t('exception.list.emptyDescription') : t('exception.list.emptyNoDataDescription')"
          >
            <template #actions>
              <el-button v-if="hasActiveFilter" @click="handleReset">{{ t('exception.list.reset') }}</el-button>
              <el-button v-else icon="Refresh" :loading="loading" @click="handleRefresh">{{ t('exception.list.refresh') }}</el-button>
            </template>
          </EmptyState>
        </template>
        <el-table-column :label="t('exception.list.eventId')" prop="eventId" :width="isCompact ? 75 : 200" show-overflow-tooltip>
          <template #default="scope">
            <el-tag effect="plain" type="info">{{ scope.row.eventId }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('exception.list.exceptionType')" prop="exceptionType" :min-width="isCompact ? 75 : 180" show-overflow-tooltip>
          <template #default="scope">
            <span class="exception-type">{{ scope.row.exceptionType }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('exception.list.serviceType')" prop="serviceType" :width="isCompact ? 55 : 100">
          <template #default="scope">
            <el-link
              v-if="scope.row.serviceType"
              type="primary"
              :underline="false"
              @click.stop="router.push({ name: 'instance-management', query: { serviceType: normalizeServiceType(scope.row.serviceType) } })"
            >
              <el-tag size="small" type="info">{{ scope.row.serviceType }}</el-tag>
            </el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('exception.list.modelName')" prop="modelName" :min-width="isCompact ? 55 : 140" show-overflow-tooltip>
          <template #default="scope">
            <span>{{ scope.row.modelName || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isCompact" :label="t('exception.list.provider')" prop="provider" width="100" show-overflow-tooltip>
          <template #default="scope">
            <span>{{ scope.row.provider || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isCompact" :label="t('exception.list.responseTime')" prop="responseTimeMs" width="120" sortable>
          <template #default="scope">
            <span>{{ scope.row.responseTimeMs != null ? scope.row.responseTimeMs : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('exception.list.errorCode')" prop="errorCode" :width="isCompact ? 45 : 100">
          <template #default="scope">
            <el-tag :type="getErrorTagType(scope.row.errorCode)" size="small">
              {{ scope.row.errorCode }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="!isCompact" :label="t('exception.list.errorCategory')" prop="errorCategory" width="120">
          <template #default="scope">
            <el-tag :type="getCategoryTagType(scope.row.errorCategory)" size="small">
              {{ formatCategory(scope.row.errorCategory) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('exception.list.exceptionMessage')" prop="sanitizedMessage" :min-width="isCompact ? 70 : 200" show-overflow-tooltip>
          <template #default="scope">
            <span>{{ scope.row.sanitizedMessage || scope.row.exceptionMessage || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="!isCompact" :label="t('exception.list.clientIp')" prop="clientIp" width="140">
          <template #default="scope">
            <span>{{ scope.row.clientIp || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('exception.list.occurredAt')" prop="occurredAt" :width="isCompact ? 75 : 180">
          <template #default="scope">
            <TimeText :value="scope.row.occurredAt" />
          </template>
        </el-table-column>
        <el-table-column :label="t('exception.list.actions')" :width="isCompact ? 45 : 100" fixed="right">
          <template #default="scope">
            <el-button link type="primary" size="small" @click.stop="handleViewDetail(scope.row)">
              {{ t('exception.list.detail') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 窄屏卡片列表 -->
    <TableCards
      v-else
      class="table-wrapper"
      :rows="exceptionList"
      row-key="eventId"
      :aria-label="t('exception.list.cards.ariaLabel')"
      :empty-title="hasActiveFilter ? t('exception.list.emptyTitle') : t('exception.list.emptyNoDataTitle')"
      :empty-description="hasActiveFilter ? t('exception.list.emptyDescription') : t('exception.list.emptyNoDataDescription')"
    >
      <template #default="{ row }">
        <div class="exception-card">
          <div class="exception-card__head">
            <span class="exception-card__title">{{ row.eventId }}</span>
            <el-tag :type="getErrorTagType(row.errorCode)" size="small">
              {{ row.errorCode || '-' }}
            </el-tag>
          </div>
          <div class="exception-card__meta">
            <div class="exception-card__field">
              <span class="exception-card__label">{{ t('exception.list.occurredAt') }}</span>
              <span class="exception-card__value"><TimeText :value="row.occurredAt" /></span>
            </div>
            <div class="exception-card__field">
              <span class="exception-card__label">{{ t('exception.list.exceptionType') }}</span>
              <span class="exception-card__value">{{ row.exceptionType || '-' }}</span>
            </div>
            <div class="exception-card__field">
              <span class="exception-card__label">{{ t('exception.list.errorCategory') }}</span>
              <span class="exception-card__value">
                <el-tag :type="getCategoryTagType(row.errorCategory)" size="small">
                  {{ formatCategory(row.errorCategory) }}
                </el-tag>
              </span>
            </div>
            <div class="exception-card__field">
              <span class="exception-card__label">{{ t('exception.list.serviceType') }}</span>
              <span class="exception-card__value">{{ row.serviceType || '-' }}</span>
            </div>
            <div class="exception-card__field">
              <span class="exception-card__label">{{ t('exception.list.clientIp') }}</span>
              <span class="exception-card__value">{{ row.clientIp || '-' }}</span>
            </div>
            <div class="exception-card__field exception-card__field--full">
              <span class="exception-card__label">{{ t('exception.list.exceptionMessage') }}</span>
              <span class="exception-card__value">{{ row.sanitizedMessage || row.exceptionMessage || '-' }}</span>
            </div>
          </div>
          <div class="exception-card__actions">
            <el-button link type="primary" size="small" @click="handleViewDetail(row)">
              {{ t('exception.list.detail') }}
            </el-button>
          </div>
        </div>
      </template>
    </TableCards>

    <!-- 分页（0 条时不渲染无意义的分页条） -->
    <template #footer>
      <el-pagination
        v-if="totalElements > 0"
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="totalElements"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </template>

  </PageSkeleton>

  <!-- 详情：只读明细走 DetailDrawer。
       宽度取 640 而非标准 520：两列描述 + 长消息块在 520 下会挤成窄条；
       消息块自身限高滚动（.message-block），不会把抽屉撑开。 -->
  <DetailDrawer v-model="detailVisible" :title="t('exception.list.detailDialogTitle')" :width="640">
    <el-descriptions v-if="selectedEvent" :column="2" border>
      <el-descriptions-item :label="t('exception.list.eventId')">{{ selectedEvent.eventId }}</el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.exceptionType')">{{ selectedEvent.exceptionType }}</el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.errorCode')">
        <el-tag :type="getErrorTagType(selectedEvent.errorCode)" size="small">
          {{ selectedEvent.errorCode }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.errorCategory')">
        <el-tag :type="getCategoryTagType(selectedEvent.errorCategory)" size="small">
          {{ formatCategory(selectedEvent.errorCategory) }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.httpStatus')">
        <el-tag :type="getHttpStatusTagType(selectedEvent.httpStatus)" size="small">
          {{ selectedEvent.httpStatus || '-' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.clientIp')">{{ selectedEvent.clientIp || '-' }}</el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.serviceType')">
        <el-link
          v-if="selectedEvent.serviceType"
          type="primary"
          :underline="false"
          @click="router.push({ name: 'service-management', query: { serviceType: normalizeServiceType(selectedEvent.serviceType) } })"
        >
          <el-tag size="small" type="info">{{ selectedEvent.serviceType }}</el-tag>
        </el-link>
        <span v-else>-</span>
      </el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.modelName')">{{ selectedEvent.modelName || '-' }}</el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.provider')">{{ selectedEvent.provider || '-' }}</el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.instanceName')">
        <el-link
          v-if="selectedEvent.instanceName && selectedEvent.serviceType"
          type="primary"
          :underline="false"
          @click="router.push({ name: 'instance-management', query: { serviceType: normalizeServiceType(selectedEvent.serviceType) } })"
        >
          {{ selectedEvent.instanceName }}
        </el-link>
        <span v-else>{{ selectedEvent.instanceName || '-' }}</span>
      </el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.responseTime')">{{ selectedEvent.responseTimeMs != null ? selectedEvent.responseTimeMs : '-' }}</el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.traceId')" :span="2">
        <el-tag effect="plain" type="info">{{ selectedEvent.traceId || '-' }}</el-tag>
      </el-descriptions-item>
      <!-- 消息可能是多行长文本：只读展示用 pre，不用输入框假装文本域 -->
      <el-descriptions-item :label="t('exception.list.exceptionMessage')" :span="2">
        <pre class="message-block">{{ selectedEvent.exceptionMessage || '-' }}</pre>
      </el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.sanitizedMessage')" :span="2">
        <pre class="message-block">{{ selectedEvent.sanitizedMessage || '-' }}</pre>
      </el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.occurrenceCount')">{{ selectedEvent.occurrenceCount || 1 }}</el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.isAggregated')">
        <el-tag :type="selectedEvent.isAggregated ? 'success' : 'info'" size="small">
          {{ selectedEvent.isAggregated ? t('exception.list.yes') : t('exception.list.no') }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.firstOccurredAt')">{{ formatTime(selectedEvent.firstOccurrence) }}</el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.lastOccurredAt')">{{ formatTime(selectedEvent.lastOccurrence) }}</el-descriptions-item>
      <el-descriptions-item :label="t('exception.list.occurredAt')">{{ formatTime(selectedEvent.occurredAt) }}</el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button @click="detailVisible = false">{{ t('exception.list.close') }}</el-button>
    </template>
  </DetailDrawer>

  <!-- 清理过期：一次性表单，统一用右侧抽屉；真正的删除由抽屉里的 ConfirmAction 提交 -->
  <el-drawer
    v-model="cleanupVisible"
    :title="t('exception.list.cleanupDialogTitle')"
    direction="rtl"
    :size="640"
    :close-on-click-modal="false"
  >
    <el-form :model="cleanupForm" label-width="100px">
      <el-form-item :label="t('exception.list.cutoffTime')">
        <el-date-picker
          v-model="cleanupForm.cutoffTime"
          type="datetime"
          :placeholder="t('exception.list.cutoffTimePlaceholder')"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item :label="t('exception.list.aggregatedOnly')">
        <el-switch v-model="cleanupForm.aggregatedOnly" />
      </el-form-item>
      <el-alert
        :title="t('exception.list.warning')"
        type="warning"
        :description="t('exception.list.cleanupWarning')"
        :closable="false"
      />
    </el-form>
    <template #footer>
      <span class="drawer-footer">
        <el-button @click="cleanupVisible = false">{{ t('exception.list.cancel') }}</el-button>
        <ConfirmAction
          :title="t('exception.list.cleanupConfirmMessage')"
          :width="320"
          :confirm-text="t('exception.list.confirmCleanup')"
          :cancel-text="t('exception.list.cancel')"
          type="danger"
          :loading="cleanupSubmitting"
          @confirm="handleCleanup"
        >
          <el-button type="danger" :loading="cleanupSubmitting">{{ t('exception.list.confirmCleanup') }}</el-button>
        </ConfirmAction>
      </span>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getErrorMessage } from '@/utils/error'
import {
  queryExceptionEvents,
  getExceptionStatistics,
  deleteOldExceptionEvents,
} from '@/api/exception'
import type { ExceptionEvent, ExceptionQueryParams } from '@/types/exception'
import MetricStrip, { type MetricStripItem } from '@/components/MetricStrip.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import FilterBar from '@/components/FilterBar.vue'
import TimeText from '@/components/TimeText.vue'
import TableCards from '@/components/TableCards.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import DetailDrawer from '@/components/DetailDrawer.vue'
import { useViewport } from '@/composables/useViewport'

const router = useRouter()

const { t } = useI18n()
const { isNarrow, isCompact } = useViewport()

// Cross-link: 将大写服务类型转为小写路由参数
const normalizeServiceType = (st?: string): string => {
  if (!st) return ''
  return st.toLowerCase()
}

// 加载状态
const loading = ref(true)

// 查询参数
const queryParams = reactive<ExceptionQueryParams>({
  exceptionType: undefined,
  errorCode: undefined,
  errorCategory: undefined,
  clientIp: undefined,
  serviceType: undefined,
  modelName: undefined,
  startTime: undefined,
  endTime: undefined,
  page: 0,
  size: 20,
  sortBy: 'occurredAt',
  sortDirection: 'desc'
})

// 时间范围
const dateRange = ref<[string, string] | null>(null)

// 是否处于筛选状态：空态文案据此在「还没有数据」与「筛选无结果」之间切换
const hasActiveFilter = computed(() =>
  Boolean(
    queryParams.exceptionType ||
      queryParams.errorCode ||
      queryParams.errorCategory ||
      queryParams.clientIp ||
      queryParams.serviceType ||
      queryParams.modelName ||
      dateRange.value
  )
)

// 分页
const currentPage = ref(1)
const pageSize = ref(20)
const totalElements = ref(0)

// 异常列表
const exceptionList = ref<ExceptionEvent[]>([])

// 统计数据
const listData = ref({
  totalElements: 0,
  totalTypes: 0,
  topExceptionType: '',
  topClientIp: ''
})

// 顶部指标条：异常总数只有真的大于 0 才上语义色，其余中性
const metricItems = computed<MetricStripItem[]>(() => [
  {
    key: 'total',
    label: t('exception.list.totalExceptionsLabel'),
    value: listData.value.totalElements,
    tone: listData.value.totalElements > 0 ? 'danger' : 'default'
  },
  {
    key: 'types',
    label: t('exception.list.totalTypesLabel'),
    value: listData.value.totalTypes
  },
  {
    key: 'topType',
    label: t('exception.list.topExceptionTypeLabel'),
    value: listData.value.topExceptionType || '—'
  },
  {
    key: 'topIp',
    label: t('exception.list.topClientIpLabel'),
    value: listData.value.topClientIp || '—'
  }
])

// 详情抽屉
const detailVisible = ref(false)
const selectedEvent = ref<ExceptionEvent | null>(null)

// 清理抽屉
const cleanupVisible = ref(false)
// 清理提交中：避免 ConfirmAction 被重复触发
const cleanupSubmitting = ref(false)
const cleanupForm = reactive({
  cutoffTime: '',
  aggregatedOnly: false
})

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

// 获取 HTTP 状态标签类型
const getHttpStatusTagType = (status?: string) => {
  if (!status) return 'info'
  const num = parseInt(status)
  if (num >= 500) return 'danger'
  if (num >= 400) return 'warning'
  return 'success'
}

// 格式化分类
const formatCategory = (category?: string) => {
  if (!category) return '-'
  return category.replace(/_/g, '')
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    // 处理时间范围
    if (dateRange.value && dateRange.value.length === 2) {
      queryParams.startTime = dateRange.value[0]
      queryParams.endTime = dateRange.value[1]
    } else {
      queryParams.startTime = undefined
      queryParams.endTime = undefined
    }

    // 查询异常列表
    const response = await queryExceptionEvents({
      ...queryParams,
      page: currentPage.value - 1,
      size: pageSize.value
    })

    exceptionList.value = response.content
    totalElements.value = response.totalElements

    // 加载统计数据
    loadStatistics()
  } catch (error) {
    console.error('加载异常列表失败:', error)
    ElMessage.error(t('exception.list.loadFailed', { message: getErrorMessage(error) || t('exception.list.unknownError') }))
  } finally {
    loading.value = false
  }
}

// 加载统计数据
const loadStatistics = async () => {
  try {
    const stats = await getExceptionStatistics(
      queryParams.startTime,
      queryParams.endTime
    )

    const topType = stats.eventsByType
      ? Object.entries(stats.eventsByType).sort((a, b) => b[1] - a[1])[0]?.[0] || ''
      : ''
    const topIp = stats.topClientIps?.[0]?.ip || '-'
    listData.value = {
      totalElements: stats.totalEvents || 0,
      totalTypes: stats.totalTypes || 0,
      topExceptionType: topType,
      topClientIp: topIp
    }
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

// 查询
const handleQuery = () => {
  currentPage.value = 1
  loadData()
}

// 重置
const handleReset = () => {
  queryParams.exceptionType = undefined
  queryParams.errorCode = undefined
  queryParams.errorCategory = undefined
  queryParams.clientIp = undefined
  queryParams.serviceType = undefined
  queryParams.modelName = undefined
  dateRange.value = null
  queryParams.startTime = undefined
  queryParams.endTime = undefined
  handleQuery()
}

// 刷新
const handleRefresh = () => {
  loadData()
  ElMessage.success(t('exception.list.refreshSuccess'))
}

// 分页大小变化
const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadData()
}

// 页码变化
const handleCurrentChange = (page: number) => {
  currentPage.value = page
  loadData()
}

// 行点击
const handleRowClick = (row: ExceptionEvent) => {
  handleViewDetail(row)
}

// 查看详情
const handleViewDetail = (row: ExceptionEvent) => {
  selectedEvent.value = { ...row }
  detailVisible.value = true
}

// 跳转到统计分析页
const goToStatistics = () => {
  router.push('/exceptions/statistics')
}

// 显示清理抽屉
const showCleanupDialog = () => {
  cleanupForm.cutoffTime = ''
  cleanupForm.aggregatedOnly = false
  cleanupVisible.value = true
}

// 清理过期数据：二次确认由抽屉里的 ConfirmAction 承担，这里直接执行
const handleCleanup = async () => {
  if (!cleanupForm.cutoffTime) {
    ElMessage.warning(t('exception.list.selectCutoffTime'))
    return
  }

  cleanupSubmitting.value = true
  try {
    const result = await deleteOldExceptionEvents(
      cleanupForm.cutoffTime,
      cleanupForm.aggregatedOnly
    )

    ElMessage.success(t('exception.list.deleteSuccess', { count: result.deletedCount }))
    cleanupVisible.value = false
    loadData()
  } catch (error) {
    console.error('清理异常事件失败:', error)
    ElMessage.error(t('exception.list.cleanupFailed', { message: getErrorMessage(error) || t('exception.list.unknownError') }))
  } finally {
    cleanupSubmitting.value = false
  }
}

// 初始化
onMounted(() => {
  loadData()
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

/* FilterBar 落在 page-toolbar 这个 flex 容器里，需要占满整行 */
.toolbar-filter {
  width: 100%;
}

/* 长消息块：限高滚动，避免撑开抽屉 */
.message-block {
  max-height: 180px;
  overflow-y: auto;
  margin: 0;
  padding: 10px;
  background-color: var(--ja-primary-light-9, var(--el-fill-color-light));
  border-radius: 4px;
  font-family: var(--el-font-family-mono, monospace);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 抽屉底部动作条：右对齐、间距统一 */
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: var(--ja-space-2, 8px);
}

/* 两行查询条件：用 flex + row-gap 控制行距，并给整块一个面板底（原来 item 的 margin-bottom 被清零 ⇒ 两行 32px 控件之间 0 间距、整块贴在页头上） */
.filter-form {
  width: 100%;
  display: flex;
  flex-wrap: wrap;
  column-gap: 18px;
  row-gap: 12px;
  padding: 14px 16px;
  background: var(--ja-paper);
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius-lg);
}

.filter-form .el-form-item {
  margin: 0;
}

.table-wrapper {
  margin-bottom: 20px;
}

.table-wrapper .exception-type {
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

/* compact：收窄单元格内边距与字号，尽量在 768px 下不出现横向滚动 */
.compact-table :deep(.cell) {
  padding: 0 3px;
  font-size: 11px;
}

/* 窄屏卡片（TableCards 内由本页插槽渲染） */
.exception-card {
  display: flex;
  flex-direction: column;
  gap: var(--ja-space-2, 8px);
  min-width: 0;
}

.exception-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--ja-space-2, 8px);
}

.exception-card__title {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  font-weight: 600;
  word-break: break-all;
  color: var(--ja-ink);
}

.exception-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--ja-space-1, 4px) var(--ja-space-3, 12px);
}

.exception-card__field {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.exception-card__field--full {
  grid-column: 1 / -1;
}

.exception-card__label {
  font-size: 12px;
  color: var(--ja-ink-3);
}

.exception-card__value {
  font-size: 13px;
  color: var(--ja-ink);
  overflow-wrap: anywhere;
}

.exception-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--ja-space-1, 4px);
  padding-top: var(--ja-space-2, 8px);
  border-top: 1px solid var(--ja-border-light);
}

@media (max-width: 420px) {
  .exception-card__meta {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
