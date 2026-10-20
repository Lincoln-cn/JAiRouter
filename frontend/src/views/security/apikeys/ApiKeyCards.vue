<template>
  <div v-loading="loading" class="api-key-cards">
    <TableCards
      v-if="!loading || rows.length > 0"
      :rows="rows"
      row-key="keyId"
      :aria-label="t('apiKeys.cards.ariaLabel')"
      :empty-title="t('apiKeys.cards.emptyTitle')"
      :empty-description="t('apiKeys.cards.emptyDescription')"
    >
      <template #default="{ row }">
        <div class="api-key-card">
          <div class="api-key-card__head">
            <span class="api-key-card__title">{{ row.keyId }}</span>
            <div class="api-key-card__head-actions">
              <el-switch
                v-model="row.enabled"
                :disabled="row.expired"
                active-color="var(--ja-success)"
                inactive-color="var(--ja-danger)"
                :aria-label="t('apiKeys.cards.toggleStatusAria')"
                @change="emit('status-change', row)"
              />
              <el-tag v-if="row.quotaAlertTriggered" type="danger" size="small">
                <el-icon><Warning /></el-icon> {{ t('apiKeys.status.alert') }}
              </el-tag>
              <el-tag v-else type="success" size="small">{{ t('apiKeys.status.normal') }}</el-tag>
            </div>
          </div>
          <div class="api-key-card__meta">
            <div class="api-key-card__field api-key-card__field--full">
              <span class="api-key-card__label">{{ t('apiKeys.columns.description') }}</span>
              <span class="api-key-card__value">{{ row.description || '-' }}</span>
            </div>
            <div class="api-key-card__field">
              <span class="api-key-card__label">{{ t('apiKeys.columns.createdAt') }}</span>
              <span class="api-key-card__value">{{ formatDateTime(row.createdAt) }}</span>
            </div>
            <div class="api-key-card__field">
              <span class="api-key-card__label">{{ t('apiKeys.columns.expiresAt') }}</span>
              <span class="api-key-card__value">{{ formatDateTime(row.expiresAt) || t('apiKeys.neverExpires') }}</span>
            </div>
            <div class="api-key-card__field">
              <span class="api-key-card__label">{{ t('apiKeys.columns.remainingDays') }}</span>
              <span class="api-key-card__value">
                <el-tag :type="getRemainingDaysType(row)" size="small">{{ getRemainingDaysText(row) }}</el-tag>
              </span>
            </div>
            <div class="api-key-card__field">
              <span class="api-key-card__label">{{ t('apiKeys.columns.permissions') }}</span>
              <span class="api-key-card__value">
                {{ t('apiKeys.permissionsCount', { count: row.permissions?.length ?? 0 }) }}
              </span>
            </div>
            <div class="api-key-card__field">
              <span class="api-key-card__label">{{ t('apiKeys.columns.usage') }}</span>
              <span class="api-key-card__value">
                {{ row.totalRequests }} ({{ row.successfulRequests }}/{{ row.failedRequests }})
              </span>
            </div>
            <div class="api-key-card__field">
              <span class="api-key-card__label">{{ t('apiKeys.columns.todayToken') }}</span>
              <span class="api-key-card__value">
                {{ row.todayTokenUsage !== undefined && row.todayTokenUsage !== null
                  ? formatTokenCount(row.todayTokenUsage)
                  : '-' }}
              </span>
            </div>
          </div>
          <div class="api-key-card__actions">
            <el-button icon="Coin" size="small" type="primary" @click="emit('quota', row)">
              {{ t('apiKeys.quota') }}
            </el-button>
            <el-dropdown trigger="click" @command="(command: string) => emit('command', command, row)">
              <el-button size="small">
                {{ t('apiKeys.moreActions') }}<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="edit" icon="Edit">{{ t('apiKeys.actions.edit') }}</el-dropdown-item>
                  <el-dropdown-item command="rotate" icon="RefreshRight" :disabled="row.expired">{{ t('apiKeys.actions.rotate') }}</el-dropdown-item>
                  <el-dropdown-item command="reset" icon="Refresh">{{ t('apiKeys.actions.reset') }}</el-dropdown-item>
                  <el-dropdown-item command="resetQuota" icon="Timer">{{ t('apiKeys.actions.quotaReset') }}</el-dropdown-item>
                  <el-dropdown-item command="delete" icon="Delete" divided>
                    <span class="text-danger">{{ t('apiKeys.actions.delete') }}</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </template>
    </TableCards>
  </div>
</template>

<script setup lang="ts">
/**
 * 窄屏（≤767px）的卡片视图，由 `TableCards` 提供 `.table-cards__item` 容器。
 *
 * 拆分前是 `ApiKeyManagement.vue` 里的 `v-else` 分支；字段顺序、空态文案、
 * 行内「配额 / 更多」操作与表格视图完全一致，事件名与表格视图统一。
 */
import { useI18n } from 'vue-i18n'
import { Warning, ArrowDown } from '@element-plus/icons-vue'
import TableCards from '@/components/TableCards.vue'
import { formatDateTime } from '@/utils/format'
import type { ApiKeyVO } from '@/types'
import { useApiKeyDisplay } from './useApiKeyDisplay'

defineProps<{
  rows: ApiKeyVO[]
  loading: boolean
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
/* 窄屏卡片列表：占满表格区域并独立滚动，分页仍固定在底部 */
.api-key-cards {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--ja-space-4);
  position: relative;
}

.api-key-card {
  display: flex;
  flex-direction: column;
  gap: var(--ja-space-2, 8px);
  min-width: 0;
}

.api-key-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--ja-space-2, 8px);
}

.api-key-card__head-actions {
  display: flex;
  align-items: center;
  gap: var(--ja-space-2, 8px);
}

.api-key-card__title {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  font-weight: 600;
  color: var(--ja-ink);
  word-break: break-all;
}

.api-key-card__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--ja-space-1, 4px) var(--ja-space-3, 12px);
}

.api-key-card__field {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.api-key-card__field--full {
  grid-column: 1 / -1;
}

.api-key-card__label {
  font-size: 12px;
  color: var(--ja-ink-3);
}

.api-key-card__value {
  font-size: 13px;
  color: var(--ja-ink);
  overflow-wrap: anywhere;
}

.api-key-card__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--ja-space-2, 8px);
  padding-top: var(--ja-space-2, 8px);
  border-top: 1px solid var(--ja-border-light);
}

.api-key-card__actions .el-button {
  margin: 0;
}

@media (max-width: 420px) {
  .api-key-card__meta {
    grid-template-columns: minmax(0, 1fr);
  }
}

.text-danger {
  color: var(--ja-danger);
}
</style>
