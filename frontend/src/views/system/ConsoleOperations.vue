<template>
  <PageSkeleton :title="t('consoleOperations.pageTitle')">
    <template #stats>
      <!-- 统计条：只读页没有主操作，指标全部来自当前这批控制台记录 -->
      <MetricStrip :items="metricItems" :loading="loading" :columns="4" />
    </template>

    <!-- 工具栏（只读集合页）：筛选 → 说明 → 刷新（只读页没有新建主操作，刷新归工具栏） -->
    <template #toolbar>
      <FilterBar :collapsible="false" class="toolbar-filter">
        <el-switch v-model="onlyErrors" :active-text="t('consoleOperations.onlyErrors')" />
        <p class="hint">
          <el-icon><InfoFilled /></el-icon>
          <span>{{ t('consoleOperations.hint') }}</span>
        </p>
        <el-button icon="Refresh" :loading="loading" @click="loadOperations">
          {{ t('consoleOperations.refresh') }}
        </el-button>
      </FilterBar>
    </template>

    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('consoleOperations.tableTitle') }}</span>
          <span class="card-sub">{{ t('consoleOperations.countHint', { total: filtered.length }) }}</span>
        </div>
      </template>

      <el-table
        v-loading="loading"
        :data="filtered"
        size="small"
      >
        <el-table-column prop="timestamp" :label="t('consoleOperations.time')" width="180">
          <template #default="{ row }">
            <TimeText v-if="row.timestamp" :value="row.timestamp" mode="absolute" precision="second" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="operator" :label="t('consoleOperations.operator')" width="140">
          <template #default="{ row }">{{ row.operator || t('consoleOperations.unknownOperator') }}</template>
        </el-table-column>
        <el-table-column prop="operation" :label="t('consoleOperations.operation')" min-width="200">
          <template #default="{ row }"><code>{{ row.operation || '-' }}</code></template>
        </el-table-column>
        <el-table-column prop="path" :label="t('consoleOperations.path')" min-width="180">
          <template #default="{ row }"><span class="path">{{ row.path || '-' }}</span></template>
        </el-table-column>
        <el-table-column prop="duration" :label="t('consoleOperations.duration')" width="120" sortable>
          <template #default="{ row }">{{ formatDuration(row.duration) }}</template>
        </el-table-column>
        <el-table-column prop="status" :label="t('consoleOperations.result')" width="110">
          <template #default="{ row }">
            <el-tag v-if="typeof row.status === 'number' && row.status >= 400" type="danger" size="small">{{ row.status }}</el-tag>
            <el-tag v-else-if="typeof row.status === 'number'" type="success" size="small">{{ row.status }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('consoleOperations.trace')" width="120">
          <template #default="{ row }">
            <el-link v-if="row.traceId" type="primary" @click="openTrace(row.traceId)">{{ t('consoleOperations.viewTrace') }}</el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState
            v-if="!loading"
            :icon="onlyErrors ? 'Search' : 'Document'"
            :title="onlyErrors ? t('consoleOperations.filterEmptyTitle') : t('consoleOperations.empty')"
            :description="onlyErrors ? t('consoleOperations.filterEmptyDescription') : t('consoleOperations.emptyDescription')"
          >
            <template v-if="onlyErrors" #actions>
              <el-button @click="clearOnlyErrors">{{ t('consoleOperations.clearFilter') }}</el-button>
            </template>
          </EmptyState>
        </template>
      </el-table>
    </el-card>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import FilterBar from '@/components/FilterBar.vue'
import TimeText from '@/components/TimeText.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import { getConsoleRecords, type ConsoleRecord } from '@/api/consoleOperations'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const operations = ref<ConsoleRecord[]>([])
const onlyErrors = ref(false)

const filtered = computed(() =>
  onlyErrors.value
    ? operations.value.filter((item) => typeof item.status === 'number' && item.status >= 400)
    : operations.value
)

/** 统计条：4 项都来自已加载的控制台记录；耗时只统计有值的样本，避免缺失值当 0 拉低均值 */
const metricItems = computed<MetricStripItem[]>(() => {
  const records = operations.value
  const failedCount = records.filter(
    (item) => typeof item.status === 'number' && item.status >= 400
  ).length
  const durations = records
    .map((item) => item.duration)
    .filter((value): value is number => typeof value === 'number' && !Number.isNaN(value))
  const avgDuration = durations.length
    ? durations.reduce((sum, value) => sum + value, 0) / durations.length
    : 0
  const maxDuration = durations.length ? Math.max(...durations) : 0
  return [
    {
      key: 'total',
      label: t('consoleOperations.stats.total'),
      value: records.length
    },
    {
      key: 'failed',
      label: t('consoleOperations.stats.failed'),
      value: failedCount,
      tone: failedCount > 0 ? 'danger' : 'default'
    },
    {
      key: 'avgDuration',
      label: t('consoleOperations.stats.avgDuration'),
      value: durations.length ? `${avgDuration.toFixed(1)} ms` : '-'
    },
    {
      key: 'maxDuration',
      label: t('consoleOperations.stats.maxDuration'),
      value: durations.length ? `${maxDuration.toFixed(1)} ms` : '-'
    }
  ]
})

async function loadOperations() {
  loading.value = true
  try {
    const response = await getConsoleRecords(100)
    // 拦截器解包层级在不同版本里不一致（数组 / {data} / {data:{data}}），逐层兼容
    const first = response as unknown as ConsoleRecord[] | { data?: ConsoleRecord[] | { data?: ConsoleRecord[] } }
    const second = Array.isArray(first) ? undefined : first?.data
    const list = Array.isArray(first) ? first : Array.isArray(second) ? second : (second as { data?: ConsoleRecord[] } | undefined)?.data
    operations.value = Array.isArray(list) ? list : []
  } catch (error) {
    ElMessage.error(t('consoleOperations.loadFailed'))
    operations.value = []
  } finally {
    loading.value = false
  }
}

/** 空态里的「清除筛选」：关掉「只看失败」，回到全量记录 */
function clearOnlyErrors() {
  onlyErrors.value = false
}

function formatDuration(ms: number | null | undefined) {
  if (typeof ms !== 'number' || Number.isNaN(ms)) return '-'
  return `${ms.toFixed(1)} ms`
}

function openTrace(traceId: string) {
  router.push({ path: '/tracing/search', query: { traceId } })
}

onMounted(loadOperations)
</script>

<style scoped>
/* FilterBar 自身不带面板样式，面板与宽度由本页补（与观测页工具栏观感一致） */
.toolbar-filter {
  width: 100%;
  padding: 12px 16px;
  background-color: var(--ja-paper);
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius);
}

.hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 13px;
  color: var(--ja-text-secondary);
}

.card-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.card-title {
  font-weight: 600;
}

.card-sub {
  font-size: 12px;
  color: var(--ja-text-secondary);
}

.op-name {
  font-family: var(--ja-font-mono, monospace);
  font-size: 12px;
}
.path {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: var(--ja-text-secondary);
}
</style>
