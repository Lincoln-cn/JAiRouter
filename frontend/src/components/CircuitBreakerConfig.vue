<template>
  <el-dialog
    v-model="dialogVisible"
    :title="title"
    width="600px"
    :before-close="handleClose"
  >
    <el-form :model="formData" label-width="140px" ref="formRef">
      <el-form-item label="启用熔断器">
        <el-switch
          v-model="formData.enabled"
          active-text="启用"
          inactive-text="禁用"
        />
      </el-form-item>

      <div v-if="formData.enabled">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="失败阈值">
              <el-input-number
                v-model="formData.failureThreshold"
                :min="1"
                :max="100"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="超时时间 (毫秒)">
              <el-input-number
                v-model="formData.timeout"
                :min="1000"
                :max="300000"
                :step="1000"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="成功阈值">
              <el-input-number
                v-model="formData.successThreshold"
                :min="1"
                :max="100"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </div>
    </el-form>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="loading">
          保存
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElForm } from 'element-plus'

interface Props {
  modelValue: boolean
  title?: string
  initialData?: any
}

const props = withDefaults(defineProps<Props>(), {
  title: '熔断器配置',
  initialData: () => ({})
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'save', data: any): void
}>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const formRef = ref<InstanceType<typeof ElForm>>()
const loading = ref(false)

const formData = reactive({
  enabled: false,
  failureThreshold: 5,
  timeout: 60000,
  successThreshold: 2
})

watch(() => props.initialData, (newData) => {
  if (newData && newData.circuitBreaker) {
    formData.enabled = newData.circuitBreaker.enabled ?? false
    formData.failureThreshold = newData.circuitBreaker.failureThreshold || 5
    formData.timeout = newData.circuitBreaker.timeout || 60000
    formData.successThreshold = newData.circuitBreaker.successThreshold || 2
  }
}, { immediate: true, deep: true })

const handleClose = () => {
  dialogVisible.value = false
  if (formRef.value) {
    formRef.value.resetFields()
  }
}

const handleSave = async () => {
  loading.value = true
  try {
    const result = {
      circuitBreaker: formData.enabled ? { ...formData } : null
    }
    emit('save', result)
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error('保存配置失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
