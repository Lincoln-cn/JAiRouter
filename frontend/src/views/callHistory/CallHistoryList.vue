<template>
  <div class="call-history-list">
    <!-- 查询筛选区 -->
    <el-card class="filter-card" shadow="hover">
      <el-form :inline="true" :model="queryForm" class="filter-form">
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item label="模型名称">
          <el-input
            v-model="queryForm.modelName"
            placeholder="输入模型名称"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="服务类型">
          <el-select v-model="queryForm.serviceType" placeholder="全部" clearable style="width: 120px">
            <el-option label="聊天" value="chat" />
            <el-option label="嵌入" value="embedding" />
            <el-option label="重排序" value="rerank" />
            <el-option label="语音合成" value="tts" />
            <el-option label="语音识别" value="stt" />
            <el-option label="图像生成" value="imgGen" />
            <el-option label="图像编辑" value="imgEdit" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryForm.isSuccess" placeholder="全部" clearable style="width: 100px">
            <el-option label="成功" :value="true" />
            <el-option label="失败" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="HTTP 状态码">
          <el-input
            v-model.number="queryForm.httpStatusCode"
            placeholder="如 200, 429"
            clearable
            style="width: 120px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleSearch">查询</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="hover">
      <template #header>
        <div class="table-header">
          <span class="chart-title">调用历史列表</span>
          <span class="total-count">共 {{ totalCount }} 条记录</span>
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
        <el-table-column label="时间" prop="createdAt" width="180" sortable="custom">
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
        <el-table-column label="模型名称" prop="modelName" min-width="160" show-overflow-tooltip />
        <el-table-column label="服务类型" prop="serviceType" width="110">
          <template #default="scope">
            <el-tag size="small">{{ getServiceTypeLabel(scope.row.serviceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提供商" prop="provider" width="100" show-overflow-tooltip />
        <el-table-column label="HTTP 状态" prop="httpStatusCode" width="100" align="center" sortable="custom">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.httpStatusCode)" size="small">
              {{ scope.row.httpStatusCode || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Token" prop="totalTokens" width="100" align="right" sortable="custom">
          <template #default="scope">
            {{ formatNumber(scope.row.totalTokens) }}
          </template>
        </el-table-column>
        <el-table-column label="响应时间 (ms)" prop="responseTimeMs" width="120" align="right" sortable="custom">
          <template #default="scope">
            <el-tag :type="getResponseTimeType(scope.row.responseTimeMs)" size="small">
              {{ scope.row.responseTimeMs?.toFixed(0) || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.isSuccess ? 'success' : 'danger'" size="small">
              {{ scope.row.isSuccess ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right" align="center">
          <template #default="scope">
            <el-button type="primary" link size="small" @click="handleDetail(scope.row)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-if="totalCount > 0"
        class="pagination"
        :current-page="(queryForm.page || 0) + 1"
        :page-sizes="[20, 50, 100]"
        :page-size="queryForm.size || 20"
        :total="totalCount"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      title="调用详情"
      size="600px"
    >
      <template v-if="selectedRecord">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="Trace ID" :span="2">
            <el-tag>{{ selectedRecord.traceId }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="请求 ID" :span="2">
            {{ selectedRecord.requestId }}
          </el-descriptions-item>
          <el-descriptions-item label="时间" :span="2">
            {{ formatTime(selectedRecord.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="请求方法">
            {{ selectedRecord.requestMethod }}
          </el-descriptions-item>
          <el-descriptions-item label="请求路径" :span="2">
            {{ selectedRecord.requestPath }}
          </el-descriptions-item>
          <el-descriptions-item label="模型名称">
            {{ selectedRecord.modelName }}
          </el-descriptions-item>
          <el-descriptions-item label="服务类型">
            {{ getServiceTypeLabel(selectedRecord.serviceType) }}
          </el-descriptions-item>
          <el-descriptions-item label="提供商">
            {{ selectedRecord.provider || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="实例名称">
            {{ selectedRecord.instanceName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="HTTP 状态码">
            <el-tag :type="getStatusType(selectedRecord.httpStatusCode)">
              {{ selectedRecord.httpStatusCode || '-' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="响应时间">
            {{ selectedRecord.responseTimeMs?.toFixed(0) || '-' }} ms
          </el-descriptions-item>
          <el-descriptions-item label="调用状态">
            <el-tag :type="selectedRecord.isSuccess ? 'success' : 'danger'">
              {{ selectedRecord.isSuccess ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="错误码">
            {{ selectedRecord.errorCode || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="错误信息" :span="2">
            <span v-if="selectedRecord.errorMessage" class="error-message">
              {{ selectedRecord.errorMessage }}
            </span>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="Token 统计" :span="2">
            <el-tag type="info" size="small">输入: {{ selectedRecord.promptTokens }}</el-tag>
            <el-tag type="success" size="small">输出: {{ selectedRecord.completionTokens }}</el-tag>
            <el-tag type="warning" size="small">总计: {{ selectedRecord.totalTokens }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="限流状态">
            <el-tag :type="selectedRecord.rateLimited ? 'danger' : 'info'" size="small">
              {{ selectedRecord.rateLimited ? '已限流' : '正常' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="熔断状态">
            <el-tag :type="selectedRecord.circuitBroken ? 'danger' : 'info'" size="small">
              {{ selectedRecord.circuitBroken ? '已熔断' : '正常' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="客户端 IP">
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
            <span>请求/响应摘要</span>
          </template>
          <div v-if="selectedRecord.requestBodySummary" class="body-summary">
            <div class="summary-label">请求体摘要:</div>
            <pre class="summary-content">{{ selectedRecord.requestBodySummary }}</pre>
          </div>
          <div v-if="selectedRecord.responseBodySummary" class="body-summary">
            <div class="summary-label">响应体摘要:</div>
            <pre class="summary-content">{{ selectedRecord.responseBodySummary }}</pre>
          </div>
        </el-card>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { queryCallHistory } from '@/api/callHistory'
import type { ApiCallHistoryRecord, CallHistoryQuery } from '@/types/callHistory'

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

// 获取服务类型标签
const getServiceTypeLabel = (type?: string) => {
  if (!type) return '未知'
  const labels: Record<string, string> = {
    chat: '聊天',
    embedding: '嵌入',
    rerank: '重排序',
    tts: '语音合成',
    stt: '语音识别',
    imgGen: '图像生成',
    imgEdit: '图像编辑'
  }
  return labels[type] || type
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
    ElMessage.error(`加载调用历史失败：${error.message || '未知错误'}`)
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
})
</script>

<style scoped>
.call-history-list {
  padding: 20px;
}

.call-history-list .filter-card {
  margin-bottom: 20px;
}

.call-history-list .filter-card .filter-form {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
}

.call-history-list .table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.call-history-list .chart-title {
  font-size: 16px;
  font-weight: bold;
}

.call-history-list .total-count {
  font-size: 14px;
  color: #909399;
}

.call-history-list .pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.call-history-list .error-message {
  color: #F56C6C;
  font-size: 12px;
  word-break: break-all;
}

.call-history-list .body-summary {
  margin-bottom: 12px;
}

.call-history-list .body-summary:last-child {
  margin-bottom: 0;
}

.call-history-list .summary-label {
  font-weight: bold;
  color: #606266;
  margin-bottom: 4px;
}

.call-history-list .summary-content {
  background-color: #f5f7fa;
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
