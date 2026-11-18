<template>
  <PageSkeleton :title="t('pageAccessHeat.pageTitle')">
    <template #actions>
      <el-button @click="load" :loading="loading">
        <el-icon><Refresh /></el-icon>
        {{ t('pageAccessHeat.reload') }}
      </el-button>
    </template>

    <template #stats>
      <MetricStrip :items="metricItems" :loading="loading" :columns="4" />
    </template>

    <template #toolbar>
      <el-select v-model="days" style="width: 160px" @change="load">
        <el-option :label="t('pageAccessHeat.range.days7')" :value="7" />
        <el-option :label="t('pageAccessHeat.range.days30')" :value="30" />
        <el-option :label="t('pageAccessHeat.range.days90')" :value="90" />
      </el-select>
      <el-select v-model="roleFilter" clearable style="width: 200px">
        <el-option
          v-for="role in roleOptions"
          :key="role"
          :label="role"
          :value="role"
        />
      </el-select>
      <el-input
        v-model="keyword"
        :placeholder="t('pageAccessHeat.searchPlaceholder')"
        clearable
        style="max-width: 260px"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
    </template>

    <el-alert
      :title="t('pageAccessHeat.hintTitle')"
      :description="t('pageAccessHeat.hintDesc', { days })"
      type="info"
      :closable="false"
      show-icon
      class="boundary-alert"
    />

    <el-card shadow="never" class="collection-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">{{ t('pageAccessHeat.title') }}</span>
          <span class="card-hint">{{ t('pageAccessHeat.rowsHint', { count: filteredRows.length }) }}</span>
        </div>
      </template>

      <el-table :data="filteredRows" v-loading="loading" size="small">
        <template #empty>
          <EmptyState
            :title="hasFilter ? t('pageAccessHeat.noMatchTitle') : t('pageAccessHeat.emptyTitle')"
            :description="hasFilter ? t('pageAccessHeat.noMatchHint') : t('pageAccessHeat.emptyHint')"
          />
        </template>
        <el-table-column prop="path" :label="t('pageAccessHeat.table.path')" min-width="260">
          <template #default="{ row }">
            <span class="path-cell">{{ row.path }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="role" :label="t('pageAccessHeat.table.role')" width="180">
          <template #default="{ row }">
            <span v-if="row.role">{{ row.role }}</span>
            <span v-else class="muted">{{ t('pageAccessHeat.table.roleUnknown') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="views" :label="t('pageAccessHeat.table.views')" width="140" align="right" />
        <el-table-column prop="users" :label="t('pageAccessHeat.table.users')" width="140" align="right" />
      </el-table>
    </el-card>
  </PageSkeleton>
</template>

<script setup lang="ts">
/**
 * 页面访问热度（B4，2026-11-18）。
 *
 * 数据来自 `security_audit` 表的 `UI_PAGE_VIEW` 事件（前端每次路由切换上报一次，
 * 同路径 60 秒节流），按「页面 × 角色」聚合。回答两个问题：
 * 哪些页面真的有人用、哪些角色的入口形同虚设。
 *
 * 无编辑动作，属于只读集合页：PageSkeleton + MetricStrip + 工具栏 + EmptyState。
 */
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Refresh, Search } from '@element-plus/icons-vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import MetricStrip, { type MetricStripItem } from '@/components/MetricStrip.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getPageViewSummary, type UiPageViewSummary } from '@/api/uiAccess'

const { t } = useI18n()

const rows = ref<UiPageViewSummary[]>([])
const loading = ref(false)
const days = ref(7)
const roleFilter = ref<string | null>(null)
const keyword = ref('')

const roleOptions = computed(() => {
  const set = new Set<string>()
  rows.value.forEach(row => {
    if (row.role) set.add(row.role)
  })
  return Array.from(set).sort()
})

const hasFilter = computed(() => Boolean(roleFilter.value) || keyword.value.trim().length > 0)

const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return rows.value.filter(row => {
    if (roleFilter.value && row.role !== roleFilter.value) return false
    if (!kw) return true
    return row.path.toLowerCase().includes(kw) || row.role.toLowerCase().includes(kw)
  })
})

const totalViews = computed(() => filteredRows.value.reduce((sum, row) => sum + row.views, 0))

const metricItems = computed<MetricStripItem[]>(() => {
  // 后端按「页面 × 角色」聚合，去重用户数无法跨行相加 ——
  // 所以第三项给的是「最热那一行」的去重用户数，不假装是全局活跃用户
  const top = filteredRows.value[0]
  return [
    {
      key: 'pages',
      label: t('pageAccessHeat.metrics.pages'),
      value: String(new Set(filteredRows.value.map(row => row.path)).size)
    },
    { key: 'views', label: t('pageAccessHeat.metrics.views'), value: String(totalViews.value) },
    {
      key: 'users',
      label: t('pageAccessHeat.metrics.topUsers'),
      value: top ? String(top.users) : '0'
    },
    {
      key: 'top',
      label: t('pageAccessHeat.metrics.topPage'),
      value: top ? top.path : '—'
    }
  ]
})

async function load(): Promise<void> {
  loading.value = true
  try {
    rows.value = await getPageViewSummary(days.value, 100)
  } catch {
    rows.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.boundary-alert {
  margin-bottom: var(--ja-space-3);
}

.collection-card {
  border: 1px solid var(--ja-border);
}

.card-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--ja-space-2);
}

.card-title {
  font-weight: 600;
  color: var(--ja-ink);
}

.card-hint {
  font-size: 12px;
  color: var(--ja-ink-3);
}

.path-cell {
  font-family: var(--ja-font-mono, ui-monospace, SFMono-Regular, Menlo, monospace);
  font-size: 12px;
  color: var(--ja-ink-2);
}

.muted {
  color: var(--ja-ink-3);
}
</style>
