<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width"
    :close-on-click-modal="closeOnClickModal"
    :destroy-on-close="destroyOnClose"
    class="detail-dialog"
  >
    <el-descriptions
      :column="column"
      :border="border"
      :direction="direction"
      :size="size"
    >
      <el-descriptions-item
        v-for="item in items"
        :key="item.key"
        :label="item.label"
        :span="item.span || 1"
      >
        <template v-if="item.slot">
          <slot :name="item.key" :value="data?.[item.key]" :row="data">
            {{ formatValue(data?.[item.key], item) }}
          </slot>
        </template>
        <template v-else-if="item.type === 'tag'">
          <el-tag :type="getTagType(item)" size="small">
            {{ formatValue(data?.[item.key], item) }}
          </el-tag>
        </template>
        <template v-else-if="item.type === 'boolean'">
          <el-tag :type="data?.[item.key] ? 'success' : 'danger'" size="small">
            {{ data?.[item.key] ? '是' : '否' }}
          </el-tag>
        </template>
        <template v-else-if="item.type === 'datetime'">
          {{ formatDateTime(data?.[item.key]) }}
        </template>
        <template v-else-if="item.type === 'json'">
          <pre class="json-pre">{{ JSON.stringify(data?.[item.key], null, 2) }}</pre>
        </template>
        <template v-else>
          {{ formatValue(data?.[item.key], item) }}
        </template>
      </el-descriptions-item>
    </el-descriptions>

    <template #footer>
      <span class="dialog-footer">
        <slot name="footer">
          <el-button @click="handleClose" size="default">{{ closeText }}</el-button>
        </slot>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

export interface DetailItem {
  key: string
  label: string
  span?: number
  type?: 'text' | 'tag' | 'boolean' | 'datetime' | 'json'
  slot?: boolean
  formatter?: (value: any, row: any) => string
  tagType?: string | ((value: any, row: any) => string)
}

interface Props {
  modelValue: boolean
  title: string
  data?: Record<string, any>
  items: DetailItem[]
  width?: string | number
  column?: number
  border?: boolean
  direction?: 'horizontal' | 'vertical'
  size?: 'large' | 'default' | 'small'
  closeOnClickModal?: boolean
  destroyOnClose?: boolean
  closeText?: string
}

const props = withDefaults(defineProps<Props>(), {
  width: '600px',
  column: 2,
  border: true,
  direction: 'horizontal',
  size: 'default',
  closeOnClickModal: true,
  destroyOnClose: true,
  closeText: '关闭'
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

const formatValue = (value: any, item: DetailItem): string => {
  if (item.formatter) {
    return item.formatter(value, props.data)
  }
  if (value === null || value === undefined || value === '') {
    return '—'
  }
  return String(value)
}

const getTagType = (item: DetailItem): string => {
  if (typeof item.tagType === 'function') {
    return item.tagType(props.data?.[item.key], props.data)
  }
  return item.tagType || 'info'
}

const formatDateTime = (value: any): string => {
  if (!value) return '—'
  try {
    const date = new Date(value)
    return `${date.getFullYear()  }-${ 
      String(date.getMonth() + 1).padStart(2, '0')  }-${ 
      String(date.getDate()).padStart(2, '0')  } ${ 
      String(date.getHours()).padStart(2, '0')  }:${ 
      String(date.getMinutes()).padStart(2, '0')  }:${ 
      String(date.getSeconds()).padStart(2, '0')}`
  } catch {
    return String(value)
  }
}

const handleClose = () => {
  emit('close')
  visible.value = false
}
</script>

<style scoped>
.detail-dialog :deep(.el-dialog__body) {
  padding: 20px 24px;
}

.json-pre {
  margin: 0;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
