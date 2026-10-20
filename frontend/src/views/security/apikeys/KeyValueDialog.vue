<template>
  <el-dialog v-model="visible" :close-on-click-modal="false" center :title="t('apiKeys.apiKeyCreatedTitle')" width="450px">
    <div class="key-value-dialog-content">
      <el-icon style="font-size: 48px; color: var(--ja-primary); margin-bottom: 16px;">
        <Key />
      </el-icon>
      <p class="key-value-tip">{{ t('apiKeys.keyValueDialog.tip') }}</p>
      <el-input :model-value="keyValue" readonly size="large">
        <template #append>
          <el-button icon="CopyDocument" type="primary" @click="emit('copy')">{{ t('apiKeys.actions.copy') }}</el-button>
        </template>
      </el-input>
      <el-alert :closable="false" show-icon style="margin-top: 16px;" type="warning">
        <template #title>
          <strong>{{ t('apiKeys.keyValueDialog.important') }}</strong>
        </template>
        {{ t('apiKeys.keyValueDialog.warning') }}
      </el-alert>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <el-button type="primary" @click="emit('close')" size="large">{{ t('apiKeys.actions.savedClose') }}</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 「创建/重置/轮换成功」后展示明文密钥值的一次性弹窗。
 *
 * 拆分前是 `ApiKeyManagement.vue` 里的内联 el-dialog。密钥值本身只读，
 * 因此这里用 `:model-value` 而不是 `v-model`：写入方仍是父组件（创建/重置/轮换的
 * 返回值），复制与关闭分别上抛 `copy` / `close`，父组件的复制文案与清空时机不变。
 */
import { useI18n } from 'vue-i18n'
import { Key } from '@element-plus/icons-vue'

defineProps<{
  keyValue: string
}>()

const emit = defineEmits<{
  (e: 'copy'): void
  (e: 'close'): void
}>()

const visible = defineModel<boolean>('visible', { required: true })

const { t } = useI18n()
</script>

<style scoped>
.key-value-dialog-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  text-align: center;
}

.key-value-tip {
  font-size: 16px;
  color: var(--ja-text-primary);
  margin-bottom: 16px;
  font-weight: 500;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

:deep(.el-dialog__header) {
  border-bottom: 1px solid var(--ja-border-light);
  padding-bottom: 16px;
}

:deep(.el-dialog__footer) {
  border-top: 1px solid var(--ja-border-light);
  padding-top: 16px;
}
</style>
