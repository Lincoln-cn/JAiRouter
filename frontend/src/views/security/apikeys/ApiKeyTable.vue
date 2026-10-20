<template>
  <el-table
    :key="compact ? 'compact' : 'wide'"
    v-loading="loading"
    :data="rows"
    border
    stripe
    style="width: 100%"
    :class="{ 'compact-table': compact }"
  >
    <el-table-column :label="t('apiKeys.columns.keyId')" prop="keyId" :width="compact ? undefined : 180" :min-width="compact ? 80 : undefined">
      <template #default="scope">
        <el-tag effect="plain" type="info">{{ scope.row.keyId }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column v-if="!compact" :label="t('apiKeys.columns.description')" min-width="150" show-overflow-tooltip>
      <template #default="scope">
        <span>{{ scope.row.description || '-' }}</span>
      </template>
    </el-table-column>
    <el-table-column v-if="!compact" :label="t('apiKeys.columns.permissions')" prop="permissions" width="160">
      <template #default="scope">
        <PermissionTags :permissions="scope.row.permissions ?? []" />
      </template>
    </el-table-column>
    <el-table-column :label="t('apiKeys.columns.createdAt')" :width="compact ? undefined : 160" :min-width="compact ? 75 : undefined">
      <template #default="scope">
        {{ formatDateTime(scope.row.createdAt) }}
      </template>
    </el-table-column>
    <el-table-column v-if="!compact" :label="t('apiKeys.columns.createdBy')" width="150" show-overflow-tooltip>
      <template #default="scope">
        <span v-if="scope.row.createdBy">{{ scope.row.createdBy }}</span>
        <span v-else class="text-muted">-</span>
      </template>
    </el-table-column>
    <el-table-column :label="t('apiKeys.columns.expiresAt')" prop="expiresAt" :width="compact ? undefined : 160" :min-width="compact ? 75 : undefined">
      <template #default="scope">
        <span :class="{ 'expired-text': scope.row.expired }">
          {{ formatDateTime(scope.row.expiresAt) || t('apiKeys.neverExpires') }}
        </span>
      </template>
    </el-table-column>
    <el-table-column v-if="!compact" :label="t('apiKeys.columns.remainingDays')" width="100" align="center">
      <template #default="scope">
        <el-tag :type="getRemainingDaysType(scope.row)" size="small">
          {{ getRemainingDaysText(scope.row) }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column :label="t('apiKeys.columns.usage')" :width="compact ? 50 : 140" align="center">
      <template #default="scope">
        <el-tooltip :content="t('apiKeys.usageTooltip', { success: scope.row.successfulRequests, failed: scope.row.failedRequests })">
          <span class="usage-stat">
            {{ scope.row.totalRequests }}
            <span v-if="!compact" class="usage-detail">({{ scope.row.successfulRequests }}/{{ scope.row.failedRequests }})</span>
          </span>
        </el-tooltip>
      </template>
    </el-table-column>
    <el-table-column :label="t('apiKeys.columns.todayToken')" :width="compact ? 45 : 120" align="center">
      <template #default="scope">
        <span v-if="scope.row.todayTokenUsage !== undefined && scope.row.todayTokenUsage !== null">
          {{ formatTokenCount(scope.row.todayTokenUsage) }}
        </span>
        <span v-else class="text-muted">-</span>
      </template>
    </el-table-column>
    <el-table-column :label="t('apiKeys.columns.alertStatus')" :width="compact ? 45 : 100" align="center">
      <template #default="scope">
        <el-tag v-if="scope.row.quotaAlertTriggered" type="danger" size="small">
          <el-icon v-if="!compact"><Warning /></el-icon> {{ t('apiKeys.status.alert') }}
        </el-tag>
        <el-tag v-else type="success" size="small">{{ t('apiKeys.status.normal') }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column :label="t('apiKeys.columns.status')" prop="enabled" :width="compact ? 50 : 80" align="center">
      <template #default="scope">
        <el-switch
            v-model="scope.row.enabled"
            :disabled="scope.row.expired"
            active-color="var(--ja-success)"
            inactive-color="var(--ja-danger)"
            @change="emit('status-change', scope.row)"
        />
      </template>
    </el-table-column>
    <el-table-column fixed="right" :label="t('apiKeys.columns.actions')" :width="compact ? 75 : 150">
      <template #default="scope">
        <div class="row-actions">
          <el-button
            v-if="!compact"
            icon="Coin"
            size="small"
            type="primary"
            @click="emit('quota', scope.row)"
          >{{ t('apiKeys.quota') }}</el-button>
          <el-button
            v-else
            icon="Coin"
            size="small"
            type="primary"
            :aria-label="t('apiKeys.quota')"
            :title="t('apiKeys.quota')"
            @click="emit('quota', scope.row)"
          />
          <el-dropdown trigger="click" @command="(command: string) => emit('command', command, scope.row)">
            <el-button size="small" :aria-label="t('apiKeys.moreActions')" :title="t('apiKeys.moreActions')">
              <template v-if="!compact">
                {{ t('apiKeys.moreActions') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </template>
              <el-icon v-else><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit" icon="Edit">{{ t('apiKeys.actions.edit') }}</el-dropdown-item>
                <el-dropdown-item command="rotate" icon="RefreshRight" :disabled="scope.row.expired">{{ t('apiKeys.actions.rotate') }}</el-dropdown-item>
                <el-dropdown-item command="reset" icon="Refresh">{{ t('apiKeys.actions.reset') }}</el-dropdown-item>
                <el-dropdown-item command="resetQuota" icon="Timer">{{ t('apiKeys.actions.quotaReset') }}</el-dropdown-item>
                <el-dropdown-item command="delete" icon="Delete" divided>
                  <span class="text-danger">{{ t('apiKeys.actions.delete') }}</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
/**
 * 宽屏（非 narrow）下的 API Key 主表格。
 *
 * 拆分前是 `ApiKeyManagement.vue` 里的 `.table-wrapper > el-table` 整块模板。
 * 列定义、列宽（compact 分支）、tooltip 文案与操作区结构逐字保留；
 * 行内动作只上抛事件（quota / command / status-change），业务逻辑仍在父组件，
 * 因此「配额」按钮、「更多」下拉的命令名与文案都不变。
 */
import { useI18n } from 'vue-i18n'
import { Warning, ArrowDown } from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils/format'
import type { ApiKeyVO } from '@/types'
import PermissionTags from './PermissionTags.vue'
import { useApiKeyDisplay } from './useApiKeyDisplay'

defineProps<{
  rows: ApiKeyVO[]
  loading: boolean
  compact: boolean
}>()

const emit = defineEmits<{
  (e: 'quota', row: ApiKeyVO): void
  (e: 'command', command: string, row: ApiKeyVO): void
  (e: 'status-change', row: ApiKeyVO): void
}>()

const { t } = useI18n()
const { getRemainingDaysText, getRemainingDaysType, formatTokenCount } = useApiKeyDisplay()
</script>

<style scoped>
/* 表格占满卡片剩余高度并独立滚动（父组件的 .table-wrapper 是 flex 列容器） */
.el-table {
  flex: 1;
  overflow: auto;
}

.expired-text {
  color: var(--ja-danger);
}

.usage-stat {
  font-weight: 500;
  color: var(--ja-primary);
}

.usage-detail {
  font-size: 12px;
  color: var(--ja-text-secondary);
  margin-left: 4px;
}

/* 行操作：1 个主操作（配额）+ 1 个「更多」下拉，列宽收紧到 150 */
.row-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.row-actions .el-button {
  margin: 0;
  padding-left: 8px;
  padding-right: 8px;
}

/* compact：收窄单元格内边距与字号，保证 768–1024px 下不出现横向滚动 */
.compact-table :deep(.cell) {
  padding: 0 3px;
  font-size: 11px;
}

.compact-table :deep(.el-tag) {
  padding: 0 4px;
}

.text-danger {
  color: var(--ja-danger);
}
</style>
