<template>
  <PageSkeleton :title="t('permissions.title')">
    <template #actions>
      <el-button @click="refresh">
        <el-icon><Refresh /></el-icon>
        {{ t('permissions.refresh') }}
      </el-button>
    </template>

    <!-- 角色选择 -->
    <el-form label-width="80px" style="margin-bottom: var(--ja-space-3);">
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

    <!-- 权限树（按模块分组展示 63 个权限码：中文名 + 弱化的原始码） -->
    <div v-loading="loading" class="tree-wrapper">
      <el-tree
        ref="permissionTreeRef"
        :data="treeData"
        node-key="key"
        show-checkbox
        default-expand-all
        :props="treeProps"
        class="permission-tree"
      >
        <template #default="{ data }">
          <span class="perm-node" :title="data.code || data.label">
            <span class="perm-name">{{ data.label }}</span>
            <code v-if="data.code" class="perm-code">{{ data.code }}</code>
          </span>
        </template>
      </el-tree>
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

const { t, te } = useI18n()

/** 权限树节点 */
interface PermissionTreeNode {
  key: string
  label: string
  /** 仅叶子节点带原始权限码（用于弱化展示与 tooltip） */
  code?: string
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

/**
 * 权限码 → 「资源 · 动作」的中文/英文名（模块名由分组标题承担）。
 * 命名口径：模块 · 资源 · 动作（如 config:services:read ⇒ 服务配置 · 查看）；
 * 没有资源段的码（如 callhistory:view）用模块名当资源，避免出现「· 查看」这种半截文案。
 * 找不到词条时回退成码片段本身，保证不漏显示。
 */
const PERMISSION_ACTIONS = ['read', 'view', 'write', 'manage', 'use']

const permissionLabel = (code: string): string => {
  const segments = code.split(':')
  const last = segments[segments.length - 1]
  const action = PERMISSION_ACTIONS.includes(last) ? last : ''
  const moduleKey = segments[0]
  const resourceRaw = action
    ? segments.slice(1, -1).join(':') || moduleKey
    : segments.slice(1).join(':') || moduleKey
  const resourceKey = `permissions.resources.${resourceRaw}`
  const resourceLabel = te(resourceKey) ? t(resourceKey) : resourceRaw
  const actionLabel = action && te(`permissions.actions.${action}`) ? t(`permissions.actions.${action}`) : ''
  return actionLabel ? `${resourceLabel} · ${actionLabel}` : resourceLabel
}

// 权限树数据（模块分组 → 权限码叶子；模块标题按当前语言翻译）
const treeData = computed<PermissionTreeNode[]>(() =>
  PERMISSION_GROUPS.map(group => ({
    key: `group:${group.module}`,
    label: t(group.module),
    children: group.codes.map(code => ({ key: code, label: permissionLabel(code), code }))
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
  border-radius: var(--ja-radius);
  padding: var(--ja-space-3);
  margin-bottom: var(--ja-space-4);
}

/* 悬停底色走中性命中色（原 --ja-bg-hover 令牌并不存在，一直落到硬编码蓝色 rgba） */
.permission-tree {
  --el-tree-node-hover-bg-color: var(--ja-surface-2);
}

/* 叶子节点：中文名 + 等宽弱化码，扫读靠中文名、核对靠码 */
.perm-node {
  display: inline-flex;
  align-items: center;
  gap: var(--ja-space-2, 8px);
  min-width: 0;
}

.perm-name {
  color: var(--ja-ink);
}

.perm-code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 18px;
  color: var(--ja-ink-3);
  background: var(--ja-surface-2);
  border-radius: var(--ja-radius-sm, 4px);
  padding: 0 6px;
}

.footer-actions {
  display: flex;
  gap: var(--ja-space-3);
}
</style>
