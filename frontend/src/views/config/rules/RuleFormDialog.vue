<template>
  <el-dialog v-model="visible" :title="isEdit ? '编辑规则' : '新增规则'" width="720px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="规则名称,如: vLLM 流量路由" />
      </el-form-item>

      <el-form-item label="优先级" prop="priority">
        <el-input-number v-model="form.priority" :min="0" :max="9999" />
        <span class="form-tip">数值越大越先匹配,首条命中即生效</span>
      </el-form-item>

      <el-divider content-position="left">匹配条件(全部满足)</el-divider>

      <div v-for="(cond, index) in form.conditions" :key="index" class="condition-row">
        <el-select v-model="cond.type" style="width: 130px" placeholder="条件类型">
          <el-option label="模型名" value="MODEL_NAME" />
          <el-option label="服务类型" value="SERVICE_TYPE" />
          <el-option label="请求头" value="HEADER" />
          <el-option label="来源IP" value="CLIENT_IP" />
          <el-option label="权重" value="WEIGHT" />
        </el-select>

        <el-input
          v-if="cond.type === 'HEADER'"
          v-model="cond.field"
          placeholder="Header名,如 x-routing"
          style="width: 160px"
          class="condition-gap"
        />

        <el-select v-model="cond.operator" style="width: 130px" class="condition-gap">
          <el-option v-for="op in operatorsFor(cond.type)" :key="op.value" :label="op.label" :value="op.value" />
        </el-select>

        <el-input
          v-if="cond.type !== 'WEIGHT'"
          v-model="cond.value"
          placeholder="匹配值"
          style="width: 160px"
          class="condition-gap"
        />
        <el-input-number
          v-else
          v-model="cond.weight"
          :min="0"
          :max="100"
          style="width: 140px"
          class="condition-gap"
        />

        <el-button type="danger" :icon="Delete" circle class="condition-gap" @click="removeCondition(index)" />
      </div>

      <el-button type="primary" plain :icon="Plus" @click="addCondition">添加条件</el-button>

      <el-divider content-position="left">执行动作</el-divider>

      <el-form-item label="动作类型" prop="actionType">
        <el-radio-group v-model="form.actionType">
          <el-radio value="TARGET_MODEL">重写模型名</el-radio>
          <el-radio value="TARGET_INSTANCE">锁定实例</el-radio>
          <el-radio value="TARGET_ADAPTER">切换适配器</el-radio>
          <el-radio value="LB_STRATEGY">LB策略</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="动作目标" prop="actionTarget">
        <el-input v-model="form.actionTarget" placeholder="目标值" />
        <span class="form-tip">
          {{
            actionTargetTip
          }}
        </span>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Plus } from '@element-plus/icons-vue'
import {
  createRule,
  updateRule,
  type RuleCondition,
  type RuleDefinition
} from '@/api/rules'

const props = defineProps<{
  rule: RuleDefinition | null
}>()

const emit = defineEmits<{
  saved: []
}>()

const visible = defineModel<boolean>({ required: true })

const formRef = ref<FormInstance>()
const saving = ref(false)

const isEdit = computed(() => !!props.rule)

const defaultCondition = (): RuleCondition => ({
  type: 'MODEL_NAME',
  operator: 'EQUALS',
  value: '',
  field: ''
})

const form = reactive<{
  name: string
  priority: number
  conditions: RuleCondition[]
  actionType: string
  actionTarget: string
}>({
  name: '',
  priority: 10,
  conditions: [defaultCondition()],
  actionType: 'TARGET_MODEL',
  actionTarget: ''
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  actionType: [{ required: true, message: '请选择动作类型', trigger: 'change' }],
  actionTarget: [{ required: true, message: '请输入动作目标', trigger: 'blur' }]
}

const operatorsFor = (type: RuleCondition['type']) => {
  if (type === 'CLIENT_IP') {
    return [
      { value: 'EQUALS', label: '等于' },
      { value: 'CIDR_MATCH', label: 'CIDR' },
      { value: 'STARTS_WITH', label: '前缀' }
    ]
  }
  return [
    { value: 'EQUALS', label: '等于' },
    { value: 'CONTAINS', label: '包含' },
    { value: 'STARTS_WITH', label: '前缀' },
    { value: 'REGEX', label: '正则' }
  ]
}

const actionTargetTip = computed(() => {
  switch (form.actionType) {
    case 'TARGET_MODEL':
      return '重写后的模型名,如 claude-3'
    case 'TARGET_INSTANCE':
      return '目标实例ID或名称'
    case 'TARGET_ADAPTER':
      return '适配器名称,如 vllm'
    case 'LB_STRATEGY':
      return 'random / round-robin / least-connections / ip-hash / consistent-hash'
    default:
      return ''
  }
})

const addCondition = () => {
  form.conditions.push(defaultCondition())
}

const removeCondition = (index: number) => {
  if (form.conditions.length <= 1) {
    ElMessage.warning('至少保留一个条件')
    return
  }
  form.conditions.splice(index, 1)
}

const loadRule = (rule: RuleDefinition | null) => {
  if (rule) {
    form.name = rule.name || ''
    form.priority = rule.priority ?? 10
    form.conditions = rule.conditions?.length
      ? rule.conditions.map(c => ({ ...c }))
      : [defaultCondition()]
    form.actionType = rule.action?.type || 'TARGET_MODEL'
    form.actionTarget =
      rule.action?.modelName || rule.action?.instanceId || rule.action?.adapterName || rule.action?.lbStrategy || ''
  } else {
    form.name = ''
    form.priority = 10
    form.conditions = [defaultCondition()]
    form.actionType = 'TARGET_MODEL'
    form.actionTarget = ''
  }
}

watch(visible, val => {
  if (val) {
    loadRule(props.rule)
  }
})

const handleSave = async () => {
  await formRef.value?.validate()
  const payload: RuleDefinition = {
    id: props.rule?.id || '',
    name: form.name,
    priority: form.priority,
    enabled: props.rule?.enabled ?? true,
    conditions: form.conditions,
    action: {
      type: form.actionType as RuleDefinition['action']['type'],
      modelName: form.actionType === 'TARGET_MODEL' ? form.actionTarget : undefined,
      instanceId: form.actionType === 'TARGET_INSTANCE' ? form.actionTarget : undefined,
      adapterName: form.actionType === 'TARGET_ADAPTER' ? form.actionTarget : undefined,
      lbStrategy: form.actionType === 'LB_STRATEGY' ? form.actionTarget : undefined
    }
  }
  saving.value = true
  try {
    if (isEdit.value && props.rule) {
      await updateRule(props.rule.id, payload)
      ElMessage.success('规则已更新')
    } else {
      await createRule(payload)
      ElMessage.success('规则已创建')
    }
    visible.value = false
    emit('saved')
  } catch (e) {
    ElMessage.error('保存失败,请检查条件与动作配置')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.condition-row {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.condition-gap {
  margin-left: 8px;
}

.form-tip {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
</style>
