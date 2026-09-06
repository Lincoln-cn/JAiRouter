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
      <!-- 统计概览卡片 -->
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card shadow="hover" class="stats-card jwt-card">
            <div class="stats-content">
              <div class="stats-icon">
                <el-icon size="32"><Key /></el-icon>
              </div>
              <div class="stats-info">
                <div class="stats-value">{{ stats.jwtOperations }}</div>
                <div class="stats-label">{{ t('auditLog.jwtOperations') }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stats-card api-card">
            <div class="stats-content">
              <div class="stats-icon">
                <el-icon size="32"><Connection /></el-icon>
              </div>
              <div class="stats-info">
                <div class="stats-value">{{ stats.apiKeyOperations }}</div>
                <div class="stats-label">{{ t('auditLog.apiKeyOperations') }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stats-card fail-card">
            <div class="stats-content">
              <div class="stats-icon">
                <el-icon size="32"><WarningFilled /></el-icon>
              </div>
              <div class="stats-info">
                <div class="stats-value">{{ stats.failedAuthentications }}</div>
                <div class="stats-label">{{ t('auditLog.failedAuth') }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="stats-card alert-card">
            <div class="stats-content">
              <div class="stats-icon">
                <el-icon size="32"><Bell /></el-icon>
              </div>
              <div class="stats-info">
                <div class="stats-value">{{ stats.suspiciousActivities }}</div>
                <div class="stats-label">{{ t('auditLog.suspicious') }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20" style="margin-top: 16px;">
        <!-- 事件类型分布图 -->
        <el-col :span="12">
          <el-card shadow="hover" :header="t('auditLog.chartEventTypes')">
            <div ref="eventTypeChartRef" style="height: 300px"></div>
          </el-card>
        </el-col>
        <!-- 操作趋势图 -->
        <el-col :span="12">
          <el-card shadow="hover" :header="t('auditLog.chartTrend')">
            <div ref="trendChartRef" style="height: 300px"></div>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <template #toolbar>
      <!-- 搜索条件 -->
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
                </el-option-group>
                <el-option-group :label="t('auditLog.groupApiKey')">
                  <el-option :label="t('auditLog.eventTypes.API_KEY_CREATED')" value="API_KEY_CREATED" />
                  <el-option :label="t('auditLog.eventTypes.API_KEY_USED')" value="API_KEY_USED" />
                  <el-option :label="t('auditLog.eventTypes.API_KEY_REVOKED')" value="API_KEY_REVOKED" />
                </el-option-group>
                <el-option-group :label="t('auditLog.groupSecurityEvents')">
                  <el-option :label="t('auditLog.eventTypes.AUTHENTICATION_FAILED')" value="AUTHENTICATION_FAILED" />
                  <el-option :label="t('auditLog.eventTypes.AUTHORIZATION_FAILED')" value="AUTHORIZATION_FAILED" />
                  <el-option :label="t('auditLog.eventTypes.SUSPICIOUS_ACTIVITY')" value="SUSPICIOUS_ACTIVITY" />
                  <el-option :label="t('auditLog.eventTypes.SECURITY_ALERT')" value="SECURITY_ALERT" />
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
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Refresh, ArrowDown, Key, Connection, WarningFilled, Bell } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  queryAuditEventsAdvanced,
  generateSecurityReport,
  getExtendedAuditStatistics,
  type AuditEvent,
  type ExtendedAuditQueryResponse,
  type SecurityReport
} from '@/api/auditLog'
import { addToBlacklist } from '@/api/blacklist'
import { useChartTheme } from '@/composables/useChartTheme'
import { useChartAutoRefresh } from '@/composables/useChartAutoRefresh'
import PageSkeleton from '@/components/PageSkeleton.vue'
import { formatDateTime as formatDateTimeBase } from '@/utils/format'

const { t } = useI18n()

const { getChartTheme } = useChartTheme()

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
// 最近一次统计图表数据源（供语言/主题切换时纯重绘复用，不重新请求）
let lastOperationsByType: Record<string, number> = {}

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
  } catch (error: any) {
    ElMessage.error(t('auditLog.exportFailed', { message: error.message || t('auditLog.unknownError') }))
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
  } catch (error: any) {
    ElMessage.error(t('auditLog.loadFailed', { message: error.message || t('auditLog.unknownError') }))
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
    lastOperationsByType = report.operationsByType || {}
    updateEventTypeChart(report.operationsByType)
    updateTrendChart(report.operationsByType)
  } catch (error) {
    console.error('加载统计数据失败:', error)
  }
}

// 更新事件类型图表
const updateEventTypeChart = (data: Record<string, number>) => {
  if (!eventTypeChartRef.value) return
  
  if (!eventTypeChart) {
    eventTypeChart = echarts.init(eventTypeChartRef.value)
  }
  
  const chartData = Object.entries(data || {}).map(([name, value]) => ({ name, value }))
  const theme = getChartTheme()
  
  eventTypeChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', right: 10, top: 'center' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 10, borderColor: 'var(--ja-bg-card, #fff)', borderWidth: 2 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
      labelLine: { show: false },
      data: chartData
    }]
  })
}

// 更新趋势图表
const updateTrendChart = (data: Record<string, number>) => {
  if (!trendChartRef.value) return
  
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  
  const categories = Object.keys(data || {})
  const values = Object.values(data || {})
  const theme = getChartTheme()
  
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: categories, axisLabel: { rotate: 45 } },
    yAxis: { type: 'value' },
    series: [{
      data: values,
      type: 'bar',
      itemStyle: { color: theme.primary, borderRadius: [4, 4, 0, 0] }
    }]
  })
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

// 获取事件类型文本
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
    'AUTHENTICATION_FAILED': t('auditLog.eventBadges.AUTHENTICATION_FAILED'),
    'AUTHORIZATION_FAILED': t('auditLog.eventBadges.AUTHORIZATION_FAILED'),
    'SUSPICIOUS_ACTIVITY': t('auditLog.eventBadges.SUSPICIOUS_ACTIVITY'),
    'SECURITY_ALERT': t('auditLog.eventBadges.SECURITY_ALERT')
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
  updateEventTypeChart(lastOperationsByType)
  updateTrendChart(lastOperationsByType)
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
.stats-card {
  border-radius: 8px;
}

.stats-card :deep(.el-card__body) {
  padding: 20px;
}

.stats-content {
  display: flex;
  align-items: center;
}

.stats-icon {
  width: 60px;
  height: 60px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
}

.jwt-card .stats-icon {
  background: linear-gradient(135deg, var(--ja-login-gradient-start) 0%, var(--ja-login-gradient-end) 100%);
  color: white;
}

.api-card .stats-icon {
  background: linear-gradient(135deg, var(--ja-success) 0%, var(--ja-primary) 100%);
  color: white;
}

.fail-card .stats-icon {
  background: linear-gradient(135deg, var(--ja-danger) 0%, var(--ja-warning) 100%);
  color: white;
}

.alert-card .stats-icon {
  background: linear-gradient(135deg, var(--ja-primary-light-3) 0%, var(--ja-danger) 100%);
  color: white;
}

.stats-info {
  flex: 1;
}

.stats-value {
  font-size: 28px;
  font-weight: bold;
  color: var(--ja-text-primary);
}

.stats-label {
  font-size: 14px;
  color: var(--ja-text-secondary);
  margin-top: 4px;
}

.search-form {
  padding: 16px;
  background-color: var(--ja-primary-light-9, var(--el-fill-color-light));
  border-radius: 4px;
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