<template>
  <PageSkeleton :title="t('adapter.management')">
    <template #actions>
      <el-button type="primary" v-permission="'config:adapters:write'" @click="handleCreate" size="medium">
        <el-icon><Plus /></el-icon>
        {{ t('adapter.newAdapter') }}
      </el-button>
      <!-- 工具栏口径：主操作在前、刷新在最后 -->
      <el-button class="refresh-button" :loading="loading" @click="fetchAdapters">
        <el-icon><Refresh /></el-icon>&nbsp;{{ t('adapter.refresh') }}
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
          <el-tag type="info" effect="plain" size="small">
            {{ row.source === 'builtin' ? t('adapter.builtin') : t('adapter.configDriven') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('adapter.capabilityColumn')" min-width="280">
        <template #default="{ row }">
          <div class="capability-tags">
            <el-tag v-if="row.capabilities?.chat" size="small" type="info" effect="plain">{{ t('adapter.capability.chat') }}</el-tag>
            <el-tag v-if="row.capabilities?.embedding" size="small" type="info" effect="plain">{{ t('adapter.capability.embedding') }}</el-tag>
            <el-tag v-if="row.capabilities?.rerank" size="small" type="info" effect="plain">{{ t('adapter.capability.rerank') }}</el-tag>
            <el-tag v-if="row.capabilities?.tts" size="small" type="info" effect="plain">{{ t('adapter.capability.tts') }}</el-tag>
            <el-tag v-if="row.capabilities?.stt" size="small" type="info" effect="plain">{{ t('adapter.capability.stt') }}</el-tag>
            <el-tag v-if="row.capabilities?.imgGen" size="small" type="info" effect="plain">{{ t('adapter.capability.imgGen') }}</el-tag>
            <el-tag v-if="row.capabilities?.imgEdit" size="small" type="info" effect="plain">{{ t('adapter.capability.imgEdit') }}</el-tag>
            <el-tag v-if="row.capabilities?.streaming" size="small" type="info" effect="plain">{{ t('adapter.capability.streaming') }}</el-tag>
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
            v-permission="'config:adapters:write'" @click="handleEdit(row)"
          >
            {{ t('adapter.edit') }}
          </el-button>
          <el-button
            link
            size="small"
            @click="handleTest(row)"
          >
            {{ t('adapter.testAction') }}
          </el-button>
          <!-- 删除二次确认统一走 ConfirmAction，文案带对象名 -->
          <ConfirmAction
            v-if="row.source !== 'builtin'"
            :title="t('adapter.deleteConfirmMessage', { name: row.name })"
            type="danger"
            :confirm-text="t('adapter.delete')"
            :cancel-text="t('adapter.cancel')"
            :loading="deletingName === row.name"
            :disabled="deletingName === row.name"
            @confirm="handleDelete(row)"
          >
            <el-button
              type="danger"
              link
              size="small"
              v-permission="'config:adapters:write'"
            >
              {{ t('adapter.delete') }}
            </el-button>
          </ConfirmAction>
          <el-button
            v-if="row.source === 'builtin'"
            link
            size="small"
            @click="handleView(row)"
          >
            {{ t('adapter.view') }}
          </el-button>
        </template>
      </el-table-column>
      <template #empty>
        <EmptyState :title="t('adapter.emptyTitle')" :description="t('adapter.emptyDescription')">
          <template #actions>
            <el-button
              type="primary"
              size="medium"
              v-permission="'config:adapters:write'"
              @click="handleCreate"
            >
              <el-icon><Plus /></el-icon>
              {{ t('adapter.newAdapter') }}
            </el-button>
          </template>
        </EmptyState>
      </template>
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

    <!-- 创建/编辑抽屉（单实例，mode 区分新建/编辑） -->
    <el-drawer
      v-model="dialogVisible"
      :title="drawerTitle"
      :size="drawerSize"
      direction="rtl"
      :close-on-click-modal="false"
      destroy-on-close
      class="adapter-drawer"
    >
      <!-- 提交失败的错误留在抽屉内展示，已填内容不清空 -->
      <el-alert
        v-if="submitError"
        :title="submitError"
        type="error"
        show-icon
        closable
        class="drawer-error"
        @close="submitError = ''"
      />

      <!-- 新建时保留「从模板创建」入口：模板选择属于选择类流程，仍在 AdapterWizard 里完成 -->
      <div v-if="drawerMode === 'create'" class="template-entry">
        <span class="template-entry__text">{{ t('adapter.templateEntryHint') }}</span>
        <el-button link type="primary" @click="handleOpenWizard">{{ t('adapter.createFromTemplate') }}</el-button>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
      >
        <el-form-item :label="t('adapter.name')" prop="name">
          <el-input
            v-model="form.name"
            :disabled="drawerMode === 'edit'"
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
        <div class="drawer-footer">
          <el-button size="large" :disabled="submitting" @click="closeDrawer">{{ t('adapter.cancel') }}</el-button>
          <el-button
            type="primary"
            size="large"
            v-permission="'config:adapters:write'"
            :loading="submitting"
            :disabled="submitting"
            @click="handleSubmit"
          >{{ t('adapter.save') }}</el-button>
        </div>
      </template>
    </el-drawer>

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
          <el-tag type="info" effect="plain" size="small">
            {{ viewData.source === 'builtin' ? t('adapter.builtin') : t('adapter.configDriven') }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getErrorMessage } from '@/utils/error'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  getAdapterList,
  getParentAdapterList,
  createAdapter,
  updateAdapter,
  deleteAdapter,
  type AdapterInfo,
  type ParentAdapterInfo
} from '@/api/adapter'
import AdapterWizard from './adapter/AdapterWizard.vue'
import AdapterTestPanel from './adapter/AdapterTestPanel.vue'
import OnboardingSteps from './adapter/OnboardingSteps.vue'
import PageSkeleton from '@/components/PageSkeleton.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useViewport } from '@/composables/useViewport'

const { t } = useI18n()

const loading = ref(false)
const submitting = ref(false)
const adapterList = ref<AdapterInfo[]>([])
const parentAdapters = ref<ParentAdapterInfo[]>([])
const dialogVisible = ref(false)
const viewDialogVisible = ref(false)
// 单实例抽屉：create / edit 两态，标题由 drawerTitle 派生
const drawerMode = ref<'create' | 'edit'>('create')
const submitError = ref('')
const deletingName = ref('')
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

// 抽屉尺寸：普通表单 520（字段不多），窄屏铺满；编辑标题带对象名
const { isNarrow } = useViewport()
const drawerSize = computed(() => (isNarrow.value ? '100%' : '520px'))
const drawerTitle = computed(() =>
  drawerMode.value === 'create'
    ? t('adapter.newAdapter')
    : t('adapter.editTitle', { name: editingName.value })
)

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
  } catch (e) {
    ElMessage.error(t('adapter.fetchListFailed', { error: getErrorMessage(e) || '' }))
  } finally {
    loading.value = false
  }
}

const handleCreate = () => {
  // 新建走抽屉（单页表单）；需要模板时用抽屉里的「从模板创建」入口进向导
  drawerMode.value = 'create'
  submitError.value = ''
  Object.assign(form, defaultForm())
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

// 抽屉与向导不同时叠两层弹层：先关抽屉再打开向导
const handleOpenWizard = () => {
  dialogVisible.value = false
  wizardVisible.value = true
}

const closeDrawer = () => {
  if (submitting.value) return
  dialogVisible.value = false
  formRef.value?.clearValidate()
}

const handleTest = (row: AdapterInfo) => {
  testingName.value = row.name
  testDialogVisible.value = true
}

const handleEdit = (row: AdapterInfo) => {
  drawerMode.value = 'edit'
  submitError.value = ''
  editingName.value = row.name
  form.name = row.name
  form.type = row.type || 'openai-compatible'
  form.capabilities = { ...row.capabilities }
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

const handleView = (row: AdapterInfo) => {
  viewData.value = { ...row }
  viewDialogVisible.value = true
}

// 删除（二次确认由 ConfirmAction 承担，此处只做「确认后」的提交）
const handleDelete = async (row: AdapterInfo) => {
  deletingName.value = row.name
  try {
    const res = await deleteAdapter(row.name)
    if (res.data?.success) {
      ElMessage.success(t('adapter.deleteSuccess'))
      fetchAdapters()
    } else {
      ElMessage.error(res.data?.message || t('adapter.deleteFailed'))
    }
  } catch (e) {
    ElMessage.error(t('adapter.deleteFailedDetail', { error: getErrorMessage(e) || '' }))
  } finally {
    deletingName.value = ''
  }
}

const handleSubmit = async () => {
  if (!formRef.value || submitting.value) return
  submitError.value = ''
  try {
    await formRef.value.validate()
  } catch {
    // 校验不通过由表单项内联提示；抽屉里的已填内容不动
    return
  }

  submitting.value = true
  try {
    const data = {
      name: form.name,
      type: form.type,
      capabilities: { ...form.capabilities },
      auth: { ...form.auth }
    }

    let res
    if (drawerMode.value === 'edit') {
      res = await updateAdapter(editingName.value, data)
    } else {
      res = await createAdapter(data)
    }

    if (res.data?.success) {
      ElMessage.success(drawerMode.value === 'edit' ? t('adapter.updateSuccess') : t('adapter.createSuccess'))
      dialogVisible.value = false
      fetchAdapters()
    } else {
      submitError.value = res.data?.message || t('adapter.operationFailed')
      ElMessage.error(submitError.value)
    }
  } catch (e) {
    submitError.value = t('adapter.operationFailedDetail', { error: getErrorMessage(e) || '' })
    ElMessage.error(submitError.value)
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
  } catch (e) {
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
  gap: var(--ja-space-1);
}

.capability-checkboxes {
  display: flex;
  flex-wrap: wrap;
  gap: var(--ja-space-4);
}

.type-hint {
  font-size: 12px;
  color: var(--ja-ink-3);
  margin-top: var(--ja-space-1);
  line-height: 1.4;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 15px;
  padding: 10px 20px;
}

.drawer-error {
  margin-bottom: 16px;
}

/* 新建时的「从模板创建」次级入口：主路径是单页表单，模板/分步/测试仍在向导里 */
.template-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ja-space-3);
  padding: var(--ja-space-2) var(--ja-space-3);
  margin-bottom: var(--ja-space-4);
  background: var(--ja-main-bg);
  border: 1px dashed var(--ja-border-light);
  border-radius: var(--ja-radius);
}

.template-entry__text {
  font-size: 13px;
  color: var(--ja-ink-3);
  line-height: 1.4;
}

.adapter-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  background-color: var(--ja-main-bg);
  border-bottom: 1px solid var(--ja-border-light);
  padding: 14px 20px;
}

.adapter-drawer :deep(.el-drawer__body) {
  padding: 20px;
}
</style>
