<template>
  <div class="search-bar">
    <el-input
      v-if="showSearch"
      v-model="searchValue"
      :placeholder="searchPlaceholder"
      clearable
      size="default"
      class="search-input"
      @clear="handleClear"
      @keyup.enter="handleSearch"
    >
      <template #prefix>
        <el-icon><Search /></el-icon>
      </template>
    </el-input>

    <el-select
      v-for="filter in filters"
      :key="filter.key"
      v-model="filterValues[filter.key]"
      :placeholder="filter.placeholder"
      clearable
      size="default"
      class="filter-select"
      @change="handleFilterChange(filter.key)"
    >
      <el-option
        v-for="option in filter.options"
        :key="option.value"
        :label="option.label"
        :value="option.value"
      />
    </el-select>

    <el-button
      v-if="showRefresh"
      type="default"
      circle
      class="refresh-button"
      @click="handleRefresh"
      title="刷新"
    >
      <el-icon><Refresh /></el-icon>
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, reactive } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'

export interface FilterOption {
  label: string
  value: string | number
}

export interface FilterConfig {
  key: string
  placeholder: string
  options: FilterOption[]
}

interface Props {
  showSearch?: boolean
  searchPlaceholder?: string
  modelValue?: string
  filters?: FilterConfig[]
  filterModelValue?: Record<string, string | number | undefined>
  showRefresh?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showSearch: true,
  searchPlaceholder: '搜索...',
  modelValue: '',
  filters: () => [],
  filterModelValue: () => ({}),
  showRefresh: true
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:filterModelValue': [value: Record<string, string | number | undefined>]
  'search': [value: string]
  'clear': []
  'refresh': []
  'filter-change': [key: string, value: string | number | undefined]
}>()

const searchValue = ref(props.modelValue)
const filterValues = reactive<Record<string, string | number | undefined>>({ ...props.filterModelValue })

watch(() => props.modelValue, (val) => {
  searchValue.value = val
})

watch(() => props.filterModelValue, (val) => {
  Object.assign(filterValues, val)
}, { deep: true })

watch(searchValue, (val) => {
  emit('update:modelValue', val)
})

watch(filterValues, (val) => {
  emit('update:filterModelValue', { ...val })
}, { deep: true })

const handleSearch = () => {
  emit('search', searchValue.value)
}

const handleClear = () => {
  emit('clear')
}

const handleRefresh = () => {
  emit('refresh')
}

const handleFilterChange = (key: string) => {
  emit('filter-change', key, filterValues[key])
}
</script>

<style scoped>
.search-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.search-input {
  width: 260px;
}

.filter-select {
  width: 140px;
}

.refresh-button {
  flex-shrink: 0;
}
</style>
