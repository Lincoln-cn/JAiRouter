<template>
  <el-drawer
    v-model="visible"
    class="rule-form-drawer"
    direction="rtl"
    :size="drawerSize"
    :title="drawerTitle"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
      <el-form-item :label="t('rule.name')" prop="name">
        <el-input v-model="form.name" :placeholder="t('rule.form.namePlaceholder')" />
      </el-form-item>

      <el-form-item :label="t('rule.priority')" prop="priority">
        <el-input-number v-model="form.priority" :min="0" :max="9999" />
        <span class="form-tip">{{ t('rule.form.priorityTip') }}</span>
      </el-form-item>

      <el-divider content-position="left">{{ t('rule.form.conditionsSection') }}</el-divider>

      <div v-for="(cond, index) in form.conditions" :key="index" class="condition-row">
        <el-select v-model="cond.type" style="width: 130px" :placeholder="t('rule.form.typePlaceholder')" @change="onConditionTypeChange(cond)">
          <el-option :label="t('rule.conditionType.MODEL_NAME')" value="MODEL_NAME" />
          <el-option :label="t('rule.conditionType.SERVICE_TYPE')" value="SERVICE_TYPE" />
          <el-option :label="t('rule.conditionType.HEADER')" value="HEADER" />
          <el-option :label="t('rule.conditionType.CLIENT_IP')" value="CLIENT_IP" />
          <el-option :label="t('rule.conditionType.WEIGHT')" value="WEIGHT" />
        </el-select>

        <el-select
          v-if="cond.type === 'HEADER'"
          v-model="cond.field"
          :placeholder="t('rule.form.headerNamePlaceholder')"
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
          :placeholder="t('rule.form.modelNamePlaceholder')"
        >
          <el-option v-for="m in modelNames" :key="m" :label="m" :value="m" />
        </el-select>

        <el-input
          v-else-if="cond.type === 'HEADER' || cond.type === 'CLIENT_IP'"
          v-model="cond.value"
          :placeholder="cond.type === 'HEADER' ? t('rule.form.headerValuePlaceholder') : t('rule.form.ipCidrPlaceholder')"
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

      <el-button type="primary" plain :icon="Plus" @click="addCondition">{{ t('rule.form.addCondition') }}</el-button>

      <el-divider content-position="left">{{ t('rule.form.actionsSection') }}</el-divider>

      <el-form-item :label="t('rule.form.actionTypeLabel')" prop="actionType">
        <el-radio-group v-model="form.actionType">
          <el-radio value="TARGET_MODEL">{{ t('rule.form.actionOptions.TARGET_MODEL') }}</el-radio>
          <el-radio value="TARGET_INSTANCE">{{ t('rule.form.actionOptions.TARGET_INSTANCE') }}</el-radio>
          <el-radio value="TARGET_ADAPTER">{{ t('rule.form.actionOptions.TARGET_ADAPTER') }}</el-radio>
          <el-radio value="LB_STRATEGY">{{ t('rule.form.actionOptions.LB_STRATEGY') }}</el-radio>
          <el-radio value="RATE_LIMIT">{{ t('rule.form.actionOptions.RATE_LIMIT') }}</el-radio>
          <el-radio value="TARGET_TAGS">{{ t('rule.form.actionOptions.TARGET_TAGS') }}</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="form.actionType === 'RATE_LIMIT'" :label="t('rule.rateLimit.capacityLabel')">
        <el-input-number v-model="form.rateLimit.capacity" :min="1" :max="1000000" style="width: 200px" />
        <span class="form-tip">{{ t('rule.rateLimit.capacityTip') }}</span>
      </el-form-item>

      <el-form-item v-if="form.actionType === 'RATE_LIMIT'" :label="t('rule.rateLimit.rateLabel')">
        <el-input-number v-model="form.rateLimit.rate" :min="1" :max="1000000" style="width: 200px" />
        <span class="form-tip">{{ t('rule.rateLimit.rateTip') }}</span>
      </el-form-item>

      <el-form-item v-if="form.actionType === 'RATE_LIMIT'" :label="t('rule.rateLimit.algorithmLabel')">
        <el-select v-model="form.rateLimit.algorithm" style="width: 200px">
          <el-option :label="t('rule.rateLimit.algorithms.tokenBucket')" value="token-bucket" />
          <el-option :label="t('rule.rateLimit.algorithms.leakyBucket')" value="leaky-bucket" />
          <el-option :label="t('rule.rateLimit.algorithms.slidingWindow')" value="sliding-window" />
        </el-select>
      </el-form-item>

      <el-form-item v-if="form.actionType !== 'RATE_LIMIT' && form.actionType !== 'TARGET_TAGS'" :label="t('rule.form.actionTargetLabel')" prop="actionTarget">
        <el-select
          v-if="form.actionType === 'TARGET_INSTANCE'"
          v-model="form.actionTarget"
          :placeholder="t('rule.form.instancePlaceholder')"
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
          :placeholder="t('rule.form.adapterPlaceholder')"
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
          :placeholder="t('rule.form.lbStrategyPlaceholder')"
          style="width: 300px"
        >
          <el-option v-for="s in LB_STRATEGIES" :key="s" :label="s" :value="s" />
        </el-select>

        <el-select
          v-else
          v-model="form.actionTarget"
          :placeholder="t('rule.form.modelNamePlaceholder')"
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

      <el-form-item v-if="form.actionType === 'TARGET_TAGS'" :label="t('rule.form.tagsLabel')">
        <div class="test-header-list">
          <div v-for="(tag, idx) in form.actionTags" :key="idx" class="test-header-row">
            <el-input v-model="tag.key" :placeholder="t('rule.form.tagKeyPlaceholder')" style="width: 140px" />
            <el-input v-model="tag.value" :placeholder="t('rule.form.valuePlaceholder')" style="width: 140px; margin-left: 8px" />
            <el-button type="danger" :icon="Delete" circle size="small" style="margin-left: 8px"
              @click="removeActionTag(idx)" />
          </div>
          <el-button type="primary" plain size="small" :icon="Plus" @click="addActionTag">{{ t('rule.form.addTag') }}</el-button>
        </div>
        <span class="form-tip">{{ t('rule.form.tagsTip') }}</span>
      </el-form-item>

      <el-form-item>
        <el-button type="success" plain :icon="MagicStick" @click="toggleTestPanel">{{ t('rule.form.simulateButton') }}</el-button>
        <span class="form-tip">{{ t('rule.form.simulateTip') }}</span>
      </el-form-item>
    </el-form>

    <!-- 规则模拟测试：抽屉内的折叠区块（不再弹第二个窗，避免叠层与关闭顺序问题） -->
    <section ref="testPanelRef" class="test-panel">
      <div class="test-panel__head">
        <span class="test-panel__title">{{ t('rule.test.title') }}</span>
        <el-button link type="primary" :icon="testPanelOpen ? ArrowUp : ArrowDown" @click="toggleTestPanel">
          {{ testPanelOpen ? t('rule.form.collapseTest') : t('rule.form.expandTest') }}
        </el-button>
      </div>

      <div v-show="testPanelOpen" class="test-panel__body">
        <el-form label-width="90px">
          <el-form-item :label="t('rule.test.serviceType')">
            <el-select v-model="testForm.serviceType" style="width: 200px">
              <el-option v-for="s in SERVICE_TYPES" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('rule.test.modelName')">
            <el-input v-model="testForm.modelName" :placeholder="t('rule.test.modelNamePlaceholder')" />
          </el-form-item>
          <el-form-item :label="t('rule.test.clientIp')">
            <el-input v-model="testForm.clientIp" :placeholder="t('rule.test.clientIpPlaceholder')" />
          </el-form-item>
          <el-form-item :label="t('rule.test.headers')">
            <div class="test-header-list">
              <div v-for="(h, idx) in testForm.headers" :key="idx" class="test-header-row">
                <el-input v-model="h.key" :placeholder="t('rule.form.headerNamePlaceholder')" style="width: 160px" />
                <el-input v-model="h.value" :placeholder="t('rule.form.valuePlaceholder')" style="width: 160px; margin-left: 8px" />
                <el-button type="danger" :icon="Delete" circle size="small" style="margin-left: 8px"
                  @click="removeTestHeader(idx)" />
              </div>
              <el-button type="primary" plain size="small" :icon="Plus" @click="addTestHeader">{{ t('rule.test.addHeader') }}</el-button>
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
              {{ t('rule.test.hitRule') }} <b>{{ testResult.ruleName }}</b> {{ t('rule.test.hitPriority', { priority: testResult.priority }) }}<br />
              {{ t('rule.test.actionLabel') }} {{ testResult.action.type }}
              <template v-if="testResult.action.target"> → {{ testResult.action.target }}</template>
            </div>
          </template>
        </el-alert>

        <div class="test-panel__actions">
          <el-button type="primary" :loading="testLoading" :disabled="testLoading" @click="runTest">
            {{ t('rule.runTest') }}
          </el-button>
        </div>
      </div>
    </section>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="visible = false">{{ t('rule.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" :disabled="saving" @click="handleSave">{{ t('rule.save') }}</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowDown, ArrowUp, Delete, Plus, MagicStick } from '@element-plus/icons-vue'
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
import { useViewport } from '@/composables/useViewport'

const props = defineProps<{
  rule: RuleDefinition | null
}>()

const emit = defineEmits<{
  saved: []
}>()

/**
 * 新建 / 编辑共用一个抽屉实例（规范 §P3「一个页面只允许存在一个编辑抽屉实例」）：
 * `mode` 由 `rule.id` 推导，父页面只切换 `editingRule` 与 `v-model`。
 */
const visible = defineModel<boolean>({ required: true })

const { t } = useI18n()
const { isNarrow } = useViewport()

const formRef = ref<FormInstance>()
const saving = ref(false)

const isEdit = computed(() => !!props.rule?.id)
const mode = computed<'create' | 'edit'>(() => (isEdit.value ? 'edit' : 'create'))

/** 字段多（条件 / 动作 / 限流 / 标签 多分支）⇒ 720；窄屏占满 */
const drawerSize = computed(() => (isNarrow.value ? '100%' : '720px'))

const drawerTitle = computed(() => {
  if (mode.value === 'create') return t('rule.createTitle')
  const name = props.rule?.name
  return name ? t('rule.editTitle', { name }) : t('rule.editRule')
})

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
  } catch {
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
  } catch {
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
  } catch {
    // 失败保留现有选项
  }
}

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
  actionTags: Array<{ key: string; value: string }>
  rateLimit: {
    capacity: number
    rate: number
    algorithm: string
    scope: string
    warmUpPeriod?: number
  }
}>({
  name: '',
  priority: 10,
  conditions: [defaultCondition()],
  actionType: 'TARGET_MODEL',
  actionTarget: '',
  actionTags: [],
  rateLimit: {
    capacity: 100,
    rate: 10,
    algorithm: 'token-bucket',
    scope: 'rule',
    warmUpPeriod: 600
  }
})

// 条件中的 SERVICE_TYPE 值(缺省 chat),变化时联动刷新模型/实例下拉
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

// ==================== 模拟测试(dry-run) ====================
/** 测试区在抽屉内折叠，不再弹第二个窗（规范 §P3 自查：页面内 el-dialog 计数 0） */
const testPanelOpen = ref(false)
const testPanelRef = ref<HTMLElement | null>(null)
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
  testPanelOpen.value = true
  // 展开后把抽屉滚动到测试区（区块在表单下方，抽屉 body 独立滚动）
  nextTick(() => {
    testPanelRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

const toggleTestPanel = () => {
  if (testPanelOpen.value) {
    testPanelOpen.value = false
    return
  }
  openTestPanel()
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
    ElMessage.warning(t('rule.test.modelNameRequired'))
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
  } catch {
    ElMessage.error(t('rule.test.runFailed'))
  } finally {
    testLoading.value = false
  }
}

// v2.9.7: TARGET_TAGS 标签编辑
const addActionTag = () => {
  form.actionTags.push({ key: '', value: '' })
}

const removeActionTag = (index: number) => {
  form.actionTags.splice(index, 1)
}

const buildActionTags = (): Record<string, string> => {
  const tags: Record<string, string> = {}
  form.actionTags.forEach(tag => {
    if (tag.key.trim() && tag.value.trim()) {
      tags[tag.key.trim()] = tag.value.trim()
    }
  })
  return tags
}

const formRules = computed<FormRules>(() => ({
  name: [{ required: true, message: t('rule.validation.nameRequired'), trigger: 'blur' }],
  actionType: [{ required: true, message: t('rule.validation.actionTypeRequired'), trigger: 'change' }],
  actionTarget: [{ required: true, message: t('rule.validation.actionTargetRequired'), trigger: 'blur' }]
}))

const operatorLabel = (value: string) => t(`rule.operator.${value}`)

const operatorsFor = (type: RuleCondition['type']) => {
  if (type === 'CLIENT_IP') {
    return [
      { value: 'EQUALS', label: operatorLabel('EQUALS') },
      { value: 'CIDR_MATCH', label: operatorLabel('CIDR_MATCH') },
      { value: 'STARTS_WITH', label: operatorLabel('STARTS_WITH') }
    ]
  }
  return [
    { value: 'EQUALS', label: operatorLabel('EQUALS') },
    { value: 'CONTAINS', label: operatorLabel('CONTAINS') },
    { value: 'STARTS_WITH', label: operatorLabel('STARTS_WITH') },
    { value: 'REGEX', label: operatorLabel('REGEX') }
  ]
}

const actionTargetTip = computed(() => {
  switch (form.actionType) {
    case 'TARGET_MODEL':
      return t('rule.form.actionTargetTips.TARGET_MODEL')
    case 'TARGET_INSTANCE':
      return t('rule.form.actionTargetTips.TARGET_INSTANCE')
    case 'TARGET_ADAPTER':
      return t('rule.form.actionTargetTips.TARGET_ADAPTER')
    case 'LB_STRATEGY':
      return t('rule.form.actionTargetTips.LB_STRATEGY')
    case 'RATE_LIMIT':
      return t('rule.form.actionTargetTips.RATE_LIMIT')
    case 'TARGET_TAGS':
      return t('rule.form.actionTargetTips.TARGET_TAGS')
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
    ElMessage.warning(t('rule.validation.atLeastOneCondition'))
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
    if (form.actionType === 'RATE_LIMIT') {
      form.rateLimit = {
        capacity: rule.action?.capacity || 100,
        rate: rule.action?.rate || 10,
        algorithm: rule.action?.algorithm || 'token-bucket',
        scope: rule.action?.scope || 'rule',
        warmUpPeriod: rule.action?.warmUpPeriod || 600
      }
    }
    // v2.9.7: TARGET_TAGS 动作参数回填
    form.actionTags = Object.entries(rule.action?.tags || {}).map(([key, value]) => ({ key, value }))
  } else {
    form.name = ''
    form.priority = 10
    form.conditions = [defaultCondition()]
    form.actionType = 'TARGET_MODEL'
    form.actionTarget = ''
    form.actionTags = []
    form.rateLimit = {
      capacity: 100,
      rate: 10,
      algorithm: 'token-bucket',
      scope: 'rule',
      warmUpPeriod: 600
    }
  }
}

watch(visible, val => {
  if (val) {
    loadRule(props.rule)
    // 每次打开都收起测试区，避免上一次的测试输入/结果串到新表单
    testPanelOpen.value = false
    testResult.value = null
  }
})

const handleSave = async () => {
  // validate() 校验失败是 reject：捕获后直接返回，不要冒泡成未处理的 Promise 拒绝
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (form.actionType === 'RATE_LIMIT' && (form.rateLimit.capacity <= 0 || form.rateLimit.rate <= 0)) {
    ElMessage.error(t('rule.rateLimit.positiveRequired'))
    return
  }
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
      lbStrategy: form.actionType === 'LB_STRATEGY' ? form.actionTarget : undefined,
      capacity: form.actionType === 'RATE_LIMIT' ? form.rateLimit.capacity : undefined,
      rate: form.actionType === 'RATE_LIMIT' ? form.rateLimit.rate : undefined,
      algorithm: form.actionType === 'RATE_LIMIT' ? form.rateLimit.algorithm : undefined,
      scope: form.actionType === 'RATE_LIMIT' ? form.rateLimit.scope : undefined,
      warmUpPeriod: form.actionType === 'RATE_LIMIT' ? form.rateLimit.warmUpPeriod : undefined,
      tags: form.actionType === 'TARGET_TAGS' ? buildActionTags() : undefined
    }
  }
  saving.value = true
  try {
    if (isEdit.value && props.rule?.id) {
      await updateRule(props.rule.id, payload)
      ElMessage.success(t('rule.ruleUpdated'))
    } else {
      await createRule(payload)
      ElMessage.success(t('rule.ruleCreated'))
    }
    visible.value = false
    emit('saved')
  } catch {
    ElMessage.error(t('rule.saveFailed'))
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
  color: var(--ja-text-secondary);
}

.test-header-list {
  width: 100%;
}

.test-header-row {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

/* 模拟测试折叠区：抽屉内的独立区块，视觉上与表单分开 */
.test-panel {
  margin-top: 8px;
  border: 1px solid var(--ja-border-lighter);
  border-radius: 8px;
  background: var(--ja-primary-light-9, #fbfdff);
  padding: 8px 12px 12px;
}

.test-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.test-panel__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ja-text-primary);
}

.test-panel__body {
  margin-top: 10px;
}

.test-panel__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
