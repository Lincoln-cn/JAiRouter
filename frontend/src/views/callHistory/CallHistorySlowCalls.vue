<template>
  <div class="call-history-slow">
    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="hover">
      <el-form :inline="true" class="filter-form">
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
        <el-form-item label="阈值 (ms)">
          <el-input-number
            v-model="threshold"
            :min="100"
            :max="30000"
            :step="100"
            style="width: 140px"
          />
        </el-form-item>
        <el-form-item label="显示数量">
          <el-select v-model="limit" style="width: 100px">
            <el-option :value="20" label="20" />
            <el-option :value="50" label="50" />
            <el-option :value="100" label="100" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计概览 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #E6A23C;">
              <el-icon><Timer /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ slowCalls.length }}</div>
              <div class="stat-label">慢调用数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #F56C6C;">
              <el-icon><WarningFilled /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ getMaxResponseTime() }}</div>
              <div class="stat-label">最长响应时间 (ms)</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #409EFF;">
              <el-icon><DataAnalysis /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ getAvgResponseTime() }}</div>
              <div class="stat-label">平均响应时间 (ms)</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 慢调用列表 -->
    <el-card shadow="hover">
      <template #header>
        <div class="table-header">
          <span class="chart-title">慢调用列表</span>
          <el-tag type="warning" size="small">阈值: {{ threshold }} ms</el-tag>
        </div>
      </template>

      <el-table v-loading="loading" :data="slowCalls" border stripe :max-height="600">
        <el-table-column label="时间" prop="createdAt" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="模型名称" prop="modelName" min-width="160" show-overflow-tooltip />
        <el-table-column label="服务类型" prop="serviceType" width="110">
          <template #default="scope">
            <el-tag size="small">{{ getServiceTypeLabel(scope.row.serviceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="响应时间 (ms)" prop="responseTimeMs" width="140" align="right" sortable>
          <template #default="scope">
            <el-tag type="danger" size="small">
              {{ scope.row.responseTimeMs?.toFixed(0) || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="HTTP 状态码" prop="httpStatusCode" width="120" align="center">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.httpStatusCode)" size="small">
              {{ scope.row.httpStatusCode || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Token" prop="totalTokens" width="100" align="right">
          <template #default="scope">
            {{ formatNumber(scope.row.totalTokens) }}
          </template>
        </el-table-column>
        <el-table-column label="调用状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.isSuccess ? 'success' : 'danger'" size="small">
              {{ scope.row.isSuccess ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提供商" prop="provider" width="100" show-overflow-tooltip />
        <el-table-column label="Trace ID" prop="traceId" width="140" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Timer, WarningFilled, DataAnalysis, Search, Refresh } from '@element-plus/icons-vue'
import { getSlowCalls } from '@/api/callHistory'
import type { ApiCallHistoryRecord } from '@/types/callHistory'

// 筛选条件
const dateRange = ref<[string, string] | null>(null)
const threshold = ref(1000)
const limit = ref(50)

// 慢调用数据
const slowCalls = ref<ApiCallHistoryRecord[]>([])
const loading = ref(false)

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

// 计算最长响应时间
const getMaxResponseTime = (): string => {
  if (slowCalls.value.length === 0) return '0'
  const max = Math.max(...slowCalls.value.map(item => item.responseTimeMs || 0))
  return max.toFixed(0)
}

// 计算平均响应时间
const getAvgResponseTime = (): string => {
  if (slowCalls.value.length === 0) return '0'
  const total = slowCalls.value.reduce((sum, item) => sum + (item.responseTimeMs || 0), 0)
  return (total / slowCalls.value.length).toFixed(0)
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    let startTime: string | undefined
    let endTime: string | undefined

    if (dateRange.value && dateRange.value.length === 2) {
      startTime = dateRange.value[0].replace(' ', 'T')
      endTime = dateRange.value[1].replace(' ', 'T')
    }

    slowCalls.value = await getSlowCalls(threshold.value, startTime, endTime, limit.value)
  } catch (error: any) {
    console.error('加载慢调用失败:', error)
    ElMessage.error(`加载慢调用失败：${error.message || '未知错误'}`)
  } finally {
    loading.value = false
  }
}

// 重置
const handleReset = () => {
  dateRange.value = null
  threshold.value = 1000
  limit.value = 50
  loadData()
}

// 初始化
onMounted(() => {
  loadData()
})
</script>

<style scoped>
.call-history-slow {
  padding: 20px;
}

.call-history-slow .filter-card {
  margin-bottom: 20px;
}

.call-history-slow .filter-card .filter-form {
  display: flex;
  justify-content: center;
}

.call-history-slow .stats-row {
  margin-bottom: 20px;
}

.call-history-slow .stats-row .stat-card .stat-content {
  display: flex;
  align-items: center;
}

.call-history-slow .stats-row .stat-card .stat-icon {
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

.call-history-slow .stats-row .stat-card .stat-info .stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

.call-history-slow .stats-row .stat-card .stat-info .stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 5px;
}

.call-history-slow .table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.call-history-slow .chart-title {
  font-size: 16px;
  font-weight: bold;
}
</style>
