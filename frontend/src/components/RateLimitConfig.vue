<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="600px"
    :before-close="handleClose"
  >
    <el-form :model="formData" label-width="140px" ref="formRef">
      <el-form-item :label="t('rateLimitConfig.enableToggle')">
        <el-switch
          v-model="formData.enabled"
          :active-text="t('rateLimitConfig.enabled')"
          :inactive-text="t('rateLimitConfig.disabled')"
        />
      </el-form-item>

      <div v-if="formData.enabled">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('rateLimitConfig.algorithm')">
              <el-select v-model="formData.algorithm" :placeholder="t('rateLimitConfig.algorithmPlaceholder')">
                <el-option :label="t('rateLimitConfig.algorithms.tokenBucket')" value="token-bucket" />
                <el-option :label="t('rateLimitConfig.algorithms.leakyBucket')" value="leaky-bucket" />
                <el-option :label="t('rateLimitConfig.algorithms.slidingWindow')" value="sliding-window" />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item :label="t('rateLimitConfig.scope')">
              <el-select v-model="formData.scope" :placeholder="t('rateLimitConfig.scopePlaceholder')">
                <el-option :label="t('rateLimitConfig.scopes.instance')" value="instance" />
                <el-option :label="t('rateLimitConfig.scopes.clientIp')" value="client-ip" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('rateLimitConfig.capacity')">
              <el-input-number
                v-model="formData.capacity"
                :min="1"
                :max="10000"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item :label="t('rateLimitConfig.rate')">
              <el-input-number
                v-model="formData.rate"
                :min="1"
                :max="10000"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item :label="t('rateLimitConfig.key')">
              <el-input
                v-model="formData.key"
                :placeholder="t('rateLimitConfig.keyPlaceholder')"
              />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item :label="t('rateLimitConfig.clientIpRateLimit')">
              <el-switch
                v-model="formData.clientIpEnable"
                :active-text="t('rateLimitConfig.enabled')"
                :inactive-text="t('rateLimitConfig.disabled')"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </div>
    </el-form>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">{{ t('rateLimitConfig.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave" :loading="loading">
          {{ t('rateLimitConfig.save') }}
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
  initialData?: any
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  initialData: () => ({})
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'save', data: any): void
}>()

const { t } = useI18n()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const dialogTitle = computed(() => props.title || t('rateLimitConfig.title'))

const formRef = ref<InstanceType<typeof ElForm>>()
const loading = ref(false)

const formData = reactive({
  enabled: false,
  algorithm: 'token-bucket',
  capacity: 100,
  rate: 10,
  scope: 'instance',
  key: '',
  clientIpEnable: false
})

watch(() => props.initialData, (newData) => {
  if (newData && newData.rateLimit) {
    formData.enabled = newData.rateLimit.enabled ?? false
    formData.algorithm = newData.rateLimit.algorithm || 'token-bucket'
    formData.capacity = newData.rateLimit.capacity || 100
    formData.rate = newData.rateLimit.rate || 10
    formData.scope = newData.rateLimit.scope || 'instance'
    formData.key = newData.rateLimit.key || ''
    formData.clientIpEnable = newData.rateLimit.clientIpEnable ?? false
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
      rateLimit: formData.enabled ? { ...formData } : null
    }
    emit('save', result)
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(t('rateLimitConfig.messages.saveFailed'))
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
