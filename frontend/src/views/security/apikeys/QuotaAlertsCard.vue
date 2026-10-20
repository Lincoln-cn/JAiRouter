<template>
  <el-card class="quota-alerts-card" shadow="hover" v-loading="loading">
    <template #header>
      <div class="card-header">
        <span class="main-title">
          <el-icon><Warning /></el-icon>
          {{ t('apiKeys.quotaAlertsTitle') }}
          <el-badge :value="alerts.length" type="danger" style="margin-left: 8px" />
        </span>
        <el-button
          icon="Refresh"
          size="small"
          @click="emit('refresh')"
          :loading="loading"
        >
          {{ t('apiKeys.refresh') }}
        </el-button>
      </div>
    </template>

    <el-table :data="alerts" stripe size="small" style="width: 100%">
      <el-table-column prop="keyId" :label="t('apiKeys.columns.keyId')" width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tag effect="plain" type="info">{{ row.keyId }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" :label="t('apiKeys.columns.description')" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.description || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="alertType" :label="t('apiKeys.columns.alertType')" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="getAlertTypeTag(row.alertType)" size="small">
            {{ formatAlertType(row.alertType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('apiKeys.columns.requestUsage')" width="110" align="center">
        <template #default="{ row }">
          <span :class="{ 'alert-high': row.dailyRequestUsagePercent >= 90 }">
            {{ formatPercent(row.dailyRequestUsagePercent) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column :label="t('apiKeys.columns.tokenUsage')" width="110" align="center">
        <template #default="{ row }">
          <span :class="{ 'alert-high': row.dailyTokenUsagePercent >= 90 }">
            {{ formatPercent(row.dailyTokenUsagePercent) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="message" :label="t('apiKeys.columns.alertDetail')" min-width="200" show-overflow-tooltip />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
/**
 * 配额告警整卡（仅在有告警时由父组件挂载）。
 *
 * 拆分前是 `ApiKeyManagement.vue` 模板里的一段内联 el-card + 三个展示格式化函数。
 * 数据与加载态仍由父组件持有（保存/重置配额后父组件会刷新告警），
 * 这里只负责渲染与「刷新」按钮的事件上抛。
 */
import { useI18n } from 'vue-i18n'
import { Warning } from '@element-plus/icons-vue'
import type { QuotaAlertInfo } from '@/types'

defineProps<{
  alerts: QuotaAlertInfo[]
  loading: boolean
}>()

const emit = defineEmits<{
  (e: 'refresh'): void
}>()

const { t } = useI18n()

/**
 * 格式化告警类型显示
 */
const formatAlertType = (alertType: string): string => {
  switch (alertType) {
    case 'REQUEST_QUOTA': return t('apiKeys.alertTypes.request')
    case 'TOKEN_QUOTA': return t('apiKeys.alertTypes.token')
    case 'GENERAL': return t('apiKeys.alertTypes.general')
    default: return alertType || t('apiKeys.alertTypes.unknown')
  }
}

/**
 * 获取告警类型标签类型
 */
const getAlertTypeTag = (alertType: string): 'danger' | 'warning' | 'info' => {
  switch (alertType) {
    case 'REQUEST_QUOTA': return 'danger'
    case 'TOKEN_QUOTA': return 'warning'
    default: return 'info'
  }
}

/**
 * 格式化百分比
 */
const formatPercent = (val: number): string => {
  if (val < 0) return '-'
  return `${val.toFixed(1)}%`
}
</script>

<style scoped>
.quota-alerts-card {
  margin-bottom: 16px;
}

.card-header {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.main-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--ja-text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
}

.alert-high {
  color: var(--ja-danger, #f56c6c);
  font-weight: 600;
}
</style>
