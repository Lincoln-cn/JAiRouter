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
          图像生成
        </el-radio-button>
        <el-radio-button value="edit">
          <el-icon><Edit /></el-icon>
          图像编辑
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
import { ref, onMounted, watch } from 'vue'
import { Picture, Edit } from '@element-plus/icons-vue'
import ImageGeneratePanel from './ImageGeneratePanel.vue'
import ImageEditPanel from './ImageEditPanel.vue'
import { usePlaygroundData } from '@/composables/usePlaygroundData'

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
  initGenerateData()
  initEditData()
})

// 切换 Tab 时刷新数据
watch(activeTab, (tab) => {
  if (tab === 'generate') {
    initGenerateData()
  } else {
    initEditData()
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
  background-color: #f5f5f5;
}

.image-tabs {
  padding: 12px 16px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  border-radius: 4px 4px 0 0;
}
</style>