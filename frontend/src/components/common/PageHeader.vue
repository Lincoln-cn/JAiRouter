<template>
  <div class="page-header">
    <div class="header-left">
      <div class="header-title">
        <el-icon v-if="icon">
          <component :is="icon" />
        </el-icon>
        <span>{{ title }}</span>
      </div>

      <SearchBar
        v-if="showSearch"
        v-model="searchValue"
        :search-placeholder="searchPlaceholder"
        :filters="filters"
        :filter-model-value="filterValues"
        :show-refresh="showRefresh"
        @search="handleSearch"
        @clear="handleClear"
        @refresh="handleRefresh"
        @filter-change="handleFilterChange"
      />
    </div>

    <div class="header-actions">
      <slot name="actions">
        <el-button
          v-if="showAdd"
          type="primary"
          size="default"
          @click="handleAdd"
          :disabled="addDisabled"
        >
          <el-icon><Plus /></el-icon>
          {{ addText }}
        </el-button>
      </slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import SearchBar, { type FilterConfig } from './SearchBar.vue'
import type { Component } from 'vue'

interface Props {
  title: string
  icon?: Component
  showSearch?: boolean
  searchPlaceholder?: string
  searchModelValue?: string
  filters?: FilterConfig[]
  filterModelValue?: Record<string, string | number | undefined>
  showRefresh?: boolean
  showAdd?: boolean
  addText?: string
  addDisabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showSearch: false,
  searchPlaceholder: '搜索...',
  searchModelValue: '',
  filters: () => [],
  filterModelValue: () => ({}),
  showRefresh: true,
  showAdd: true,
  addText: '添加',
  addDisabled: false
})

const emit = defineEmits<{
  'update:searchModelValue': [value: string]
  'update:filterModelValue': [value: Record<string, string | number | undefined>]
  'search': [value: string]
  'clear': []
  'refresh': []
  'filter-change': [key: string, value: string | number | undefined]
  'add': []
}>()

const searchValue = ref(props.searchModelValue)
const filterValues = ref({ ...props.filterModelValue })

watch(() => props.searchModelValue, (val) => {
  searchValue.value = val
})

watch(() => props.filterModelValue, (val) => {
  filterValues.value = { ...val }
}, { deep: true })

watch(searchValue, (val) => {
  emit('update:searchModelValue', val)
})

watch(filterValues, (val) => {
  emit('update:filterModelValue', { ...val })
}, { deep: true })

const handleSearch = (value: string) => {
  emit('search', value)
}

const handleClear = () => {
  emit('clear')
}

const handleRefresh = () => {
  emit('refresh')
}

const handleFilterChange = (key: string, value: string | number | undefined) => {
  emit('filter-change', key, value)
}

const handleAdd = () => {
  emit('add')
}
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18px 22px;
  gap: 12px;
  flex-wrap: wrap;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 1;
  min-width: 320px;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}

.header-title .el-icon {
  font-size: 20px;
  color: #409EFF;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
