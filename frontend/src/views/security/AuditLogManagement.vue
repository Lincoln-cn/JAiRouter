<template>
  <div class="audit-log-management">
    <!-- 统计概览卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stats-card jwt-card">
          <div class="stats-content">
            <div class="stats-icon">
              <el-icon size="32"><Key /></el-icon>
            </div>
            <div class="stats-info">
              <div class="stats-value">{{ stats.jwtOperations }}</div>
              <div class="stats-label">JWT操作</div>
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
              <div class="stats-label">API Key操作</div>
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
              <div class="stats-label">认证失败</div>
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
              <div class="stats-label">可疑活动</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <!-- 事件类型分布图 -->
      <el-col :span="12">
        <el-card shadow="hover" header="事件类型分布">
          <div ref="eventTypeChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
      <!-- 操作趋势图 -->
      <el-col :span="12">
        <el-card shadow="hover" header="操作趋势">
          <div ref="trendChartRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 审计日志表格 -->
    <el-card class="table-card">
      <template #header>
        <div class="card-header">
          <span>审计日志</span>
          <div class="header-actions">
            <el-button-group>
              <el-button @click="handleRefresh" :icon="Refresh">刷新</el-button>
              <el-dropdown @command="handleExport">
                <el-button type="primary">
                  导出<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="csv">导出 CSV</el-dropdown-item>
                    <el-dropdown-item command="excel">导出 Excel</el-dropdown-item>
                    <el-dropdown-item command="json">导出 JSON</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </el-button-group>
          </div>
        </div>
      </template>

      <!-- 搜索条件 -->
      <el-form :model="searchForm" class="search-form">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="时间范围">
              <el-select v-model="searchForm.quickTime" placeholder="快捷选择" @change="handleQuickTimeChange" clearable>
                <el-option label="今日" value="today" />
                <el-option label="昨日" value="yesterday" />
                <el-option label="本周" value="week" />
                <el-option label="本月" value="month" />
                <el-option label="最近7天" value="last7days" />
                <el-option label="最近30天" value="last30days" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="开始时间">
              <el-date-picker
                v-model="searchForm.startTime"
                type="datetime"
                placeholder="选择开始时间"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="结束时间">
              <el-date-picker
                v-model="searchForm.endTime"
                type="datetime"
                placeholder="选择结束时间"
                format="YYYY-MM-DD HH:mm:ss"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="事件类型">
              <el-select v-model="searchForm.eventType" placeholder="请选择事件类型" clearable filterable>
                <el-option-group label="JWT令牌">
                  <el-option label="JWT令牌颁发" value="JWT_TOKEN_ISSUED" />
                  <el-option label="JWT令牌刷新" value="JWT_TOKEN_REFRESHED" />
                  <el-option label="JWT令牌撤销" value="JWT_TOKEN_REVOKED" />
                  <el-option label="JWT令牌验证" value="JWT_TOKEN_VALIDATED" />
                </el-option-group>
                <el-option-group label="API Key">
                  <el-option label="API密钥创建" value="API_KEY_CREATED" />
                  <el-option label="API密钥使用" value="API_KEY_USED" />
                  <el-option label="API密钥撤销" value="API_KEY_REVOKED" />
                </el-option-group>
                <el-option-group label="安全事件">
                  <el-option label="认证失败" value="AUTHENTICATION_FAILED" />
                  <el-option label="授权失败" value="AUTHORIZATION_FAILED" />
                  <el-option label="可疑活动" value="SUSPICIOUS_ACTIVITY" />
                  <el-option label="安全告警" value="SECURITY_ALERT" />
                </el-option-group>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="用户ID">
              <el-input v-model="searchForm.userId" placeholder="请输入用户ID" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="客户端IP">
              <el-input v-model="searchForm.clientIp" placeholder="请输入客户端IP" clearable />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="操作结果">
              <el-select v-model="searchForm.success" placeholder="请选择操作结果" clearable>
                <el-option label="成功" :value="true" />
                <el-option label="失败" :value="false" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label=" ">
              <el-button type="primary" @click="handleSearch">搜索</el-button>
              <el-button @click="handleReset">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <!-- 日志表格 -->
      <el-table :data="logs" style="width: 100%" border v-loading="loading" :row-class-name="tableRowClassName">
        <el-table-column prop="timestamp" label="时间" width="180" sortable>
          <template #default="scope">
            {{ formatDateTime(scope.row.timestamp) }}
          </template>
        </el-table-column>
        <el-table-column prop="userId" label="用户ID" width="120" show-overflow-tooltip />
        <el-table-column prop="type" label="事件类型" width="140">
          <template #default="scope">
            <el-tag :type="getEventTypeColor(scope.row.type)" size="small">
              {{ getEventTypeText(scope.row.type) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="resourceId" label="资源ID" width="150" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="客户端IP" width="140" />
        <el-table-column prop="riskLevel" label="风险等级" width="100">
          <template #default="scope">
            <el-tag :type="getRiskLevelColor(scope.row.riskLevel)" size="small" v-if="scope.row.riskLevel">
              {{ scope.row.riskLevel }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="success" label="结果" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.success ? 'success' : 'danger'" size="small">
              {{ scope.row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="details" label="描述" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" link @click="handleViewDetail(scope.row)">详情</el-button>
            <el-dropdown v-if="scope.row.ipAddress" trigger="click" @command="(cmd: string) => handleAddToBlacklist(scope.row, cmd)">
              <el-button size="small" type="warning" link>黑名单</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="IP">封禁IP</el-dropdown-item>
                  <el-dropdown-item command="USER" :disabled="!scope.row.userId">封禁用户</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.currentPage"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        class="pagination"
      />
    </el-card>

    <!-- 日志详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="日志详情" width="650px">
      <el-descriptions v-if="currentLog" :column="2" border>
        <el-descriptions-item label="事件ID" :span="2">{{ currentLog.id }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ formatDateTime(currentLog.timestamp) }}</el-descriptions-item>
        <el-descriptions-item label="风险等级">
          <el-tag :type="getRiskLevelColor(currentLog.riskLevel)" v-if="currentLog.riskLevel">
            {{ currentLog.riskLevel }}
          </el-tag>
          <span v-else>LOW</span>
        </el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ currentLog.userId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="事件类型">
          <el-tag :type="getEventTypeColor(currentLog.type)">
            {{ getEventTypeText(currentLog.type) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="资源ID" :span="2">{{ currentLog.resourceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作">{{ currentLog.action || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户端IP">{{ currentLog.ipAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户代理" :span="2">{{ currentLog.userAgent || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结果">
          <el-tag :type="currentLog.success ? 'success' : 'danger'">
            {{ currentLog.success ? '成功' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="地理位置">{{ currentLog.geoLocation || '-' }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ currentLog.details || '-' }}</el-descriptions-item>
        <el-descriptions-item label="元数据" :span="2" v-if="currentLog.metadata && Object.keys(currentLog.metadata).length > 0">
          <pre class="metadata-pre">{{ JSON.stringify(currentLog.metadata, null, 2) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
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
  ElMessage.success('搜索完成')
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
  ElMessage.success('数据已刷新')
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
    
    ElMessage.success(`已导出 ${result.events.length} 条记录`)
  } catch (error: any) {
    ElMessage.error(`导出失败: ${  error.message || '未知错误'}`)
  }
}

const convertToCSV = (events: AuditEvent[]): string => {
  const headers = ['时间', '事件类型', '用户ID', '资源ID', '客户端IP', '结果', '风险等级', '描述']
  const rows = events.map(e => [
    e.timestamp || '',
    e.type || '',
    e.userId || '',
    e.resourceId || '',
    e.ipAddress || '',
    e.success ? '成功' : '失败',
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
      reason = `可疑活动封禁IP - 事件: ${log.type}`
      break
    case 'USER':
      targetValue = log.userId || ''
      reason = `可疑活动封禁用户 - 事件: ${log.type}`
      break
  }

  if (!targetValue) {
    ElMessage.warning('目标值不存在，无法添加到黑名单')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要将${type === 'IP' ? 'IP' : '用户'}添加到黑名单吗？\n目标: ${targetValue}`,
      '添加黑名单确认',
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
      ElMessage.success('已添加到黑名单')
    } else {
      ElMessage.error(result.message || '添加失败')
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
    ElMessage.error(`加载审计日志失败: ${  error.message || '未知错误'}`)
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
  
  eventTypeChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { orient: 'vertical', right: 10, top: 'center' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
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
  
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: categories, axisLabel: { rotate: 45 } },
    yAxis: { type: 'value' },
    series: [{
      data: values,
      type: 'bar',
      itemStyle: { color: '#409EFF', borderRadius: [4, 4, 0, 0] }
    }]
  })
}

// 格式化日期时间
const formatDateTime = (dateTime: string) => {
  if (!dateTime) return ''
  return new Date(dateTime).toLocaleString('zh-CN')
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
    'JWT_TOKEN_ISSUED': 'JWT颁发',
    'JWT_TOKEN_REFRESHED': 'JWT刷新',
    'JWT_TOKEN_REVOKED': 'JWT撤销',
    'JWT_TOKEN_VALIDATED': 'JWT验证',
    'JWT_TOKEN_EXPIRED': 'JWT过期',
    'API_KEY_CREATED': 'Key创建',
    'API_KEY_USED': 'Key使用',
    'API_KEY_REVOKED': 'Key撤销',
    'API_KEY_EXPIRED': 'Key过期',
    'AUTHENTICATION_FAILED': '认证失败',
    'AUTHORIZATION_FAILED': '授权失败',
    'SUSPICIOUS_ACTIVITY': '可疑活动',
    'SECURITY_ALERT': '安全告警'
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

onMounted(async () => {
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
.audit-log-management {
  padding: 20px;
}

.stats-row {
  margin-bottom: 20px;
}

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
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
}

.api-card .stats-icon {
  background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
  color: white;
}

.fail-card .stats-icon {
  background: linear-gradient(135deg, #eb3349 0%, #f45c43 100%);
  color: white;
}

.alert-card .stats-icon {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  color: white;
}

.stats-info {
  flex: 1;
}

.stats-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.stats-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.table-card {
  margin-top: 20px;
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
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.pagination {
  margin-top: 20px;
  text-align: right;
}

.metadata-pre {
  max-height: 200px;
  overflow-y: auto;
  background-color: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
}

:deep(.warning-row) {
  background-color: #fdf6ec;
}

:deep(.error-row) {
  background-color: #fef0f0;
}
</style>