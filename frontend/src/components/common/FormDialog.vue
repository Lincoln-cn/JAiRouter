<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width"
    :before-close="handleClose"
    :close-on-click-modal="closeOnClickModal"
    :destroy-on-close="destroyOnClose"
    class="form-dialog"
  >
    <el-form
      ref="formRef"
      :model="modelValue"
      :rules="rules"
      :label-width="labelWidth"
      :label-position="labelPosition"
      v-loading="loading"
    >
      <slot />
    </el-form>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleCancel" size="default">{{ cancelText }}</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="loading" size="default">
          {{ submitText }}
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

interface Props {
  modelValue: boolean
  title: string
  width?: string | number
  loading?: boolean
  rules?: FormRules
  labelWidth?: string | number
  labelPosition?: 'left' | 'right' | 'top'
  closeOnClickModal?: boolean
  destroyOnClose?: boolean
  cancelText?: string
  submitText?: string
}

const props = withDefaults(defineProps<Props>(), {
  width: '600px',
  loading: false,
  rules: () => ({}),
  labelWidth: '120px',
  labelPosition: 'right',
  closeOnClickModal: false,
  destroyOnClose: true,
  cancelText: '取消',
  submitText: '保存'
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'submit': []
  'cancel': []
  'close': []
}>()

const formRef = ref<FormInstance>()
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

const handleCancel = () => {
  emit('cancel')
  visible.value = false
}

const handleSubmit = async () => {
  if (!formRef.value) {
    emit('submit')
    return
  }

  try {
    await formRef.value.validate()
    emit('submit')
  } catch (error) {
    // 验证失败，不提交
  }
}

const validate = async () => {
  if (!formRef.value) return true
  return formRef.value.validate()
}

const resetFields = () => {
  formRef.value?.resetFields()
}

const clearValidate = (props?: string | string[]) => {
  formRef.value?.clearValidate(props)
}

defineExpose({
  validate,
  resetFields,
  clearValidate,
  formRef
})
</script>

<style scoped>
.form-dialog :deep(.el-dialog__body) {
  padding: 20px 24px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
