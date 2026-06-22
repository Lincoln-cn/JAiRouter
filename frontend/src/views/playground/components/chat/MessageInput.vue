<template>
  <div class="message-input">
    <div class="input-wrapper">
      <el-input
        ref="textareaRef"
        v-model="inputContent"
        type="textarea"
        :autosize="{ minRows: 1, maxRows: 6 }"
        :placeholder="placeholder"
        :disabled="disabled"
        :maxlength="maxLength"
        resize="none"
        class="input-textarea"
        @keydown="handleKeyDown"
      />
      <div class="input-actions">
        <el-button
          v-if="loading"
          type="danger"
          circle
          size="small"
          @click="handleCancel"
        >
          <el-icon><Close /></el-icon>
        </el-button>
        <el-button
          v-else
          type="primary"
          circle
          size="small"
          :disabled="disabled || !inputContent.trim()"
          @click="handleSend"
        >
          <el-icon><Promotion /></el-icon>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { Promotion, Close } from '@element-plus/icons-vue'
import { ElInput } from 'element-plus'

interface Props {
  disabled?: boolean
  loading?: boolean
  placeholder?: string
  maxLength?: number
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  loading: false,
  placeholder: '输入消息...',
  maxLength: 8000
})

const emit = defineEmits<{
  submit: [content: string]
  cancel: []
}>()

const inputContent = ref('')
const textareaRef = ref<InstanceType<typeof ElInput>>()

// 处理键盘事件
const handleKeyDown = (event: Event | KeyboardEvent) => {
  const kbEvent = event as KeyboardEvent
  if (kbEvent.key === 'Enter' && !kbEvent.shiftKey) {
    kbEvent.preventDefault()
    // 必须检查 disabled 和 loading 状态，否则会在禁用状态下仍然发送
    if (props.disabled || props.loading || !inputContent.value.trim()) {
      return
    }
    handleSend()
  }
}

// 发送消息
const handleSend = () => {
  if (!inputContent.value.trim() || props.disabled || props.loading) return

  emit('submit', inputContent.value)
}

// 取消请求
const handleCancel = () => {
  emit('cancel')
}

// 清空输入
const clear = () => {
  inputContent.value = ''
  nextTick(() => {
    textareaRef.value?.focus()
  })
}

// 设置内容
const setContent = (content: string) => {
  inputContent.value = content
  nextTick(() => {
    textareaRef.value?.focus()
  })
}

// 获取焦点
const focus = () => {
  textareaRef.value?.focus()
}

// 暴露方法
defineExpose({
  clear,
  setContent,
  focus
})
</script>

<style scoped>
.message-input {
  width: 100%;
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  background-color: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 12px;
  padding: 8px 12px;
  transition: border-color 0.2s;
}

.input-wrapper:focus-within {
  border-color: #409eff;
}

.input-textarea {
  flex: 1;
}

.input-textarea :deep(.el-textarea__inner) {
  border: none;
  padding: 0;
  box-shadow: none;
  resize: none;
  font-size: 14px;
  line-height: 1.5;
  background-color: transparent;
}

.input-textarea :deep(.el-textarea__inner:focus) {
  border: none;
  box-shadow: none;
}

.input-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}
</style>