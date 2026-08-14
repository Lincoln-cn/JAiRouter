<template>
  <div
    class="adapter-template-card"
    :class="{ selected, disabled }"
    @click="!disabled && $emit('select')"
  >
    <div class="template-icon">
      <el-icon v-if="!template.icon"><MagicStick /></el-icon>
      <span v-else class="template-icon-text">{{ template.icon }}</span>
    </div>
    <div class="template-info">
      <div class="template-name">{{ template.name }}</div>
      <div class="template-desc">{{ template.description }}</div>
      <div class="template-tags">
        <el-tag size="small" :type="categoryTagType" effect="light">
          {{ categoryLabel }}
        </el-tag>
        <el-tag size="small" type="info" effect="plain">
          {{ template.type === 'ollama-compatible' ? 'Ollama' : 'OpenAI兼容' }}
        </el-tag>
      </div>
    </div>
    <div v-if="selected" class="template-check">
      <el-icon><CircleCheckFilled /></el-icon>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { MagicStick, CircleCheckFilled } from '@element-plus/icons-vue'
import type { AdapterTemplate } from '@/api/adapter'

const props = defineProps<{
  template: AdapterTemplate
  selected?: boolean
  disabled?: boolean
}>()

defineEmits<{
  (e: 'select'): void
}>()

const categoryLabel = computed(() => {
  const map: Record<string, string> = {
    domestic: '国内',
    international: '国际',
    local: '本地',
    custom: '自定义'
  }
  return map[props.template.category] || props.template.category
})

const categoryTagType = computed(() => {
  const map: Record<string, string> = {
    domestic: 'success',
    international: 'primary',
    local: 'warning',
    custom: 'info'
  }
  return map[props.template.category] || 'info'
})
</script>

<style scoped>
.adapter-template-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fff;
}

.adapter-template-card:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.15);
}

.adapter-template-card.selected {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2);
}

.adapter-template-card.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.template-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: #409eff;
  flex-shrink: 0;
}

.template-icon-text {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.template-info {
  flex: 1;
  min-width: 0;
}

.template-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.template-desc {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.template-tags {
  display: flex;
  gap: 4px;
}

.template-check {
  position: absolute;
  top: 10px;
  right: 10px;
  color: #409eff;
  font-size: 18px;
}
</style>
