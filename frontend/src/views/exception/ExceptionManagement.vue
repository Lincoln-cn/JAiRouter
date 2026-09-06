<template>
  <div class="exception-management">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <StatCard :icon="Warning" :label="t('exception.list.totalExceptionsLabel')" :value="listData.totalElements" tone="danger" />
      </el-col>
      <el-col :span="6">
        <StatCard :icon="DataLine" :label="t('exception.list.totalTypesLabel')" :value="listData.totalTypes" tone="warning" />
      </el-col>
      <el-col :span="6">
        <StatCard :icon="Monitor" :label="t('exception.list.topExceptionTypeLabel')" :value="listData.topExceptionType || '-'" tone="primary" />
      </el-col>
      <el-col :span="6">
        <StatCard :icon="TrendCharts" :label="t('exception.list.topClientIpLabel')" :value="listData.topClientIp || '-'" tone="info" />
      </el-col>
    </el-row>

    <!-- 主卡片 -->
    <el-card class="main-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="main-title">
            <el-icon><Warning /></el-icon>
            {{ t('exception.list.title') }}
          </span>
          <div class="header-buttons">
            <el-button icon="Refresh" @click="handleRefresh">{{ t('exception.list.refresh') }}</el-button>
            <el-button icon="Delete" type="danger" @click="showCleanupDialog">{{ t('exception.list.cleanupExpired') }}</el-button>
            <el-button icon="DataAnalysis" type="success" @click="goToStatistics">{{ t('exception.list.goStatistics') }}</el-button>
          </div>
        </div>
      </template>

      <!-- 筛选条件 -->
      <div class="filter-section">
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
              <el-option :label="t('exception.list.serviceTypes.chat')" value="CHAT" />
              <el-option :label="t('exception.list.serviceTypes.embedding')" value="EMBEDDING" />
              <el-option :label="t('exception.list.serviceTypes.rerank')" value="RERANK" />
              <el-option :label="t('exception.list.serviceTypes.tts')" value="TTS" />
              <el-option :label="t('exception.list.serviceTypes.stt')" value="STT" />
              <el-option :label="t('exception.list.serviceTypes.imageGeneration')" value="IMG_GENERATE" />
              <el-option :label="t('exception.list.serviceTypes.imageEditing')" value="IMG_EDIT" />
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
            <el-button icon="Refresh" @click="handleReset">{{ t('exception.list.reset') }}</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 表格 -->
      <div class="table-wrapper">
        <el-table
          v-loading="loading"
          :data="exceptionList"
          border
          stripe
          :max-height="500"
          @row-click="handleRowClick"
        >
          <el-table-column :label="t('exception.list.eventId')" prop="eventId" width="200" show-overflow-tooltip>
            <template #default="scope">
              <el-tag effect="plain" type="info">{{ scope.row.eventId }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('exception.list.exceptionType')" prop="exceptionType" min-width="180" show-overflow-tooltip>
            <template #default="scope">
              <span class="exception-type">{{ scope.row.exceptionType }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('exception.list.serviceType')" prop="serviceType" width="100">
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
          <el-table-column :label="t('exception.list.modelName')" prop="modelName" min-width="140" show-overflow-tooltip>
            <template #default="scope">
              <span>{{ scope.row.modelName || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('exception.list.provider')" prop="provider" width="100" show-overflow-tooltip>
            <template #default="scope">
              <span>{{ scope.row.provider || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('exception.list.responseTime')" prop="responseTimeMs" width="120" sortable>
            <template #default="scope">
              <span>{{ scope.row.responseTimeMs != null ? scope.row.responseTimeMs : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('exception.list.errorCode')" prop="errorCode" width="100">
            <template #default="scope">
              <el-tag :type="getErrorTagType(scope.row.errorCode)" size="small">
                {{ scope.row.errorCode }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('exception.list.errorCategory')" prop="errorCategory" width="120">
            <template #default="scope">
              <el-tag :type="getCategoryTagType(scope.row.errorCategory)" size="small">
                {{ formatCategory(scope.row.errorCategory) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('exception.list.exceptionMessage')" prop="sanitizedMessage" min-width="200" show-overflow-tooltip>
            <template #default="scope">
              <span>{{ scope.row.sanitizedMessage || scope.row.exceptionMessage || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('exception.list.clientIp')" prop="clientIp" width="140">
            <template #default="scope">
              <span>{{ scope.row.clientIp || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('exception.list.occurredAt')" prop="occurredAt" width="180">
            <template #default="scope">
              <span>{{ formatTime(scope.row.occurredAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('exception.list.actions')" width="100" fixed="right">
            <template #default="scope">
              <el-button link type="primary" size="small" @click.stop="handleViewDetail(scope.row)">
                {{ t('exception.list.detail') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="pagination-section">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalElements"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      :title="t('exception.list.detailDialogTitle')"
      width="800px"
      :close-on-click-modal="false"
    >
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
        <el-descriptions-item :label="t('exception.list.exceptionMessage')" :span="2">
          <el-input
            v-model="selectedEvent.exceptionMessage"
            type="textarea"
            :rows="4"
            readonly
          />
        </el-descriptions-item>
        <el-descriptions-item :label="t('exception.list.sanitizedMessage')" :span="2">
          <el-input
            v-model="selectedEvent.sanitizedMessage"
            type="textarea"
            :rows="3"
            readonly
          />
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
    </el-dialog>

    <!-- 清理对话框 -->
    <el-dialog
      v-model="cleanupVisible"
      :title="t('exception.list.cleanupDialogTitle')"
      width="500px"
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
        <el-button @click="cleanupVisible = false">{{ t('exception.list.cancel') }}</el-button>
        <el-button type="danger" @click="handleCleanup">{{ t('exception.list.confirmCleanup') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Warning,
  DataLine,
  Monitor,
  TrendCharts,
  Refresh,
  Delete,
  DataAnalysis,
  Search
} from '@element-plus/icons-vue'
import {
  queryExceptionEvents,
  getExceptionStatistics,
  deleteOldExceptionEvents,
  getExceptionDashboardData
} from '@/api/exception'
import type { ExceptionEvent, ExceptionQueryParams, ExceptionQueryResponse } from '@/types/exception'
import StatCard from '@/components/StatCard.vue'

const router = useRouter()

const { t } = useI18n()

// Cross-link: 将大写服务类型转为小写路由参数
const normalizeServiceType = (st?: string): string => {
  if (!st) return ''
  return st.toLowerCase()
}

// 加载状态
const loading = ref(false)

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

// 详情对话框
const detailVisible = ref(false)
const selectedEvent = ref<ExceptionEvent | null>(null)

// 清理对话框
const cleanupVisible = ref(false)
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
  } catch (error: any) {
    console.error('加载异常列表失败:', error)
    ElMessage.error(t('exception.list.loadFailed', { message: error.message || t('exception.list.unknownError') }))
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

// 显示清理对话框
const showCleanupDialog = () => {
  cleanupForm.cutoffTime = ''
  cleanupForm.aggregatedOnly = false
  cleanupVisible.value = true
}

// 清理过期数据
const handleCleanup = async () => {
  if (!cleanupForm.cutoffTime) {
    ElMessage.warning(t('exception.list.selectCutoffTime'))
    return
  }

  try {
    await ElMessageBox.confirm(
      t('exception.list.cleanupConfirmMessage'),
      t('exception.list.warning'),
      {
        confirmButtonText: t('exception.list.confirm'),
        cancelButtonText: t('exception.list.cancel'),
        type: 'warning'
      }
    )

    const result = await deleteOldExceptionEvents(
      cleanupForm.cutoffTime,
      cleanupForm.aggregatedOnly
    )

    ElMessage.success(t('exception.list.deleteSuccess', { count: result.deletedCount }))
    cleanupVisible.value = false
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('清理异常事件失败:', error)
      ElMessage.error(t('exception.list.cleanupFailed', { message: error.message || t('exception.list.unknownError') }))
    }
  }
}

// 初始化
onMounted(() => {
  loadData()
})
</script>

<style scoped>
.exception-management {
  padding: 20px;
}

.stats-row {
  margin-bottom: 20px;
}

.main-card .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.main-card .main-title {
  font-size: 18px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 8px;
}

.main-card .header-buttons {
  display: flex;
  gap: 10px;
}

.main-card .filter-section {
  margin-bottom: 20px;
}

.main-card .filter-section .filter-form .el-form-item {
  margin-bottom: 0;
  margin-right: 15px;
}

.main-card .table-wrapper {
  margin-bottom: 20px;
}

.main-card .table-wrapper .exception-type {
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

.main-card .pagination-section {
  display: flex;
  justify-content: flex-end;
}
</style>
