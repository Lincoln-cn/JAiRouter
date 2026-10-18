<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="600px"
    :before-close="handleClose"
  >
    <el-form :model="formData" label-width="140px" ref="formRef">
      <el-form-item :label="t('circuitBreakerConfig.enableToggle')">
        <el-switch
          v-model="formData.enabled"
          :active-text="t('circuitBreakerConfig.enabled')"
          :inactive-text="t('circuitBreakerConfig.disabled')"
        />
      </el-form-item>

      <div v-if="formData.enabled">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('circuitBreakerConfig.failureThreshold')">
              <el-input-number
                v-model="formData.failureThreshold"
                :min="1"
                :max="100"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item :label="t('circuitBreakerConfig.timeout')">
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
            <el-form-item :label="t('circuitBreakerConfig.successThreshold')">
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
        <el-button @click="handleClose">{{ t('circuitBreakerConfig.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave" :loading="loading">
          {{ t('circuitBreakerConfig.save') }}
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElForm } from 'element-plus'

interface Props {
  modelValue: boolean
  title?: string
  initialData?: CircuitBreakerConfigInitialData
}

/** 熔断表单载荷（与 `formData` 同形，保存时整块下发） */
export interface CircuitBreakerFormData {
  enabled: boolean
  failureThreshold: number
  timeout: number
  successThreshold: number
}

/** 弹窗入参：父页面持有整份实例策略，本组件只关心 `circuitBreaker` 这一段（#521） */
export interface CircuitBreakerConfigInitialData {
  circuitBreaker?: Partial<CircuitBreakerFormData> | null
}

/** `save` 事件的载荷：关闭开关时显式下发 `null` 表示「清除该段配置」 */
export interface CircuitBreakerConfigPayload {
  circuitBreaker: CircuitBreakerFormData | null
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  initialData: () => ({})
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'save', data: CircuitBreakerConfigPayload): void
}>()

const { t } = useI18n()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const dialogTitle = computed(() => props.title || t('circuitBreakerConfig.title'))

const formRef = ref<InstanceType<typeof ElForm>>()
const loading = ref(false)

const formData = reactive<CircuitBreakerFormData>({
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
    ElMessage.error(t('circuitBreakerConfig.messages.saveFailed'))
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
