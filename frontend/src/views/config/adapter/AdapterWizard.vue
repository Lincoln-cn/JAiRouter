<template>
  <el-dialog
    v-model="visible"
    title="新增 Adapter"
    width="720px"
    :close-on-click-modal="false"
    :before-close="handleClose"
    destroy-on-close
  >
    <el-steps :active="currentStep" align-center finish-status="success" class="wizard-steps">
      <el-step title="选择方式" />
      <el-step title="基本配置" />
      <el-step title="高级配置" />
      <el-step title="测试连接" />
    </el-steps>

    <div class="wizard-content">
      <!-- Step 1: 选择方式 -->
      <div v-if="currentStep === 0" class="step-panel">
        <el-radio-group v-model="createMode" class="mode-select">
          <el-radio-button value="template">从模板创建</el-radio-button>
          <el-radio-button value="custom">自定义创建</el-radio-button>
        </el-radio-group>

        <template v-if="createMode === 'template'">
          <div class="template-toolbar">
            <el-input
              v-model="templateSearch"
              placeholder="搜索模板..."
              clearable
              size="small"
              class="template-search"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-radio-group v-model="templateCategory" size="small">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="domestic">国内</el-radio-button>
              <el-radio-button value="international">国际</el-radio-button>
              <el-radio-button value="local">本地</el-radio-button>
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
          <el-empty v-if="filteredTemplates.length === 0" description="没有匹配的模板" :image-size="80" />
        </template>

        <div v-else class="custom-mode-hint">
          <el-alert
            title="自定义创建"
            type="info"
            :closable="false"
            description="手动配置适配器参数，适用于不在模板列表中的服务商"
          />
        </div>
      </div>

      <!-- Step 2: 基本配置 -->
      <div v-if="currentStep === 1" class="step-panel">
        <el-form ref="basicFormRef" :model="form" :rules="basicRules" label-width="110px">
          <el-form-item label="名称" prop="name">
            <el-input v-model="form.name" placeholder="适配器名称，如 my-deepseek" />
          </el-form-item>

          <el-form-item label="类型" prop="type">
            <el-select v-model="form.type" style="width: 100%">
              <el-option label="OpenAI 兼容" value="openai-compatible" />
              <el-option label="Ollama 兼容" value="ollama-compatible" />
              <el-option label="继承扩展" value="extend" />
            </el-select>
          </el-form-item>

          <el-form-item v-if="form.type === 'extend'" label="父 Adapter" prop="parent">
            <el-select v-model="form.parent" placeholder="选择要继承的父 adapter" style="width: 100%">
              <el-option
                v-for="item in parentAdapters"
                :key="item.name"
                :label="item.name + (item.source === 'builtin' ? ' (内置)' : ' (配置驱动)')"
                :value="item.name"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="Base URL" prop="baseUrl">
            <el-input v-model="form.baseUrl" placeholder="API 基础地址，如 https://api.deepseek.com" />
          </el-form-item>

          <el-form-item label="API Key">
            <el-input
              v-model="form.apiKey"
              type="password"
              show-password
              placeholder="服务商 API Key"
            />
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 3: 高级配置 -->
      <div v-if="currentStep === 2" class="step-panel">
        <el-form label-width="110px">
          <el-form-item label="能力配置">
            <div class="capability-checkboxes">
              <el-checkbox v-model="form.capabilities.chat">Chat</el-checkbox>
              <el-checkbox v-model="form.capabilities.embedding">Embedding</el-checkbox>
              <el-checkbox v-model="form.capabilities.rerank">Rerank</el-checkbox>
              <el-checkbox v-model="form.capabilities.tts">TTS</el-checkbox>
              <el-checkbox v-model="form.capabilities.stt">STT</el-checkbox>
              <el-checkbox v-model="form.capabilities.imgGen">图像生成</el-checkbox>
              <el-checkbox v-model="form.capabilities.imgEdit">图像编辑</el-checkbox>
              <el-checkbox v-model="form.capabilities.streaming">流式</el-checkbox>
            </div>
          </el-form-item>

          <el-form-item label="认证 Header">
            <el-input v-model="form.auth.headerName" placeholder="Authorization" />
          </el-form-item>

          <el-form-item label="Header 前缀">
            <el-input v-model="form.auth.headerPrefix" placeholder="Bearer " />
          </el-form-item>

          <el-form-item label="额外请求头">
            <div class="header-list">
              <div v-for="(h, idx) in form.additionalHeaders" :key="idx" class="header-row">
                <el-input v-model="h.key" placeholder="Header 名" size="small" />
                <el-input v-model="h.value" placeholder="值" size="small" />
                <el-button type="danger" link size="small" @click="removeHeader(idx)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <el-button type="primary" link size="small" @click="addHeader">
                <el-icon><Plus /></el-icon>
                添加请求头
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
            title="可以跳过测试直接保存"
            type="warning"
            :closable="false"
            show-icon
          />
        </div>
      </div>
    </div>

    <template #footer>
      <div class="wizard-footer">
        <el-button v-if="currentStep > 0" @click="currentStep--">上一步</el-button>
        <el-button v-if="currentStep < 3" type="primary" @click="handleNext">下一步</el-button>
        <el-button v-else type="primary" :loading="saving" @click="handleFinish">
          保存
        </el-button>
        <el-button @click="handleClose">取消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
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

const basicRules: FormRules = {
  name: [
    { required: true, message: '请输入适配器名称', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: '只能包含字母、数字、下划线和横线', trigger: 'blur' }
  ],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  baseUrl: [
    { required: true, message: '请输入 Base URL', trigger: 'blur' },
    {
      pattern: /^https?:\/\/.+/,
      message: '请输入有效的 URL（以 http:// 或 https:// 开头）',
      trigger: 'blur'
    }
  ]
}

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
    ElMessage.warning('请先选择一个模板，或切换到自定义创建')
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
      ElMessage.success('适配器创建成功')
      visible.value = false
      emit('created')
    } else {
      ElMessage.error(res.data?.message || '创建失败')
    }
  } catch (e: any) {
    ElMessage.error('创建失败: ' + (e.message || ''))
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
