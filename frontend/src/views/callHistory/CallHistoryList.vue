<template>
  <PageSkeleton :title="t('callHistory.list.pageTitle')">
    <template #toolbar>
      <!-- 查询筛选区 -->
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
            <el-option :label="t('callHistory.serviceTypes.chat')" value="chat" />
            <el-option :label="t('callHistory.serviceTypes.embedding')" value="embedding" />
            <el-option :label="t('callHistory.serviceTypes.rerank')" value="rerank" />
            <el-option :label="t('callHistory.serviceTypes.tts')" value="tts" />
            <el-option :label="t('callHistory.serviceTypes.stt')" value="stt" />
            <el-option :label="t('callHistory.serviceTypes.imgGen')" value="imgGen" />
            <el-option :label="t('callHistory.serviceTypes.imgEdit')" value="imgEdit" />
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
          <el-button type="primary" icon="Search" @click="handleSearch">{{ t('callHistory.common.query') }}</el-button>
          <el-button icon="Refresh" @click="handleReset">{{ t('callHistory.common.reset') }}</el-button>
        </el-form-item>
      </el-form>
    </template>

    <!-- v2.9.2 记录治理设置 -->
    <el-card class="settings-card" shadow="hover" v-loading="configLoading">
      <template #header>
        <div class="table-header">
          <span class="chart-title">{{ t('callHistory.list.recordGovernanceTitle') }}</span>
        </div>
      </template>
      <el-form label-width="100px" class="settings-form">
        <el-form-item :label="t('callHistory.list.recordLevelLabel')">
          <el-radio-group v-model="recordLevel" :disabled="configSaving">
            <el-radio value="METADATA_ONLY">{{ t('callHistory.list.recordLevels.metadataOnly') }}</el-radio>
            <el-radio value="SUMMARY">{{ t('callHistory.list.recordLevels.summary') }}</el-radio>
            <el-radio value="FULL">{{ t('callHistory.list.recordLevels.full') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="configSaving"
            :disabled="configSaving || configLoading"
            @click="handleSaveConfig"
          >
            {{ t('callHistory.common.save') }}
          </el-button>
        </el-form-item>
      </el-form>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="t('callHistory.list.levelGuideTitle')"
        :description="t('callHistory.list.levelGuideDescription')"
      />
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="hover">
      <template #header>
        <div class="table-header">
          <span class="chart-title">{{ t('callHistory.list.listTitle') }}</span>
          <span class="total-count">{{ t('callHistory.list.totalRecords', { count: totalCount }) }}</span>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        :max-height="600"
        @sort-change="handleSortChange"
      >
        <el-table-column :label="t('callHistory.common.time')" prop="createdAt" width="180" sortable="custom">
          <template #default="scope">
            {{ formatTime(scope.row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="Trace ID" prop="traceId" width="140" show-overflow-tooltip>
          <template #default="scope">
            <el-link type="primary" @click="handleTraceIdClick(scope.row.traceId)">
              {{ scope.row.traceId?.substring(0, 8) }}...
            </el-link>
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.common.modelName')" prop="modelName" min-width="160" show-overflow-tooltip />
        <el-table-column :label="t('callHistory.common.serviceType')" prop="serviceType" width="110">
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
        <el-table-column :label="t('callHistory.common.provider')" prop="provider" width="100" show-overflow-tooltip />
        <el-table-column :label="t('callHistory.common.httpStatus')" prop="httpStatusCode" width="100" align="center" sortable="custom">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.httpStatusCode)" size="small">
              {{ scope.row.httpStatusCode || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.common.token')" prop="totalTokens" width="100" align="right" sortable="custom">
          <template #default="scope">
            {{ formatNumber(scope.row.totalTokens) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.common.responseTimeMs')" prop="responseTimeMs" width="120" align="right" sortable="custom">
          <template #default="scope">
            <el-tag :type="getResponseTimeType(scope.row.responseTimeMs)" size="small">
              {{ scope.row.responseTimeMs?.toFixed(0) || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.common.status')" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.isSuccess ? 'success' : 'danger'" size="small">
              {{ scope.row.isSuccess ? t('callHistory.common.success') : t('callHistory.common.failed') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('callHistory.common.action')" width="80" fixed="right" align="center">
          <template #default="scope">
            <el-button type="primary" link size="small" @click="handleDetail(scope.row)">
              {{ t('callHistory.common.details') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

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
  </PageSkeleton>

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
            <el-tag :type="selectedRecord.isSuccess ? 'success' : 'danger'">
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
            <el-tag type="success" size="small">{{ t('callHistory.list.tokenStats.output', { value: selectedRecord.completionTokens }) }}</el-tag>
            <el-tag type="warning" size="small">{{ t('callHistory.list.tokenStats.total', { value: selectedRecord.totalTokens }) }}</el-tag>
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
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { queryCallHistory, getCallHistoryConfig, updateCallHistoryConfig } from '@/api/callHistory'
import type { CallHistoryConfig } from '@/api/callHistory'
import type { ApiCallHistoryRecord, CallHistoryQuery } from '@/types/callHistory'
import PageSkeleton from '@/components/PageSkeleton.vue'

const router = useRouter()
const { t } = useI18n()

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
const loading = ref(false)
const totalCount = ref(0)

// 详情抽屉
const drawerVisible = ref(false)
const selectedRecord = ref<ApiCallHistoryRecord | null>(null)

// ---- v2.9.2 记录治理 settings ----
const configLoading = ref(false)
const configSaving = ref(false)
const recordLevel = ref<string>('METADATA_ONLY')
const configLoaded = ref(false)

const loadConfig = async () => {
  configLoading.value = true
  try {
    const cfg: CallHistoryConfig = await getCallHistoryConfig()
    recordLevel.value = cfg.recordLevel || 'METADATA_ONLY'
    configLoaded.value = true
  } catch (error: any) {
    console.error('加载记录配置失败:', error)
    ElMessage.error(t('callHistory.list.loadConfigFailed', { message: error.message || t('callHistory.common.unknownError') }))
  } finally {
    configLoading.value = false
  }
}

const handleSaveConfig = async () => {
  configSaving.value = true
  try {
    await updateCallHistoryConfig({ recordLevel: recordLevel.value })
    ElMessage.success(t('callHistory.list.saveSuccess'))
  } catch (error: any) {
    console.error('保存记录配置失败:', error)
    ElMessage.error(t('callHistory.list.saveConfigFailed', { message: error.message || t('callHistory.common.unknownError') }))
  } finally {
    configSaving.value = false
  }
}

// 格式化数字
const formatNumber = (num?: number): string => {
  if (!num && num !== 0) return '0'
  return num.toLocaleString()
}

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

// 获取服务类型标签
const getServiceTypeLabel = (type?: string) => {
  if (!type) return t('callHistory.common.unknown')
  return serviceTypeKeyMap[type] ? t(`callHistory.serviceTypes.${serviceTypeKeyMap[type]}`) : type
}

// 获取 HTTP 状态码标签类型
const getStatusType = (code?: number) => {
  if (!code) return 'info'
  if (code >= 200 && code < 300) return 'success'
  if (code >= 400 && code < 500) return 'warning'
  if (code >= 500) return 'danger'
  return 'info'
}

// 获取响应时间标签类型
const getResponseTimeType = (ms?: number) => {
  if (!ms) return 'info'
  if (ms < 500) return 'success'
  if (ms < 1000) return 'warning'
  return 'danger'
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
  } catch (error: any) {
    console.error('加载调用历史失败:', error)
    ElMessage.error(t('callHistory.list.loadFailed', { message: error.message || t('callHistory.common.unknownError') }))
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
  } catch (error: any) {
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
  loadConfig()
})
</script>

<style scoped>
.settings-card {
  margin-bottom: 16px;
}

.settings-form {
  margin-bottom: 12px;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
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
  color: var(--ja-text-secondary);
}

.error-message {
  color: var(--el-color-danger);
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
  color: var(--ja-text-regular);
  margin-bottom: 4px;
}

.summary-content {
  background-color: var(--el-fill-color-light);
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 12px;
  word-break: break-all;
  white-space: pre-wrap;
  max-height: 200px;
  overflow-y: auto;
  margin: 0;
}
</style>
