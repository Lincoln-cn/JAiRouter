<template>
  <div class="form-section">
    <el-divider v-if="title" content-position="left">
      {{ title }}
    </el-divider>
    <slot />
  </div>
</template>

<script setup lang="ts">
interface Props {
  title?: string
}

withDefaults(defineProps<Props>(), {
  title: ''
})
</script>

<style scoped>
.form-section {
  margin-bottom: 16px;
}

.form-section :deep(.el-divider) {
  margin: 16px 0;
}

.form-section :deep(.el-divider__text) {
  font-size: 14px;
  font-weight: 500;
  color: #606266;
}
</style>
