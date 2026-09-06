<template>
  <PageSkeleton :title="t('circuitBreaker.history.title')">
    <template #actions>
      <el-button size="small" @click="loadHistory" :loading="loadingHistory">{{ t('circuitBreaker.history.refresh') }}</el-button>
      <el-button size="small" type="danger" @click="cleanupHistory">{{ t('circuitBreaker.history.cleanupExpired') }}</el-button>
    </template>

    <template #stats>
      <el-row :gutter="20">
        <el-col :span="6">
          <el-statistic :title="t('circuitBreaker.history.stats.total')" :value="historyStats.totalCount" />
        </el-col>
        <el-col :span="6">
          <el-statistic :title="t('circuitBreaker.history.stats.today')" :value="historyStats.todayCount" />
        </el-col>
        <el-col :span="6">
          <el-statistic :title="t('circuitBreaker.history.stats.week')" :value="historyStats.weekCount" />
        </el-col>
        <el-col :span="6">
          <el-statistic :title="t('circuitBreaker.history.stats.month')" :value="historyStats.monthCount" />
        </el-col>
      </el-row>
    </template>

    <el-table :data="historyRecords" stripe v-loading="loadingHistory" class="flex-table">
      <el-table-column prop="instanceId" :label="t('circuitBreaker.history.columns.instanceId')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="instanceName" :label="t('circuitBreaker.history.columns.instanceName')" min-width="120" show-overflow-tooltip />
      <el-table-column prop="serviceType" :label="t('circuitBreaker.history.columns.serviceType')" min-width="80">
        <template #default="{ row }">
          <el-tag size="small">{{ row.serviceType || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('circuitBreaker.history.columns.stateTransition')" min-width="150">
        <template #default="{ row }">
          <el-tag :type="getStateTagType(row.previousState)" size="small">{{ row.previousState }}</el-tag>
          <span style="margin: 0 8px">→</span>
          <el-tag :type="getStateTagType(row.currentState)" size="small">{{ row.currentState }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="triggerReasonDesc" :label="t('circuitBreaker.history.columns.triggerReason')" min-width="150" />
      <el-table-column prop="failureCount" :label="t('circuitBreaker.history.columns.failureCount')" min-width="80" />
      <el-table-column prop="successCount" :label="t('circuitBreaker.history.columns.successCount')" min-width="80" />
      <el-table-column prop="changedAt" :label="t('circuitBreaker.history.columns.changedAt')" min-width="150">
        <template #default="{ row }">
          {{ formatDateTime(row.changedAt) }}
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-pagination
        v-model:current-page="historyPage"
        v-model:page-size="historyPageSize"
        :total="historyTotal"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @size-change="loadHistory"
        @current-change="loadHistory"
      />
    </template>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { formatDateTime as formatDateTimeBase } from '@/utils/format'
import PageSkeleton from '@/components/PageSkeleton.vue'

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

const { t } = useI18n()

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
  return formatDateTimeBase(datetime)
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
    ElMessage.error(t('circuitBreaker.history.messages.loadFailed'))
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
    await ElMessageBox.confirm(
      t('circuitBreaker.history.confirmations.cleanupMessage'),
      t('circuitBreaker.history.confirmations.cleanupTitle'),
      {
        confirmButtonText: t('circuitBreaker.history.confirmations.confirm'),
        cancelButtonText: t('circuitBreaker.history.confirmations.cancel'),
        type: 'warning'
      }
    )
    const response = await request.delete('/config/circuit-breaker/history/cleanup')
    if (response.data?.success) {
      ElMessage.success(response.data.message || t('circuitBreaker.history.messages.cleanupCompleted'))
      loadHistory()
      loadHistoryStats()
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('Failed to cleanup history:', error)
      ElMessage.error(t('circuitBreaker.history.messages.cleanupFailed'))
    }
  }
}

onMounted(() => {
  loadHistory()
  loadHistoryStats()
})
</script>

<style scoped>
.flex-table {
  width: 100%;
  table-layout: auto;
}
</style>
