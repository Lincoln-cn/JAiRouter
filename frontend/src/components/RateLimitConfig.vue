<template>
  <el-dialog
    v-model="dialogVisible"
    :title="title"
    width="600px"
    :before-close="handleClose"
  >
    <el-form :model="formData" label-width="140px" ref="formRef">
      <el-form-item label="启用限流">
        <el-switch
          v-model="formData.enabled"
          active-text="启用"
          inactive-text="禁用"
        />
      </el-form-item>

      <div v-if="formData.enabled">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="限流算法">
              <el-select v-model="formData.algorithm" placeholder="请选择算法">
                <el-option label="令牌桶" value="token-bucket" />
                <el-option label="漏桶" value="leaky-bucket" />
                <el-option label="滑动窗口" value="sliding-window" />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="作用域">
              <el-select v-model="formData.scope" placeholder="请选择作用域">
                <el-option label="实例级别" value="instance" />
                <el-option label="客户端 IP 级别" value="client-ip" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="容量">
              <el-input-number
                v-model="formData.capacity"
                :min="1"
                :max="10000"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="速率">
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
            <el-form-item label="限流键值">
              <el-input
                v-model="formData.key"
                placeholder="可选，用于自定义限流键"
              />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="客户端 IP 限流">
              <el-switch
                v-model="formData.clientIpEnable"
                active-text="启用"
                inactive-text="禁用"
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
  title: '限流器配置',
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
