<template>
  <el-empty
    :image-size="imageSize"
    :description="description"
    class="empty-state"
  >
    <template #image>
      <img v-if="image" :src="image" alt="empty" />
      <slot v-else name="image">
        <img src="https://static-element.eleme.cn/e/element-ui/empty.svg" alt="empty" />
      </slot>
    </template>
    <template #default>
      <slot>
        <el-button v-if="showAction" type="primary" @click="handleAction">
          {{ actionText }}
        </el-button>
      </slot>
    </template>
  </el-empty>
</template>

<script setup lang="ts">
interface Props {
  description?: string
  imageSize?: number
  image?: string
  showAction?: boolean
  actionText?: string
}

const props = withDefaults(defineProps<Props>(), {
  description: '暂无数据',
  imageSize: 120,
  showAction: false,
  actionText: '添加'
})

const emit = defineEmits<{
  'action': []
}>()

const handleAction = () => {
  emit('action')
}
</script>

<style scoped>
.empty-state {
  padding: 40px 0;
}

.empty-state :deep(.el-empty__image) {
  width: var(--el-empty-image-width);
}
</style>
