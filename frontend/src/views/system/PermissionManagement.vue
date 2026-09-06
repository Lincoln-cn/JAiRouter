<template>
  <PageSkeleton :title="t('permissions.title')">
    <template #actions>
      <el-button @click="refresh">
        <el-icon><Refresh /></el-icon>
        {{ t('permissions.refresh') }}
      </el-button>
    </template>

    <!-- 角色选择 -->
    <el-form label-width="80px" style="margin-bottom: 12px;">
      <el-form-item :label="t('permissions.role')">
        <el-select
          v-model="selectedRole"
          :placeholder="t('permissions.rolePlaceholder')"
          style="width: 480px"
          @change="handleRoleChange"
        >
          <el-option
            v-for="option in roleOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="selectedRole"
      :title="t('permissions.changeAlert.title')"
      type="warning"
      :closable="false"
      show-icon
      :description="t('permissions.changeAlert.description')"
      style="margin-bottom: 16px"
    />

    <!-- 权限树（按模块分组展示 43 个权限码） -->
    <div v-loading="loading" class="tree-wrapper">
      <el-tree
        ref="permissionTreeRef"
        :data="treeData"
        node-key="key"
        show-checkbox
        default-expand-all
        :props="treeProps"
        class="permission-tree"
      />
    </div>

    <template #footer>
      <div class="footer-actions">
        <el-button type="primary" :disabled="!selectedRole" :loading="saving" @click="handleSave">
          {{ t('permissions.save') }}
        </el-button>
        <el-button :disabled="!selectedRole || loading" @click="resetSelection">
          {{ t('permissions.reset') }}
        </el-button>
      </div>
    </template>
  </PageSkeleton>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { ElTree } from 'element-plus'
import {
  ALL_PERMISSION_CODES,
  PERMISSION_GROUPS,
  ROLES,
  ROLE_DESCRIPTIONS,
  getRolePermissions,
  updateRolePermissions,
  type RoleName
} from '@/api/permission'
import PageSkeleton from '@/components/PageSkeleton.vue'

const { t } = useI18n()

/** 权限树节点 */
interface PermissionTreeNode {
  key: string
  label: string
  children?: PermissionTreeNode[]
}

const loading = ref(false)
const saving = ref(false)
const selectedRole = ref<RoleName | ''>('')
const permissionTreeRef = ref<InstanceType<typeof ElTree>>()

// 角色 → 权限码缓存（避免切换角色时反复请求）
const rolePermissionMap = ref<Record<string, string[]>>({})

const treeProps = { label: 'label', children: 'children' }

/** 角色下拉选项（角色名 + i18n 角色说明；computed 保证语言切换后即时更新） */
const roleOptions = computed<{ value: RoleName; label: string }[]>(() =>
  ROLES.map(role => ({
    value: role,
    label: `${role} - ${t(ROLE_DESCRIPTIONS[role])}`
  }))
)

// 权限树数据（模块分组 → 权限码叶子；模块标题按当前语言翻译）
const treeData = computed<PermissionTreeNode[]>(() =>
  PERMISSION_GROUPS.map(group => ({
    key: `group:${group.module}`,
    label: t(group.module),
    children: group.codes.map(code => ({ key: code, label: code }))
  }))
)

// 加载全部角色权限（进入页面 + 刷新时）
const loadRoles = async (): Promise<void> => {
  loading.value = true
  try {
    rolePermissionMap.value = await getRolePermissions()
    // 若已选中角色，同步刷新其勾选状态
    if (selectedRole.value) {
      applyRoleSelection(selectedRole.value)
    }
  } catch (error) {
    console.error('加载角色权限失败:', error)
    ElMessage.error(t('permissions.messages.loadRolesFailed'))
  } finally {
    loading.value = false
  }
}

// 将角色权限码应用到树勾选（仅叶子 key，父节点状态自动联动）
const applyRoleSelection = (role: RoleName): void => {
  const codes = rolePermissionMap.value[role] || []
  permissionTreeRef.value?.setCheckedKeys(codes)
}

// 角色切换
const handleRoleChange = async (role: RoleName): Promise<void> => {
  if (!rolePermissionMap.value[role]) {
    await loadRoles()
  } else {
    applyRoleSelection(role)
  }
}

// 收集当前勾选的权限码（仅叶子：全选父节点 + 半选父节点的叶子）
const collectSelectedCodes = (): string[] => {
  const tree = permissionTreeRef.value
  if (!tree) {
    return []
  }
  const codeSet = new Set(ALL_PERMISSION_CODES)
  const checkedKeys = tree.getCheckedKeys() as (string | number)[]
  const halfCheckedKeys = tree.getHalfCheckedKeys() as (string | number)[]
  const selected: string[] = []
  for (const key of checkedKeys) {
    if (typeof key === 'string' && codeSet.has(key)) {
      selected.push(key)
    }
  }
  for (const key of halfCheckedKeys) {
    if (typeof key === 'string' && codeSet.has(key)) {
      selected.push(key)
    }
  }
  return selected
}

// 保存角色权限（整体替换）
const handleSave = async (): Promise<void> => {
  if (!selectedRole.value) {
    return
  }
  const codes = collectSelectedCodes()
  saving.value = true
  try {
    await updateRolePermissions(selectedRole.value, codes)
    rolePermissionMap.value[selectedRole.value] = codes
    ElMessage.success(t('permissions.messages.saveSuccess'))
  } catch (error) {
    console.error('保存权限失败:', error)
    ElMessage.error(t('permissions.messages.saveFailed'))
  } finally {
    saving.value = false
  }
}

// 重置为服务器保存的最新权限
const resetSelection = async (): Promise<void> => {
  if (!selectedRole.value) {
    return
  }
  await loadRoles()
  ElMessage.info(t('permissions.messages.resetApplied'))
}

// 刷新（重新拉取全部角色权限）
const refresh = async (): Promise<void> => {
  await loadRoles()
}

// 初始化
loadRoles()
</script>

<style scoped>
.tree-wrapper {
  min-height: 320px;
  max-height: calc(100vh - 380px);
  overflow: auto;
  border: 1px solid var(--ja-border);
  border-radius: 4px;
  padding: 12px;
  margin-bottom: 16px;
}

.permission-tree {
  --el-tree-node-hover-bg-color: var(--ja-bg-hover, rgba(64, 158, 255, 0.08));
}

.footer-actions {
  display: flex;
  gap: 12px;
}
</style>
