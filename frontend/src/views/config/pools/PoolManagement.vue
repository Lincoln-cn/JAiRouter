<template>
  <PageSkeleton :title="t('pool.title')">
    <template #actions>
      <el-button :icon="Refresh" circle @click="fetchPools" :title="t('pool.refreshList')" />
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>&nbsp;{{ t('pool.addPool') }}
      </el-button>
    </template>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      :title="t('pool.routeHint')"
      style="margin-bottom: 16px"
    />

    <el-table :data="pools" v-loading="loading" style="width: 100%" row-key="poolName">
        <el-table-column :label="t('pool.enabled')" width="70" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="(val: boolean) => handleToggle(row, val)" />
          </template>
        </el-table-column>
        <el-table-column prop="poolName" :label="t('pool.poolNameCol')" min-width="150" />
        <el-table-column prop="name" :label="t('pool.displayName')" min-width="120" />
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
        <el-table-column :label="t('pool.actions')" width="180" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">{{ t('pool.edit') }}</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">{{ t('pool.delete') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
  </PageSkeleton>

    <el-dialog v-model="dialogVisible" :title="isEdit ? t('pool.editPool') : t('pool.addPool')" width="640px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item :label="t('pool.poolName')" required>
          <el-input v-model="form.poolName" :placeholder="t('pool.poolNamePlaceholder')" :disabled="isEdit" />
        </el-form-item>
        <el-form-item :label="t('pool.displayName')">
          <el-input v-model="form.name" :placeholder="t('pool.displayNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('pool.serviceType')" required>
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
        <el-form-item :label="t('pool.members')">
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
        <el-button @click="dialogVisible = false">{{ t('pool.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ t('pool.save') }}</el-button>
      </template>
    </el-dialog>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
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

const { t } = useI18n()
const pools = ref<PoolDefinition[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)

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
  isEdit.value = false
  dialogVisible.value = true
  loadInstances(form.serviceType)
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
  isEdit.value = true
  dialogVisible.value = true
  loadInstances(form.serviceType)
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
  if (!form.poolName.trim()) {
    ElMessage.warning(t('pool.messages.poolNameRequired'))
    return
  }
  if (!form.members.length || form.members.some(m => !m.instanceId)) {
    ElMessage.warning(t('pool.messages.memberRequired'))
    return
  }
  saving.value = true
  try {
    const payload: PoolDefinition = {
      ...form,
      members: form.members.map(m => ({ ...m }))
    }
    if (isEdit.value) {
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

const handleDelete = (row: PoolDefinition) => {
  ElMessageBox.confirm(
    t('pool.deleteDialog.message', { name: row.poolName }),
    t('pool.deleteDialog.title'),
    { confirmButtonText: t('pool.deleteDialog.confirm'), cancelButtonText: t('pool.deleteDialog.cancel'), type: 'warning' }
  ).then(async () => {
    try {
      await deletePool(row.poolName)
      ElMessage.success(t('pool.messages.deleted'))
      await fetchPools()
    } catch (e) {
      ElMessage.error(t('pool.messages.deleteFailed'))
    }
  }).catch(() => {})
}

const formatMembers = (members?: PoolMember[]) => {
  if (!members || !members.length) return '-'
  return members.map(m => `${m.instanceId}(w=${m.weight})`).join(' · ')
}

onMounted(() => { fetchPools() })
</script>

<style scoped>
.form-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--ja-text-secondary);
}
</style>
