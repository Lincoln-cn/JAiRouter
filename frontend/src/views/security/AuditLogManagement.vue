<template>
  <PageSkeleton :title="t('auditLog.pageTitle')">
    <template #actions>
      <el-button-group>
        <el-button @click="handleRefresh" :icon="Refresh">{{ t('auditLog.refresh') }}</el-button>
        <el-dropdown @command="handleExport">
          <el-button type="primary">
            {{ t('auditLog.export') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="csv">{{ t('auditLog.exportCsv') }}</el-dropdown-item>
              <el-dropdown-item command="excel">{{ t('auditLog.exportExcel') }}</el-dropdown-item>
              <el-dropdown-item command="json">{{ t('auditLog.exportJson') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-button-group>
    </template>

    <template #stats>
      <!-- 统计概览：4 张渐变图标卡 → 一条中性 MetricStrip（仅异常计数带语义色） -->
      <MetricStrip :items="kpiItems" :columns="4" />

      <el-row :gutter="20" style="margin-top: 16px;">
        <!-- 事件类型分布：环形图 → 横向条形 Top5 -->
        <el-col :span="12">
          <ChartCard
            :title="t('auditLog.chartEventTypes')"
            :loading="loading"
            :empty="eventTypeEmpty"
            :empty-title="t('common.chartEmpty')"
            :empty-description="t('common.chartEmptyHint')"
            :height="300"
          >
            <div ref="eventTypeChartRef" class="chart-container"></div>
          </ChartCard>
        </el-col>
        <!-- 操作趋势图 -->
        <el-col :span="12">
          <ChartCard
            :title="t('auditLog.chartTrend')"
            :loading="loading"
            :empty="trendEmpty"
            :empty-title="t('common.chartEmpty')"
            :empty-description="t('common.chartEmptyHint')"
            :height="300"
          >
            <div ref="trendChartRef" class="chart-container"></div>
          </ChartCard>
        </el-col>
      </el-row>
    </template>

    <template #toolbar>
      <!-- 搜索条件：并入 FilterBar（白底面板，不再用第三套容器样式） -->
      <FilterBar :collapsible="false" class="toolbar-filter">
        <el-form :model="searchForm" class="search-form">
          <el-row :gutter="20">
            <el-col :span="6">
              <el-form-item :label="t('auditLog.timeRange')">
                <el-select v-model="searchForm.quickTime" :placeholder="t('auditLog.quickTimePlaceholder')" @change="handleQuickTimeChange" clearable>
                  <el-option :label="t('auditLog.today')" value="today" />
                  <el-option :label="t('auditLog.yesterday')" value="yesterday" />
                  <el-option :label="t('auditLog.thisWeek')" value="week" />
                  <el-option :label="t('auditLog.thisMonth')" value="month" />
                  <el-option :label="t('auditLog.last7Days')" value="last7days" />
                  <el-option :label="t('auditLog.last30Days')" value="last30days" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item :label="t('auditLog.startTime')">
                <el-date-picker
                  v-model="searchForm.startTime"
                  type="datetime"
                  :placeholder="t('auditLog.startTimePlaceholder')"
                  format="YYYY-MM-DD HH:mm:ss"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item :label="t('auditLog.endTime')">
                <el-date-picker
                  v-model="searchForm.endTime"
                  type="datetime"
                  :placeholder="t('auditLog.endTimePlaceholder')"
                  format="YYYY-MM-DD HH:mm:ss"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item :label="t('auditLog.eventType')">
                <el-select v-model="searchForm.eventType" :placeholder="t('auditLog.eventTypePlaceholder')" clearable filterable>
                  <el-option-group :label="t('auditLog.groupJwtTokens')">
                    <el-option :label="t('auditLog.eventTypes.JWT_TOKEN_ISSUED')" value="JWT_TOKEN_ISSUED" />
                    <el-option :label="t('auditLog.eventTypes.JWT_TOKEN_REFRESHED')" value="JWT_TOKEN_REFRESHED" />
                    <el-option :label="t('auditLog.eventTypes.JWT_TOKEN_REVOKED')" value="JWT_TOKEN_REVOKED" />
                    <el-option :label="t('auditLog.eventTypes.JWT_TOKEN_VALIDATED')" value="JWT_TOKEN_VALIDATED" />
                    <el-option :label="t('auditLog.eventTypes.JWT_TOKEN_EXPIRED')" value="JWT_TOKEN_EXPIRED" />
                  </el-option-group>
                  <el-option-group :label="t('auditLog.groupApiKey')">
                    <el-option :label="t('auditLog.eventTypes.API_KEY_CREATED')" value="API_KEY_CREATED" />
                    <el-option :label="t('auditLog.eventTypes.API_KEY_USED')" value="API_KEY_USED" />
                    <el-option :label="t('auditLog.eventTypes.API_KEY_REVOKED')" value="API_KEY_REVOKED" />
                    <el-option :label="t('auditLog.eventTypes.API_KEY_EXPIRED')" value="API_KEY_EXPIRED" />
                    <el-option :label="t('auditLog.eventTypes.API_KEY_UPDATED')" value="API_KEY_UPDATED" />
                    <el-option :label="t('auditLog.eventTypes.API_KEY_ROTATED')" value="API_KEY_ROTATED" />
                    <el-option :label="t('auditLog.eventTypes.API_KEY_BATCH_IMPORT')" value="API_KEY_BATCH_IMPORT" />
                    <el-option :label="t('auditLog.eventTypes.API_KEY_MISSING')" value="API_KEY_MISSING" />
                    <el-option :label="t('auditLog.eventTypes.API_KEY_INVALID')" value="API_KEY_INVALID" />
                    <el-option :label="t('auditLog.eventTypes.API_KEY_VALIDATION_FAILED')" value="API_KEY_VALIDATION_FAILED" />
                  </el-option-group>
                  <el-option-group :label="t('auditLog.groupSecurityEvents')">
                    <el-option :label="t('auditLog.eventTypes.AUTHENTICATION_FAILED')" value="AUTHENTICATION_FAILED" />
                    <el-option :label="t('auditLog.eventTypes.AUTHORIZATION_FAILED')" value="AUTHORIZATION_FAILED" />
                    <el-option :label="t('auditLog.eventTypes.BRUTE_FORCE_ATTEMPT')" value="BRUTE_FORCE_ATTEMPT" />
                    <el-option :label="t('auditLog.eventTypes.SUSPICIOUS_ACTIVITY')" value="SUSPICIOUS_ACTIVITY" />
                    <el-option :label="t('auditLog.eventTypes.SECURITY_ALERT')" value="SECURITY_ALERT" />
                  </el-option-group>
                  <el-option-group :label="t('auditLog.groupDataAndSystem')">
                    <el-option :label="t('auditLog.eventTypes.DATA_SANITIZATION')" value="DATA_SANITIZATION" />
                    <el-option :label="t('auditLog.eventTypes.RESPONSE_SANITIZATION')" value="RESPONSE_SANITIZATION" />
                    <el-option :label="t('auditLog.eventTypes.FULL_CONTENT_ACCESS')" value="FULL_CONTENT_ACCESS" />
                    <el-option :label="t('auditLog.eventTypes.RECORD_LEVEL_CHANGE')" value="RECORD_LEVEL_CHANGE" />
                    <el-option :label="t('auditLog.eventTypes.SYSTEM_CLEANUP')" value="SYSTEM_CLEANUP" />
                    <el-option :label="t('auditLog.eventTypes.SYSTEM_MAINTENANCE')" value="SYSTEM_MAINTENANCE" />
                  </el-option-group>
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="6">
              <el-form-item :label="t('auditLog.userId')">
                <el-input v-model="searchForm.userId" :placeholder="t('auditLog.userIdPlaceholder')" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item :label="t('auditLog.clientIp')">
                <el-input v-model="searchForm.clientIp" :placeholder="t('auditLog.clientIpPlaceholder')" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item :label="t('auditLog.result')">
                <el-select v-model="searchForm.success" :placeholder="t('auditLog.resultPlaceholder')" clearable>
                  <el-option :label="t('auditLog.success')" :value="true" />
                  <el-option :label="t('auditLog.failed')" :value="false" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label=" ">
                <el-button type="primary" @click="handleSearch">{{ t('auditLog.search') }}</el-button>
                <el-button @click="handleReset">{{ t('auditLog.reset') }}</el-button>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </FilterBar>
    </template>

    <!-- 日志表格 -->
    <el-table :data="logs" style="width: 100%" border v-loading="loading" :row-class-name="tableRowClassName">
      <el-table-column prop="timestamp" :label="t('auditLog.time')" width="180" sortable>
        <template #default="scope">
          {{ formatDateTime(scope.row.timestamp) }}
        </template>
      </el-table-column>
      <el-table-column prop="userId" :label="t('auditLog.userId')" width="120" show-overflow-tooltip />
      <el-table-column prop="type" :label="t('auditLog.eventType')" width="140">
        <template #default="scope">
          <el-tag :type="getEventTypeColor(scope.row.type)" size="small">
            {{ getEventTypeText(scope.row.type) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="resourceId" :label="t('auditLog.resourceId')" width="150" show-overflow-tooltip />
      <el-table-column prop="ipAddress" :label="t('auditLog.clientIp')" width="140" />
      <el-table-column prop="riskLevel" :label="t('auditLog.riskLevel')" width="100">
        <template #default="scope">
          <el-tag :type="getRiskLevelColor(scope.row.riskLevel)" size="small" v-if="scope.row.riskLevel">
            {{ scope.row.riskLevel }}
          </el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="success" :label="t('auditLog.outcome')" width="80">
        <template #default="scope">
          <el-tag :type="scope.row.success ? 'success' : 'danger'" size="small">
            {{ scope.row.success ? t('auditLog.success') : t('auditLog.failed') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="details" :label="t('auditLog.description')" show-overflow-tooltip />
      <el-table-column :label="t('auditLog.actions')" width="140" fixed="right">
        <template #default="scope">
          <el-button size="small" type="primary" link @click="handleViewDetail(scope.row)">{{ t('auditLog.detail') }}</el-button>
          <el-dropdown v-if="scope.row.ipAddress" trigger="click" @command="(cmd: string) => handleAddToBlacklist(scope.row, cmd)">
            <el-button size="small" type="warning" link>{{ t('auditLog.blacklist') }}</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="IP">{{ t('auditLog.banIp') }}</el-dropdown-item>
                <el-dropdown-item command="USER" :disabled="!scope.row.userId">{{ t('auditLog.banUser') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-pagination
        v-model:current-page="pagination.currentPage"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </template>
  </PageSkeleton>

    <!-- 日志详情对话框 -->
    <el-dialog v-model="detailDialogVisible" :title="t('auditLog.logDetailTitle')" width="650px">
      <el-descriptions v-if="currentLog" :column="2" border>
        <el-descriptions-item :label="t('auditLog.eventId')" :span="2">{{ currentLog.id }}</el-descriptions-item>
        <el-descriptions-item :label="t('auditLog.time')">{{ formatDateTime(currentLog.timestamp) }}</el-descriptions-item>
        <el-descriptions-item :label="t('auditLog.riskLevel')">
          <el-tag :type="getRiskLevelColor(currentLog.riskLevel)" v-if="currentLog.riskLevel">
            {{ currentLog.riskLevel }}
          </el-tag>
          <span v-else>LOW</span>
        </el-descriptions-item>
        <el-descriptions-item :label="t('auditLog.userId')">{{ currentLog.userId || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('auditLog.eventType')">
          <el-tag :type="getEventTypeColor(currentLog.type)">
            {{ getEventTypeText(currentLog.type) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('auditLog.resourceId')" :span="2">{{ currentLog.resourceId || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('auditLog.action')">{{ currentLog.action || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('auditLog.clientIp')">{{ currentLog.ipAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('auditLog.userAgent')" :span="2">{{ currentLog.userAgent || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('auditLog.outcome')">
          <el-tag :type="currentLog.success ? 'success' : 'danger'">
            {{ currentLog.success ? t('auditLog.success') : t('auditLog.failed') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item :label="t('auditLog.geoLocation')">{{ currentLog.geoLocation || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('auditLog.description')" :span="2">{{ currentLog.details || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('auditLog.metadata')" :span="2" v-if="currentLog.metadata && Object.keys(currentLog.metadata).length > 0">
          <pre class="metadata-pre">{{ JSON.stringify(currentLog.metadata, null, 2) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialogVisible = false">{{ t('auditLog.close') }}</el-button>
      </template>
    </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getErrorMessage } from '@/utils/error'
import { useI18n } from 'vue-i18n'
import { Refresh, ArrowDown } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  queryAuditEventsAdvanced,
  generateSecurityReport,
  type AuditEvent,
  type ExtendedAuditQueryResponse,
  type SecurityReport,
} from '@/api/auditLog'
import { addToBlacklist } from '@/api/blacklist'
import { useChartSkin } from '@/composables/useChartSkin'
import { useChartAutoRefresh } from '@/composables/useChartAutoRefresh'
import PageSkeleton from '@/components/PageSkeleton.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import ChartCard from '@/components/ChartCard.vue'
import FilterBar from '@/components/FilterBar.vue'
import { formatDateTime as formatDateTimeBase } from '@/utils/format'

const { t } = useI18n()

/** 图表统一皮肤（分类色板只含主色相 + 中性阶，语义色只给状态）。 */
const { skin } = useChartSkin()

// 搜索表单
const searchForm = reactive({
  quickTime: '',
  startTime: '',
  endTime: '',
  eventType: '',
  userId: '',
  clientIp: '',
  success: null as boolean | null
})

// 分页
const pagination = reactive({
  currentPage: 1,
  pageSize: 20,
  total: 0
})

// 审计日志数据
const logs = ref<AuditEvent[]>([])
const loading = ref(false)

const detailDialogVisible = ref(false)
const currentLog = ref<AuditEvent | null>(null)

// 统计数据
const stats = reactive({
  jwtOperations: 0,
  apiKeyOperations: 0,
  failedAuthentications: 0,
  suspiciousActivities: 0
})

// 图表引用
const eventTypeChartRef = ref<HTMLElement>()
const trendChartRef = ref<HTMLElement>()
let eventTypeChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null
// 最近一次统计图表数据源（响应式：语言/主题切换时纯重绘复用，不重新请求）
const lastOperationsByType = ref<Record<string, number>>({})

/**
 * 指标条数据。
 *
 * 前两项只是操作计数；后两项是「异常状态」⇒ 只有计数 > 0 时才带语义色
 * （0 用中性色，避免常态下满屏红黄）。
 */
const kpiItems = computed<MetricStripItem[]>(() => [
  {
    key: 'jwtOperations',
    label: t('auditLog.jwtOperations'),
    value: stats.jwtOperations
  },
  {
    key: 'apiKeyOperations',
    label: t('auditLog.apiKeyOperations'),
    value: stats.apiKeyOperations
  },
  {
    key: 'failedAuthentications',
    label: t('auditLog.failedAuth'),
    value: stats.failedAuthentications,
    tone: stats.failedAuthentications > 0 ? 'danger' : 'default'
  },
  {
    key: 'suspiciousActivities',
    label: t('auditLog.suspicious'),
    value: stats.suspiciousActivities,
    tone: stats.suspiciousActivities > 0 ? 'warning' : 'default'
  }
])

// 两张图的空态判定：都取自同一份 operationsByType（事件类型分布 / 趋势）
const eventTypeEmpty = computed(() => Object.keys(lastOperationsByType.value || {}).length === 0)
const trendEmpty = computed(() => Object.keys(lastOperationsByType.value || {}).length === 0)

// 快捷时间选择
const handleQuickTimeChange = (value: string) => {
  const now = new Date()
  let start: Date
  
  switch (value) {
    case 'today':
      start = new Date(now.getFullYear(), now.getMonth(), now.getDate())
      break
    case 'yesterday':
      start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
      searchForm.endTime = formatDate(now)
      break
    case 'week':
      start = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
      break
    case 'month':
      start = new Date(now.getFullYear(), now.getMonth(), 1)
      break
    case 'last7days':
      start = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
      break
    case 'last30days':
      start = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000)
      break
    default:
      return
  }
  
  searchForm.startTime = formatDate(start)
  if (value !== 'yesterday') {
    searchForm.endTime = formatDate(now)
  }
}

const formatDate = (date: Date): string => {
  return date.toISOString().slice(0, 19).replace('T', ' ')
}

// 搜索
const handleSearch = async () => {
  pagination.currentPage = 1
  await loadAuditLogs()
  ElMessage.success(t('auditLog.searchDone'))
}

// 重置
const handleReset = async () => {
  Object.assign(searchForm, {
    quickTime: '',
    startTime: '',
    endTime: '',
    eventType: '',
    userId: '',
    clientIp: '',
    success: null
  })
  pagination.currentPage = 1
  await loadAuditLogs()
}

// 刷新
const handleRefresh = async () => {
  await Promise.all([loadAuditLogs(), loadStatistics()])
  ElMessage.success(t('auditLog.refreshed'))
}

// 导出
const handleExport = async (format: string) => {
  try {
    const query = buildQuery()
    const result: ExtendedAuditQueryResponse = await queryAuditEventsAdvanced({ ...query, size: 10000 })
    
    let content = ''
    let filename = `audit-log-${new Date().toISOString().slice(0, 10)}`
    
    if (format === 'csv') {
      content = convertToCSV(result.events)
      filename += '.csv'
    } else if (format === 'json') {
      content = JSON.stringify(result.events, null, 2)
      filename += '.json'
    } else if (format === 'excel') {
      // 简化版：使用CSV格式，用户可以用Excel打开
      content = convertToCSV(result.events)
      filename += '.csv'
    }
    
    // 创建下载
    const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
    
    ElMessage.success(t('auditLog.exported', { count: result.events.length }))
  } catch (error) {
    ElMessage.error(t('auditLog.exportFailed', { message: getErrorMessage(error) || t('auditLog.unknownError') }))
  }
}

const convertToCSV = (events: AuditEvent[]): string => {
  const headers = [t('auditLog.time'), t('auditLog.eventType'), t('auditLog.userId'), t('auditLog.resourceId'), t('auditLog.clientIp'), t('auditLog.outcome'), t('auditLog.riskLevel'), t('auditLog.description')]
  const rows = events.map(e => [
    e.timestamp || '',
    e.type || '',
    e.userId || '',
    e.resourceId || '',
    e.ipAddress || '',
    e.success ? t('auditLog.success') : t('auditLog.failed'),
    e.riskLevel || 'LOW',
    e.details || ''
  ])
  return [headers.join(','), ...rows.map(r => r.map(c => `"${c}"`).join(','))].join('\n')
}

// 查看详情
const handleViewDetail = (row: AuditEvent) => {
  currentLog.value = row
  detailDialogVisible.value = true
}

// 添加到黑名单
const handleAddToBlacklist = async (log: AuditEvent, type: string) => {
  let targetValue = ''
  let reason = ''

  switch (type) {
    case 'IP':
      targetValue = log.ipAddress || ''
      reason = t('auditLog.banReasonIp', { type: log.type })
      break
    case 'USER':
      targetValue = log.userId || ''
      reason = t('auditLog.banReasonUser', { type: log.type })
      break
  }

  if (!targetValue) {
    ElMessage.warning(t('auditLog.noTargetValue'))
    return
  }

  try {
    await ElMessageBox.confirm(
      t('auditLog.confirmMessage', {
        entity: type === 'IP' ? t('auditLog.entityIp') : t('auditLog.entityUser'),
        target: targetValue
      }),
      t('auditLog.confirmTitle'),
      { type: 'warning' }
    )

    const result = await addToBlacklist({
      blacklistType: type === 'IP' ? 'IP' : 'DEVICE',
      targetValue,
      userId: type === 'USER' ? targetValue : log.userId,
      reason,
      riskLevel: 'HIGH'
    })

    if (result.success) {
      ElMessage.success(t('auditLog.addedToBlacklist'))
    } else {
      ElMessage.error(result.message || t('auditLog.addFailed'))
    }
  } catch {
    // 用户取消
  }
}

// 分页
const handleSizeChange = async (val: number) => {
  pagination.pageSize = val
  pagination.currentPage = 1
  await loadAuditLogs()
}

const handleCurrentChange = async (val: number) => {
  pagination.currentPage = val
  await loadAuditLogs()
}

// 构建查询参数
const buildQuery = () => ({
  startTime: searchForm.startTime || undefined,
  endTime: searchForm.endTime || undefined,
  userId: searchForm.userId || undefined,
  ipAddress: searchForm.clientIp || undefined,
  success: searchForm.success,
  eventType: searchForm.eventType || undefined,
  page: pagination.currentPage - 1,
  size: pagination.pageSize
})

// 加载审计日志
const loadAuditLogs = async () => {
  loading.value = true
  try {
    const query = buildQuery()
    const result: ExtendedAuditQueryResponse = await queryAuditEventsAdvanced(query)
    logs.value = result.events
    pagination.total = result.totalElements
  } catch (error) {
    ElMessage.error(t('auditLog.loadFailed', { message: getErrorMessage(error) || t('auditLog.unknownError') }))
  } finally {
    loading.value = false
  }
}

// 加载统计数据
const loadStatistics = async () => {
  try {
    const report: SecurityReport = await generateSecurityReport(searchForm.startTime, searchForm.endTime)
    stats.jwtOperations = report.totalJwtOperations
    stats.apiKeyOperations = report.totalApiKeyOperations
    stats.failedAuthentications = report.failedAuthentications
    stats.suspiciousActivities = report.suspiciousActivities
    
    // 更新图表
    lastOperationsByType.value = report.operationsByType || {}
    updateEventTypeChart(report.operationsByType)
    updateTrendChart(report.operationsByType)
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

// 更新事件类型图表（横向条形 Top5：环形图在图例压环、单类整圈两个方向上都不成立）
const updateEventTypeChart = (data: Record<string, number>) => {
  if (!eventTypeChartRef.value) return

  if (!eventTypeChart) {
    eventTypeChart = echarts.init(eventTypeChartRef.value)
  }

  const chartSkin = skin.value

  // 按数量降序取前 5 类；类目轴自下而上排列，故倒序后「最多的一类」落在最上方
  const top5 = Object.entries(data || {})
    .map(([type, count]) => ({ type, count: Number(count) || 0 }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 5)
    .reverse()

  eventTypeChart.setOption({
    tooltip: {
      ...chartSkin.tooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    // 类别名较长：containLabel + 独立的左右留白（left 8 会让中文类型名被裁）
    grid: { ...chartSkin.grid, left: 8, right: 40 },
    xAxis: { ...chartSkin.valueAxis, type: 'value' },
    yAxis: {
      ...chartSkin.categoryAxis,
      type: 'category',
      data: top5.map(item => getEventTypeText(item.type)),
      axisLabel: { color: chartSkin.textMuted, fontSize: 12 }
    },
    series: [{
      type: 'bar',
      data: top5.map(item => item.count),
      barMaxWidth: 16,
      itemStyle: {
        color: chartSkin.palette[0],
        borderRadius: [0, 4, 4, 0]
      },
      label: {
        show: true,
        position: 'right',
        color: chartSkin.textMuted,
        fontSize: 12
      }
    }]
  }, { notMerge: true })
}

// 更新趋势图表（保留纵向柱图；颜色统一到皮肤主色）
const updateTrendChart = (data: Record<string, number>) => {
  if (!trendChartRef.value) return

  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }

  const chartSkin = skin.value
  const categories = Object.keys(data || {}).map(type => getEventTypeText(type))
  const values = Object.values(data || {})

  trendChart.setOption({
    tooltip: {
      ...chartSkin.tooltip,
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: { ...chartSkin.grid },
    xAxis: {
      ...chartSkin.categoryAxis,
      type: 'category',
      data: categories,
      axisLabel: { color: chartSkin.textMuted, fontSize: 12, rotate: 45, interval: 0 }
    },
    yAxis: { ...chartSkin.valueAxis, type: 'value' },
    series: [{
      data: values,
      type: 'bar',
      barMaxWidth: 24,
      itemStyle: {
        color: chartSkin.palette[0],
        borderRadius: [4, 4, 0, 0]
      },
      label: { show: false }
    }]
  }, { notMerge: true })
}

// 格式化日期时间（委托共享 format.ts）
const formatDateTime = (dateTime: string) => {
  if (!dateTime) return ''
  return formatDateTimeBase(dateTime)
}

// 获取事件类型颜色
const getEventTypeColor = (type: string) => {
  if (type?.startsWith('JWT_TOKEN')) return 'primary'
  if (type?.startsWith('API_KEY')) return 'success'
  if (type?.includes('FAILED') || type?.includes('SUSPICIOUS')) return 'danger'
  if (type?.includes('ALERT')) return 'warning'
  return 'info'
}

// 获取风险等级颜色
const getRiskLevelColor = (level: string) => {
  switch (level) {
    case 'CRITICAL': return 'danger'
    case 'HIGH': return 'warning'
    case 'MEDIUM': return 'info'
    default: return 'success'
  }
}

// 获取事件类型文本（覆盖后端 AuditEventType 全部 26 个枚举值；未知类型回退原枚举名）
const getEventTypeText = (type: string) => {
  const typeMap: Record<string, string> = {
    'JWT_TOKEN_ISSUED': t('auditLog.eventBadges.JWT_TOKEN_ISSUED'),
    'JWT_TOKEN_REFRESHED': t('auditLog.eventBadges.JWT_TOKEN_REFRESHED'),
    'JWT_TOKEN_REVOKED': t('auditLog.eventBadges.JWT_TOKEN_REVOKED'),
    'JWT_TOKEN_VALIDATED': t('auditLog.eventBadges.JWT_TOKEN_VALIDATED'),
    'JWT_TOKEN_EXPIRED': t('auditLog.eventBadges.JWT_TOKEN_EXPIRED'),
    'API_KEY_CREATED': t('auditLog.eventBadges.API_KEY_CREATED'),
    'API_KEY_USED': t('auditLog.eventBadges.API_KEY_USED'),
    'API_KEY_REVOKED': t('auditLog.eventBadges.API_KEY_REVOKED'),
    'API_KEY_EXPIRED': t('auditLog.eventBadges.API_KEY_EXPIRED'),
    'API_KEY_UPDATED': t('auditLog.eventBadges.API_KEY_UPDATED'),
    'API_KEY_ROTATED': t('auditLog.eventBadges.API_KEY_ROTATED'),
    'API_KEY_BATCH_IMPORT': t('auditLog.eventBadges.API_KEY_BATCH_IMPORT'),
    'API_KEY_MISSING': t('auditLog.eventBadges.API_KEY_MISSING'),
    'API_KEY_INVALID': t('auditLog.eventBadges.API_KEY_INVALID'),
    'API_KEY_VALIDATION_FAILED': t('auditLog.eventBadges.API_KEY_VALIDATION_FAILED'),
    'AUTHENTICATION_FAILED': t('auditLog.eventBadges.AUTHENTICATION_FAILED'),
    'AUTHORIZATION_FAILED': t('auditLog.eventBadges.AUTHORIZATION_FAILED'),
    'BRUTE_FORCE_ATTEMPT': t('auditLog.eventBadges.BRUTE_FORCE_ATTEMPT'),
    'SUSPICIOUS_ACTIVITY': t('auditLog.eventBadges.SUSPICIOUS_ACTIVITY'),
    'SECURITY_ALERT': t('auditLog.eventBadges.SECURITY_ALERT'),
    'DATA_SANITIZATION': t('auditLog.eventBadges.DATA_SANITIZATION'),
    'RESPONSE_SANITIZATION': t('auditLog.eventBadges.RESPONSE_SANITIZATION'),
    'FULL_CONTENT_ACCESS': t('auditLog.eventBadges.FULL_CONTENT_ACCESS'),
    'RECORD_LEVEL_CHANGE': t('auditLog.eventBadges.RECORD_LEVEL_CHANGE'),
    'SYSTEM_CLEANUP': t('auditLog.eventBadges.SYSTEM_CLEANUP'),
    'SYSTEM_MAINTENANCE': t('auditLog.eventBadges.SYSTEM_MAINTENANCE')
  }
  return typeMap[type] || type
}

// 表格行样式
const tableRowClassName = ({ row }: { row: AuditEvent }) => {
  if (row.riskLevel === 'CRITICAL' || row.riskLevel === 'HIGH') return 'warning-row'
  if (!row.success) return 'error-row'
  return ''
}

// 窗口大小变化时重绘图表
const handleResize = () => {
  eventTypeChart?.resize()
  trendChart?.resize()
}

// 语言 / 主题切换后基于最近统计结果重绘图表（纯重绘，无网络请求）
const rebuildAll = () => {
  updateEventTypeChart(lastOperationsByType.value)
  updateTrendChart(lastOperationsByType.value)
}

onMounted(async () => {
  useChartAutoRefresh(rebuildAll)
  await loadAuditLogs()
  await loadStatistics()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  eventTypeChart?.dispose()
  trendChart?.dispose()
})
</script>

<style scoped>
/* ChartCard body 内的图表容器：撑满固定高度，保证 echarts 初始化时尺寸已确定 */
.chart-container {
  width: 100%;
  height: 100%;
}

/* FilterBar 落在 page-toolbar 这个 flex 容器里，需要占满整行 */
.toolbar-filter {
  width: 100%;
}

.search-form {
  width: 100%;
  padding: 16px;
  background-color: var(--ja-paper);
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius);
}

.metadata-pre {
  max-height: 200px;
  overflow-y: auto;
  background-color: var(--ja-primary-light-9, var(--el-fill-color-light));
  padding: 10px;
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
}

:deep(.warning-row) {
  background-color: var(--el-color-warning-light-9);
}

:deep(.error-row) {
  background-color: var(--el-color-danger-light-9);
}
</style>