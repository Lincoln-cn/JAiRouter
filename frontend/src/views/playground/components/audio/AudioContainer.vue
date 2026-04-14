<template>
  <div class="audio-container">
    <!-- 服务切换 Tabs（保持顶部 Tab 切换） -->
    <div class="audio-tabs">
      <el-radio-group
        v-model="activeTab"
        size="small"
      >
        <el-radio-button value="tts">
          <el-icon><Microphone /></el-icon>
          语音合成 (TTS)
        </el-radio-button>
        <el-radio-button value="stt">
          <el-icon><Headset /></el-icon>
          语音识别 (STT)
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- TTS 面板 -->
    <TtsPanel
      v-if="activeTab === 'tts'"
      ref="ttsRef"
      :instances="ttsInstances"
      :loading="ttsLoading"
    />

    <!-- STT 面板 -->
    <SttPanel
      v-if="activeTab === 'stt'"
      ref="sttRef"
      :instances="sttInstances"
      :loading="sttLoading"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { Microphone, Headset } from '@element-plus/icons-vue'
import TtsPanel from './TtsPanel.vue'
import SttPanel from './SttPanel.vue'
import { usePlaygroundData } from '@/composables/usePlaygroundData'

// 状态
const activeTab = ref<'tts' | 'stt'>('tts')
const ttsRef = ref()
const sttRef = ref()

// TTS 数据
const {
  availableInstances: ttsInstances,
  instancesLoading: ttsLoading,
  initializeData: initTtsData
} = usePlaygroundData('tts')

// STT 数据
const {
  availableInstances: sttInstances,
  instancesLoading: sttLoading,
  initializeData: initSttData
} = usePlaygroundData('stt')

// 初始化
onMounted(() => {
  initTtsData()
  initSttData()
})

// 切换 Tab 时刷新数据
watch(activeTab, (tab) => {
  if (tab === 'tts') {
    initTtsData()
  } else {
    initSttData()
  }
})

// 暴露方法
defineExpose({
  refreshData: () => {
    if (activeTab.value === 'tts') {
      ttsRef.value?.refreshData?.()
    } else {
      sttRef.value?.refreshData?.()
    }
  }
})
</script>

<style scoped>
.audio-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  background-color: #f5f5f5;
}

.audio-tabs {
  padding: 12px 16px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  border-radius: 4px 4px 0 0;
}
</style>