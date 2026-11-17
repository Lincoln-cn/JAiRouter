<template>
  <PageSkeleton :title="t('circuitBreaker.history.title')">
    <template #actions>
      <el-button size="small" @click="refreshAll" :loading="loadingHistory">{{ t('circuitBreaker.history.refresh') }}</el-button>
      <!-- 清理过期记录不可恢复：二次确认由 ConfirmAction（danger）承担，权限仍走 v-permission -->
      <ConfirmAction
        v-permission="'config:circuitbreaker:write'"
        :title="t('circuitBreaker.history.confirmations.cleanupMessage')"
        :width="300"
        :confirm-text="t('circuitBreaker.history.confirmations.confirm')"
        :cancel-text="t('circuitBreaker.history.confirmations.cancel')"
        type="danger"
        :loading="cleaningUp"
        @confirm="cleanupHistory"
      >
        <el-button size="small" type="danger" plain :loading="cleaningUp" :disabled="cleaningUp">
          {{ t('circuitBreaker.history.cleanupExpired') }}
        </el-button>
      </ConfirmAction>
    </template>

    <template #stats>
      <MetricStrip :items="statItems" :loading="loadingHistory" :columns="4" />
    </template>

    <template #toolbar>
      <!--
        工具栏顺序：维度筛选 → 刷新（刷新在页头 actions，只读集合页口径）。
        时间范围位空缺：后端 /history/time-range 返回非分页 List，接入会破坏分页口径，本批不接；
        服务类型筛选走 /history/service-type（同排序、同分页形状），因此无需改后端。
      -->
      <el-select
        v-model="filterServiceType"
        :placeholder="t('circuitBreaker.history.filterServiceType')"
        clearable
        filterable
        allow-create
        default-first-option
        style="width: 200px"
        @change="handleFilterChange"
      >
        <el-option v-for="type in serviceTypeOptions" :key="type" :label="type" :value="type" />
      </el-select>
      <el-button v-if="hasActiveFilter" @click="clearFilters">
        {{ t('circuitBreaker.history.clearFilters') }}
      </el-button>
    </template>

    <!-- 与监控页一致的卡片容器：此前 8 列表格直铺在页面底色上，容器语言与监控页不一致 -->
    <el-card class="history-card" shadow="never">
      <div class="table-scroll">
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
          <!-- 后端枚举名固定，前端做文案映射；未知枚举回落后端描述，避免出现空白 -->
          <el-table-column :label="t('circuitBreaker.history.columns.triggerReason')" min-width="150">
            <template #default="{ row }">{{ triggerReasonLabel(row) }}</template>
          </el-table-column>
          <el-table-column prop="failureCount" :label="t('circuitBreaker.history.columns.failureCount')" min-width="80" />
          <el-table-column prop="successCount" :label="t('circuitBreaker.history.columns.successCount')" min-width="80" />
          <el-table-column prop="changedAt" :label="t('circuitBreaker.history.columns.changedAt')" min-width="160">
            <template #default="{ row }">
              <TimeText :value="row.changedAt" mode="absolute" precision="second" />
            </template>
          </el-table-column>
          <template #empty>
            <EmptyState
              icon="Clock"
              :title="hasActiveFilter ? t('circuitBreaker.history.emptySearchTitle') : t('circuitBreaker.history.emptyTitle')"
              :description="hasActiveFilter ? t('circuitBreaker.history.emptySearchDescription') : t('circuitBreaker.history.emptyDescription')"
            >
              <template #actions>
                <el-button v-if="hasActiveFilter" size="small" @click="clearFilters">
                  {{ t('circuitBreaker.history.clearFilters') }}
                </el-button>
                <el-button v-else size="small" @click="refreshAll">
                  {{ t('circuitBreaker.history.refresh') }}
                </el-button>
              </template>
            </EmptyState>
          </template>
        </el-table>
      </div>
    </el-card>

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
import { computed, ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import ConfirmAction from '@/components/ConfirmAction.vue'
import EmptyState from '@/components/EmptyState.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import TimeText from '@/components/TimeText.vue'

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

/** 后端 CircuitBreakerStateHistoryDTO.TriggerReason 的固定枚举名。 */
const TRIGGER_REASONS = [
  'FAILURE_THRESHOLD',
  'SUCCESS_THRESHOLD',
  'TIMEOUT',
  'MANUAL_RESET',
  'MANUAL_FORCE_OPEN',
  'MANUAL_FORCE_CLOSE'
] as const

const historyRecords = ref<HistoryRecord[]>([])
const loadingHistory = ref(false)
const cleaningUp = ref(false)
const historyPage = ref(1)
const historyPageSize = ref(20)
const historyTotal = ref(0)
const filterServiceType = ref<string>('')
const historyStats = ref<HistoryStats>({
  totalCount: 0,
  todayCount: 0,
  weekCount: 0,
  monthCount: 0
})

const { t } = useI18n()

/** 工具栏是否处于筛选态：决定空态文案取「暂无数据」还是「筛选无结果」 */
const hasActiveFilter = computed(() => Boolean(filterServiceType.value))

/**
 * 服务类型候选：从当前页记录里归纳（后端没有「服务类型字典」端点），
 * 并保留当前选中的值；el-select 允许 allow-create，覆盖不在当前页的类型。
 */
const serviceTypeOptions = computed<string[]>(() => {
  const types = new Set<string>()
  historyRecords.value.forEach(item => {
    if (item.serviceType) types.add(item.serviceType)
  })
  if (filterServiceType.value) types.add(filterServiceType.value)
  return Array.from(types).sort()
})

const statItems = computed<MetricStripItem[]>(() => [
  { key: 'total', label: t('circuitBreaker.history.stats.total'), value: historyStats.value.totalCount },
  { key: 'today', label: t('circuitBreaker.history.stats.today'), value: historyStats.value.todayCount },
  { key: 'week', label: t('circuitBreaker.history.stats.week'), value: historyStats.value.weekCount },
  { key: 'month', label: t('circuitBreaker.history.stats.month'), value: historyStats.value.monthCount }
])

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

const triggerReasonLabel = (row: HistoryRecord) => {
  const reason = row.triggerReason
  if (reason && (TRIGGER_REASONS as readonly string[]).includes(reason)) {
    return t(`circuitBreaker.history.triggerReasons.${reason}`)
  }
  return row.triggerReasonDesc || reason || '-'
}

const loadHistory = async () => {
  loadingHistory.value = true
  try {
    // 无筛选走全量分页端点；有服务类型筛选走 /history/service-type（同排序、同分页形状）
    const useServiceTypeFilter = Boolean(filterServiceType.value)
    const response = await request.get(
      useServiceTypeFilter
        ? '/config/circuit-breaker/history/service-type'
        : '/config/circuit-breaker/history',
      {
        params: {
          ...(useServiceTypeFilter ? { serviceType: filterServiceType.value } : {}),
          page: historyPage.value - 1,
          size: historyPageSize.value
        }
      }
    )
    if (response.data?.success) {
      const pageData = response.data.data
      if (pageData) {
        historyRecords.value = pageData.content || []
        historyTotal.value = pageData.totalElements || 0
      }
    }
  } catch (error) {
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
  } catch (error) {
    console.error('Failed to load history stats:', error)
  }
}

/** 筛选变化后回到第一页再取数（否则会停在越界页码上看到空表） */
const handleFilterChange = () => {
  historyPage.value = 1
  loadHistory()
}

const clearFilters = () => {
  filterServiceType.value = ''
  handleFilterChange()
}

const refreshAll = async () => {
  await Promise.all([loadHistory(), loadHistoryStats()])
}

// 清理过期记录不可恢复 ⇒ 二次确认由页头 ConfirmAction（danger）承担，这里只做执行。
const cleanupHistory = async () => {
  cleaningUp.value = true
  try {
    const response = await request.delete('/config/circuit-breaker/history/cleanup')
    if (response.data?.success) {
      ElMessage.success(response.data.message || t('circuitBreaker.history.messages.cleanupCompleted'))
    }
    await refreshAll()
  } catch (error) {
    console.error('Failed to cleanup history:', error)
    ElMessage.error(t('circuitBreaker.history.messages.cleanupFailed'))
  } finally {
    cleaningUp.value = false
  }
}

onMounted(() => {
  loadHistory()
  loadHistoryStats()
})
</script>

<style scoped>
.history-card {
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius-lg);
  background: var(--ja-paper);
}

.history-card :deep(.el-card__body) {
  padding: var(--ja-space-4, 16px);
}

.table-scroll {
  width: 100%;
  overflow-x: auto;
}

.flex-table {
  width: 100%;
  table-layout: auto;
}
</style>
