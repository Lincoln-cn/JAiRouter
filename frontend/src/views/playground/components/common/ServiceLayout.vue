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
            :placeholder="resolvedModelPlaceholder"
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
            {{ t('playgroundCommon.action.clear') }}
          </el-button>
          <el-button
            v-if="showConfig"
            text
            @click="showConfigPanel = !showConfigPanel"
          >
            <el-icon><Setting /></el-icon>
            {{ showConfigPanel ? t('playgroundCommon.action.hideConfig') : t('playgroundCommon.action.showConfig') }}
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
            {{ t('playgroundCommon.status.duration', { duration: String(metrics.duration) }) }}
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
import { useI18n } from 'vue-i18n'
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
    [key: string]: unknown
  }
  config?: Record<string, unknown>
}

const props = withDefaults(defineProps<Props>(), {
  instances: () => [],
  instancesLoading: false,
  modelPlaceholder: '',
  showConfig: true,
  showClear: true,
  showStatusBar: true,
  status: 'idle',
  metrics: () => ({}),
  config: () => ({})
})

const emit = defineEmits<{
  'update:model': [modelId: string]
  'update:config': [config: Record<string, unknown>]
  clear: []
}>()

const { t } = useI18n()

const selectedModel = ref('')
const showConfigPanel = ref(false)
const localConfig = ref({ ...props.config })

// 模型选择框占位文案（父级未传入时使用翻译默认值）
const resolvedModelPlaceholder = computed(() => {
  return props.modelPlaceholder || t('playgroundCommon.model.placeholder')
})

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
      return t('playgroundCommon.status.loading')
    case 'success':
      return t('playgroundCommon.status.success')
    case 'error':
      return t('playgroundCommon.status.error')
    default:
      return t('playgroundCommon.status.idle')
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
const updateConfig = (key: string, value: unknown) => {
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
  background-color: var(--ja-surface);
}

.service-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--ja-space-3) var(--ja-space-5);
  background-color: var(--ja-paper);
  border-bottom: 1px solid var(--ja-border);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--ja-space-3);
  flex: 1;
  max-width: 400px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--ja-space-2);
}

.service-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.config-panel {
  padding: var(--ja-space-4) var(--ja-space-5);
  background-color: var(--ja-paper);
  border-bottom: 1px solid var(--ja-border);
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
  padding: var(--ja-space-2) var(--ja-space-5);
  background-color: var(--ja-paper);
  border-top: 1px solid var(--ja-border);
}

.status-bar {
  display: flex;
  align-items: center;
  gap: var(--ja-space-4);
  font-size: 12px;
  color: var(--ja-ink-3);
}

.status-item {
  display: flex;
  align-items: center;
  gap: var(--ja-space-1);
}

.status-idle {
  color: var(--ja-ink-3);
}

.status-loading {
  color: var(--ja-primary);
  animation: pulse 1s infinite;
}

.status-success {
  color: var(--ja-success);
}

.status-error {
  color: var(--ja-danger);
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