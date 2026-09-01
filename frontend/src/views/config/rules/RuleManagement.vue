<template>
  <div class="rule-management">
    <el-card class="rule-card">
      <template #header>
        <div class="card-header">
          <span>路由规则管理</span>
          <div>
            <el-button :icon="Refresh" circle @click="refresh" title="刷新列表与命中统计" />
            <el-button type="success" plain @click="templateDialogVisible = true">
              <el-icon><MagicStick /></el-icon>&nbsp;从模板创建
            </el-button>
            <el-button type="primary" @click="handleCreate">
              <el-icon><Plus /></el-icon>&nbsp;新增规则
            </el-button>
          </div>
        </div>
      </template>

      <el-table ref="tableRef" :data="rules" v-loading="loading" style="width: 100%" row-key="id">
        <el-table-column label="排序" width="60" align="center">
          <template #default="{ row }">
            <el-icon v-if="row.source !== 'YAML'" class="drag-handle"><Rank /></el-icon>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="70" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="(val: boolean) => handleToggle(row, val)" />
          </template>
        </el-table-column>
        <el-table-column label="名称" min-width="140">
          <template #default="{ row }">
            {{ row.name }}
            <el-tag v-if="row.source === 'YAML'" size="small" type="info" style="margin-left: 6px">YAML</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" align="center" />
        <el-table-column label="命中" width="90" align="center">
          <template #default="{ row }">
            <el-badge v-if="statsMap[row.id]" :value="statsMap[row.id]" type="success" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="条件" min-width="200">
          <template #default="{ row }">{{ formatConditions(row.conditions) }}</template>
        </el-table-column>
        <el-table-column label="动作" min-width="160">
          <template #default="{ row }">{{ formatAction(row.action) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <RuleFormDialog
      v-model="dialogVisible"
      :rule="editingRule"
      @saved="fetchRules"
    />

    <RuleTemplateDialog v-model="templateDialogVisible" @created="handleTemplateCreated" />
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, MagicStick, Rank, Refresh } from '@element-plus/icons-vue'
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

const rules = ref<RuleDefinition[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingRule = ref<RuleDefinition | null>(null)
const statsMap = ref<Record<string, number>>({})
const tableRef = ref()
const templateDialogVisible = ref(false)

const fetchRules = async () => {
  loading.value = true
  try {
    const res = await getRuleList()
    rules.value = res.data?.data || []
    await nextTick()
    initDrag()
  } catch (e) {
    ElMessage.error('获取规则列表失败')
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

const commitPriorities = async () => {
  const items = rules.value.map((rule, index) => ({
    id: rule.id!,
    priority: rules.value.length - index
  }))
  try {
    const res = await updateRulePriorities(items)
    const data = res.data?.data
    if (data?.skipped) {
      ElMessage.warning(`已更新 ${data.updated} 条,跳过 ${data.skipped} 条(YAML 规则)`)
    } else {
      ElMessage.success('优先级已更新')
    }
    fetchRules()
  } catch (e) {
    ElMessage.error('优先级更新失败')
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
    await ElMessageBox.confirm(`确定删除规则「${row.name}」?`, '删除确认', { type: 'warning' })
    await deleteRule(row.id!)
    ElMessage.success('规则已删除')
    fetchRules()
  } catch (e) {
    // 用户取消
  }
}

const handleToggle = async (row: RuleDefinition, enabled: boolean) => {
  try {
    if (enabled) {
      await enableRule(row.id!)
    } else {
      await disableRule(row.id!)
    }
    ElMessage.success(enabled ? '规则已启用' : '规则已停用')
    fetchRules()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const conditionTypeMap: Record<string, string> = {
  SERVICE_TYPE: '服务类型',
  MODEL_NAME: '模型名',
  HEADER: '请求头',
  CLIENT_IP: '来源IP',
  WEIGHT: '权重'
}

const operatorMap: Record<string, string> = {
  EQUALS: '等于',
  CONTAINS: '包含',
  STARTS_WITH: '前缀',
  REGEX: '正则',
  CIDR_MATCH: 'CIDR'
}

const actionTypeMap: Record<string, string> = {
  TARGET_MODEL: '重写模型',
  TARGET_INSTANCE: '锁定实例',
  TARGET_ADAPTER: '切换适配器',
  LB_STRATEGY: 'LB策略',
  TARGET_TAGS: '标签路由'
}

const formatConditions = (conditions: RuleCondition[]) => {
  return conditions
    .map(c => {
      const prefix = c.type === 'HEADER' ? `${c.field}:` : ''
      return `${conditionTypeMap[c.type] || c.type} ${operatorMap[c.operator] || c.operator} ${prefix}${c.value}`
    })
    .join(' 且 ')
}

const formatAction = (action: RuleAction) => {
  if (!action) return '-'
  const label = actionTypeMap[action.type] || action.type
  if (action.type === 'TARGET_TAGS') {
    const tags = Object.entries(action.tags || {})
      .map(([k, v]) => `${k}=${v}`)
      .join(',')
    return `${label}: ${tags || '-'}`
  }
  const target = action.modelName || action.instanceId || action.adapterName || action.lbStrategy || '-'
  return `${label}: ${target}`
}

onMounted(() => {
  fetchRules()
  fetchStats()
})
</script>

<style scoped>
.rule-card {
  margin: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.drag-handle {
  cursor: grab;
}
</style>
