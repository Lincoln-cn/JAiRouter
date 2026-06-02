<template>
  <el-row :gutter="gutter" class="stat-card-row">
    <el-col v-for="(stat, index) in stats" :key="index" :span="span">
      <StatCard
        :icon="stat.icon"
        :value="stat.value"
        :label="stat.label"
        :icon-background="stat.iconBackground"
        :card-class="stat.cardClass"
        :formatter="stat.formatter"
      />
    </el-col>
  </el-row>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'
import StatCard from './StatCard.vue'

export interface StatItem {
  icon: Component
  value: number | string
  label: string
  iconBackground?: string
  cardClass?: string
  formatter?: (value: number | string) => string
}

interface Props {
  stats: StatItem[]
  gutter?: number
  columns?: 2 | 3 | 4 | 6
}

const props = withDefaults(defineProps<Props>(), {
  gutter: 20,
  columns: 4
})

const span = computed(() => 24 / props.columns)
</script>

<style scoped>
.stat-card-row {
  margin-bottom: 20px;
}

.stat-card-row .el-col {
  margin-bottom: 0;
}
</style>
