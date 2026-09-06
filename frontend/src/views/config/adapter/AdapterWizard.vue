<template>
  <el-dialog
    v-model="visible"
    :title="t('adapter.wizard.title')"
    width="720px"
    :close-on-click-modal="false"
    :before-close="handleClose"
    destroy-on-close
  >
    <el-steps :active="currentStep" align-center finish-status="success" class="wizard-steps">
      <el-step :title="t('adapter.wizard.stepMode')" />
      <el-step :title="t('adapter.wizard.stepBasic')" />
      <el-step :title="t('adapter.wizard.stepAdvanced')" />
      <el-step :title="t('adapter.wizard.stepTest')" />
    </el-steps>

    <div class="wizard-content">
      <!-- Step 1: 选择方式 -->
      <div v-if="currentStep === 0" class="step-panel">
        <el-radio-group v-model="createMode" class="mode-select">
          <el-radio-button value="template">{{ t('adapter.wizard.fromTemplate') }}</el-radio-button>
          <el-radio-button value="custom">{{ t('adapter.wizard.custom') }}</el-radio-button>
        </el-radio-group>

        <template v-if="createMode === 'template'">
          <div class="template-toolbar">
            <el-input
              v-model="templateSearch"
              :placeholder="t('adapter.wizard.searchPlaceholder')"
              clearable
              size="small"
              class="template-search"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-radio-group v-model="templateCategory" size="small">
              <el-radio-button value="">{{ t('adapter.category.all') }}</el-radio-button>
              <el-radio-button value="domestic">{{ t('adapter.category.domestic') }}</el-radio-button>
              <el-radio-button value="international">{{ t('adapter.category.international') }}</el-radio-button>
              <el-radio-button value="local">{{ t('adapter.category.local') }}</el-radio-button>
            </el-radio-group>
          </div>

          <div class="template-grid">
            <AdapterTemplateCard
              v-for="tpl in filteredTemplates"
              :key="tpl.id"
              :template="tpl"
              :selected="selectedTemplateId === tpl.id"
              @select="selectTemplate(tpl)"
            />
          </div>
          <el-empty v-if="filteredTemplates.length === 0" :description="t('adapter.wizard.noTemplate')" :image-size="80" />
        </template>

        <div v-else class="custom-mode-hint">
          <el-alert
            :title="t('adapter.wizard.custom')"
            type="info"
            :closable="false"
            :description="t('adapter.wizard.customHint')"
          />
        </div>
      </div>

      <!-- Step 2: 基本配置 -->
      <div v-if="currentStep === 1" class="step-panel">
        <el-form ref="basicFormRef" :model="form" :rules="basicRules" label-width="110px">
          <el-form-item :label="t('adapter.name')" prop="name">
            <el-input v-model="form.name" :placeholder="t('adapter.wizard.namePlaceholder')" />
          </el-form-item>

          <el-form-item :label="t('adapter.type')" prop="type">
            <el-select v-model="form.type" style="width: 100%">
              <el-option :label="t('adapter.wizard.typeOpenai')" value="openai-compatible" />
              <el-option :label="t('adapter.wizard.typeOllama')" value="ollama-compatible" />
              <el-option :label="t('adapter.types.extend')" value="extend" />
            </el-select>
          </el-form-item>

          <el-form-item v-if="form.type === 'extend'" :label="t('adapter.wizard.parentAdapter')" prop="parent">
            <el-select v-model="form.parent" :placeholder="t('adapter.wizard.parentPlaceholder')" style="width: 100%">
              <el-option
                v-for="item in parentAdapters"
                :key="item.name"
                :label="item.name + (item.source === 'builtin' ? t('adapter.parentBuiltinSuffix') : t('adapter.parentConfigDrivenSuffix'))"
                :value="item.name"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="Base URL" prop="baseUrl">
            <el-input v-model="form.baseUrl" :placeholder="t('adapter.wizard.baseUrlPlaceholder')" />
          </el-form-item>

          <el-form-item label="API Key">
            <el-input
              v-model="form.apiKey"
              type="password"
              show-password
              :placeholder="t('adapter.wizard.apiKeyPlaceholder')"
            />
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 3: 高级配置 -->
      <div v-if="currentStep === 2" class="step-panel">
        <el-form label-width="110px">
          <el-form-item :label="t('adapter.capabilityConfig')">
            <div class="capability-checkboxes">
              <el-checkbox v-model="form.capabilities.chat">{{ t('adapter.capability.chat') }}</el-checkbox>
              <el-checkbox v-model="form.capabilities.embedding">{{ t('adapter.capability.embedding') }}</el-checkbox>
              <el-checkbox v-model="form.capabilities.rerank">{{ t('adapter.capability.rerank') }}</el-checkbox>
              <el-checkbox v-model="form.capabilities.tts">{{ t('adapter.capability.tts') }}</el-checkbox>
              <el-checkbox v-model="form.capabilities.stt">{{ t('adapter.capability.stt') }}</el-checkbox>
              <el-checkbox v-model="form.capabilities.imgGen">{{ t('adapter.capability.imgGen') }}</el-checkbox>
              <el-checkbox v-model="form.capabilities.imgEdit">{{ t('adapter.capability.imgEdit') }}</el-checkbox>
              <el-checkbox v-model="form.capabilities.streaming">{{ t('adapter.capability.streaming') }}</el-checkbox>
            </div>
          </el-form-item>

          <el-form-item :label="t('adapter.wizard.authHeader')">
            <el-input v-model="form.auth.headerName" placeholder="Authorization" />
          </el-form-item>

          <el-form-item :label="t('adapter.wizard.headerPrefix')">
            <el-input v-model="form.auth.headerPrefix" placeholder="Bearer " />
          </el-form-item>

          <el-form-item :label="t('adapter.wizard.extraHeaders')">
            <div class="header-list">
              <div v-for="(h, idx) in form.additionalHeaders" :key="idx" class="header-row">
                <el-input v-model="h.key" :placeholder="t('adapter.wizard.headerNamePlaceholder')" size="small" />
                <el-input v-model="h.value" :placeholder="t('adapter.wizard.valuePlaceholder')" size="small" />
                <el-button type="danger" link size="small" @click="removeHeader(idx)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <el-button type="primary" link size="small" @click="addHeader">
                <el-icon><Plus /></el-icon>
                {{ t('adapter.wizard.addHeader') }}
              </el-button>
            </div>
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 4: 测试连接 -->
      <div v-if="currentStep === 3" class="step-panel">
        <AdapterTestPanel ref="testPanelRef" :show-api-key="false" />
        <div class="skip-hint">
          <el-alert
            :title="t('adapter.wizard.testSkipHint')"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>
      </div>
    </div>

    <template #footer>
      <div class="wizard-footer">
        <el-button v-if="currentStep > 0" @click="currentStep--">{{ t('adapter.wizard.previous') }}</el-button>
        <el-button v-if="currentStep < 3" type="primary" @click="handleNext">{{ t('adapter.wizard.next') }}</el-button>
        <el-button v-else type="primary" :loading="saving" @click="handleFinish">
          {{ t('adapter.save') }}
        </el-button>
        <el-button @click="handleClose">{{ t('adapter.cancel') }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Delete, Plus } from '@element-plus/icons-vue'
import AdapterTemplateCard from './AdapterTemplateCard.vue'
import AdapterTestPanel from './AdapterTestPanel.vue'
import {
  getAdapterTemplates,
  getParentAdapterList,
  createAdapter,
  type AdapterTemplate,
  type AdapterCapabilities,
  type ParentAdapterInfo
} from '@/api/adapter'

const visible = defineModel<boolean>({ default: false })

const emit = defineEmits<{
  (e: 'created'): void
}>()

const { t } = useI18n()

const currentStep = ref(0)
const createMode = ref<'template' | 'custom'>('template')
const templateSearch = ref('')
const templateCategory = ref('')
const selectedTemplateId = ref('')
const templates = ref<AdapterTemplate[]>([])
const parentAdapters = ref<ParentAdapterInfo[]>([])
const saving = ref(false)
const basicFormRef = ref<FormInstance>()
const testPanelRef = ref<InstanceType<typeof AdapterTestPanel>>()

const form = reactive({
  name: '',
  type: 'openai-compatible',
  parent: '',
  baseUrl: '',
  apiKey: '',
  capabilities: {
    chat: true,
    embedding: false,
    rerank: false,
    tts: false,
    stt: false,
    imgGen: false,
    imgEdit: false,
    streaming: true
  } as AdapterCapabilities,
  auth: {
    headerName: 'Authorization',
    headerPrefix: 'Bearer '
  },
  additionalHeaders: [] as { key: string; value: string }[]
})

// v2.10.3: computed 化，语言切换后校验文案即时更新
const basicRules = computed<FormRules>(() => ({
  name: [
    { required: true, message: t('adapter.wizard.validation.nameRequired'), trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: t('adapter.wizard.validation.namePattern'), trigger: 'blur' }
  ],
  type: [{ required: true, message: t('adapter.wizard.validation.typeRequired'), trigger: 'change' }],
  baseUrl: [
    { required: true, message: t('adapter.wizard.validation.baseUrlRequired'), trigger: 'blur' },
    {
      pattern: /^https?:\/\/.+/,
      message: t('adapter.wizard.validation.baseUrlInvalid'),
      trigger: 'blur'
    }
  ]
}))

const filteredTemplates = computed(() => {
  return templates.value.filter((tpl) => {
    const matchCategory = !templateCategory.value || tpl.category === templateCategory.value
    const search = templateSearch.value.trim().toLowerCase()
    const matchSearch = !search
      || tpl.name.toLowerCase().includes(search)
      || tpl.id.toLowerCase().includes(search)
      || (tpl.description || '').toLowerCase().includes(search)
    return matchCategory && matchSearch
  })
})

const selectTemplate = (tpl: AdapterTemplate) => {
  selectedTemplateId.value = tpl.id
  form.type = tpl.type
  form.baseUrl = tpl.defaultBaseUrl
  form.capabilities = {
    chat: tpl.capabilities.chat,
    embedding: tpl.capabilities.embedding,
    rerank: tpl.capabilities.rerank,
    tts: tpl.capabilities.tts,
    stt: tpl.capabilities.stt,
    imgGen: tpl.capabilities.imgGen,
    imgEdit: tpl.capabilities.imgEdit,
    streaming: tpl.capabilities.streaming
  }
  if (tpl.auth) {
    form.auth.headerName = tpl.auth.headerName
    form.auth.headerPrefix = tpl.auth.headerPrefix
  }
}

const addHeader = () => {
  form.additionalHeaders.push({ key: '', value: '' })
}

const removeHeader = (idx: number) => {
  form.additionalHeaders.splice(idx, 1)
}

const handleNext = async () => {
  if (currentStep.value === 0 && createMode.value === 'template' && !selectedTemplateId.value) {
    ElMessage.warning(t('adapter.wizard.selectTemplateWarn'))
    return
  }
  if (currentStep.value === 1) {
    if (!basicFormRef.value) return
    try {
      await basicFormRef.value.validate()
    } catch {
      return
    }
  }
  currentStep.value++
}

const handleFinish = async () => {
  saving.value = true
  try {
    const headers: Record<string, string> = {}
    form.additionalHeaders.forEach((h) => {
      if (h.key.trim()) headers[h.key.trim()] = h.value.trim()
    })

    const data = {
      name: form.name,
      type: form.type,
      parent: form.type === 'extend' ? form.parent : undefined,
      capabilities: { ...form.capabilities },
      auth: { ...form.auth },
      additionalHeaders: Object.keys(headers).length > 0 ? headers : undefined
    }

    const res = await createAdapter(data)
    if (res.data?.success) {
      ElMessage.success(t('adapter.wizard.createSuccess'))
      visible.value = false
      emit('created')
    } else {
      ElMessage.error(res.data?.message || t('adapter.wizard.createFailed'))
    }
  } catch (e: any) {
    ElMessage.error(t('adapter.wizard.createFailedDetail', { error: e.message || '' }))
  } finally {
    saving.value = false
  }
}

const handleClose = () => {
  visible.value = false
}

watch(visible, (val) => {
  if (val) {
    currentStep.value = 0
    createMode.value = 'template'
    selectedTemplateId.value = ''
    templateSearch.value = ''
    templateCategory.value = ''
    Object.assign(form, {
      name: '',
      type: 'openai-compatible',
      parent: '',
      baseUrl: '',
      apiKey: '',
      capabilities: {
        chat: true,
        embedding: false,
        rerank: false,
        tts: false,
        stt: false,
        imgGen: false,
        imgEdit: false,
        streaming: true
      },
      auth: {
        headerName: 'Authorization',
        headerPrefix: 'Bearer '
      },
      additionalHeaders: []
    })
    testPanelRef.value?.reset()
  }
})

onMounted(async () => {
  try {
    const res = await getAdapterTemplates()
    if (res.data?.success) {
      templates.value = res.data.data || []
    }
  } catch (e: any) {
    console.error('获取模板失败:', e)
  }
  try {
    const res = await getParentAdapterList()
    if (res.data?.success) {
      parentAdapters.value = res.data.data || []
    }
  } catch (e: any) {
    console.error('获取父 adapter 失败:', e)
  }
})
</script>

<style scoped>
.wizard-steps {
  margin-bottom: 24px;
}

.wizard-content {
  min-height: 320px;
}

.step-panel {
  padding: 8px 0;
}

.mode-select {
  margin-bottom: 16px;
}

.template-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.template-search {
  width: 200px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  max-height: 320px;
  overflow-y: auto;
  padding: 4px;
}

.custom-mode-hint {
  margin-top: 16px;
}

.capability-checkboxes {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.header-list {
  width: 100%;
}

.header-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.skip-hint {
  margin-top: 16px;
}

.wizard-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
