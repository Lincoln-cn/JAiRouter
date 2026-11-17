<template>
  <div class="config-section" :class="{ 'is-error': !!error }">
    <div class="section-header">
      <div class="section-title-line">
        <span class="section-title">{{ title }}</span>
        <el-tag v-if="restartRequired" size="small" type="warning" effect="plain">{{ t('configShell.restartRequired') }}</el-tag>
      </div>
      <div v-if="$slots.actions" class="section-actions">
        <slot name="actions" />
      </div>
    </div>
    <p v-if="description" class="section-desc">{{ description }}</p>
    <slot />
    <p v-if="error" class="section-error">
      <el-icon><WarningFilled /></el-icon>
      <span>{{ error }}</span>
    </p>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { WarningFilled } from '@element-plus/icons-vue'

withDefaults(defineProps<{
  title: string
  description?: string
  /** 该分区有字段需重启才生效（后端只改内存时用它提醒） */
  restartRequired?: boolean
  /** 分区级错误：保存失败时由 ConfigPageShell 的 useConfigDraft 填充 */
  error?: string
}>(), { description: '', restartRequired: false, error: '' })

const { t } = useI18n()
</script>

<style scoped>
.config-section {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ja-radius-md, 8px);
  padding: var(--ja-space-4, 16px);
  margin-bottom: var(--ja-space-4, 16px);
  background: var(--el-bg-color);
  transition: border-color 0.2s;
}

.config-section.is-error {
  border-color: var(--el-color-danger);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.section-title-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ja-text-primary);
}

.section-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.section-desc {
  margin: 6px 0 14px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.section-error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--el-color-danger);
}
</style>
