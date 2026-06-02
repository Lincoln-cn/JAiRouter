<template>
  <el-tabs v-model="activeTab" class="service-tabs" type="card" @tab-change="handleChange">
    <el-tab-pane
      v-for="tab in tabs"
      :key="tab.name"
      :label="tab.label"
      :name="tab.name"
      :disabled="tab.disabled"
    >
      <template #label>
        <span class="tab-label">
          <el-icon v-if="tab.icon">
            <component :is="tab.icon" />
          </el-icon>
          {{ tab.label }}
          <el-badge
            v-if="tab.badge !== undefined && tab.badge > 0"
            :value="tab.badge"
            class="tab-badge"
          />
        </span>
      </template>
      <slot :name="tab.name" />
    </el-tab-pane>
  </el-tabs>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { Component } from 'vue'

export interface TabItem {
  name: string
  label: string
  icon?: Component
  disabled?: boolean
  badge?: number
}

interface Props {
  modelValue?: string
  tabs: TabItem[]
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'change': [value: string]
}>()

const activeTab = ref(props.modelValue || (props.tabs.length > 0 ? props.tabs[0].name : ''))

watch(() => props.modelValue, (val) => {
  if (val && val !== activeTab.value) {
    activeTab.value = val
  }
})

watch(activeTab, (val) => {
  emit('update:modelValue', val)
})

const handleChange = (name: string) => {
  emit('change', name)
}
</script>

<style scoped>
.service-tabs {
  width: 100%;
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tab-badge {
  margin-left: 4px;
}

.service-tabs :deep(.el-tabs__content) {
  padding: 16px 0;
}
</style>
