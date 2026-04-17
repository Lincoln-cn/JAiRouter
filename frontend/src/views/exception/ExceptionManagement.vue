<template>
  <div class="exception-management">
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #F56C6C;">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ listData.totalElements }}</div>
              <div class="stat-label">异常总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #E6A23C;">
              <el-icon><DataLine /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ listData.totalTypes }}</div>
              <div class="stat-label">异常类型数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #409EFF;">
              <el-icon><Monitor /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ listData.serverErrorCount || 0 }}</div>
              <div class="stat-label">服务端错误</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #909399;">
              <el-icon><TrendCharts /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ listData.clientErrorCount || 0 }}</div>
              <div class="stat-label">客户端错误</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 主卡片 -->
    <el-card class="main-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="main-title">
            <el-icon><Warning /></el-icon>
            异常事件管理
          </span>
          <div class="header-buttons">
            <el-button icon="Refresh" @click="handleRefresh">刷新</el-button>
            <el-button icon="Delete" type="danger" @click="showCleanupDialog">清理过期数据</el-button>
            <el-button icon="DataAnalysis" type="success" @click="goToStatistics">统计分析</el-button>
          </div>
        </div>
      </template>

      <!-- 筛选条件 -->
      <div class="filter-section">
        <el-form :inline="true" :model="queryParams" class="filter-form">
          <el-form-item label="异常类型">
            <el-input
              v-model="queryParams.exceptionType"
              placeholder="输入异常类型"
              clearable
              style="width: 200px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="错误代码">
            <el-select v-model="queryParams.errorCode" placeholder="选择错误代码" clearable style="width: 120px">
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
          <el-form-item label="错误分类">
            <el-select v-model="queryParams.errorCategory" placeholder="选择分类" clearable style="width: 150px">
              <el-option label="客户端错误" value="CLIENT_ERROR" />
              <el-option label="服务端错误" value="SERVER_ERROR" />
              <el-option label="网络错误" value="NETWORK_ERROR" />
              <el-option label="超时错误" value="TIMEOUT_ERROR" />
              <el-option label="验证错误" value="VALIDATION_ERROR" />
              <el-option label="安全错误" value="SECURITY_ERROR" />
            </el-select>
          </el-form-item>
          <el-form-item label="客户端 IP">
            <el-input
              v-model="queryParams.clientIp"
              placeholder="输入 IP 地址"
              clearable
              style="width: 150px"
              @keyup.enter="handleQuery"
            />
          </el-form-item>
          <el-form-item label="时间范围">
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 400px"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
            <el-button icon="Refresh" @click="handleReset">重置</el-button>
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
          <el-table-column label="事件 ID" prop="eventId" width="200" show-overflow-tooltip>
            <template #default="scope">
              <el-tag effect="plain" type="info">{{ scope.row.eventId }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="异常类型" prop="exceptionType" min-width="180" show-overflow-tooltip>
            <template #default="scope">
              <span class="exception-type">{{ scope.row.exceptionType }}</span>
            </template>
          </el-table-column>
          <el-table-column label="错误代码" prop="errorCode" width="100">
            <template #default="scope">
              <el-tag :type="getErrorTagType(scope.row.errorCode)" size="small">
                {{ scope.row.errorCode }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="错误分类" prop="errorCategory" width="120">
            <template #default="scope">
              <el-tag :type="getCategoryTagType(scope.row.errorCategory)" size="small">
                {{ formatCategory(scope.row.errorCategory) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="异常消息" prop="sanitizedMessage" min-width="200" show-overflow-tooltip>
            <template #default="scope">
              <span>{{ scope.row.sanitizedMessage || scope.row.exceptionMessage || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="客户端 IP" prop="clientIp" width="140">
            <template #default="scope">
              <span>{{ scope.row.clientIp || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="发生时间" prop="occurredAt" width="180">
            <template #default="scope">
              <span>{{ formatTime(scope.row.occurredAt) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="scope">
              <el-button link type="primary" size="small" @click.stop="handleViewDetail(scope.row)">
                详情
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
      title="异常事件详情"
      width="800px"
      :close-on-click-modal="false"
    >
      <el-descriptions v-if="selectedEvent" :column="2" border>
        <el-descriptions-item label="事件 ID">{{ selectedEvent.eventId }}</el-descriptions-item>
        <el-descriptions-item label="异常类型">{{ selectedEvent.exceptionType }}</el-descriptions-item>
        <el-descriptions-item label="错误代码">
          <el-tag :type="getErrorTagType(selectedEvent.errorCode)" size="small">
            {{ selectedEvent.errorCode }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="错误分类">
          <el-tag :type="getCategoryTagType(selectedEvent.errorCategory)" size="small">
            {{ formatCategory(selectedEvent.errorCategory) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="HTTP 状态码">
          <el-tag :type="getHttpStatusTagType(selectedEvent.httpStatus)" size="small">
            {{ selectedEvent.httpStatus || '-' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="客户端 IP">{{ selectedEvent.clientIp || '-' }}</el-descriptions-item>
        <el-descriptions-item label="追踪 ID" :span="2">
          <el-tag effect="plain" type="info">{{ selectedEvent.traceId || '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="异常消息" :span="2">
          <el-input
            v-model="selectedEvent.exceptionMessage"
            type="textarea"
            :rows="4"
            readonly
          />
        </el-descriptions-item>
        <el-descriptions-item label="脱敏消息" :span="2">
          <el-input
            v-model="selectedEvent.sanitizedMessage"
            type="textarea"
            :rows="3"
            readonly
          />
        </el-descriptions-item>
        <el-descriptions-item label="发生次数">{{ selectedEvent.occurrenceCount || 1 }}</el-descriptions-item>
        <el-descriptions-item label="是否聚合">
          <el-tag :type="selectedEvent.isAggregated ? 'success' : 'info'" size="small">
            {{ selectedEvent.isAggregated ? '是' : '否' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="首次出现">{{ formatTime(selectedEvent.firstOccurrence) }}</el-descriptions-item>
        <el-descriptions-item label="最后出现">{{ formatTime(selectedEvent.lastOccurrence) }}</el-descriptions-item>
        <el-descriptions-item label="发生时间">{{ formatTime(selectedEvent.occurredAt) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 清理对话框 -->
    <el-dialog
      v-model="cleanupVisible"
      title="清理过期异常事件"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form :model="cleanupForm" label-width="100px">
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="cleanupForm.cutoffTime"
            type="datetime"
            placeholder="选择截止时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="仅聚合事件">
          <el-switch v-model="cleanupForm.aggregatedOnly" />
        </el-form-item>
        <el-alert
          title="警告"
          type="warning"
          description="此操作将永久删除选定的异常事件，无法恢复，请谨慎操作！"
          :closable="false"
        />
      </el-form>
      <template #footer>
        <el-button @click="cleanupVisible = false">取消</el-button>
        <el-button type="danger" @click="handleCleanup">确认清理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
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

const router = useRouter()

// 加载状态
const loading = ref(false)

// 查询参数
const queryParams = reactive<ExceptionQueryParams>({
  exceptionType: undefined,
  errorCode: undefined,
  errorCategory: undefined,
  clientIp: undefined,
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
  serverErrorCount: 0,
  clientErrorCount: 0
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
    ElMessage.error('加载异常列表失败：' + (error.message || '未知错误'))
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

    listData.value = {
      totalElements: stats.totalEvents || 0,
      totalTypes: stats.totalTypes || 0,
      serverErrorCount: stats.eventsByCategory?.['SERVER_ERROR'] || 0,
      clientErrorCount: stats.eventsByCategory?.['CLIENT_ERROR'] || 0
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
  dateRange.value = null
  queryParams.startTime = undefined
  queryParams.endTime = undefined
  handleQuery()
}

// 刷新
const handleRefresh = () => {
  loadData()
  ElMessage.success('刷新成功')
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
    ElMessage.warning('请选择截止时间')
    return
  }

  try {
    await ElMessageBox.confirm(
      '确定要清理选定的异常事件吗？此操作不可恢复！',
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const result = await deleteOldExceptionEvents(
      cleanupForm.cutoffTime,
      cleanupForm.aggregatedOnly
    )

    ElMessage.success(`成功删除 ${result.deletedCount} 条异常事件`)
    cleanupVisible.value = false
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('清理异常事件失败:', error)
      ElMessage.error('清理失败：' + (error.message || '未知错误'))
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

.stat-card .stat-content {
  display: flex;
  align-items: center;
}

.stat-card .stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 15px;
  color: white;
  font-size: 28px;
}

.stat-card .stat-info .stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

.stat-card .stat-info .stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 5px;
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
