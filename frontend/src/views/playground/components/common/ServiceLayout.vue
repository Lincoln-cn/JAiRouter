<template>
  <div class="service-layout">
    <!-- 顶部工具栏 -->
    <div class="service-header">
      <div class="header-left">
        <slot name="header-left">
          <ModelSelector
            v-model="selectedModel"
            :instances="instances"
            :loading="instancesLoading"
            :placeholder="modelPlaceholder"
            @change="handleModelChange"
          />
        </slot>
      </div>
      <div class="header-right">
        <slot name="header-right">
          <el-button
            v-if="showClear"
            text
            @click="handleClear"
          >
            <el-icon><Delete /></el-icon>
            清空
          </el-button>
          <el-button
            v-if="showConfig"
            text
            @click="showConfigPanel = !showConfigPanel"
          >
            <el-icon><Setting /></el-icon>
            {{ showConfigPanel ? '隐藏配置' : '显示配置' }}
          </el-button>
        </slot>
      </div>
    </div>

    <!-- 主内容区域 -->
    <div class="service-main">
      <!-- 配置面板 -->
      <Transition name="slide">
        <div
          v-if="showConfigPanel"
          class="config-panel"
        >
          <slot name="config" :config="localConfig" :update-config="updateConfig">
            <!-- 默认配置内容 -->
          </slot>
        </div>
      </Transition>

      <!-- 内容区域 -->
      <div class="content-area">
        <slot :model="selectedModel" :config="localConfig">
          <!-- 默认内容 -->
        </slot>
      </div>
    </div>

    <!-- 底部状态栏 -->
    <div
      v-if="showStatusBar"
      class="service-footer"
    >
      <slot
        name="footer"
        :status="status"
        :metrics="metrics"
      >
        <div class="status-bar">
          <span class="status-item">
            <el-icon :class="statusClass"><CircleCheck /></el-icon>
            {{ statusText }}
          </span>
          <span
            v-if="metrics.duration"
            class="status-item"
          >
            耗时: {{ metrics.duration }}ms
          </span>
          <span
            v-if="metrics.tokens"
            class="status-item"
          >
            Tokens: {{ metrics.tokens }}
          </span>
        </div>
      </slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Delete, Setting, CircleCheck } from '@element-plus/icons-vue'
import ModelSelector from './ModelSelector.vue'

export interface ModelInstance {
  id: string
  name: string
  modelName?: string
  type?: string
  endpoint?: string
}

interface Props {
  instances?: ModelInstance[]
  instancesLoading?: boolean
  modelPlaceholder?: string
  showConfig?: boolean
  showClear?: boolean
  showStatusBar?: boolean
  status?: 'idle' | 'loading' | 'success' | 'error'
  metrics?: {
    duration?: number
    tokens?: number
    [key: string]: any
  }
  config?: Record<string, any>
}

const props = withDefaults(defineProps<Props>(), {
  instances: () => [],
  instancesLoading: false,
  modelPlaceholder: '选择模型',
  showConfig: true,
  showClear: true,
  showStatusBar: true,
  status: 'idle',
  metrics: () => ({}),
  config: () => ({})
})

const emit = defineEmits<{
  'update:model': [modelId: string]
  'update:config': [config: Record<string, any>]
  clear: []
}>()

const selectedModel = ref('')
const showConfigPanel = ref(false)
const localConfig = ref({ ...props.config })

// 状态样式
const statusClass = computed(() => {
  switch (props.status) {
    case 'loading':
      return 'status-loading'
    case 'success':
      return 'status-success'
    case 'error':
      return 'status-error'
    default:
      return 'status-idle'
  }
})

// 状态文本
const statusText = computed(() => {
  switch (props.status) {
    case 'loading':
      return '处理中...'
    case 'success':
      return '成功'
    case 'error':
      return '错误'
    default:
      return '就绪'
  }
})

// 监听配置变化
watch(
  () => props.config,
  (val) => {
    localConfig.value = { ...val }
  },
  { deep: true }
)

// 处理模型变化
const handleModelChange = (modelId: string) => {
  emit('update:model', modelId)
}

// 更新配置
const updateConfig = (key: string, value: any) => {
  localConfig.value[key] = value
  emit('update:config', { ...localConfig.value })
}

// 清空
const handleClear = () => {
  emit('clear')
}
</script>

<style scoped>
.service-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
  background-color: var(--el-bg-color-page);
}

.service-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background-color: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  max-width: 400px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.service-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.config-panel {
  padding: 16px 20px;
  background-color: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s ease;
}

.slide-enter-from,
.slide-leave-to {
  transform: translateY(-10px);
  opacity: 0;
}

.content-area {
  flex: 1;
  overflow: auto;
}

.service-footer {
  padding: 8px 20px;
  background-color: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);
}

.status-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.status-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-idle {
  color: var(--el-text-color-placeholder);
}

.status-loading {
  color: var(--el-color-primary);
  animation: pulse 1s infinite;
}

.status-success {
  color: var(--el-color-success);
}

.status-error {
  color: var(--el-color-danger);
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}
</style>