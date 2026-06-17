<template>
  <div class="playground-main">
    <!-- 侧边导航 -->
    <div class="sidebar">
      <div class="sidebar-header">
        <el-icon
          :size="24"
          color="#409eff"
        >
          <Monitor />
        </el-icon>
        <span class="sidebar-title">AI 试验场</span>
      </div>

      <div class="sidebar-nav">
        <div
          v-for="item in serviceNavItems"
          :key="item.key"
          :class="['nav-item', { active: activeService === item.key }]"
          @click="switchService(item.key)"
        >
          <el-icon :size="18">
            <component :is="item.icon" />
          </el-icon>
          <span class="nav-label">{{ item.label }}</span>
          <el-tag
            v-if="item.count"
            size="small"
            type="info"
          >
            {{ item.count }}
          </el-tag>
        </div>
      </div>

      <div class="sidebar-footer">
        <el-button
          text
          size="small"
          @click="refreshAllData"
        >
          <el-icon><Refresh /></el-icon>
          刷新数据
        </el-button>
      </div>
    </div>

    <!-- 主内容区域 -->
    <div class="main-content">
      <!-- 服务标题 -->
      <div class="service-header">
        <div class="service-info">
          <h2 class="service-title">{{ currentServiceTitle }}</h2>
          <p class="service-desc">{{ currentServiceDesc }}</p>
        </div>
      </div>

      <!-- 服务内容 -->
      <div class="service-content">
        <ChatContainer
          v-if="activeService === 'chat'"
          ref="chatRef"
        />
        <EmbeddingContainer
          v-if="activeService === 'embedding'"
          ref="embeddingRef"
        />
        <RerankContainer
          v-if="activeService === 'rerank'"
          ref="rerankRef"
        />
        <AudioContainer
          v-if="activeService === 'audio'"
          ref="audioRef"
        />
        <ImageContainer
          v-if="activeService === 'image'"
          ref="imageRef"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  Monitor,
  Refresh,
  ChatDotRound,
  DataLine,
  Sort,
  Headset,
  Picture
} from '@element-plus/icons-vue'
import ChatContainer from './components/chat/ChatContainer.vue'
import EmbeddingContainer from './components/embedding/EmbeddingContainer.vue'
import RerankContainer from './components/rerank/RerankContainer.vue'
import AudioContainer from './components/audio/AudioContainer.vue'
import ImageContainer from './components/image/ImageContainer.vue'
import { clearCache } from '@/stores/playgroundCache'

// 状态
const activeService = ref('chat')
const chatRef = ref()
const embeddingRef = ref()
const rerankRef = ref()
const audioRef = ref()
const imageRef = ref()

// SSE 连接
let eventSource: EventSource | null = null

// 服务导航项
interface ServiceNavItem {
  key: string
  label: string
  icon: any
  desc: string
  count?: number
}

const serviceNavItems: ServiceNavItem[] = [
  {
    key: 'chat',
    label: '对话',
    icon: ChatDotRound,
    desc: '与 AI 进行智能对话，支持多种模型'
  },
  {
    key: 'embedding',
    label: '向量生成',
    icon: DataLine,
    desc: '将文本转换为高维向量表示'
  },
  {
    key: 'rerank',
    label: '重排序',
    icon: Sort,
    desc: '根据查询对文档进行相关性排序'
  },
  {
    key: 'audio',
    label: '语音服务',
    icon: Headset,
    desc: '语音合成(TTS)和语音识别(STT)'
  },
  {
    key: 'image',
    label: '图像服务',
    icon: Picture,
    desc: '图像生成和编辑'
  }
]

// 当前服务标题
const currentServiceTitle = computed(() => {
  const item = serviceNavItems.find((i) => i.key === activeService.value)
  return item?.label || ''
})

// 当前服务描述
const currentServiceDesc = computed(() => {
  const item = serviceNavItems.find((i) => i.key === activeService.value)
  return item?.desc || ''
})

// 切换服务
const switchService = (service: string) => {
  activeService.value = service
}

// 刷新所有数据
const refreshAllData = () => {
  switch (activeService.value) {
    case 'chat':
      chatRef.value?.refreshData?.()
      break
    case 'embedding':
      embeddingRef.value?.refreshData?.()
      break
    case 'rerank':
      rerankRef.value?.refreshData?.()
      break
    case 'audio':
      audioRef.value?.refreshData?.()
      break
    case 'image':
      imageRef.value?.refreshData?.()
      break
  }
}

// 初始化 SSE 连接
const initSSE = () => {
  const baseURL = import.meta.env.VITE_API_BASE_URL || ''
  const url = `${baseURL}/api/health-status/stream`
  
  try {
    eventSource = new EventSource(url)
    
    eventSource.addEventListener('health-update', (event) => {
      try {
        const data = JSON.parse(event.data)
        console.log('[SSE] 收到健康状态更新:', data)
        
        // 解析健康状态数据，更新对应服务的实例状态
        if (data.instanceHealth) {
          // 清除所有服务的缓存，强制重新获取
          clearCache()
          
          // 刷新当前活动服务的数据
          refreshAllData()
        }
      } catch (error) {
        console.error('[SSE] 解析健康状态数据失败:', error)
      }
    })
    
    eventSource.onerror = (error) => {
      console.warn('[SSE] 连接错误，将在 5 秒后重试:', error)
      // 关闭当前连接，稍后重试
      if (eventSource) {
        eventSource.close()
        eventSource = null
      }
      setTimeout(initSSE, 5000)
    }
    
    console.log('[SSE] 健康状态监听已启动')
  } catch (error) {
    console.error('[SSE] 初始化失败:', error)
  }
}

// 关闭 SSE 连接
const closeSSE = () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
    console.log('[SSE] 健康状态监听已关闭')
  }
}

// 初始化
onMounted(() => {
  // 默认聚焦在 Chat 服务
  // 启动 SSE 监听健康状态变化
  initSSE()
})

// 清理
onUnmounted(() => {
  closeSSE()
})
</script>

<style scoped>
.playground-main {
  display: flex;
  height: calc(100vh - 100px); /* 减去 Layout header 和 padding */
  background-color: #f0f2f5;
  border-radius: 8px;
}

/* 侧边栏 */
.sidebar {
  width: 220px;
  background-color: #fff;
  border-right: 1px solid #e5e5e5;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-header {
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid #e5e5e5;
}

.sidebar-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.sidebar-nav {
  flex: 1;
  padding: 12px 8px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 4px;
}

.nav-item:hover {
  background-color: #f5f5f5;
}

.nav-item.active {
  background-color: #ecf5ff;
  color: #409eff;
}

.nav-item.active .nav-label {
  font-weight: 600;
}

.nav-label {
  font-size: 14px;
  color: #606266;
}

.sidebar-footer {
  padding: 12px;
  border-top: 1px solid #e5e5e5;
}

/* 主内容区域 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.service-header {
  padding: 16px 24px;
  background-color: #fff;
  border-bottom: 1px solid #e5e5e5;
}

.service-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 4px 0;
}

.service-desc {
  font-size: 13px;
  color: #909399;
  margin: 0;
}

.service-content {
  flex: 1;
  overflow: hidden;
}

/* 响应式 */
@media (max-width: 768px) {
  .sidebar {
    width: 60px;
  }

  .sidebar-title,
  .nav-label,
  .sidebar-footer span {
    display: none;
  }

  .nav-item {
    justify-content: center;
    padding: 12px;
  }

  .service-header {
    padding: 12px 16px;
  }
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .playground-main {
    background-color: #1a1a1a;
  }

  .sidebar,
  .service-header {
    background-color: #2d2d2d;
    border-color: #3d3d3d;
  }

  .sidebar-title,
  .service-title,
  .nav-label {
    color: #e5e5e5;
  }

  .service-desc {
    color: #888;
  }

  .nav-item:hover {
    background-color: #3d3d3d;
  }

  .nav-item.active {
    background-color: #1e3a5f;
  }
}
</style>