<template>
  <div class="rule-management">
    <el-card class="rule-card">
      <template #header>
        <div class="card-header">
          <span>路由规则管理</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>&nbsp;新增规则
          </el-button>
        </div>
      </template>

      <el-table :data="rules" v-loading="loading" style="width: 100%">
        <el-table-column label="启用" width="70" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="(val: boolean) => handleToggle(row, val)" />
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="priority" label="优先级" width="80" align="center" />
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getRuleList,
  deleteRule,
  enableRule,
  disableRule,
  type RuleDefinition,
  type RuleCondition,
  type RuleAction
} from '@/api/rules'
import RuleFormDialog from './RuleFormDialog.vue'

const rules = ref<RuleDefinition[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingRule = ref<RuleDefinition | null>(null)

const fetchRules = async () => {
  loading.value = true
  try {
    const res = await getRuleList()
    rules.value = res.data?.data || []
  } catch (e) {
    ElMessage.error('获取规则列表失败')
  } finally {
    loading.value = false
  }
}

const handleCreate = () => {
  editingRule.value = null
  dialogVisible.value = true
}

const handleEdit = (row: RuleDefinition) => {
  editingRule.value = row
  dialogVisible.value = true
}

const handleDelete = async (row: RuleDefinition) => {
  try {
    await ElMessageBox.confirm(`确定删除规则「${row.name}」?`, '删除确认', { type: 'warning' })
    await deleteRule(row.id)
    ElMessage.success('规则已删除')
    fetchRules()
  } catch (e) {
    // 用户取消
  }
}

const handleToggle = async (row: RuleDefinition, enabled: boolean) => {
  try {
    if (enabled) {
      await enableRule(row.id)
    } else {
      await disableRule(row.id)
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
  LB_STRATEGY: 'LB策略'
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
  const target = action.modelName || action.instanceId || action.adapterName || action.lbStrategy || '-'
  return `${actionTypeMap[action.type] || action.type}: ${target}`
}

onMounted(fetchRules)
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
</style>
