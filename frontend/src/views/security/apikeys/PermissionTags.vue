<template>
  <el-popover
      v-if="permissions.length > 3"
      trigger="click"
      :width="260"
      placement="left"
  >
    <template #reference>
      <el-tag size="small" class="permission-count">
        {{ t('apiKeys.permissionsCount', { count: permissions.length }) }}
      </el-tag>
    </template>
    <div class="permission-popover">
      <el-tag v-for="permission in permissions" :key="permission" size="small">
        {{ formatPermission(permission) }}
      </el-tag>
    </div>
  </el-popover>
  <div v-else-if="permissions.length" class="permission-tags">
    <el-tag
        v-for="permission in permissions"
        :key="permission"
        :type="getPermissionTagType(permission)"
        size="small"
    >
      {{ formatPermission(permission) }}
    </el-tag>
  </div>
  <span v-else>-</span>
</template>

<script setup lang="ts">
/**
 * 表格「权限」列的标签渲染：超过 3 个权限时折叠成计数 + 点击展开的 popover。
 *
 * 拆分前是 `ApiKeyManagement.vue` 里 el-table-column 作用域插槽的一段内联模板；
 * 这里只把 `row.permissions` 提升成 props，渲染与交互（trigger/width/placement）不变。
 */
import { useI18n } from 'vue-i18n'
import { useApiKeyDisplay } from './useApiKeyDisplay'

defineProps<{
  permissions: string[]
}>()

const { t } = useI18n()
const { formatPermission, getPermissionTagType } = useApiKeyDisplay()
</script>

<style scoped>
.permission-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-width: 140px;
  line-height: 1.8;
}

.permission-tags .el-tag {
  margin: 0;
}

.permission-count {
  cursor: pointer;
}

.permission-popover {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
