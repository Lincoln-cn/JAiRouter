<template>
  <el-dialog v-model="visible" :title="title" center width="680px">
    <el-form ref="formRef" :model="model" label-width="120px" status-icon>
      <el-form-item :label="t('apiKeys.form.keyId')" prop="keyId" :rules="rules">
        <el-input v-model="keyId" :disabled="isEdit" maxlength="64" :placeholder="t('apiKeys.form.keyIdPlaceholder')"
                  show-word-limit/>
      </el-form-item>
      <el-form-item :label="t('apiKeys.form.description')">
        <el-input v-model="description" maxlength="128" :placeholder="t('apiKeys.form.descriptionPlaceholder')" show-word-limit
                  type="textarea"/>
      </el-form-item>
      <el-form-item :label="t('apiKeys.form.expiresAt')">
        <el-date-picker
            v-model="expiresAt"
            clearable
            format="YYYY-MM-DD HH:mm:ss"
            :placeholder="t('apiKeys.form.expiresAtPlaceholder')"
            style="width: 100%;"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
      <el-form-item :label="t('apiKeys.form.permissions')">
        <el-checkbox-group v-model="permissions">
          <el-checkbox label="chat">{{ t('apiKeys.permissions.chat') }}</el-checkbox>
          <el-checkbox label="embedding">{{ t('apiKeys.permissions.embedding') }}</el-checkbox>
          <el-checkbox label="rerank">{{ t('apiKeys.permissions.rerank') }}</el-checkbox>
          <el-checkbox label="tts">{{ t('apiKeys.permissions.tts') }}</el-checkbox>
          <el-checkbox label="stt">{{ t('apiKeys.permissions.stt') }}</el-checkbox>
          <el-checkbox label="imgGen">{{ t('apiKeys.permissions.imgGen') }}</el-checkbox>
          <el-checkbox label="imgEdit">{{ t('apiKeys.permissions.imgEdit') }}</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item :label="t('apiKeys.form.ipWhitelist')">
        <el-select
            v-model="allowedIpAddresses"
            allow-create
            clearable
            filterable
            multiple
            :placeholder="t('apiKeys.form.ipWhitelistPlaceholder')"
            style="width: 100%;"
        >
        </el-select>
        <div class="form-hint">{{ t('apiKeys.form.ipWhitelistHint') }}</div>
      </el-form-item>
      <el-form-item :label="t('apiKeys.form.dailyRequestLimit')">
        <el-input-number v-model="dailyRequestLimit" :min="0" :step="100" :placeholder="t('apiKeys.form.zeroMeansUnlimitedPlaceholder')"/>
        <div class="form-hint">{{ t('apiKeys.form.zeroMeansUnlimited') }}</div>
      </el-form-item>
      <el-form-item :label="t('apiKeys.form.dailyTokenLimit')">
        <el-input-number v-model="dailyTokenLimit" :min="0" :step="1000" :placeholder="t('apiKeys.form.zeroMeansUnlimitedPlaceholder')"/>
        <div class="form-hint">{{ t('apiKeys.form.tokenLimitHint') }}</div>
      </el-form-item>
      <el-form-item :label="t('apiKeys.form.rateLimitPerMinute')">
        <el-input-number v-model="rateLimitPerMinute" :min="0" :step="10" :placeholder="t('apiKeys.form.zeroMeansUnlimitedPlaceholder')"/>
        <div class="form-hint">{{ t('apiKeys.form.rateLimitHint') }}</div>
      </el-form-item>
      <el-form-item :label="t('apiKeys.form.quotaAlertThreshold')">
        <el-slider v-model="quotaAlertThreshold" :min="0.05" :max="1" :step="0.05" show-input :format-tooltip="(val: number) => `${Math.round(val * 100)}%`"/>
        <div class="form-hint">{{ t('apiKeys.form.thresholdHint') }}</div>
      </el-form-item>
      <el-form-item :label="t('apiKeys.form.rotationPeriod')">
        <el-input-number v-model="rotationPeriodDays" :min="0" :step="30" :placeholder="t('apiKeys.form.rotationPeriodPlaceholder')"/>
        <div class="form-hint">{{ t('apiKeys.form.rotationPeriodHint') }}</div>
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="visible = false">{{ t('apiKeys.actions.cancel') }}</el-button>
        <el-button type="primary" @click="emit('save')" :loading="saving">{{ t('apiKeys.actions.save') }}</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 创建/编辑 API 密钥弹窗。
 *
 * 拆分前是 `ApiKeyManagement.vue` 里 683 行的内联 el-dialog。表单值仍然存在
 * 父组件的 `form` 里（`useApiKeyManagement` 之外的单测直接读写它），这里通过
 * 逐字段 `defineModel` 双向绑定，既不改父组件的数据形状，也不在本组件内
 * 复制一份表单状态；校验由 `formRef` 在本组件内完成并通过 `validate()` 暴露，
 * 父组件的 `handleSave` 仍是「先后端再提示」的唯一执行者。
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormItemRule } from 'element-plus'
import type { ApiKeyFormModel } from './types'

defineProps<{
  /** 仅用于 el-form 的 :model（字段注册与 keyId 校验），值仍是父组件那一个对象 */
  model: ApiKeyFormModel
  title: string
  isEdit: boolean
  rules: FormItemRule[]
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'save'): void
}>()

const visible = defineModel<boolean>('visible', { required: true })
const keyId = defineModel<string>('keyId', { required: true })
const description = defineModel<string>('description', { required: true })
const expiresAt = defineModel<string>('expiresAt', { required: true })
const permissions = defineModel<string[]>('permissions', { required: true })
const allowedIpAddresses = defineModel<string[]>('allowedIpAddresses', { required: true })
const dailyRequestLimit = defineModel<number>('dailyRequestLimit', { required: true })
const dailyTokenLimit = defineModel<number>('dailyTokenLimit', { required: true })
const rateLimitPerMinute = defineModel<number>('rateLimitPerMinute', { required: true })
const quotaAlertThreshold = defineModel<number>('quotaAlertThreshold', { required: true })
const rotationPeriodDays = defineModel<number>('rotationPeriodDays', { required: true })

const { t } = useI18n()

const formRef = ref<FormInstance>()

/** 供父组件 handleSave 调用：与原 `if (formRef.value) await formRef.value.validate()` 等价 */
const validate = async (): Promise<void> => {
  if (formRef.value) {
    await formRef.value.validate()
  }
}

defineExpose({ validate })
</script>

<style scoped>
.form-hint {
  font-size: 12px;
  color: var(--ja-text-secondary);
  margin-top: 4px;
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
