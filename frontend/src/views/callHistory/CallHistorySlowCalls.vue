<template>
  <ListPageLayout
    :loading="loading"
    :empty="!loading && slowCalls.length === 0"
    :empty-title="t('callHistory.slowCalls.emptyTitle')"
    :empty-description="t('callHistory.slowCalls.emptyDescription')"
  >
    <template #header>
      <h2 class="page-title">{{ t('callHistory.slowCalls.pageTitle') }}</h2>
      <p class="page-subtitle">{{ t('callHistory.slowCalls.pageDescription') }}</p>
    </template>

    <template #filters>
      <FilterBar>
        <el-form :inline="true" class="filter-form">
          <el-form-item :label="t('callHistory.common.timeRange')">
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              :range-separator="t('callHistory.common.rangeSeparator')"
              :start-placeholder="t('callHistory.common.startTime')"
              :end-placeholder="t('callHistory.common.endTime')"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 400px"
            />
          </el-form-item>
          <el-form-item :label="t('callHistory.slowCalls.thresholdLabel')">
            <el-input-number
              v-model="threshold"
              :min="100"
              :max="30000"
              :step="100"
              style="width: 140px"
            />
          </el-form-item>
          <el-form-item :label="t('callHistory.slowCalls.displayCountLabel')">
            <el-select v-model="limit" style="width: 100px">
              <el-option :value="20" label="20" />
              <el-option :value="50" label="50" />
              <el-option :value="100" label="100" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" icon="Search" @click="loadData">{{ t('callHistory.common.query') }}</el-button>
            <el-button icon="Refresh" @click="handleReset">{{ t('callHistory.common.reset') }}</el-button>
          </el-form-item>
        </el-form>
      </FilterBar>
    </template>

    <template #toolbar>
      <div class="list-toolbar">
        <span class="chart-title">{{ t('callHistory.slowCalls.listTitle') }}</span>
        <el-tag type="info" size="small">{{ t('callHistory.slowCalls.thresholdTag', { value: threshold }) }}</el-tag>
      </div>
      <!-- 慢调用指标：3 张彩色图标卡 → 一条中性 MetricStrip（本页数字没有失败语义，全部 default） -->
      <MetricStrip :items="kpiItems" :loading="loading" :columns="3" />
    </template>

    <!-- 慢调用列表 -->
    <el-table v-loading="loading" :data="slowCalls" border stripe :max-height="600">
      <el-table-column :label="t('callHistory.common.time')" prop="createdAt" width="180">
        <template #default="scope">
          <TimeText :value="scope.row.createdAt" />
        </template>
      </el-table-column>
      <el-table-column :label="t('callHistory.common.modelName')" prop="modelName" min-width="160" show-overflow-tooltip />
      <el-table-column :label="t('callHistory.common.serviceType')" prop="serviceType" width="110">
        <template #default="scope">
          <el-tag size="small">{{ getServiceTypeLabel(scope.row.serviceType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('callHistory.common.responseTimeMs')" prop="responseTimeMs" width="140" align="right" sortable>
        <template #default="scope">
          <el-tag type="danger" size="small">
            {{ scope.row.responseTimeMs?.toFixed(0) || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('callHistory.common.httpStatusCode')" prop="httpStatusCode" width="120" align="center">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.httpStatusCode)" size="small">
            {{ scope.row.httpStatusCode || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('callHistory.common.token')" prop="totalTokens" width="100" align="right">
        <template #default="scope">
          {{ formatNumber(scope.row.totalTokens) }}
        </template>
      </el-table-column>
      <el-table-column :label="t('callHistory.common.callStatus')" width="100" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.isSuccess ? 'info' : 'danger'" size="small">
            {{ scope.row.isSuccess ? t('callHistory.common.success') : t('callHistory.common.failed') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('callHistory.common.provider')" prop="provider" width="100" show-overflow-tooltip />
      <el-table-column label="Trace ID" prop="traceId" width="140" show-overflow-tooltip />
    </el-table>
  </ListPageLayout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getErrorMessage } from '@/utils/error'
import { getSlowCalls } from '@/api/callHistory'
import type { ApiCallHistoryRecord } from '@/types/callHistory'
import ListPageLayout from '@/components/ListPageLayout.vue'
import FilterBar from '@/components/FilterBar.vue'
import TimeText from '@/components/TimeText.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'

const { t } = useI18n()

// 筛选条件
const dateRange = ref<[string, string] | null>(null)
const threshold = ref(1000)
const limit = ref(50)

// 慢调用数据
const slowCalls = ref<ApiCallHistoryRecord[]>([])
const loading = ref(true)

// 格式化数字
const formatNumber = (num?: number): string => {
  if (!num && num !== 0) return '0'
  return num.toLocaleString()
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

// 获取服务类型标签（v2.10.4: 文案收敛到顶层 serviceTypes.*，未知类型回退原值）
const getServiceTypeLabel = (type?: string) => {
  if (!type) return t('callHistory.common.unknown')
  return serviceTypeKeyMap[type] ? t(`serviceTypes.${serviceTypeKeyMap[type]}`) : type
}

// 获取 HTTP 状态码标签类型（成功/失败两态：4xx 与 5xx 都是失败，其余中性）
const getStatusType = (code?: number) => {
  if (!code) return 'info'
  return code >= 400 ? 'danger' : 'info'
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

/**
 * 指标条数据。
 *
 * 三项都是「慢调用统计量」：慢调用数量本身是查询条件的结果，不是失败计数，
 * 最长/平均耗时是耗时度量而非告警阈值 ⇒ 全部保持 default，不给语义色。
 */
const kpiItems = computed<MetricStripItem[]>(() => [
  {
    key: 'slowCallCount',
    label: t('callHistory.slowCalls.slowCallCountLabel'),
    value: slowCalls.value.length
  },
  {
    key: 'maxResponseTime',
    label: t('callHistory.slowCalls.maxResponseTimeLabel'),
    value: `${getMaxResponseTime()} ms`
  },
  {
    key: 'avgResponseTime',
    label: t('callHistory.slowCalls.avgResponseTimeLabel'),
    value: `${getAvgResponseTime()} ms`
  }
])

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
  } catch (error) {
    console.error('加载慢调用失败:', error)
    ElMessage.error(t('callHistory.slowCalls.loadFailed', { message: getErrorMessage(error) || t('callHistory.common.unknownError') }))
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
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.4;
}

.page-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--ja-ink-3);
  line-height: 1.5;
}

.filter-form {
  display: flex;
  justify-content: center;
  width: 100%;
}

.list-toolbar {
  display: flex;
  align-items: center;
  gap: var(--ja-space-2);
  flex-wrap: wrap;
}

.chart-title {
  font-size: 16px;
  font-weight: bold;
  color: var(--ja-ink);
}
</style>
