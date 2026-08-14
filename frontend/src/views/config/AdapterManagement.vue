<template>
  <div class="adapter-management">
    <el-card class="adapter-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <div class="header-title">
              <el-icon><Connection /></el-icon>
              <span>Adapter管理</span>
            </div>
          </div>
          <div class="header-actions">
            <el-button type="primary" @click="handleCreate" size="medium">
              <el-icon><Plus /></el-icon>
              新增Adapter
            </el-button>
          </div>
        </div>
      </template>
      <el-table
        :data="adapterList"
        v-loading="loading"
        stripe
        border
        style="width: 100%"
      >
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="type" label="类型" min-width="120">
          <template #default="{ row }">
            <el-tag :type="row.source === 'builtin' ? 'info' : 'success'" size="small">
              {{ row.source === 'builtin' ? '内置' : '配置驱动' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="能力" min-width="280">
          <template #default="{ row }">
            <div class="capability-tags">
              <el-tag v-if="row.capabilities?.chat" size="small" type="primary">Chat</el-tag>
              <el-tag v-if="row.capabilities?.embedding" size="small" type="success">Embedding</el-tag>
              <el-tag v-if="row.capabilities?.rerank" size="small" type="warning">Rerank</el-tag>
              <el-tag v-if="row.capabilities?.tts" size="small" type="danger">TTS</el-tag>
              <el-tag v-if="row.capabilities?.stt" size="small" type="danger">STT</el-tag>
              <el-tag v-if="row.capabilities?.imgGen" size="small" type="info">图像生成</el-tag>
              <el-tag v-if="row.capabilities?.imgEdit" size="small" type="info">图像编辑</el-tag>
              <el-tag v-if="row.capabilities?.streaming" size="small" type="primary">流式</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.source !== 'builtin'"
              type="primary"
              link
              size="small"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              type="success"
              link
              size="small"
              @click="handleTest(row)"
            >
              测试
            </el-button>
            <el-button
              v-if="row.source !== 'builtin'"
              type="danger"
              link
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
            <el-button
              v-if="row.source === 'builtin'"
              type="info"
              link
              size="small"
              @click="handleView(row)"
            >
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 测试弹窗 -->
    <el-dialog
      v-model="testDialogVisible"
      :title="'测试 Adapter: ' + testingName"
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
      :title="isEditing ? '编辑Adapter' : '新增Adapter'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="120px"
      >
        <el-form-item label="名称" prop="name">
          <el-input
            v-model="form.name"
            :disabled="isEditing"
            placeholder="请输入adapter名称"
          />
        </el-form-item>

        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width: 100%" @change="handleTypeChange">
            <el-option label="OpenAI兼容" value="openai-compatible" />
            <el-option label="Ollama兼容" value="ollama-compatible" />
            <el-option label="继承扩展" value="extend" />
          </el-select>
          <div class="type-hint" v-if="form.type === 'ollama-compatible'">
            Ollama兼容：使用Ollama API格式，适用于本地Ollama服务
          </div>
          <div class="type-hint" v-else-if="form.type === 'extend'">
            继承扩展：基于现有adapter创建变体，可覆盖能力配置和认证方式
          </div>
          <div class="type-hint" v-else>
            OpenAI兼容：使用标准OpenAI API格式，适用于大多数云端API
          </div>
        </el-form-item>

        <el-form-item v-if="form.type === 'extend'" label="父Adapter" prop="parent">
          <el-select v-model="form.parent" placeholder="选择要继承的父adapter" style="width: 100%">
            <el-option
              v-for="item in parentAdapters"
              :key="item.name"
              :label="item.name + (item.source === 'builtin' ? ' (内置)' : ' (配置驱动)')"
              :value="item.name"
            />
          </el-select>
          <div class="type-hint">选择一个现有adapter作为基础，新adapter将继承其所有功能</div>
        </el-form-item>

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

        <el-form-item label="Header名称">
          <el-input v-model="form.auth.headerName" placeholder="Authorization" />
        </el-form-item>

        <el-form-item label="Header前缀">
          <el-input v-model="form.auth.headerPrefix" placeholder="Bearer " />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>

    <!-- 查看详情弹窗 -->
    <el-dialog
      v-model="viewDialogVisible"
      title="Adapter详情"
      width="500px"
    >
      <el-descriptions :column="1" border>
        <el-descriptions-item label="名称">{{ viewData.name }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ viewData.type }}</el-descriptions-item>
        <el-descriptions-item label="来源">
          <el-tag :type="viewData.source === 'builtin' ? 'info' : 'success'" size="small">
            {{ viewData.source === 'builtin' ? '内置' : '配置驱动' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Connection, Plus } from '@element-plus/icons-vue'
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

const rules: FormRules = {
  name: [
    { required: true, message: '请输入adapter名称', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: '只能包含字母、数字、下划线和横线', trigger: 'blur' }
  ],
  type: [
    { required: true, message: '请选择adapter类型', trigger: 'change' }
  ]
}

const fetchAdapters = async () => {
  loading.value = true
  try {
    const res = await getAdapterList()
    if (res.data?.success) {
      adapterList.value = res.data.data || []
    }
  } catch (e: any) {
    ElMessage.error('获取adapter列表失败: ' + (e.message || ''))
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
      `确定要删除adapter "${row.name}" 吗？`,
      '确认删除',
      { type: 'warning' }
    )
    const res = await deleteAdapter(row.name)
    if (res.data?.success) {
      ElMessage.success('adapter删除成功')
      fetchAdapters()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || ''))
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
      ElMessage.success(isEditing.value ? 'adapter更新成功' : 'adapter创建成功')
      dialogVisible.value = false
      fetchAdapters()
    } else {
      ElMessage.error(res.data?.message || '操作失败')
    }
  } catch (e: any) {
    ElMessage.error('操作失败: ' + (e.message || ''))
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
.adapter-management {
  padding: 0;
}

.adapter-card {
  min-height: calc(100vh - 180px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

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
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}
</style>
