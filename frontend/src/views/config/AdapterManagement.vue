<template>
  <PageSkeleton :title="t('adapter.management')">
    <template #actions>
      <el-button type="primary" @click="handleCreate" size="medium">
        <el-icon><Plus /></el-icon>
        {{ t('adapter.newAdapter') }}
      </el-button>
    </template>

    <template #stats>
      <OnboardingSteps :current-step="1" />
    </template>

    <el-table
      :data="adapterList"
      v-loading="loading"
      stripe
      border
      style="width: 100%"
    >
      <el-table-column prop="name" :label="t('adapter.name')" min-width="150" />
      <el-table-column prop="type" :label="t('adapter.type')" min-width="120">
        <template #default="{ row }">
          <el-tag :type="row.source === 'builtin' ? 'info' : 'success'" size="small">
            {{ row.source === 'builtin' ? t('adapter.builtin') : t('adapter.configDriven') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('adapter.capability')" min-width="280">
        <template #default="{ row }">
          <div class="capability-tags">
            <el-tag v-if="row.capabilities?.chat" size="small" type="primary">{{ t('adapter.capability.chat') }}</el-tag>
            <el-tag v-if="row.capabilities?.embedding" size="small" type="success">{{ t('adapter.capability.embedding') }}</el-tag>
            <el-tag v-if="row.capabilities?.rerank" size="small" type="warning">{{ t('adapter.capability.rerank') }}</el-tag>
            <el-tag v-if="row.capabilities?.tts" size="small" type="danger">{{ t('adapter.capability.tts') }}</el-tag>
            <el-tag v-if="row.capabilities?.stt" size="small" type="danger">{{ t('adapter.capability.stt') }}</el-tag>
            <el-tag v-if="row.capabilities?.imgGen" size="small" type="info">{{ t('adapter.capability.imgGen') }}</el-tag>
            <el-tag v-if="row.capabilities?.imgEdit" size="small" type="info">{{ t('adapter.capability.imgEdit') }}</el-tag>
            <el-tag v-if="row.capabilities?.streaming" size="small" type="primary">{{ t('adapter.capability.streaming') }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column :label="t('adapter.action')" width="190" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.source !== 'builtin'"
            type="primary"
            link
            size="small"
            @click="handleEdit(row)"
          >
            {{ t('adapter.edit') }}
          </el-button>
          <el-button
            type="success"
            link
            size="small"
            @click="handleTest(row)"
          >
            {{ t('adapter.test') }}
          </el-button>
          <el-button
            v-if="row.source !== 'builtin'"
            type="danger"
            link
            size="small"
            @click="handleDelete(row)"
          >
            {{ t('adapter.delete') }}
          </el-button>
          <el-button
            v-if="row.source === 'builtin'"
            type="info"
            link
            size="small"
            @click="handleView(row)"
          >
            {{ t('adapter.view') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </PageSkeleton>

    <!-- 测试弹窗 -->
    <el-dialog
      v-model="testDialogVisible"
      :title="t('adapter.testDialogTitle', { name: testingName })"
      width="560px"
      :close-on-click-modal="false"
    >
      <AdapterTestPanel :adapter-name="testingName" />
    </el-dialog>

    <!-- 新增向导 -->
    <AdapterWizard v-model="wizardVisible" @created="fetchAdapters" />

    <!-- 创建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? t('adapter.editAdapter') : t('adapter.newAdapter')"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
      >
        <el-form-item :label="t('adapter.name')" prop="name">
          <el-input
            v-model="form.name"
            :disabled="isEditing"
            :placeholder="t('adapter.namePlaceholder')"
          />
        </el-form-item>

        <el-form-item :label="t('adapter.type')" prop="type">
          <el-select v-model="form.type" style="width: 100%" @change="handleTypeChange">
            <el-option :label="t('adapter.types.openaiCompatible')" value="openai-compatible" />
            <el-option :label="t('adapter.types.ollamaCompatible')" value="ollama-compatible" />
            <el-option :label="t('adapter.types.extend')" value="extend" />
          </el-select>
          <div class="type-hint" v-if="form.type === 'ollama-compatible'">
            {{ t('adapter.typeHintOllama') }}
          </div>
          <div class="type-hint" v-else-if="form.type === 'extend'">
            {{ t('adapter.typeHintExtend') }}
          </div>
          <div class="type-hint" v-else>
            {{ t('adapter.typeHintOpenai') }}
          </div>
        </el-form-item>

        <el-form-item v-if="form.type === 'extend'" :label="t('adapter.parentAdapter')" prop="parent">
          <el-select v-model="form.parent" :placeholder="t('adapter.parentPlaceholder')" style="width: 100%">
            <el-option
              v-for="item in parentAdapters"
              :key="item.name"
              :label="item.name + (item.source === 'builtin' ? t('adapter.parentBuiltinSuffix') : t('adapter.parentConfigDrivenSuffix'))"
              :value="item.name"
            />
          </el-select>
          <div class="type-hint">{{ t('adapter.parentHint') }}</div>
        </el-form-item>

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

        <el-form-item :label="t('adapter.headerName')">
          <el-input v-model="form.auth.headerName" placeholder="Authorization" />
        </el-form-item>

        <el-form-item :label="t('adapter.headerPrefix')">
          <el-input v-model="form.auth.headerPrefix" placeholder="Bearer " />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('adapter.cancel') }}</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">{{ t('adapter.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 查看详情弹窗 -->
    <el-dialog
      v-model="viewDialogVisible"
      :title="t('adapter.detailTitle')"
      width="500px"
    >
      <el-descriptions :column="1" border>
        <el-descriptions-item :label="t('adapter.name')">{{ viewData.name }}</el-descriptions-item>
        <el-descriptions-item :label="t('adapter.type')">{{ viewData.type }}</el-descriptions-item>
        <el-descriptions-item :label="t('adapter.source')">
          <el-tag :type="viewData.source === 'builtin' ? 'info' : 'success'" size="small">
            {{ viewData.source === 'builtin' ? t('adapter.builtin') : t('adapter.configDriven') }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getAdapterList,
  getParentAdapterList,
  createAdapter,
  updateAdapter,
  deleteAdapter,
  type AdapterInfo,
  type AdapterCapabilities,
  type ParentAdapterInfo
} from '@/api/adapter'
import AdapterWizard from './adapter/AdapterWizard.vue'
import AdapterTestPanel from './adapter/AdapterTestPanel.vue'
import OnboardingSteps from './adapter/OnboardingSteps.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'

const { t } = useI18n()

const loading = ref(false)
const submitting = ref(false)
const adapterList = ref<AdapterInfo[]>([])
const parentAdapters = ref<ParentAdapterInfo[]>([])
const dialogVisible = ref(false)
const viewDialogVisible = ref(false)
const isEditing = ref(false)
const editingName = ref('')
const formRef = ref<FormInstance>()
const viewData = ref<AdapterInfo>({
  name: '',
  source: 'builtin',
  type: '',
  capabilities: {
    chat: false,
    embedding: false,
    rerank: false,
    tts: false,
    stt: false,
    imgGen: false,
    imgEdit: false,
    streaming: false
  }
})

// 新增向导 + 测试面板
const wizardVisible = ref(false)
const testDialogVisible = ref(false)
const testingName = ref('')

const defaultForm = () => ({
  name: '',
  type: 'openai-compatible',
  parent: '',
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
  }
})

const getTypeDefaults = (type: string) => {
  if (type === 'ollama-compatible') {
    return {
      capabilities: {
        chat: true,
        embedding: true,
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
      }
    }
  }
  return defaultForm()
}

const handleTypeChange = (type: string) => {
  const defaults = getTypeDefaults(type)
  form.capabilities = { ...defaults.capabilities }
  form.auth = { ...defaults.auth }
}

const form = reactive(defaultForm())

// v2.10.3: computed 化，语言切换后校验文案即时更新
const rules = computed<FormRules>(() => ({
  name: [
    { required: true, message: t('adapter.validation.nameRequired'), trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: t('adapter.validation.namePattern'), trigger: 'blur' }
  ],
  type: [
    { required: true, message: t('adapter.validation.typeRequired'), trigger: 'change' }
  ]
}))

const fetchAdapters = async () => {
  loading.value = true
  try {
    const res = await getAdapterList()
    if (res.data?.success) {
      adapterList.value = res.data.data || []
    }
  } catch (e: any) {
    ElMessage.error(t('adapter.fetchListFailed', { error: e.message || '' }))
  } finally {
    loading.value = false
  }
}

const handleCreate = () => {
  // 打开新增向导（支持模板选择 + 分步配置 + 测试）
  wizardVisible.value = true
}

const handleTest = (row: AdapterInfo) => {
  testingName.value = row.name
  testDialogVisible.value = true
}

const handleEdit = (row: AdapterInfo) => {
  isEditing.value = true
  editingName.value = row.name
  form.name = row.name
  form.type = row.type || 'openai-compatible'
  form.capabilities = { ...row.capabilities }
  dialogVisible.value = true
}

const handleView = (row: AdapterInfo) => {
  viewData.value = { ...row }
  viewDialogVisible.value = true
}

const handleDelete = async (row: AdapterInfo) => {
  try {
    await ElMessageBox.confirm(
      t('adapter.deleteConfirmMessage', { name: row.name }),
      t('adapter.deleteConfirmTitle'),
      { type: 'warning' }
    )
    const res = await deleteAdapter(row.name)
    if (res.data?.success) {
      ElMessage.success(t('adapter.deleteSuccess'))
      fetchAdapters()
    } else {
      ElMessage.error(res.data?.message || t('adapter.deleteFailed'))
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(t('adapter.deleteFailedDetail', { error: e.message || '' }))
    }
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate()

  submitting.value = true
  try {
    const data = {
      name: form.name,
      type: form.type,
      capabilities: { ...form.capabilities },
      auth: { ...form.auth }
    }

    let res
    if (isEditing.value) {
      res = await updateAdapter(editingName.value, data)
    } else {
      res = await createAdapter(data)
    }

    if (res.data?.success) {
      ElMessage.success(isEditing.value ? t('adapter.updateSuccess') : t('adapter.createSuccess'))
      dialogVisible.value = false
      fetchAdapters()
    } else {
      ElMessage.error(res.data?.message || t('adapter.operationFailed'))
    }
  } catch (e: any) {
    ElMessage.error(t('adapter.operationFailedDetail', { error: e.message || '' }))
  } finally {
    submitting.value = false
  }
}

const fetchParentAdapters = async () => {
  try {
    const res = await getParentAdapterList()
    if (res.data?.success) {
      parentAdapters.value = res.data.data || []
    }
  } catch (e: any) {
    console.error('获取父adapter列表失败:', e)
  }
}

onMounted(() => {
  fetchAdapters()
  fetchParentAdapters()
})
</script>

<style scoped>
.capability-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.capability-checkboxes {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.type-hint {
  font-size: 12px;
  color: var(--ja-text-secondary);
  margin-top: 4px;
  line-height: 1.4;
}
</style>
