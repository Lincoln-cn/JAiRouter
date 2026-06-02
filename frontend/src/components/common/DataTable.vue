<template>
  <div class="data-table">
    <el-skeleton :loading="loading && data.length === 0" :rows="skeletonRows" animated>
      <template #template>
        <el-skeleton-item variant="h2" style="width: 40%; margin-bottom: 12px;" />
        <el-skeleton-item style="height: 42px; margin-bottom: 12px;" />
        <el-skeleton-item :style="{ height: skeletonTableHeight }" />
      </template>

      <template #default>
        <el-table
          :data="paginatedData"
          style="width: 100%"
          v-loading="loading"
          :element-loading-text="loadingText"
          element-loading-background="rgba(0, 0, 0, 0.05)"
          :row-key="rowKey"
          border
          fit
          :max-height="maxHeight"
          @selection-change="handleSelectionChange"
          @row-click="handleRowClick"
        >
          <el-table-column
            v-if="showSelection"
            type="selection"
            width="55"
          />

          <el-table-column
            v-for="col in columns"
            :key="col.prop"
            :prop="col.prop"
            :label="col.label"
            :width="col.width"
            :min-width="col.minWidth"
            :fixed="col.fixed"
            :sortable="col.sortable"
            :align="col.align || 'left'"
          >
            <template #default="scope">
              <slot :name="`column-${col.prop}`" :row="scope.row" :column="col">
                <template v-if="col.type === 'tag'">
                  <el-tag :type="getTagType(scope.row, col)" class="table-tag">
                    {{ formatValue(scope.row, col) }}
                  </el-tag>
                </template>
                <template v-else-if="col.type === 'switch'">
                  <el-switch
                    v-model="scope.row[col.prop]"
                    :disabled="col.disabled"
                    @change="(val: boolean) => handleSwitchChange(scope.row, col.prop, val)"
                  />
                </template>
                <template v-else-if="col.type === 'ellipsis'">
                  <el-tooltip :content="String(scope.row[col.prop] || '')" placement="top">
                    <div class="ellipsis">{{ scope.row[col.prop] || '—' }}</div>
                  </el-tooltip>
                </template>
                <template v-else>
                  {{ formatValue(scope.row, col) }}
                </template>
              </slot>
            </template>
          </el-table-column>

          <el-table-column
            v-if="showActions"
            label="操作"
            :width="actionsWidth"
            :fixed="actionsFixed"
            align="center"
          >
            <template #default="scope">
              <slot name="actions" :row="scope.row">
                <el-button
                  v-if="showEdit"
                  size="small"
                  type="primary"
                  plain
                  circle
                  title="编辑"
                  @click="handleEdit(scope.row)"
                >
                  <el-icon><Edit /></el-icon>
                </el-button>
                <el-button
                  v-if="showDelete"
                  size="small"
                  type="danger"
                  plain
                  circle
                  title="删除"
                  @click="handleDelete(scope.row)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </slot>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="data.length === 0 && !loading" class="empty-wrap">
          <el-empty :description="emptyText">
            <template #image>
              <img src="https://static-element.eleme.cn/e/element-ui/empty.svg" alt="empty" />
            </template>
          </el-empty>
        </div>

        <div class="table-footer" v-if="showPagination && data.length > 0">
          <div class="footer-info">
            共 {{ total }} 条（第 {{ currentPage }} / {{ totalPages }} 页）
          </div>
          <div class="footer-actions">
            <el-pagination
              v-model:current-page="currentPage"
              :page-size="pageSize"
              :total="total"
              :layout="paginationLayout"
              :page-sizes="pageSizes"
              @size-change="handleSizeChange"
              @current-change="handlePageChange"
            />
          </div>
        </div>
      </template>
    </el-skeleton>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Edit, Delete } from '@element-plus/icons-vue'

export interface TableColumn {
  prop: string
  label: string
  width?: number | string
  minWidth?: number | string
  fixed?: 'left' | 'right' | boolean
  sortable?: boolean
  align?: 'left' | 'center' | 'right'
  type?: 'tag' | 'switch' | 'ellipsis' | 'text'
  formatter?: (row: any, col: TableColumn) => string
  tagType?: string | ((row: any, col: TableColumn) => string)
  disabled?: boolean
}

interface Props {
  data: any[]
  columns: TableColumn[]
  loading?: boolean
  loadingText?: string
  rowKey?: string | ((row: any) => string)
  showSelection?: boolean
  showActions?: boolean
  showEdit?: boolean
  showDelete?: boolean
  actionsWidth?: number | string
  actionsFixed?: 'left' | 'right' | boolean
  showPagination?: boolean
  pageSize?: number
  pageSizes?: number[]
  paginationLayout?: string
  maxHeight?: number | string
  emptyText?: string
  skeletonRows?: number
  skeletonTableHeight?: string
  serverSide?: boolean
  totalItems?: number
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  loadingText: '加载中...',
  rowKey: 'id',
  showSelection: false,
  showActions: true,
  showEdit: true,
  showDelete: true,
  actionsWidth: 180,
  actionsFixed: 'right',
  showPagination: true,
  pageSize: 10,
  pageSizes: () => [5, 10, 20, 50],
  paginationLayout: 'prev, pager, next, sizes, jumper',
  emptyText: '暂无数据',
  skeletonRows: 6,
  skeletonTableHeight: '300px',
  serverSide: false,
  totalItems: 0
})

const emit = defineEmits<{
  'edit': [row: any]
  'delete': [row: any]
  'selection-change': [selection: any[]]
  'row-click': [row: any]
  'switch-change': [row: any, prop: string, value: boolean]
  'page-change': [page: number, size: number]
}>()

const currentPage = ref(1)
const localPageSize = ref(props.pageSize)

const total = computed(() => props.serverSide ? props.totalItems : props.data.length)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / localPageSize.value)))

const paginatedData = computed(() => {
  if (props.serverSide) {
    return props.data
  }
  const start = (currentPage.value - 1) * localPageSize.value
  return props.data.slice(start, start + localPageSize.value)
})

watch([() => props.data, localPageSize], () => {
  if (currentPage.value > totalPages.value) {
    currentPage.value = totalPages.value
  }
})

const getTagType = (row: any, col: TableColumn): string => {
  if (typeof col.tagType === 'function') {
    return col.tagType(row, col)
  }
  return col.tagType || 'info'
}

const formatValue = (row: any, col: TableColumn): string => {
  if (col.formatter) {
    return col.formatter(row, col)
  }
  const value = row[col.prop]
  if (value === null || value === undefined) {
    return '—'
  }
  return String(value)
}

const handleSelectionChange = (selection: any[]) => {
  emit('selection-change', selection)
}

const handleRowClick = (row: any) => {
  emit('row-click', row)
}

const handleSwitchChange = (row: any, prop: string, value: boolean) => {
  emit('switch-change', row, prop, value)
}

const handleEdit = (row: any) => {
  emit('edit', row)
}

const handleDelete = (row: any) => {
  emit('delete', row)
}

const handleSizeChange = (size: number) => {
  localPageSize.value = size
  currentPage.value = 1
  emit('page-change', currentPage.value, localPageSize.value)
}

const handlePageChange = (page: number) => {
  currentPage.value = page
  emit('page-change', currentPage.value, localPageSize.value)
}
</script>

<style scoped>
.data-table {
  width: 100%;
}

.table-tag {
  font-size: 12px;
}

.ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-wrap {
  padding: 40px 0;
}

.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.footer-info {
  font-size: 14px;
  color: #606266;
}

.footer-actions {
  display: flex;
  align-items: center;
}
</style>
