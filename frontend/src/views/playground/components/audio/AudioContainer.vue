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
          {{ t('playgroundAudio.ttsTab') }}
        </el-radio-button>
        <el-radio-button value="stt">
          <el-icon><Headset /></el-icon>
          {{ t('playgroundAudio.sttTab') }}
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
import { ref, onMounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Microphone, Headset } from '@element-plus/icons-vue'
import TtsPanel from './TtsPanel.vue'
import SttPanel from './SttPanel.vue'
import { usePlaygroundData } from '@/composables/usePlaygroundData'
import { useRoutePreselect, preselectInstanceName } from '@/composables/useRoutePreselect'

const { t } = useI18n()

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
  // Switch tab first if serviceType demands it (before data arrives)
  const st = requestedServiceType()
  if (st === 'stt' || st === 'tts') {
    activeTab.value = st
  }
  initTtsData()
  initSttData()
})

// Route preselect: activate the right tab when serviceType matches
const { requestedServiceType } = useRoutePreselect()
const preselectDone = ref(false)

watch(activeTab, (tab) => {
  const st = requestedServiceType()
  if (tab === 'tts') {
    initTtsData()
    if (st === 'tts' && ttsInstances.value.length > 0 && !preselectDone.value) {
      nextTick(() => {
        const name = preselectInstanceName(ttsInstances.value)
        if (name && ttsRef.value) {
          ttsRef.value.selectedModel = name
          preselectDone.value = true
        }
      })
    }
  } else {
    initSttData()
    if (st === 'stt' && sttInstances.value.length > 0 && !preselectDone.value) {
      nextTick(() => {
        const name = preselectInstanceName(sttInstances.value)
        if (name && sttRef.value) {
          sttRef.value.selectedModel = name
          preselectDone.value = true
        }
      })
    }
  }
}, { immediate: true })

// Preselect instance when data arrives asynchronously
watch(ttsInstances, (instances) => {
  if (requestedServiceType() === 'tts' && activeTab.value === 'tts' && !preselectDone.value) {
    const name = preselectInstanceName(instances)
    if (name) {
      nextTick(() => {
        if (ttsRef.value) {
          ttsRef.value.selectedModel = name
          preselectDone.value = true
        }
      })
    }
  }
})

watch(sttInstances, (instances) => {
  if (requestedServiceType() === 'stt' && activeTab.value === 'stt' && !preselectDone.value) {
    const name = preselectInstanceName(instances)
    if (name) {
      nextTick(() => {
        if (sttRef.value) {
          sttRef.value.selectedModel = name
          preselectDone.value = true
        }
      })
    }
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
  background-color: var(--ja-main-bg);
}

.audio-tabs {
  padding: 12px 16px;
  background-color: var(--ja-bg-page);
  border-bottom: 1px solid var(--el-border-color);
  border-radius: 4px 4px 0 0;
}
</style>