<template>
  <div class="circuit-breaker-history">
    <el-card class="history-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">熔断器历史记录</span>
          <div class="header-actions">
            <el-button size="small" @click="loadHistory" :loading="loadingHistory">刷新</el-button>
            <el-button size="small" type="danger" @click="cleanupHistory">清理过期记录</el-button>
          </div>
        </div>
      </template>

      <!-- 历史记录统计 -->
      <el-row :gutter="20" style="margin-bottom: 16px">
        <el-col :span="6">
          <el-statistic title="总记录数" :value="historyStats.totalCount" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="今日记录" :value="historyStats.todayCount" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="最近7天" :value="historyStats.weekCount" />
        </el-col>
        <el-col :span="6">
          <el-statistic title="最近30天" :value="historyStats.monthCount" />
        </el-col>
      </el-row>

      <el-table :data="historyRecords" stripe v-loading="loadingHistory" class="flex-table">
        <el-table-column prop="instanceId" label="实例ID" min-width="200" show-overflow-tooltip />
        <el-table-column prop="instanceName" label="实例名称" min-width="120" show-overflow-tooltip />
        <el-table-column prop="serviceType" label="服务类型" min-width="80">
          <template #default="{ row }">
            <el-tag size="small">{{ row.serviceType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态变化" min-width="150">
          <template #default="{ row }">
            <el-tag :type="getStateTagType(row.previousState)" size="small">{{ row.previousState }}</el-tag>
            <span style="margin: 0 8px">→</span>
            <el-tag :type="getStateTagType(row.currentState)" size="small">{{ row.currentState }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggerReasonDesc" label="触发原因" min-width="150" />
        <el-table-column prop="failureCount" label="失败次数" min-width="80" />
        <el-table-column prop="successCount" label="成功次数" min-width="80" />
        <el-table-column prop="changedAt" label="变化时间" min-width="150">
          <template #default="{ row }">
            {{ formatDateTime(row.changedAt) }}
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="historyPage"
        v-model:page-size="historyPageSize"
        :total="historyTotal"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @size-change="loadHistory"
        @current-change="loadHistory"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

interface HistoryRecord {
  id: number
  instanceId: string
  instanceName: string
  serviceType: string
  previousState: string
  currentState: string
  triggerReason: string
  triggerReasonDesc: string
  failureCount: number
  successCount: number
  changedAt: string
}

interface HistoryStats {
  totalCount: number
  todayCount: number
  weekCount: number
  monthCount: number
}

const historyRecords = ref<HistoryRecord[]>([])
const loadingHistory = ref(false)
const historyPage = ref(1)
const historyPageSize = ref(20)
const historyTotal = ref(0)
const historyStats = ref<HistoryStats>({
  totalCount: 0,
  todayCount: 0,
  weekCount: 0,
  monthCount: 0
})

const getStateTagType = (state: string) => {
  switch (state) {
    case 'CLOSED':
      return 'success'
    case 'OPEN':
      return 'danger'
    case 'HALF_OPEN':
      return 'warning'
    default:
      return 'info'
  }
}

const formatDateTime = (datetime: string | null) => {
  if (!datetime) return '-'
  return new Date(datetime).toLocaleString('zh-CN')
}

const loadHistory = async () => {
  loadingHistory.value = true
  try {
    const response = await request.get('/config/circuit-breaker/history', {
      params: {
        page: historyPage.value - 1,
        size: historyPageSize.value
      }
    })
    if (response.data?.success) {
      const pageData = response.data.data
      if (pageData) {
        historyRecords.value = pageData.content || []
        historyTotal.value = pageData.totalElements || 0
      }
    }
  } catch (error: any) {
    console.error('Failed to load history:', error)
    ElMessage.error('加载历史记录失败')
  } finally {
    loadingHistory.value = false
  }
}

const loadHistoryStats = async () => {
  try {
    const response = await request.get('/config/circuit-breaker/history/stats')
    if (response.data?.success) {
      historyStats.value = response.data.data
    }
  } catch (error: any) {
    console.error('Failed to load history stats:', error)
  }
}

const cleanupHistory = async () => {
  try {
    await ElMessageBox.confirm('确定要清理过期的历史记录吗？', '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const response = await request.delete('/config/circuit-breaker/history/cleanup')
    if (response.data?.success) {
      ElMessage.success(response.data.message || '清理完成')
      loadHistory()
      loadHistoryStats()
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('Failed to cleanup history:', error)
      ElMessage.error('清理历史记录失败')
    }
  }
}

onMounted(() => {
  loadHistory()
  loadHistoryStats()
})
</script>

<style scoped>
.circuit-breaker-history {
  padding: 24px;
  background: linear-gradient(180deg, #f7f9fc 0%, #ffffff 100%);
  min-height: calc(100vh - 80px);
}

.history-card {
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.06);
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.flex-table {
  width: 100%;
  table-layout: auto;
}
</style>
