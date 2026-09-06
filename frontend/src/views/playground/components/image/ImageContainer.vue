<template>
  <div class="image-container">
    <!-- 服务切换 Tabs -->
    <div class="image-tabs">
      <el-radio-group
        v-model="activeTab"
        size="small"
      >
        <el-radio-button value="generate">
          <el-icon><Picture /></el-icon>
          {{ t('playgroundImage.generateTab') }}
        </el-radio-button>
        <el-radio-button value="edit">
          <el-icon><Edit /></el-icon>
          {{ t('playgroundImage.editTab') }}
        </el-radio-button>
      </el-radio-group>
    </div>

    <!-- 生成面板 -->
    <ImageGeneratePanel
      v-if="activeTab === 'generate'"
      ref="generateRef"
      :instances="generateInstances"
      :loading="generateLoading"
    />

    <!-- 编辑面板 -->
    <ImageEditPanel
      v-if="activeTab === 'edit'"
      ref="editRef"
      :instances="editInstances"
      :loading="editLoading"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Picture, Edit } from '@element-plus/icons-vue'
import ImageGeneratePanel from './ImageGeneratePanel.vue'
import ImageEditPanel from './ImageEditPanel.vue'
import { usePlaygroundData } from '@/composables/usePlaygroundData'
import { useRoutePreselect, preselectInstanceName } from '@/composables/useRoutePreselect'

const { t } = useI18n()

// 状态
const activeTab = ref<'generate' | 'edit'>('generate')
const generateRef = ref()
const editRef = ref()

// 图像生成数据
const {
  availableInstances: generateInstances,
  instancesLoading: generateLoading,
  initializeData: initGenerateData
} = usePlaygroundData('imageGenerate')

// 图像编辑数据
const {
  availableInstances: editInstances,
  instancesLoading: editLoading,
  initializeData: initEditData
} = usePlaygroundData('imageEdit')

// 初始化
onMounted(() => {
  // Switch tab first if serviceType demands it (before data arrives)
  const st = requestedServiceType()
  if (st === 'imgGen' || st === 'imgEdit') {
    activeTab.value = st === 'imgGen' ? 'generate' : 'edit'
  }
  initGenerateData()
  initEditData()
})

// Route preselect: activate the right tab when serviceType matches
const { requestedServiceType } = useRoutePreselect()
const preselectDone = ref(false)

watch(activeTab, (tab) => {
  const st = requestedServiceType()
  if (tab === 'generate') {
    initGenerateData()
    if (st === 'imgGen' && generateInstances.value.length > 0 && !preselectDone.value) {
      nextTick(() => {
        const name = preselectInstanceName(generateInstances.value)
        if (name && generateRef.value) {
          generateRef.value.selectedModel = name
          preselectDone.value = true
        }
      })
    }
  } else {
    initEditData()
    if (st === 'imgEdit' && editInstances.value.length > 0 && !preselectDone.value) {
      nextTick(() => {
        const name = preselectInstanceName(editInstances.value)
        if (name && editRef.value) {
          editRef.value.selectedModel = name
          preselectDone.value = true
        }
      })
    }
  }
}, { immediate: true })

// Preselect instance when data arrives asynchronously
watch(generateInstances, (instances) => {
  if (requestedServiceType() === 'imgGen' && activeTab.value === 'generate' && !preselectDone.value) {
    const name = preselectInstanceName(instances)
    if (name) {
      nextTick(() => {
        if (generateRef.value) {
          generateRef.value.selectedModel = name
          preselectDone.value = true
        }
      })
    }
  }
})

watch(editInstances, (instances) => {
  if (requestedServiceType() === 'imgEdit' && activeTab.value === 'edit' && !preselectDone.value) {
    const name = preselectInstanceName(instances)
    if (name) {
      nextTick(() => {
        if (editRef.value) {
          editRef.value.selectedModel = name
          preselectDone.value = true
        }
      })
    }
  }
})

// 暴露方法
defineExpose({
  refreshData: () => {
    if (activeTab.value === 'generate') {
      generateRef.value?.refreshData?.()
    } else {
      editRef.value?.refreshData?.()
    }
  }
})
</script>

<style scoped>
.image-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  background-color: var(--ja-main-bg);
}

.image-tabs {
  padding: 12px 16px;
  background-color: var(--ja-bg-page);
  border-bottom: 1px solid var(--el-border-color);
  border-radius: 4px 4px 0 0;
}
</style>