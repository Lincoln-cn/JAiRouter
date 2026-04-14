<template>
  <div class="chat-config-panel">
    <el-row :gutter="16">
      <!-- 流式响应 -->
      <el-col :span="6">
        <div class="config-item">
          <label class="config-label">流式响应</label>
          <el-switch
            v-model="config.stream"
            :disabled="disabled"
          />
        </div>
      </el-col>

      <!-- Temperature -->
      <el-col :span="6">
        <div class="config-item">
          <label class="config-label">Temperature</label>
          <div class="config-control">
            <el-slider
              v-model="config.temperature"
              :min="0"
              :max="2"
              :step="0.1"
              :disabled="disabled"
              show-input
              :show-input-controls="false"
              size="small"
            />
          </div>
        </div>
      </el-col>

      <!-- Max Tokens -->
      <el-col :span="6">
        <div class="config-item">
          <label class="config-label">Max Tokens</label>
          <el-input-number
            v-model="config.maxTokens"
            :min="1"
            :max="32000"
            :step="256"
            :disabled="disabled"
            size="small"
            controls-position="right"
          />
        </div>
      </el-col>

      <!-- Top P -->
      <el-col :span="6">
        <div class="config-item">
          <label class="config-label">Top P</label>
          <div class="config-control">
            <el-slider
              v-model="config.topP"
              :min="0"
              :max="1"
              :step="0.1"
              :disabled="disabled"
              show-input
              :show-input-controls="false"
              size="small"
            />
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row
      :gutter="16"
      style="margin-top: 12px"
    >
      <!-- Frequency Penalty -->
      <el-col :span="6">
        <div class="config-item">
          <label class="config-label">Frequency Penalty</label>
          <div class="config-control">
            <el-slider
              v-model="config.frequencyPenalty"
              :min="-2"
              :max="2"
              :step="0.1"
              :disabled="disabled"
              show-input
              :show-input-controls="false"
              size="small"
            />
          </div>
        </div>
      </el-col>

      <!-- Presence Penalty -->
      <el-col :span="6">
        <div class="config-item">
          <label class="config-label">Presence Penalty</label>
          <div class="config-control">
            <el-slider
              v-model="config.presencePenalty"
              :min="-2"
              :max="2"
              :step="0.1"
              :disabled="disabled"
              show-input
              :show-input-controls="false"
              size="small"
            />
          </div>
        </div>
      </el-col>

      <!-- Stop Sequences -->
      <el-col :span="12">
        <div class="config-item">
          <label class="config-label">停止词</label>
          <el-select
            v-model="config.stop"
            :disabled="disabled"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入停止词"
            size="small"
            style="width: 100%"
          />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { ChatRequestConfig } from '../../types/playground'

interface Props {
  modelValue?: Partial<ChatRequestConfig>
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({
    stream: true,
    temperature: 0.7,
    maxTokens: 4096,
    topP: 1,
    frequencyPenalty: 0,
    presencePenalty: 0,
    stop: []
  }),
  disabled: false
})

const emit = defineEmits<{
  'update:modelValue': [config: Partial<ChatRequestConfig>]
}>()

const config = ref({
  stream: props.modelValue.stream ?? true,
  temperature: props.modelValue.temperature ?? 0.7,
  maxTokens: props.modelValue.maxTokens ?? 4096,
  topP: props.modelValue.topP ?? 1,
  frequencyPenalty: props.modelValue.frequencyPenalty ?? 0,
  presencePenalty: props.modelValue.presencePenalty ?? 0,
  stop: props.modelValue.stop ?? []
})

// 监听配置变化
watch(
  config,
  (val) => {
    emit('update:modelValue', { ...val })
  },
  { deep: true }
)

// 监听外部值变化
watch(
  () => props.modelValue,
  (val) => {
    config.value = {
      stream: val.stream ?? true,
      temperature: val.temperature ?? 0.7,
      maxTokens: val.maxTokens ?? 4096,
      topP: val.topP ?? 1,
      frequencyPenalty: val.frequencyPenalty ?? 0,
      presencePenalty: val.presencePenalty ?? 0,
      stop: val.stop ?? []
    }
  },
  { deep: true }
)
</script>

<style scoped>
.chat-config-panel {
  background-color: #f5f5f5;
  padding: 12px;
  border-radius: 8px;
}

.config-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.config-label {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
}

.config-control {
  width: 100%;
}

.config-control :deep(.el-slider__runway) {
  height: 4px;
}

.config-control :deep(.el-slider__button) {
  width: 12px;
  height: 12px;
}

.config-control :deep(.el-input-number) {
  width: 100%;
}

.config-control :deep(.el-slider__input) {
  width: 60px;
}

/* 响应式 */
@media (max-width: 768px) {
  .chat-config-panel :deep(.el-col) {
    margin-bottom: 12px;
  }
}
</style>