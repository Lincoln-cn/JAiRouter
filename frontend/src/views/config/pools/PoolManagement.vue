<template>
  <PageSkeleton :title="t('pool.title')">
    <template #actions>
      <el-button type="primary" v-permission="'config:pools:write'" @click="handleCreate">
        <el-icon><Plus /></el-icon>&nbsp;{{ t('pool.addPool') }}
      </el-button>
      <!-- 工具栏口径：主操作在前、刷新在最后 -->
      <el-button class="refresh-button" :loading="loading" @click="fetchPools">
        <el-icon><Refresh /></el-icon>&nbsp;{{ t('pool.refreshList') }}
      </el-button>
    </template>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      :title="t('pool.routeHint')"
      style="margin-bottom: 16px"
    />

    <el-card class="table-card" shadow="never">
      <el-table :data="pools" v-loading="loading" style="width: 100%" row-key="poolName">
        <el-table-column :label="t('pool.enabled')" width="70" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="(val: boolean) => handleToggle(row, val)" />
          </template>
        </el-table-column>
        <el-table-column prop="poolName" :label="t('pool.poolNameCol')" min-width="200">
          <template #default="{ row }">
            <!-- 合并「显示名称」列：多数池未填该项，空列占比高 -->
            <div class="pool-name">
              <span class="pool-name__main">{{ row.poolName }}</span>
              <span v-if="row.name" class="pool-name__sub">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="serviceType" :label="t('pool.serviceType')" width="110" align="center">
          <template #default="{ row }">
            <el-link
              type="primary"
              :underline="false"
              @click.stop="router.push({ name: 'instance-management', query: { serviceType: row.serviceType } })"
            >
              {{ row.serviceType }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="strategy" :label="t('pool.strategy')" width="140" align="center" />
        <el-table-column :label="t('pool.members')" min-width="160">
          <template #default="{ row }">
            <el-link
              type="primary"
              :underline="false"
              @click.stop="router.push({ name: 'instance-management', query: { serviceType: row.serviceType } })"
            >
              {{ formatMembers(row.members) }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column :label="t('pool.actions')" width="140" align="right">
          <template #default="{ row }">
            <!-- 与服务管理/路由规则一致：行内操作用文字按钮 -->
            <el-button link size="small" v-permission="'config:pools:write'" @click="handleEdit(row)">{{ t('pool.edit') }}</el-button>
            <!-- 删除二次确认统一走 ConfirmAction，文案带对象名 -->
            <ConfirmAction
              :title="t('pool.deleteDialog.message', { name: row.poolName })"
              type="danger"
              :confirm-text="t('pool.deleteDialog.confirm')"
              :cancel-text="t('pool.deleteDialog.cancel')"
              :loading="deletingName === row.poolName"
              :disabled="deletingName === row.poolName"
              @confirm="handleDelete(row)"
            >
              <el-button link size="small" type="danger" v-permission="'config:pools:write'">{{ t('pool.delete') }}</el-button>
            </ConfirmAction>
          </template>
        </el-table-column>
        <template #empty>
          <EmptyState :title="t('pool.emptyTitle')" :description="t('pool.emptyDescription')">
            <template #actions>
              <el-button type="primary" v-permission="'config:pools:write'" @click="handleCreate">
                <el-icon><Plus /></el-icon>&nbsp;{{ t('pool.addPool') }}
              </el-button>
            </template>
          </EmptyState>
        </template>
      </el-table>
    </el-card>
  </PageSkeleton>

    <!-- 创建/编辑抽屉（单实例，mode 区分新建/编辑） -->
    <el-drawer
      v-model="dialogVisible"
      :title="drawerTitle"
      :size="drawerSize"
      direction="rtl"
      :close-on-click-modal="false"
      destroy-on-close
      class="pool-drawer"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item :label="t('pool.poolName')" prop="poolName" required>
          <el-input v-model="form.poolName" :placeholder="t('pool.poolNamePlaceholder')" :disabled="drawerMode === 'edit'" />
        </el-form-item>
        <el-form-item :label="t('pool.displayName')">
          <el-input v-model="form.name" :placeholder="t('pool.displayNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('pool.serviceType')" prop="serviceType" required>
          <el-select v-model="form.serviceType" :placeholder="t('pool.selectServiceType')" style="width: 200px">
            <el-option v-for="s in SERVICE_TYPES" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('pool.selectStrategy')">
          <el-select v-model="form.strategy" style="width: 200px">
            <el-option :label="t('pool.strategies.weightedRandom')" value="weighted-random" />
            <el-option :label="t('pool.strategies.roundRobin')" value="round-robin" />
            <el-option :label="t('pool.strategies.leastConnections')" value="least-connections" />
            <el-option :label="t('pool.strategies.ipHash')" value="ip-hash" />
            <el-option :label="t('pool.strategies.consistentHash')" value="consistent-hash" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('pool.enabled')">
          <el-switch v-model="form.enabled" :active-text="t('pool.enabled')" :inactive-text="t('pool.disabled')" />
        </el-form-item>
        <el-form-item :label="t('pool.members')" prop="members">
          <div style="width: 100%">
            <div v-for="(m, idx) in form.members" :key="idx" style="display: flex; gap: 8px; margin-bottom: 8px">
              <el-select v-model="m.instanceId" :placeholder="t('pool.selectInstance')" style="flex: 1">
                <el-option v-for="o in optionsForRow(idx)" :key="o.value" :label="o.label" :value="o.value" :disabled="o.disabled" />
              </el-select>
              <el-input-number v-model="m.weight" :min="1" :max="10000" :placeholder="t('pool.weightPlaceholder')" style="width: 120px" />
              <el-button type="danger" :icon="Delete" circle size="small" @click="removeMember(idx)" />
            </div>
            <el-button type="primary" plain size="small" :icon="Plus" @click="addMember">{{ t('pool.addMember') }}</el-button>
            <div v-if="!instancesLoading && instances.length === 0 && form.members.length > 0" class="form-tip">{{ t('pool.noInstancesTip') }}</div>
            <div class="form-tip">{{ t('pool.memberWeightTip') }}</div>
          </div>
        </el-form-item>
        <el-form-item :label="t('pool.description')">
          <el-input v-model="form.description" type="textarea" :rows="2" :placeholder="t('pool.descriptionPlaceholder')" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="drawer-footer">
          <el-button size="large" :disabled="saving" @click="closeDrawer">{{ t('pool.cancel') }}</el-button>
          <el-button
            type="primary"
            size="large"
            v-permission="'config:pools:write'"
            :loading="saving"
            :disabled="saving"
            @click="handleSave"
          >{{ t('pool.save') }}</el-button>
        </div>
      </template>
    </el-drawer>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Delete } from '@element-plus/icons-vue'
import { ALL_SERVICE_TYPES as SERVICE_TYPES } from '@/constants/serviceTypes'
import {
  getPoolList,
  createPool,
  updatePool,
  deletePool,
  type PoolDefinition,
  type PoolMember
} from '@/api/pools'
import { getServiceInstances, type InstanceConfig } from '@/api/instance'
import PageSkeleton from '@/components/PageSkeleton.vue'
import ConfirmAction from '@/components/ConfirmAction.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useViewport } from '@/composables/useViewport'

const { t } = useI18n()
const pools = ref<PoolDefinition[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
// 单实例抽屉：create / edit 两态，标题由 drawerTitle 派生
const drawerMode = ref<'create' | 'edit'>('create')
const deletingName = ref('')
const formRef = ref<FormInstance>()

const instances = ref<InstanceConfig[]>([])
const instancesLoading = ref(false)
const router = useRouter()

const form = reactive<PoolDefinition>({
  poolName: '',
  name: '',
  serviceType: 'chat',
  enabled: true,
  strategy: 'weighted-random',
  description: '',
  members: [{ instanceId: '', weight: 1 }]
})

// 抽屉尺寸：字段较多（成员动态行），宽屏 640、窄屏铺满
const { isNarrow } = useViewport()
const drawerSize = computed(() => (isNarrow.value ? '100%' : '640px'))
const drawerTitle = computed(() =>
  drawerMode.value === 'create'
    ? t('pool.addPool')
    : t('pool.editTitle', { name: form.poolName })
)

// 校验走 el-form rules：池名必填、成员至少一个且都选了实例
const rules = computed<FormRules>(() => ({
  poolName: [{ required: true, message: t('pool.messages.poolNameRequired'), trigger: 'blur' }],
  serviceType: [{ required: true, message: t('pool.selectServiceType'), trigger: 'change' }],
  members: [
    {
      validator: (_rule, value, callback) => {
        const list = Array.isArray(value) ? (value as PoolMember[]) : []
        if (list.length === 0 || list.some((m) => !m.instanceId)) {
          callback(new Error(t('pool.messages.memberRequired')))
          return
        }
        callback()
      },
      trigger: 'change'
    }
  ]
}))

const fetchPools = async () => {
  loading.value = true
  try {
    const res = await getPoolList()
    pools.value = res.data?.data || []
  } catch (e) {
    ElMessage.error(t('pool.messages.fetchListFailed'))
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  Object.assign(form, {
    poolName: '',
    name: '',
    serviceType: 'chat',
    enabled: true,
    strategy: 'weighted-random',
    description: '',
    members: [{ instanceId: '', weight: 1 }]
  })
}

const loadInstances = async (serviceType: string) => {
  instancesLoading.value = true
  try {
    const res = await getServiceInstances(serviceType)
    instances.value = res.data?.data || []
  } catch {
    ElMessage.warning(t('pool.messages.loadInstancesFailed'))
    instances.value = []
  } finally {
    instancesLoading.value = false
  }
}

const instanceOptions = computed(() =>
  instances.value
    .map((i) => ({ value: i.instanceId ?? '', label: `${i.name}(${i.instanceId})` }))
    .filter((o): o is { value: string; label: string } => Boolean(o.value))
)

const optionsForRow = (idx: number): { value: string; label: string; disabled?: boolean }[] => {
  const currentId = form.members[idx]?.instanceId
  const otherSelected = new Set(
    form.members
      .filter((m, i) => i !== idx && m.instanceId)
      .map((m) => m.instanceId)
  )
  const filtered: { value: string; label: string; disabled?: boolean }[] =
    instanceOptions.value.filter((o) => !otherSelected.has(o.value))
  if (currentId && !filtered.some((o) => o.value === currentId)) {
    filtered.push({ value: currentId, label: t('pool.instanceDeleted', { id: currentId }), disabled: true })
  }
  return filtered
}

watch(() => form.serviceType, (st) => {
  if (st) loadInstances(st)
})

const handleCreate = () => {
  resetForm()
  drawerMode.value = 'create'
  dialogVisible.value = true
  loadInstances(form.serviceType)
  nextTick(() => formRef.value?.clearValidate())
}

const handleEdit = (row: PoolDefinition) => {
  Object.assign(form, {
    poolName: row.poolName,
    name: row.name || '',
    serviceType: row.serviceType,
    enabled: row.enabled,
    strategy: row.strategy || 'weighted-random',
    description: row.description || '',
    members: (row.members || []).map(m => ({ instanceId: m.instanceId, weight: m.weight }))
  })
  drawerMode.value = 'edit'
  dialogVisible.value = true
  loadInstances(form.serviceType)
  nextTick(() => formRef.value?.clearValidate())
}

// 关闭抽屉：保存中不允许关，避免请求还在飞就丢了上下文
const closeDrawer = () => {
  if (saving.value) return
  dialogVisible.value = false
  formRef.value?.clearValidate()
}

const addMember = () => {
  form.members.push({ instanceId: '', weight: 1 })
}

const removeMember = (idx: number) => {
  if (form.members.length <= 1) {
    ElMessage.warning(t('pool.messages.atLeastOneMember'))
    return
  }
  form.members.splice(idx, 1)
}

const handleSave = async () => {
  if (!formRef.value || saving.value) return
  try {
    await formRef.value.validate()
  } catch {
    // 表单内联提示已给出原因，抽屉保留用户已填内容
    return
  }
  saving.value = true
  try {
    const payload: PoolDefinition = {
      ...form,
      members: form.members.map(m => ({ ...m }))
    }
    if (drawerMode.value === 'edit') {
      await updatePool(form.poolName, payload)
      ElMessage.success(t('pool.messages.updated'))
    } else {
      await createPool(payload)
      ElMessage.success(t('pool.messages.created'))
    }
    dialogVisible.value = false
    await fetchPools()
  } catch (e) {
    ElMessage.error(t('pool.messages.saveFailed'))
  } finally {
    saving.value = false
  }
}

const handleToggle = async (row: PoolDefinition, val: boolean) => {
  try {
    await updatePool(row.poolName, { ...row, enabled: val })
    row.enabled = val
    ElMessage.success(val ? t('pool.messages.enabled') : t('pool.messages.disabled'))
  } catch (e) {
    ElMessage.error(t('pool.messages.operationFailed'))
  }
}

// 删除：二次确认由 ConfirmAction 承担（type=danger + 对象名），这里只做确认后的提交
const handleDelete = async (row: PoolDefinition) => {
  deletingName.value = row.poolName
  try {
    await deletePool(row.poolName)
    ElMessage.success(t('pool.messages.deleted'))
    await fetchPools()
  } catch (e) {
    ElMessage.error(t('pool.messages.deleteFailed'))
  } finally {
    deletingName.value = ''
  }
}

const formatMembers = (members?: PoolMember[]) => {
  if (!members || !members.length) return '-'
  return members.map(m => `${m.instanceId}(w=${m.weight})`).join(' · ')
}

onMounted(() => { fetchPools() })
</script>

<style scoped>
/* 表格套中性卡片容器，与 服务管理 / 路由规则 保持一致 */
.table-card {
  border: 1px solid var(--ja-border);
  border-radius: var(--ja-radius-lg);
  background: var(--ja-paper);
  box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);
}

.table-card :deep(.el-card__body) {
  padding: 0;
}

.form-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--ja-text-secondary);
}

.pool-name {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.pool-name__main {
  color: var(--ja-ink, var(--ja-text-primary));
}

.pool-name__sub {
  font-size: 12px;
  color: var(--ja-ink-3, var(--ja-text-secondary));
}

/* 抽屉：标题区与主体统一留白，footer 固定在底部 */
.pool-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 14px 20px;
  border-bottom: 1px solid var(--ja-border-light);
  background-color: var(--ja-main-bg);
}

.pool-drawer :deep(.el-drawer__body) {
  padding: 20px;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 15px;
  padding: 10px 20px;
}
</style>
