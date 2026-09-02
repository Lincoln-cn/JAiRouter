<template>
  <div class="permission-management">
    <el-card class="main-card">
      <template #header>
        <div class="card-header">
          <span>权限管理</span>
          <div class="header-actions">
            <el-button @click="refresh">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <!-- 角色选择 -->
      <el-form label-width="80px" class="role-form">
        <el-form-item label="角色">
          <el-select
            v-model="selectedRole"
            placeholder="请选择角色"
            style="width: 480px"
            @change="handleRoleChange"
          >
            <el-option
              v-for="role in ROLES"
              :key="role"
              :label="`${role} - ${ROLE_DESCRIPTIONS[role]}`"
              :value="role"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="selectedRole"
        title="权限变更提示"
        type="warning"
        :closable="false"
        show-icon
        description="权限变更后需重新登录方可生效（权限内嵌于 JWT），服务端缓存约 5 分钟后过期。"
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

      <div class="footer-actions">
        <el-button type="primary" :disabled="!selectedRole" :loading="saving" @click="handleSave">
          保存权限
        </el-button>
        <el-button :disabled="!selectedRole || loading" @click="resetSelection">
          重置
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
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

// 权限树数据（模块分组 → 权限码叶子）
const treeData = computed<PermissionTreeNode[]>(() =>
  PERMISSION_GROUPS.map(group => ({
    key: `group:${group.module}`,
    label: group.module,
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
    ElMessage.error('加载角色权限失败，请重试')
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
    ElMessage.success('权限保存成功，变更需重新登录生效')
  } catch (error) {
    console.error('保存权限失败:', error)
    ElMessage.error('保存权限失败，请重试')
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
  ElMessage.info('已重置为服务器保存的权限')
}

// 刷新（重新拉取全部角色权限）
const refresh = async (): Promise<void> => {
  await loadRoles()
}

// 初始化
loadRoles()
</script>

<style scoped>
.permission-management {
  padding: 4px;
}

.main-card {
  min-height: calc(100vh - 120px);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.role-form {
  margin-bottom: 4px;
}

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
