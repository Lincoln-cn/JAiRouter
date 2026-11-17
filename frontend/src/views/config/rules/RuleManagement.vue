<template>
  <PageSkeleton :title="t('rule.pageTitle')">
    <template #actions>
      <!-- 主操作只有一个：新增规则（刷新与「从模板创建」移到工具栏） -->
      <el-button type="primary" v-permission="'config:rules:write'" @click="handleCreate">
        <el-icon><Plus /></el-icon>&nbsp;{{ t('rule.addRule') }}
      </el-button>
    </template>

    <template #stats>
      <!-- 指标条：规则数 / 启用数 / YAML 只读规则数 / 累计命中，全部由现有列表与统计接口算出 -->
      <MetricStrip :items="metricItems" :loading="loading" :columns="4" />
    </template>

    <template #toolbar>
      <!-- 工具栏顺序：筛选 → 刷新 → 次要动作 -->
      <el-input
        v-model="keyword"
        :placeholder="t('rule.searchPlaceholder')"
        clearable
        style="width: 240px;"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button :loading="loading" @click="refresh">
        <el-icon><Refresh /></el-icon>&nbsp;{{ t('rule.refresh') }}
      </el-button>
      <el-button plain @click="templateDialogVisible = true">
        <el-icon><MagicStick /></el-icon>&nbsp;{{ t('rule.createFromTemplate') }}
      </el-button>
    </template>

    <el-card shadow="never" class="table-card">
      <el-table
        ref="tableRef"
        :data="filteredRules"
        v-loading="loading"
        style="width: 100%"
        row-key="id"
      >
        <el-table-column :label="t('rule.sort')" width="60" align="center">
          <template #default="{ row }">
            <el-icon
              v-if="row.source !== 'YAML' && reorderEnabled"
              class="drag-handle"
              :title="t('rule.dragToSort')"
            >
              <Rank />
            </el-icon>
          </template>
        </el-table-column>
        <el-table-column :label="t('rule.enabled')" width="70" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" v-permission="'config:rules:write'" @change="(val: boolean) => handleToggle(row, val)" />
          </template>
        </el-table-column>
        <el-table-column :label="t('rule.name')" min-width="140">
          <template #default="{ row }">
            {{ row.name }}
            <el-tag v-if="row.source === 'YAML'" size="small" type="info" style="margin-left: 6px">YAML</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" :label="t('rule.priority')" width="80" align="center" />
        <el-table-column :label="t('rule.hits')" width="90" align="center">
          <template #default="{ row }">
            <el-badge v-if="statsMap[row.id]" :value="statsMap[row.id]" type="success" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('rule.condition')" min-width="200">
          <template #default="{ row }">{{ formatConditions(row.conditions) }}</template>
        </el-table-column>
        <el-table-column :label="t('rule.action')" min-width="160">
          <template #default="{ row }">
            <el-link
              v-if="row.action && (row.action.type === 'TARGET_MODEL' || row.action.type === 'TARGET_INSTANCE' || row.action.type === 'TARGET_ADAPTER')"
              type="primary"
              :underline="false"
              @click.stop="navigateActionTarget(row.action)"
            >
              {{ formatAction(row.action) }}
            </el-link>
            <span v-else>{{ formatAction(row.action) }}</span>
          </template>
        </el-table-column>
        <el-table-column :label="t('rule.operations')" width="160" align="right">
          <template #default="{ row }">
            <!-- 行内操作统一为文字按钮（与 服务管理/实例管理 一致）：危险语义仍在，但不给整行加一块红 -->
            <el-button link size="small" v-permission="'config:rules:write'" @click="handleEdit(row)">{{ t('rule.edit') }}</el-button>
            <ConfirmAction
              v-if="canWrite"
              :title="t('rule.deleteConfirmMessage', { name: row.name })"
              :confirm-text="t('rule.deleteConfirmTitle')"
              type="danger"
              @confirm="handleDelete(row)"
            >
              <el-button link size="small" type="danger">{{ t('rule.delete') }}</el-button>
            </ConfirmAction>
          </template>
        </el-table-column>
        <template #empty>
          <!-- 空态区分「还没有规则」与「筛选无结果」：无数据时把主操作放进空态 -->
          <EmptyState
            icon="Files"
            :title="keyword ? t('rule.noMatchTitle') : t('rule.emptyTitle')"
            :description="keyword ? t('rule.noMatchHint') : t('rule.emptyDescription')"
          >
            <template #actions>
              <el-button v-if="keyword" @click="keyword = ''">{{ t('rule.clearFilter') }}</el-button>
              <template v-else>
                <el-button @click="templateDialogVisible = true">
                  <el-icon><MagicStick /></el-icon>&nbsp;{{ t('rule.createFromTemplate') }}
                </el-button>
                <el-button type="primary" v-permission="'config:rules:write'" @click="handleCreate">
                  <el-icon><Plus /></el-icon>&nbsp;{{ t('rule.addRule') }}
                </el-button>
              </template>
            </template>
          </EmptyState>
        </template>
      </el-table>
    </el-card>
  </PageSkeleton>

  <RuleFormDialog
    v-model="dialogVisible"
    :rule="editingRule"
    @saved="fetchRules"
  />

  <RuleTemplateDialog v-model="templateDialogVisible" @created="handleTemplateCreated" />
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Plus, MagicStick, Rank, Refresh, Search } from '@element-plus/icons-vue'
import Sortable from 'sortablejs'
import {
  getRuleList,
  deleteRule,
  enableRule,
  disableRule,
  getRuleStats,
  updateRulePriorities,
  type RuleDefinition,
  type RuleCondition,
  type RuleAction
} from '@/api/rules'
import RuleFormDialog from './RuleFormDialog.vue'
import RuleTemplateDialog from './RuleTemplateDialog.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import EmptyState from '@/components/EmptyState.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import MetricStrip from '@/components/MetricStrip.vue'
import type { MetricStripItem } from '@/components/MetricStrip.vue'
import { usePermission } from '@/composables/usePermission'

const rules = ref<RuleDefinition[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingRule = ref<RuleDefinition | null>(null)
const statsMap = ref<Record<string, number>>({})
const tableRef = ref()
const templateDialogVisible = ref(false)
// 工具栏筛选：命中规则名、条件、动作目标等原始字段（不匹配展示用的翻译文案）
const keyword = ref('')
const { t } = useI18n()
const router = useRouter()
const { hasPermission } = usePermission()

/**
 * 拖拽排序 = 写操作（改的是 priority 并立即落库），必须与页内其它写按钮同一口径。
 *
 * 此前只有按钮挂了 `v-permission`，拖拽手柄是裸的：只读角色（`config:rules:read`）
 * 仍能拖动行，松手即触发 `PUT /api/config/rules/priorities` → 403（issue #468）。
 */
const canWrite = computed(() => hasPermission('config:rules:write'))

/**
 * 拖拽只在「有写权限 + 未筛选」时可用：
 * 排序锚定的是全量有序数组的索引，筛选后 DOM 行与 `rules` 下标不再一一对应，
 * 放开拖拽会把错误顺序写回后端。
 */
const reorderEnabled = computed(() => canWrite.value && !keyword.value.trim())

/** 统计条：规则总数 / 已启用 / YAML 只读规则 / 累计命中（统计接口按 ruleId 累加） */
const metricItems = computed<MetricStripItem[]>(() => {
  const total = rules.value.length
  const enabled = rules.value.filter((rule) => rule.enabled).length
  const yamlCount = rules.value.filter((rule) => rule.source === 'YAML').length
  const hits = Object.values(statsMap.value).reduce((sum, value) => sum + (value || 0), 0)
  return [
    { key: 'total', label: t('rule.metricTotal'), value: total },
    { key: 'enabled', label: t('rule.metricEnabled'), value: enabled, tone: 'success' },
    { key: 'yaml', label: t('rule.metricYaml'), value: yamlCount },
    { key: 'hits', label: t('rule.metricHits'), value: hits }
  ]
})

/** 搜索文本：只拼原始字段值，避免把随语言切换的展示文案算进匹配 */
const ruleSearchText = (rule: RuleDefinition) => {
  const parts: string[] = [rule.name || '', String(rule.priority ?? ''), rule.source || '']
  for (const c of rule.conditions || []) {
    parts.push(c.type || '', c.operator || '', c.value || '', c.field || '')
  }
  const action = rule.action
  if (action) {
    parts.push(action.type || '', action.modelName || '', action.instanceId || '', action.adapterName || '', action.lbStrategy || '')
    Object.values(action.tags || {}).forEach((value) => parts.push(String(value)))
  }
  return parts.join(' ').toLowerCase()
}

const filteredRules = computed<RuleDefinition[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return rules.value
  return rules.value.filter((rule) => ruleSearchText(rule).includes(kw))
})

const fetchRules = async () => {
  loading.value = true
  try {
    const res = await getRuleList()
    rules.value = res.data?.data || []
    await nextTick()
    initDrag()
  } catch (e) {
    ElMessage.error(t('rule.fetchListFailed'))
  } finally {
    loading.value = false
  }
}

const fetchStats = async () => {
  try {
    const res = await getRuleStats()
    const stats = res.data?.data || []
    const map: Record<string, number> = {}
    stats.forEach(s => {
      map[s.ruleId] = (map[s.ruleId] || 0) + s.hits
    })
    statsMap.value = map
  } catch (e) {
    // 统计加载失败不阻塞列表
  }
}

const refresh = () => {
  fetchRules()
  fetchStats()
}

// ==================== 优先级拖拽 ====================
let sortable: Sortable | null = null

const initDrag = () => {
  if (!tableRef.value?.$el) return
  const tbody = tableRef.value.$el.querySelector('.el-table__body-wrapper tbody')
  if (!tbody || sortable) return
  sortable = Sortable.create(tbody, {
    handle: '.drag-handle',
    filter: '.yaml-row',
    animation: 150,
    // 无写权限或正在筛选时整体禁用（手柄本身也不渲染，这里是第二道保险：
    // 权限在会话内变化 / 手柄选择器将来被改宽时仍然拖不动）。
    disabled: !reorderEnabled.value,
    onEnd: (evt) => {
      const { oldIndex, newIndex } = evt
      if (oldIndex == null || newIndex == null || oldIndex === newIndex) return
      const arr = [...rules.value]
      const [moved] = arr.splice(oldIndex, 1)
      arr.splice(newIndex, 0, moved)
      rules.value = arr
      commitPriorities()
    }
  })
}

// 权限在运行时变化（切换账号 / 刷新权限）或进入筛选时，同步 Sortable 的 disabled 状态。
watch(reorderEnabled, (allowed) => {
  sortable?.option('disabled', !allowed)
})

const commitPriorities = async () => {
  const items = rules.value.map((rule, index) => ({
    id: rule.id!,
    priority: rules.value.length - index
  }))
  try {
    const res = await updateRulePriorities(items)
    const data = res.data?.data
    if (data?.skipped) {
      ElMessage.warning(
        t('rule.priorityUpdatedPartial', { updated: data.updated, skipped: data.skipped })
      )
    } else {
      ElMessage.success(t('rule.priorityUpdated'))
    }
    fetchRules()
  } catch (e) {
    ElMessage.error(t('rule.priorityUpdateFailed'))
    fetchRules()
  }
}

const handleCreate = () => {
  editingRule.value = null
  dialogVisible.value = true
}

const handleTemplateCreated = (draft: RuleDefinition) => {
  editingRule.value = draft
  dialogVisible.value = true
}

const handleEdit = (row: RuleDefinition) => {
  editingRule.value = row
  dialogVisible.value = true
}

const handleDelete = async (row: RuleDefinition) => {
  try {
    await deleteRule(row.id!)
    ElMessage.success(t('rule.ruleDeleted'))
    fetchRules()
  } catch (e) {
    ElMessage.error(t('rule.operationFailed'))
  }
}

const handleToggle = async (row: RuleDefinition, enabled: boolean) => {
  try {
    if (enabled) {
      await enableRule(row.id!)
    } else {
      await disableRule(row.id!)
    }
    ElMessage.success(enabled ? t('rule.ruleEnabled') : t('rule.ruleDisabled'))
    fetchRules()
  } catch (e) {
    ElMessage.error(t('rule.operationFailed'))
  }
}

const conditionTypeMap: Record<string, string> = {
  SERVICE_TYPE: 'rule.conditionType.SERVICE_TYPE',
  MODEL_NAME: 'rule.conditionType.MODEL_NAME',
  HEADER: 'rule.conditionType.HEADER',
  CLIENT_IP: 'rule.conditionType.CLIENT_IP',
  WEIGHT: 'rule.conditionType.WEIGHT'
}

const operatorMap: Record<string, string> = {
  EQUALS: 'rule.operator.EQUALS',
  CONTAINS: 'rule.operator.CONTAINS',
  STARTS_WITH: 'rule.operator.STARTS_WITH',
  REGEX: 'rule.operator.REGEX',
  CIDR_MATCH: 'rule.operator.CIDR_MATCH'
}

const actionTypeMap: Record<string, string> = {
  TARGET_MODEL: 'rule.actionType.TARGET_MODEL',
  TARGET_INSTANCE: 'rule.actionType.TARGET_INSTANCE',
  TARGET_ADAPTER: 'rule.actionType.TARGET_ADAPTER',
  LB_STRATEGY: 'rule.actionType.LB_STRATEGY',
  TARGET_TAGS: 'rule.actionType.TARGET_TAGS'
}

const formatConditions = (conditions: RuleCondition[]) => {
  return conditions
    .map(c => {
      const prefix = c.type === 'HEADER' ? `${c.field}:` : ''
      const typeLabel = conditionTypeMap[c.type] ? t(conditionTypeMap[c.type]) : c.type
      const opLabel = operatorMap[c.operator] ? t(operatorMap[c.operator]) : c.operator
      return `${typeLabel} ${opLabel} ${prefix}${c.value}`
    })
    .join(t('rule.conditionsAnd'))
}

const formatAction = (action: RuleAction) => {
  if (!action) return '-'
  const label = actionTypeMap[action.type] ? t(actionTypeMap[action.type]) : action.type
  if (action.type === 'TARGET_TAGS') {
    const tags = Object.entries(action.tags || {})
      .map(([k, v]) => `${k}=${v}`)
      .join(',')
    return `${label}: ${tags || '-'}`
  }
  const target = action.modelName || action.instanceId || action.adapterName || action.lbStrategy || '-'
  return `${label}: ${target}`
}

// Cross-link: 根据动作类型导航到对应配置页
const navigateActionTarget = (action: RuleAction) => {
  if (!action) return
  if (action.type === 'TARGET_ADAPTER' && action.adapterName) {
    router.push({ name: 'adapter-management' })
  } else if (action.type === 'TARGET_MODEL' && action.modelName) {
    router.push({ name: 'service-management' })
  } else if (action.type === 'TARGET_INSTANCE' && action.instanceId) {
    router.push({ name: 'service-management' })
  }
}

onMounted(() => {
  fetchRules()
  fetchStats()
})
</script>

<style scoped>
/* 表格容器：把表格从灰底上「托」起来，避免整页无内容容器的观感 */
.table-card {
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius-lg);
  background: var(--ja-paper);
  /* 中性投影：让容器从灰底上「浮」起来，不用语义色 */
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
  overflow: hidden;
}

.table-card :deep(.el-card__body) {
  padding: 0;
}

/* 拖拽手柄：放大点击区 + hover 变色（不改拖拽逻辑，Sortable 仍绑 .drag-handle） */
.drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--ja-radius-sm);
  color: var(--ja-ink-3);
  font-size: 16px;
  cursor: grab;
  transition: color 0.15s ease, background-color 0.15s ease;
}

.drag-handle:hover {
  color: var(--ja-ink);
  background: var(--ja-surface-2);
}

.drag-handle:active {
  cursor: grabbing;
}
</style>
