<template>
  <PageSkeleton :title="t('consoleOperations.pageTitle')">
    <template #actions>
      <el-switch
        v-model="onlyErrors"
        :active-text="t('consoleOperations.onlyErrors')"
        style="margin-right: 12px"
      />
      <el-button @click="loadOperations" :loading="loading" size="default">
        <el-icon><Refresh /></el-icon>
        {{ t('consoleOperations.refresh') }}
      </el-button>
    </template>

    <template #toolbar>
      <p class="hint">
        <el-icon><InfoFilled /></el-icon>
        <span>{{ t('consoleOperations.hint') }}</span>
      </p>
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
        :empty-text="t('consoleOperations.empty')"
      >
        <el-table-column prop="timestamp" :label="t('consoleOperations.time')" width="180">
          <template #default="{ row }">{{ formatTime(row.timestamp) }}</template>
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
</el-table>
    </el-card>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { InfoFilled, Refresh } from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
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

function formatTime(value: number | null | undefined) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString()
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
