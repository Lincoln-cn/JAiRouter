<template>
  <div class="playground-main" @keydown="handleKeydown">
    <el-card class="playground-container">
      <template #header>
        <div class="card-header">
          <span>API 测试试验场</span>
          <div class="header-actions">
            <el-button v-if="!isMobile" type="info" size="default" @click="showKeyboardShortcuts = true">
              <el-icon>
                <InfoFilled />
              </el-icon>
              快捷键
            </el-button>
            <el-button type="success" :size="isMobile ? 'small' : 'default'" @click="refreshAllModels"
              :loading="refreshingModels">
              <el-icon>
                <Refresh />
              </el-icon>
              <span v-if="!isMobile">刷新实例</span>
            </el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab" type="card" class="playground-tabs" @tab-change="onTabChange">
        <el-tab-pane label="对话" name="chat">
          <ChatPlayground :global-config="globalConfig" @response="onResponse" @request="onRequest"
            @update:global-config="onGlobalConfigUpdated" />
        </el-tab-pane>
        <el-tab-pane label="文本嵌入" name="embedding">
          <EmbeddingPlayground :global-config="globalConfig" @response="onResponse" @request="onRequest"
            @update:global-config="onGlobalConfigUpdated" />
        </el-tab-pane>
        <el-tab-pane label="重排序" name="rerank">
          <RerankPlayground :global-config="globalConfig" @response="onResponse" @request="onRequest"
            @update:global-config="onGlobalConfigUpdated" />
        </el-tab-pane>
        <el-tab-pane label="语音" name="audio">
          <AudioPlayground :global-config="globalConfig" @response="onResponse" @request="onRequest" />
        </el-tab-pane>
        <el-tab-pane label="图像" name="image">
          <ImagePlayground :global-config="globalConfig" @response="onResponse" @request="onRequest" />
        </el-tab-pane>
      </el-tabs>

      <!-- 请求和响应面板 -->
      <div class="panels-section">
        <el-row :gutter="isMobile ? 12 : 20">
          <el-col :xs="24" :sm="24" :md="12" :lg="12" :xl="12">
            <RequestPanel :request="currentRequest" :request-size="requestSize" :is-mobile="isMobile" />
          </el-col>
          <el-col :xs="24" :sm="24" :md="12" :lg="12" :xl="12">
            <ResponsePanel :response="currentResponse" :loading="responseLoading" :error="responseError"
              :request-size="requestSize" :method="currentRequest?.method" :endpoint="currentRequest?.endpoint"
              :is-mobile="isMobile" @performance-metrics="onPerformanceMetrics" />
          </el-col>
        </el-row>

        <!-- 性能监控面板 -->
        <div v-if="!isMobile" class="performance-section">
          <PerformanceMonitor ref="performanceMonitor" :current-metrics="null" />
        </div>
      </div>

      <!-- 键盘快捷键帮助对话框 -->
      <el-dialog v-model="showKeyboardShortcuts" title="键盘快捷键" width="500px" :close-on-click-modal="true">
        <div class="shortcuts-content">
          <div class="shortcut-group">
            <h4>通用快捷键</h4>
            <div class="shortcut-item">
              <kbd>Ctrl</kbd> + <kbd>Enter</kbd>
              <span>发送请求</span>
            </div>
            <div class="shortcut-item">
              <kbd>Ctrl</kbd> + <kbd>R</kbd>
              <span>重置表单</span>
            </div>
          </div>

          <div class="shortcut-group">
            <h4>选项卡切换</h4>
            <div class="shortcut-item">
              <kbd>Ctrl</kbd> + <kbd>1-5</kbd>
              <span>切换到对应服务类型</span>
            </div>
            <div class="shortcut-item">
              <kbd>Tab</kbd>
              <span>在表单字段间切换</span>
            </div>
          </div>

          <div class="shortcut-group">
            <h4>响应操作</h4>
            <div class="shortcut-item">
              <kbd>Ctrl</kbd> + <kbd>C</kbd>
              <span>复制响应内容</span>
            </div>
            <div class="shortcut-item">
              <kbd>Esc</kbd>
              <span>取消当前请求</span>
            </div>
          </div>
        </div>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, provide, computed, onMounted, onUnmounted } from 'vue'
import { InfoFilled, Refresh } from '@element-plus/icons-vue'
import ChatPlayground from './components/ChatPlayground.vue'
import EmbeddingPlayground from './components/EmbeddingPlayground.vue'
import RerankPlayground from './components/RerankPlayground.vue'
import AudioPlayground from './components/AudioPlayground.vue'
import ImagePlayground from './components/ImagePlayground.vue'
import ResponsePanel from './components/ResponsePanel.vue'
import RequestPanel from './components/RequestPanel.vue'
import PerformanceMonitor from './components/PerformanceMonitor.vue'
import type {
  ServiceType,
  GlobalConfig,
  PlaygroundResponse,
  PlaygroundRequest,
  TabState,
  SavedConfiguration
} from './types/playground'

// 选项卡状态管理
const activeTab = ref<ServiceType>('chat')
const showKeyboardShortcuts = ref(false)
const refreshingModels = ref(false)

// 响应式状态
const windowWidth = ref(window.innerWidth)
const isMobile = computed(() => windowWidth.value < 768)
const isTablet = computed(() => windowWidth.value >= 768 && windowWidth.value < 1024)

// 全局配置状态
const globalConfig = reactive<GlobalConfig>({
  authorization: '',
  customHeaders: {}
})

// 响应状态管理
const currentResponse = ref<PlaygroundResponse | null>(null)
const responseLoading = ref(false)
const responseError = ref<string | null>(null)

// 请求状态管理
const currentRequest = ref<PlaygroundRequest | null>(null)
const requestSize = ref(0)

// 性能监控
const performanceMonitor = ref<InstanceType<typeof PerformanceMonitor>>()

// 选项卡状态
const tabState = reactive<TabState>({
  activeTab: 'chat',
  activeAudioTab: 'tts',
  activeImageTab: 'generate'
})

// 当前配置状态 - 用于配置管理
const currentConfigs = reactive<Record<ServiceType, any>>({
  chat: {},
  embedding: {},
  rerank: {},
  tts: {},
  stt: {},
  imageGenerate: {},
  imageEdit: {}
})

// 提供全局状态给子组件
provide('globalConfig', globalConfig)
provide('tabState', tabState)

// 事件处理函数
const onTabChange = (tabName: string) => {
  tabState.activeTab = tabName as ServiceType
  // 清除之前的请求和响应数据
  currentRequest.value = null
  currentResponse.value = null
  responseError.value = null
  requestSize.value = 0
}

const onGlobalConfigUpdated = (config: GlobalConfig) => {
  Object.assign(globalConfig, config)
}

const onResponse = (response: PlaygroundResponse | null, loading = false, error: string | null = null) => {
  currentResponse.value = response
  responseLoading.value = loading
  responseError.value = error
}

const onRequest = (request: PlaygroundRequest | null, size = 0) => {
  currentRequest.value = request
  requestSize.value = size
}

const onPerformanceMetrics = (metrics: any) => {
  performanceMonitor.value?.addMetrics(metrics)
}

// 简化的配置管理
const updateCurrentConfig = (serviceType: ServiceType, config: any) => {
  currentConfigs[serviceType] = { ...config }
}

// 提供配置更新方法给子组件
provide('updateCurrentConfig', updateCurrentConfig)

// 响应式窗口大小监听
const handleResize = () => {
  windowWidth.value = window.innerWidth
}

// 键盘快捷键处理
const handleKeydown = (event: KeyboardEvent) => {
  // 如果在输入框中，不处理快捷键
  const target = event.target as HTMLElement
  if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') {
    return
  }

  if (event.ctrlKey || event.metaKey) {
    switch (event.key) {
      case 'Enter':
        event.preventDefault()
        // 触发当前选项卡的发送请求
        triggerSendRequest()
        break
      case 'r':
      case 'R':
        event.preventDefault()
        // 触发重置表单
        triggerResetForm()
        break

      case '1':
      case '2':
      case '3':
      case '4':
      case '5':
        event.preventDefault()
        // 切换选项卡
        const tabMap: Record<string, ServiceType> = {
          '1': 'chat',
          '2': 'embedding',
          '3': 'rerank',
          '4': 'tts',
          '5': 'imageGenerate'
        }
        const newTab = tabMap[event.key]
        if (newTab) {
          activeTab.value = newTab
          onTabChange(newTab)
        }
        break
      case 'c':
      case 'C':
        // 复制响应内容 - 由ResponsePanel处理
        break
    }
  } else if (event.key === 'Escape') {
    // 取消当前请求或关闭对话框
    if (showKeyboardShortcuts.value) {
      showKeyboardShortcuts.value = false
    } else {
      triggerCancelRequest()
    }
  }
}

// 触发发送请求 - 需要子组件实现
const triggerSendRequest = () => {
  // 通过事件总线或其他方式通知当前活跃的子组件发送请求
  const event = new CustomEvent('playground-send-request')
  document.dispatchEvent(event)
}

// 触发重置表单
const triggerResetForm = () => {
  const event = new CustomEvent('playground-reset-form')
  document.dispatchEvent(event)
}

// 触发取消请求
const triggerCancelRequest = () => {
  const event = new CustomEvent('playground-cancel-request')
  document.dispatchEvent(event)
}

// 刷新所有模型列表
const refreshAllModels = async () => {
  refreshingModels.value = true

  try {
    // 使用缓存管理器刷新所有数据
    const { refreshAllCache } = await import('@/stores/playgroundCache')
    await refreshAllCache()
    
    // 通知所有子组件数据已刷新
    const event = new CustomEvent('playground-refresh-models')
    document.dispatchEvent(event)
  } catch (error) {
    console.error('刷新数据失败:', error)
  } finally {
    refreshingModels.value = false
  }
}

// 生命周期
onMounted(async () => {
  window.addEventListener('resize', handleResize)
  // 初始化时获取窗口大小
  handleResize()
  
  // 预加载常用数据到缓存
  try {
    const { preloadCommonData } = await import('@/stores/playgroundCache')
    preloadCommonData()
    
    // 开发环境下添加调试工具
    if (process.env.NODE_ENV === 'development') {
      await import('@/utils/debugPlayground')
      console.log('💡 调试提示: 在控制台输入 debugPlaygroundData() 可以查看数据加载情况')
    }
  } catch (error) {
    console.error('预加载数据失败:', error)
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

// 暴露方法给父组件使用
defineExpose({
  activeTab,
  globalConfig,
  currentResponse,
  updateCurrentConfig,
  isMobile,
  isTablet
})
</script>

<style scoped>
/* 主容器样式 - 与系统布局保持一致 */
.playground-main {
  padding: 24px;
  height: calc(100vh - 120px);
  overflow: auto;
  background-color: var(--el-bg-color-page);
}

.playground-container {
  min-height: calc(100vh - 168px);
  display: flex;
  flex-direction: column;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
  border-radius: 8px;
  overflow: visible;
}

/* 卡片头部样式 - 统一系统风格 */
.playground-container :deep(.el-card__header) {
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);
  border-bottom: 2px solid var(--el-color-primary-light-8);
  padding: 16px 24px;
  color: var(--el-text-color-primary);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 16px;
  color: var(--el-text-color-primary);
}

.card-header span {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-header span::before {
  content: '';
  width: 4px;
  height: 20px;
  background: linear-gradient(135deg, var(--el-color-primary), var(--el-color-primary-light-3));
  border-radius: 2px;
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.header-actions .el-button {
  border-radius: 6px;
  font-weight: 500;
  transition: all 0.3s ease;
}

.header-actions .el-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(64, 158, 255, 0.3);
}



/* 选项卡样式 - 统一系统主题 */
.playground-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.playground-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
  background-color: var(--el-bg-color);
  border-bottom: 2px solid var(--el-border-color-light);
  padding: 0 16px;
}

.playground-tabs :deep(.el-tabs__nav-wrap) {
  padding: 8px 0;
}

.playground-tabs :deep(.el-tabs__item) {
  padding: 12px 20px;
  font-weight: 500;
  border-radius: 6px 6px 0 0;
  margin-right: 4px;
  transition: all 0.3s ease;
  position: relative;
}

.playground-tabs :deep(.el-tabs__item:hover) {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.playground-tabs :deep(.el-tabs__item.is-active) {
  background: linear-gradient(135deg, var(--el-color-primary), var(--el-color-primary-light-3));
  color: white;
  font-weight: 600;
}

.playground-tabs :deep(.el-tabs__item.is-active::after) {
  content: '';
  position: absolute;
  bottom: -2px;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--el-color-primary);
}

.playground-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: visible;
  padding: 24px;
  background-color: var(--el-bg-color);
  min-height: 600px;
}

.playground-tabs :deep(.el-tab-pane) {
  min-height: 600px;
  overflow: visible;
}

/* 面板区域样式 */
.panels-section {
  margin-top: 24px;
  border-top: 2px solid var(--el-border-color-light);
  padding-top: 24px;
  background: linear-gradient(135deg, var(--el-bg-color-page) 0%, var(--el-bg-color) 100%);
  border-radius: 8px;
  padding: 24px;
}

.performance-section {
  margin-top: 24px;
  padding: 16px;
  background-color: var(--el-bg-color-page);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
}

/* 响应式设计 - 桌面端优化 */
@media (min-width: 1400px) {
  .playground-main {
    padding: 32px;
  }

  .playground-container :deep(.el-card__header) {
    padding: 20px 32px;
  }

  .playground-tabs :deep(.el-tabs__content) {
    padding: 32px;
  }
}

/* 大屏幕优化 */
@media (max-width: 1200px) {
  .playground-main {
    padding: 16px;
  }

  .panels-section .el-col {
    margin-bottom: 16px;
  }

  .playground-tabs :deep(.el-tabs__item) {
    padding: 10px 16px;
  }
}

/* 平板端优化 */
@media (max-width: 1024px) {
  .playground-main {
    padding: 14px;
  }

  .card-header span::before {
    width: 3px;
    height: 16px;
  }

  .playground-tabs :deep(.el-tabs__item) {
    padding: 8px 14px;
    font-size: 14px;
  }

  .panels-section {
    padding: 20px;
  }
}

/* 移动端优化 */
@media (max-width: 768px) {
  .playground-main {
    padding: 12px;
    min-height: calc(100vh - 60px);
    height: auto;
  }

  .playground-container :deep(.el-card__header) {
    padding: 12px 16px;
  }

  .card-header {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
    text-align: center;
    font-size: 14px;
  }

  .card-header span::before {
    display: none;
  }

  .header-actions {
    justify-content: center;
    flex-wrap: wrap;
    gap: 8px;
  }

  .header-actions .el-button {
    flex: 1;
    min-width: 0;
  }

  .playground-tabs :deep(.el-tabs__header) {
    padding: 0 8px;
  }

  .playground-tabs :deep(.el-tabs__nav-wrap) {
    padding: 4px 0;
  }

  .playground-tabs :deep(.el-tabs__item) {
    padding: 8px 10px;
    font-size: 13px;
    margin-right: 2px;
  }

  .playground-tabs :deep(.el-tabs__content) {
    padding: 16px;
    min-height: 500px;
  }

  .panels-section {
    padding: 16px;
    margin-top: 16px;
  }

  .panels-section .el-col {
    margin-bottom: 16px;
  }

  .performance-section {
    display: none;
    /* 移动端隐藏性能监控 */
  }
}

/* 小屏幕手机优化 */
@media (max-width: 480px) {
  .playground-main {
    padding: 8px;
    min-height: calc(100vh - 50px);
    height: auto;
  }

  .playground-container :deep(.el-card__header) {
    padding: 8px 12px;
  }

  .card-header {
    font-size: 13px;
  }

  .header-actions .el-button {
    font-size: 12px;
    padding: 6px 8px;
  }

  .playground-tabs :deep(.el-tabs__header) {
    padding: 0 4px;
  }

  .playground-tabs :deep(.el-tabs__item) {
    padding: 6px 8px;
    font-size: 12px;
    margin-right: 1px;
  }

  .playground-tabs :deep(.el-tabs__content) {
    padding: 12px;
    min-height: 400px;
  }

  .panels-section {
    padding: 12px;
    margin-top: 12px;
  }
}

/* 横屏模式优化 */
@media (max-width: 768px) and (orientation: landscape) {
  .playground-main {
    min-height: calc(100vh - 40px);
    height: auto;
  }

  .card-header {
    flex-direction: row;
    text-align: left;
  }

  .header-actions {
    justify-content: flex-end;
  }

  .playground-tabs :deep(.el-tabs__content) {
    padding: 12px;
  }
}

/* 触摸设备优化 */
@media (hover: none) and (pointer: coarse) {
  .playground-tabs :deep(.el-tabs__item) {
    padding: 12px 16px;
    min-height: 44px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .header-actions .el-button {
    min-height: 44px;
    padding: 8px 16px;
  }
}

/* 加载动画和过渡效果 */
.playground-container {
  animation: slideInUp 0.4s ease-out;
}

@keyframes slideInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 滚动条统一样式 */
.playground-tabs :deep(.el-tab-pane)::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.playground-tabs :deep(.el-tab-pane)::-webkit-scrollbar-track {
  background: var(--el-bg-color-page);
  border-radius: 4px;
}

.playground-tabs :deep(.el-tab-pane)::-webkit-scrollbar-thumb {
  background: var(--el-border-color-dark);
  border-radius: 4px;
  transition: background 0.3s ease;
}

.playground-tabs :deep(.el-tab-pane)::-webkit-scrollbar-thumb:hover {
  background: var(--el-color-primary-light-5);
}

/* 焦点和无障碍样式 */
.playground-tabs :deep(.el-tabs__item:focus) {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

.header-actions .el-button:focus {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

/* 高对比度模式支持 */
@media (prefers-contrast: high) {
  .playground-container {
    border: 2px solid var(--el-text-color-primary);
  }

  .playground-tabs :deep(.el-tabs__item.is-active) {
    background: var(--el-text-color-primary);
    color: var(--el-bg-color);
  }


}

/* 键盘快捷键帮助样式 */
.shortcuts-content {
  max-height: 400px;
  overflow-y: auto;
}

.shortcut-group {
  margin-bottom: 24px;
}

.shortcut-group h4 {
  margin: 0 0 12px 0;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: 8px;
}

.shortcut-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.shortcut-item:last-child {
  border-bottom: none;
}

.shortcut-item kbd {
  display: inline-block;
  padding: 2px 6px;
  font-size: 11px;
  line-height: 1.4;
  color: var(--el-text-color-primary);
  background-color: var(--el-bg-color-page);
  border: 1px solid var(--el-border-color);
  border-radius: 3px;
  box-shadow: 0 1px 0 var(--el-border-color);
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  margin: 0 2px;
}

.shortcut-item span {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

/* 键盘焦点样式增强 */
.playground-main:focus-within .playground-tabs :deep(.el-tabs__item:focus) {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
  border-radius: 4px;
}

.playground-main:focus-within .header-actions .el-button:focus {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

/* 减少动画模式支持 */
@media (prefers-reduced-motion: reduce) {

  .playground-container,
  .header-actions .el-button,
  .playground-tabs :deep(.el-tabs__item),
  .header-config-panel {
    animation: none;
    transition: none;
  }
}

/* 深色主题键盘快捷键样式 */
@media (prefers-color-scheme: dark) {
  .shortcut-item kbd {
    background-color: var(--el-bg-color-overlay);
    border-color: var(--el-border-color);
    box-shadow: 0 1px 0 var(--el-border-color-dark);
  }
}
</style>