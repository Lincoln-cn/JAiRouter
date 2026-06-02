<template>
  <el-card shadow="hover" class="stat-card" :class="cardClass">
    <div class="stat-content">
      <div class="stat-icon" :style="{ background: iconBackground }">
        <el-icon :size="iconSize">
          <component :is="icon" />
        </el-icon>
      </div>
      <div class="stat-info">
        <div class="stat-value">{{ formattedValue }}</div>
        <div class="stat-label">{{ label }}</div>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'

interface Props {
  icon: Component
  value: number | string
  label: string
  iconBackground?: string
  iconSize?: number
  cardClass?: string
  formatter?: (value: number | string) => string
}

const props = withDefaults(defineProps<Props>(), {
  iconBackground: '#409EFF',
  iconSize: 32,
  cardClass: ''
})

const formattedValue = computed(() => {
  if (props.formatter) {
    return props.formatter(props.value)
  }
  return props.value
})
</script>

<style scoped>
.stat-card {
  border-radius: 12px;
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}
</style>
