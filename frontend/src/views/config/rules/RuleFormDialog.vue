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
        <el-select v-model="cond.type" style="width: 130px" placeholder="条件类型" @change="onConditionTypeChange(cond)">
          <el-option label="模型名" value="MODEL_NAME" />
          <el-option label="服务类型" value="SERVICE_TYPE" />
          <el-option label="请求头" value="HEADER" />
          <el-option label="来源IP" value="CLIENT_IP" />
          <el-option label="权重" value="WEIGHT" />
        </el-select>

        <el-select
          v-if="cond.type === 'HEADER'"
          v-model="cond.field"
          placeholder="Header名"
          style="width: 160px"
          class="condition-gap"
          allow-create
          filterable
          default-first-option
        >
          <el-option v-for="h in COMMON_HEADERS" :key="h" :label="h" :value="h" />
        </el-select>

        <el-select v-model="cond.operator" style="width: 130px" class="condition-gap">
          <el-option v-for="op in operatorsFor(cond.type)" :key="op.value" :label="op.label" :value="op.value" />
        </el-select>

        <el-select
          v-if="cond.type === 'SERVICE_TYPE'"
          v-model="cond.value"
          style="width: 160px"
          class="condition-gap"
        >
          <el-option v-for="s in SERVICE_TYPES" :key="s" :label="s" :value="s" />
        </el-select>

        <el-select
          v-else-if="cond.type === 'MODEL_NAME'"
          v-model="cond.value"
          style="width: 160px"
          class="condition-gap"
          filterable
          allow-create
          default-first-option
          :loading="loadingModels"
          placeholder="选择或输入模型名"
        >
          <el-option v-for="m in modelNames" :key="m" :label="m" :value="m" />
        </el-select>

        <el-input
          v-else-if="cond.type === 'HEADER' || cond.type === 'CLIENT_IP'"
          v-model="cond.value"
          :placeholder="cond.type === 'HEADER' ? 'Header值' : 'IP或CIDR'"
          style="width: 160px"
          class="condition-gap"
        />

        <el-input-number
          v-if="cond.type === 'WEIGHT'"
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
        <el-select
          v-if="form.actionType === 'TARGET_INSTANCE'"
          v-model="form.actionTarget"
          placeholder="选择目标实例"
          filterable
          allow-create
          default-first-option
          style="width: 300px"
        >
          <el-option v-for="n in instanceNames" :key="n" :label="n" :value="n" />
        </el-select>

        <el-select
          v-else-if="form.actionType === 'TARGET_ADAPTER'"
          v-model="form.actionTarget"
          placeholder="选择适配器"
          filterable
          allow-create
          default-first-option
          style="width: 300px"
        >
          <el-option v-for="n in adapterNames" :key="n" :label="n" :value="n" />
        </el-select>

        <el-select
          v-else-if="form.actionType === 'LB_STRATEGY'"
          v-model="form.actionTarget"
          placeholder="选择LB策略"
          style="width: 300px"
        >
          <el-option v-for="s in LB_STRATEGIES" :key="s" :label="s" :value="s" />
        </el-select>

        <el-select
          v-else
          v-model="form.actionTarget"
          placeholder="选择或输入模型名"
          filterable
          allow-create
          default-first-option
          :loading="loadingModels"
          style="width: 300px"
        >
          <el-option v-for="m in modelNames" :key="m" :label="m" :value="m" />
        </el-select>

        <span class="form-tip">
          {{
            actionTargetTip
          }}
        </span>
      </el-form-item>

      <el-form-item>
        <el-button type="success" plain :icon="MagicStick" @click="openTestPanel">模拟测试</el-button>
        <span class="form-tip">用示例请求验证规则是否命中,无需保存</span>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>

    <!-- 模拟测试面板 -->
    <el-dialog v-model="testVisible" title="规则模拟测试(dry-run)" width="560px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="服务类型">
          <el-select v-model="testForm.serviceType" style="width: 200px">
            <el-option v-for="s in SERVICE_TYPES" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="模型名">
          <el-input v-model="testForm.modelName" placeholder="如 gpt-4" />
        </el-form-item>
        <el-form-item label="来源IP">
          <el-input v-model="testForm.clientIp" placeholder="如 127.0.0.1" />
        </el-form-item>
        <el-form-item label="请求头">
          <div class="test-header-list">
            <div v-for="(h, idx) in testForm.headers" :key="idx" class="test-header-row">
              <el-input v-model="h.key" placeholder="Header名" style="width: 160px" />
              <el-input v-model="h.value" placeholder="值" style="width: 160px; margin-left: 8px" />
              <el-button type="danger" :icon="Delete" circle size="small" style="margin-left: 8px"
                @click="removeTestHeader(idx)" />
            </div>
            <el-button type="primary" plain size="small" :icon="Plus" @click="addTestHeader">添加请求头</el-button>
          </div>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="testResult"
        :type="testResult.matched ? 'success' : 'info'"
        :title="testResult.message"
        :closable="false"
        style="margin-top: 8px"
      >
        <template v-if="testResult.matched && testResult.action" #default>
          <div style="margin-top: 6px">
            命中规则: <b>{{ testResult.ruleName }}</b> (优先级 {{ testResult.priority }})<br />
            执行动作: {{ testResult.action.type }}
            <template v-if="testResult.action.target"> → {{ testResult.action.target }}</template>
          </div>
        </template>
      </el-alert>

      <template #footer>
        <el-button @click="testVisible = false">关闭</el-button>
        <el-button type="primary" :loading="testLoading" @click="runTest">测试</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Delete, Plus, MagicStick } from '@element-plus/icons-vue'
import {
  createRule,
  updateRule,
  validateRule,
  type RuleCondition,
  type RuleDefinition,
  type RuleValidateResult
} from '@/api/rules'
import { getAdapterList, type AdapterInfo } from '@/api/adapter'
import { getServiceInstances, type InstanceConfig } from '@/api/instance'
import { getModelsByServiceType } from '@/api/models'

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

// ==================== 下拉数据源 ====================
const adapterNames = ref<string[]>([])
const modelNames = ref<string[]>([])
const instanceNames = ref<string[]>([])
const loadingModels = ref(false)

const LB_STRATEGIES = ['random', 'round-robin', 'least-connections', 'ip-hash', 'consistent-hash']
const COMMON_HEADERS = ['x-routing', 'x-tenant', 'x-user-id', 'authorization', 'content-type', 'x-api-key']
const SERVICE_TYPES = ['chat', 'embedding', 'rerank', 'tts', 'stt', 'imgGen', 'imgEdit']

const loadOptions = async () => {
  try {
    const [adapterRes, instanceRes, modelRes] = await Promise.allSettled([
      getAdapterList(),
      getServiceInstances('chat'),
      getModelsByServiceType('chat')
    ])
    if (adapterRes.status === 'fulfilled' && adapterRes.value.data?.data) {
      adapterNames.value = adapterRes.value.data.data.map((a: AdapterInfo) => a.name)
    }
    if (instanceRes.status === 'fulfilled' && instanceRes.value.data?.data) {
      instanceNames.value = instanceRes.value.data.data
        .map((i: InstanceConfig) => i.name || i.instanceId)
        .filter((n): n is string => Boolean(n))
    }
    if (modelRes.status === 'fulfilled' && Array.isArray(modelRes.value)) {
      modelNames.value = modelRes.value
    }
  } catch (e) {
    // 下拉加载失败不阻塞表单(保留手动输入兜底)
  }
}

const loadModelsForService = async (serviceType: string) => {
  loadingModels.value = true
  try {
    const models = await getModelsByServiceType(serviceType)
    if (Array.isArray(models)) {
      modelNames.value = models
    }
  } catch (e) {
    // 失败保留现有选项
  } finally {
    loadingModels.value = false
  }
}

const loadInstancesForService = async (serviceType: string) => {
  try {
    const res = await getServiceInstances(serviceType)
    if (res.data?.data) {
      instanceNames.value = res.data.data
        .map((i: InstanceConfig) => i.name || i.instanceId)
        .filter((n): n is string => Boolean(n))
    }
  } catch (e) {
    // 失败保留现有选项
  }
}

const currentServiceType = computed(() => {
  const cond = form.conditions.find(c => c.type === 'SERVICE_TYPE')
  return cond?.value || 'chat'
})

watch(currentServiceType, val => {
  if (val) {
    loadModelsForService(val)
    loadInstancesForService(val)
  }
})

onMounted(loadOptions)

// ==================== 表单 ====================
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

// ==================== 模拟测试(dry-run) ====================
const testVisible = ref(false)
const testForm = reactive<{
  serviceType: string
  modelName: string
  clientIp: string
  headers: Array<{ key: string; value: string }>
}>({
  serviceType: 'chat',
  modelName: '',
  clientIp: '127.0.0.1',
  headers: [{ key: '', value: '' }]
})
const testLoading = ref(false)
const testResult = ref<RuleValidateResult | null>(null)

const openTestPanel = () => {
  // 预填:取条件中的 SERVICE_TYPE / MODEL_NAME / HEADER / CLIENT_IP 作为测试输入
  testForm.serviceType = currentServiceType.value
  testForm.modelName = form.conditions.find(c => c.type === 'MODEL_NAME')?.value || ''
  testForm.clientIp = form.conditions.find(c => c.type === 'CLIENT_IP')?.value || '127.0.0.1'
  const headerConds = form.conditions.filter(c => c.type === 'HEADER')
  testForm.headers = headerConds.length
    ? headerConds.map(c => ({ key: c.field || '', value: c.value || '' }))
    : [{ key: '', value: '' }]
  testResult.value = null
  testVisible.value = true
}

const addTestHeader = () => {
  testForm.headers.push({ key: '', value: '' })
}

const removeTestHeader = (index: number) => {
  if (testForm.headers.length <= 1) return
  testForm.headers.splice(index, 1)
}

const runTest = async () => {
  if (!testForm.modelName.trim()) {
    ElMessage.warning('请输入测试模型名')
    return
  }
  testLoading.value = true
  try {
    const headers: Record<string, string> = {}
    testForm.headers.forEach(h => {
      if (h.key.trim()) headers[h.key.trim()] = h.value
    })
    const res = await validateRule({
      serviceType: testForm.serviceType,
      modelName: testForm.modelName.trim(),
      clientIp: testForm.clientIp.trim() || '127.0.0.1',
      headers
    })
    testResult.value = res.data?.data || null
  } catch (e) {
    ElMessage.error('测试失败,请检查输入')
  } finally {
    testLoading.value = false
  }
}

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
      return '重写后的模型名'
    case 'TARGET_INSTANCE':
      return '目标实例名称'
    case 'TARGET_ADAPTER':
      return '切换到的适配器'
    case 'LB_STRATEGY':
      return '负载均衡策略'
    default:
      return ''
  }
})

const addCondition = () => {
  form.conditions.push(defaultCondition())
}

const onConditionTypeChange = (cond: RuleCondition) => {
  // 切换条件类型时重置不适用字段
  if (cond.type === 'WEIGHT') {
    cond.value = ''
    cond.weight = cond.weight ?? 50
  } else if (cond.type === 'HEADER') {
    if (!cond.field) cond.field = COMMON_HEADERS[0]
  } else {
    cond.weight = undefined
  }
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

.test-header-list {
  width: 100%;
}

.test-header-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}
</style>
