<template>
  <el-alert
    v-if="visible"
    :title="title"
    :type="type"
    :description="description"
    show-icon
    :closable="closable"
    @close="handleClose"
    class="page-alert"
  />
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

interface Props {
  modelValue?: boolean
  title?: string
  type?: 'success' | 'warning' | 'info' | 'error'
  description?: string
  closable?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: true,
  title: '',
  type: 'info',
  description: '',
  closable: true
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'close': []
}>()

const visible = ref(props.modelValue)

watch(() => props.modelValue, (val) => {
  visible.value = val
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

const handleClose = () => {
  emit('close')
  visible.value = false
}
</script>

<style scoped>
.page-alert {
  margin-bottom: 16px;
}
</style>
