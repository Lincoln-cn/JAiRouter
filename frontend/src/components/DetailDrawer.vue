<template>
  <el-drawer
    class="detail-drawer"
    :model-value="modelValue"
    :title="title"
    :size="width"
    :close-on-click-modal="true"
    @update:model-value="handleVisibleChange"
  >
    <div v-if="loading" class="detail-drawer__loading">
      <el-skeleton :rows="4" animated />
    </div>
    <slot v-else />
    <template v-if="$slots.footer" #footer>
      <slot name="footer" />
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
/**
 * 详情抽屉（设计 §4.8 的用户口径版本）：`v-model` 控制开合，`width` 直接映射
 * `el-drawer` 的 `size`（Element Plus 会给数字补 `px`）。
 *
 * `loading` 为真时用骨架屏替换默认内容；头部与 `footer` 插槽保持可用，
 * 便于调用方在加载期间仍能关闭抽屉或展示取消按钮。
 */
withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    /** 抽屉宽度，数字按 px 处理，也接受 `%`/`vw` 等字符串 */
    width?: number | string
    loading?: boolean
  }>(),
  { width: 480, loading: false }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const handleVisibleChange = (value: boolean): void => {
  emit('update:modelValue', value)
}
</script>

<style scoped>
.detail-drawer__loading {
  padding: var(--ja-space-2, 8px) 0;
}
</style>
