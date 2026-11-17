<template>
  <span class="settings-switch">
    <el-switch
      :model-value="modelValue"
      :loading="pending"
      :disabled="disabled || pending"
      @update:model-value="handleToggle"
    />
    <span v-if="immediateHint" class="switch-hint">{{ immediateHint }}</span>
  </span>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 即时生效的布尔开关。
 *
 * <p>为什么单独做一个组件：配置页里「开关」有两种语义 —— 草稿（等页脚保存）与
 * 即时（拨动即写库）。同一个页面混用两种语义时，用户无法从外观判断，这是
 * 「页面表现很奇怪」的主要来源之一。这里把即时语义固化：拨动 → 提交 → 失败
 * 由受控值自动回弹（父组件不更新 modelValue，开关就停在原位）。</p>
 */
const props = withDefaults(defineProps<{
  modelValue: boolean
  disabled?: boolean
  /** 提交函数；抛错即视为失败，开关回弹 */
  submit: (next: boolean) => Promise<void>
  /** 二次确认文案（危险开关必填） */
  confirmMessage?: string
  confirmTitle?: string
  /** 开关右侧的小字说明，例如「立即生效」 */
  immediateHint?: string
}>(), {
  disabled: false,
  confirmMessage: '',
  confirmTitle: '',
  immediateHint: ''
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'failed', error: unknown): void
}>()

const pending = ref(false)

const messageOf = (error: unknown): string => {
  if (error instanceof Error && error.message) {
    return error.message
  }
  return typeof error === 'string' && error ? error : ''
}

const handleToggle = async (next: boolean): Promise<void> => {
  if (props.disabled || pending.value) {
    return
  }
  if (props.confirmMessage) {
    try {
      await ElMessageBox.confirm(props.confirmMessage, props.confirmTitle || props.confirmMessage, {
        type: 'warning',
        confirmButtonText: '确认',
        cancelButtonText: '取消'
      })
    } catch {
      return
    }
  }
  pending.value = true
  try {
    await props.submit(next)
    emit('update:modelValue', next)
  } catch (error) {
    emit('failed', error)
    const message = messageOf(error)
    if (message) {
      ElMessage.error(message)
    }
  } finally {
    pending.value = false
  }
}
</script>

<style scoped>
.settings-switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.switch-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
