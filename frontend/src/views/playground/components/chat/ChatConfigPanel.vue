<template>
  <div class="chat-config-panel">
    <el-row :gutter="16">
      <!-- 流式响应 -->
      <el-col :span="6">
        <div class="config-item">
          <label class="config-label">流式响应</label>
          <el-switch
            v-model="stream"
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
              v-model="temperature"
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
            v-model="maxTokens"
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
              v-model="topP"
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
              v-model="frequencyPenalty"
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
              v-model="presencePenalty"
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
            v-model="stop"
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
import { computed } from 'vue'
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

// 为每个属性创建独立的 computed getter/setter
// 这样 v-model 绑定才能正确触发更新
const stream = computed({
  get: () => props.modelValue.stream ?? true,
  set: (val) => emit('update:modelValue', { ...props.modelValue, stream: val })
})

const temperature = computed({
  get: () => props.modelValue.temperature ?? 0.7,
  set: (val) => emit('update:modelValue', { ...props.modelValue, temperature: val })
})

const maxTokens = computed({
  get: () => props.modelValue.maxTokens ?? 4096,
  set: (val) => emit('update:modelValue', { ...props.modelValue, maxTokens: val })
})

const topP = computed({
  get: () => props.modelValue.topP ?? 1,
  set: (val) => emit('update:modelValue', { ...props.modelValue, topP: val })
})

const frequencyPenalty = computed({
  get: () => props.modelValue.frequencyPenalty ?? 0,
  set: (val) => emit('update:modelValue', { ...props.modelValue, frequencyPenalty: val })
})

const presencePenalty = computed({
  get: () => props.modelValue.presencePenalty ?? 0,
  set: (val) => emit('update:modelValue', { ...props.modelValue, presencePenalty: val })
})

const stop = computed({
  get: () => props.modelValue.stop ?? [],
  set: (val) => emit('update:modelValue', { ...props.modelValue, stop: val })
})
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