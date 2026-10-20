<template>
  <span class="confirm-action">
    <el-popconfirm
      v-if="interactive"
      :title="title"
      :confirm-button-text="confirmText"
      :cancel-button-text="cancelText"
      :confirm-button-type="type"
      @confirm="handleConfirm"
    >
      <template #reference>
        <span
          ref="triggerRef"
          class="confirm-action__trigger"
          tabindex="0"
          @keydown="handleKeydown"
        >
          <slot />
        </span>
      </template>
    </el-popconfirm>

    <span
      v-else
      ref="triggerRef"
      class="confirm-action__trigger is-disabled"
      tabindex="-1"
      aria-disabled="true"
    >
      <slot />
    </span>
  </span>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

/**
 * 二次确认触发器（设计 §4.7 的用户口径版本）：默认插槽放触发元素
 * （通常是 `el-button`），点击后由 `el-popconfirm` 弹出确认气泡，
 * 确认时 emit `confirm`。
 *
 * - `disabled` / `loading` 时不渲染 `el-popconfirm`，触发器带
 *   `aria-disabled="true"` 且 `pointer-events: none`，`handleConfirm`
 *   里再做一次守卫，保证不会重复 emit。
 * - 键盘：触发器的 Enter / Space 会转发为一次 click（气泡由
 *   `el-popconfirm` 自己打开），确认气泡内的按钮由 Element Plus
 *   保证键盘可达。
 */
const props = withDefaults(
  defineProps<{
    /** 确认气泡文案 */
    title: string
    confirmText?: string
    cancelText?: string
    type?: 'primary' | 'danger' | 'warning'
    disabled?: boolean
    loading?: boolean
  }>(),
  { type: 'primary', disabled: false, loading: false, confirmText: undefined, cancelText: undefined }
)

const emit = defineEmits<{
  (e: 'confirm'): void
}>()

const triggerRef = ref<HTMLElement | null>(null)
const interactive = computed(() => !props.disabled && !props.loading)

const handleConfirm = (): void => {
  if (!interactive.value) return
  emit('confirm')
}

const handleKeydown = (event: KeyboardEvent): void => {
  if (!interactive.value) return
  if (event.key !== 'Enter' && event.key !== ' ') return
  // 内层按钮会自己派发 click；只处理落在触发器自身上的按键
  if (event.target !== triggerRef.value) return
  event.preventDefault()
  triggerRef.value?.click()
}
</script>

<style scoped>
.confirm-action {
  display: inline-flex;
  min-width: 0;
}

.confirm-action__trigger {
  display: inline-flex;
  align-items: center;
  border-radius: var(--ja-radius, 4px);
  outline-offset: 2px;
}

.confirm-action__trigger:focus-visible {
  outline: 2px solid var(--ja-primary);
}

.confirm-action__trigger.is-disabled {
  opacity: 0.6;
  cursor: not-allowed;
  pointer-events: none;
}
</style>
