<template>
  <el-card class="management-card" shadow="hover" :body-style="bodyStyle">
    <template #header>
      <slot name="header">
        <PageHeader
          :title="title"
          :icon="icon"
          :show-search="showSearch"
          :search-placeholder="searchPlaceholder"
          v-model:search-model-value="searchValue"
          :filters="filters"
          :show-refresh="showRefresh"
          :show-add="showAdd"
          :add-text="addText"
          :add-disabled="addDisabled"
          @search="handleSearch"
          @clear="handleClear"
          @refresh="handleRefresh"
          @add="handleAdd"
        >
          <template #actions>
            <slot name="actions" />
          </template>
        </PageHeader>
      </slot>
    </template>

    <slot>
      <DataTable
        :data="data"
        :columns="columns"
        :loading="loading"
        :row-key="rowKey"
        :show-selection="showSelection"
        :show-actions="showActions"
        :show-edit="showEdit"
        :show-delete="showDelete"
        :actions-width="actionsWidth"
        :show-pagination="showPagination"
        :page-size="pageSize"
        :empty-text="emptyText"
        @edit="handleEdit"
        @delete="handleDelete"
        @selection-change="handleSelectionChange"
        @page-change="handlePageChange"
      >
        <template v-for="col in columns" :key="col.prop" #[`column-${col.prop}`]="slotProps">
          <slot :name="`column-${col.prop}`" v-bind="slotProps" />
        </template>
        <template #actions="slotProps">
          <slot name="actions" v-bind="slotProps" />
        </template>
      </DataTable>
    </slot>
  </el-card>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import PageHeader from './PageHeader.vue'
import DataTable, { type TableColumn } from './DataTable.vue'
import type { FilterConfig } from './SearchBar.vue'
import type { Component } from 'vue'

interface Props {
  title: string
  icon?: Component
  showSearch?: boolean
  searchPlaceholder?: string
  filters?: FilterConfig[]
  showRefresh?: boolean
  showAdd?: boolean
  addText?: string
  addDisabled?: boolean
  data?: any[]
  columns?: TableColumn[]
  loading?: boolean
  rowKey?: string | ((row: any) => string)
  showSelection?: boolean
  showActions?: boolean
  showEdit?: boolean
  showDelete?: boolean
  actionsWidth?: number | string
  showPagination?: boolean
  pageSize?: number
  emptyText?: string
  bodyStyle?: Record<string, string>
}

const props = withDefaults(defineProps<Props>(), {
  showSearch: false,
  showRefresh: true,
  showAdd: true,
  addText: '添加',
  addDisabled: false,
  data: () => [],
  columns: () => [],
  loading: false,
  rowKey: 'id',
  showSelection: false,
  showActions: true,
  showEdit: true,
  showDelete: true,
  actionsWidth: 180,
  showPagination: true,
  pageSize: 10,
  emptyText: '暂无数据',
  bodyStyle: () => ({ padding: '0' })
})

const emit = defineEmits<{
  'search': [value: string]
  'clear': []
  'refresh': []
  'add': []
  'edit': [row: any]
  'delete': [row: any]
  'selection-change': [selection: any[]]
  'page-change': [page: number, size: number]
}>()

const searchValue = ref('')

const handleSearch = (value: string) => {
  emit('search', value)
}

const handleClear = () => {
  emit('clear')
}

const handleRefresh = () => {
  emit('refresh')
}

const handleAdd = () => {
  emit('add')
}

const handleEdit = (row: any) => {
  emit('edit', row)
}

const handleDelete = (row: any) => {
  emit('delete', row)
}

const handleSelectionChange = (selection: any[]) => {
  emit('selection-change', selection)
}

const handlePageChange = (page: number, size: number) => {
  emit('page-change', page, size)
}
</script>

<style scoped>
.management-card {
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.06);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 0;
  min-height: 480px;
}

.management-card :deep(.el-card__header) {
  padding: 0;
  border-bottom: 1px solid #ebeef5;
}

.management-card :deep(.el-card__body) {
  flex: 1;
  overflow: auto;
}
</style>
