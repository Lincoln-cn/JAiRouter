<template>
  <div class="pool-management">
    <el-card class="pool-card">
      <template #header>
        <div class="card-header">
          <span>资源池管理</span>
          <div>
            <el-button :icon="Refresh" circle @click="fetchPools" title="刷新列表" />
            <el-button type="primary" @click="handleCreate">
              <el-icon><Plus /></el-icon>&nbsp;新增资源池
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="请求 model 使用池名（约定名 auto-model）时,自动从池内健康实例中选择执行;未配置池时 auto-model 回退为该服务全部健康实例"
        style="margin-bottom: 16px"
      />

      <el-table :data="pools" v-loading="loading" style="width: 100%" row-key="poolName">
        <el-table-column label="启用" width="70" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="(val: boolean) => handleToggle(row, val)" />
          </template>
        </el-table-column>
        <el-table-column prop="poolName" label="池名(虚拟模型名)" min-width="150" />
        <el-table-column prop="name" label="显示名" min-width="120" />
        <el-table-column prop="serviceType" label="服务类型" width="110" align="center" />
        <el-table-column prop="strategy" label="策略" width="140" align="center" />
        <el-table-column label="成员" min-width="160">
          <template #default="{ row }">{{ formatMembers(row.members) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" align="center">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑资源池' : '新增资源池'" width="640px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item label="池名" required>
          <el-input v-model="form.poolName" placeholder="如 auto-model(请求 model 用它触发池路由)" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="form.name" placeholder="可选,如 默认自动分流池" />
        </el-form-item>
        <el-form-item label="服务类型" required>
          <el-select v-model="form.serviceType" placeholder="选择服务类型" style="width: 200px">
            <el-option v-for="s in SERVICE_TYPES" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="选择策略">
          <el-select v-model="form.strategy" style="width: 200px">
            <el-option label="权重随机(默认)" value="weighted-random" />
            <el-option label="轮询" value="round-robin" />
            <el-option label="最少连接" value="least-connections" />
            <el-option label="IP 哈希" value="ip-hash" />
            <el-option label="一致性哈希(忽略权重)" value="consistent-hash" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
        <el-form-item label="成员">
          <div style="width: 100%">
            <div v-for="(m, idx) in form.members" :key="idx" style="display: flex; gap: 8px; margin-bottom: 8px">
              <el-select v-model="m.instanceId" placeholder="选择实例" style="flex: 1">
                <el-option v-for="o in optionsForRow(idx)" :key="o.value" :label="o.label" :value="o.value" :disabled="o.disabled" />
              </el-select>
              <el-input-number v-model="m.weight" :min="1" :max="10000" placeholder="权重" style="width: 120px" />
              <el-button type="danger" :icon="Delete" circle size="small" @click="removeMember(idx)" />
            </div>
            <el-button type="primary" plain size="small" :icon="Plus" @click="addMember">添加成员</el-button>
            <div v-if="!instancesLoading && instances.length === 0 && form.members.length > 0" class="form-tip">该服务类型下暂无实例，请先在实例管理添加</div>
            <div class="form-tip">成员按实例 ID 引用;权重决定被选中概率(一致哈希策略下无效)</div>
          </div>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
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

const pools = ref<PoolDefinition[]>([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)

const instances = ref<InstanceConfig[]>([])
const instancesLoading = ref(false)

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
    ElMessage.error('获取资源池列表失败')
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
    ElMessage.warning('实例加载失败')
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
    filtered.push({ value: currentId, label: `${currentId}(实例已删除)`, disabled: true })
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
    ElMessage.warning('至少保留一个成员')
    return
  }
  form.members.splice(idx, 1)
}

const handleSave = async () => {
  if (!form.poolName.trim()) {
    ElMessage.warning('请填写池名')
    return
  }
  if (!form.members.length || form.members.some(m => !m.instanceId)) {
    ElMessage.warning('请填写至少一个有效的成员实例 ID')
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
      ElMessage.success('资源池已更新')
    } else {
      await createPool(payload)
      ElMessage.success('资源池已创建')
    }
    dialogVisible.value = false
    await fetchPools()
  } catch (e) {
    ElMessage.error('保存失败,请检查输入')
  } finally {
    saving.value = false
  }
}

const handleToggle = async (row: PoolDefinition, val: boolean) => {
  try {
    await updatePool(row.poolName, { ...row, enabled: val })
    row.enabled = val
    ElMessage.success(val ? '已启用' : '已禁用')
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const handleDelete = (row: PoolDefinition) => {
  ElMessageBox.confirm(
    `确定要删除资源池 "${row.poolName}" 吗?`,
    '删除确认',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    try {
      await deletePool(row.poolName)
      ElMessage.success('资源池已删除')
      await fetchPools()
    } catch (e) {
      ElMessage.error('删除失败')
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
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.form-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
}
</style>
