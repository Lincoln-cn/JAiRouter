/**
 * 通用组件导出
 * 统一导出所有可复用组件
 */

// 统计卡片组件
export { default as StatCard } from './StatCard.vue'
export { default as StatCardRow } from './StatCardRow.vue'
export type { StatItem } from './StatCardRow.vue'

// 搜索栏组件
export { default as SearchBar } from './SearchBar.vue'
export type { FilterOption, FilterConfig } from './SearchBar.vue'

// 页面头部组件
export { default as PageHeader } from './PageHeader.vue'

// 数据表格组件
export { default as DataTable } from './DataTable.vue'
export type { TableColumn } from './DataTable.vue'

// 表单对话框组件
export { default as FormDialog } from './FormDialog.vue'

// 详情对话框组件
export { default as DetailDialog } from './DetailDialog.vue'
export type { DetailItem } from './DetailDialog.vue'

// 管理卡片组件（组合 PageHeader + DataTable）
export { default as ManagementCard } from './ManagementCard.vue'

// 表单分区组件
export { default as FormSection } from './FormSection.vue'

// 表单字段组组件
export { default as FormGroup } from './FormGroup.vue'
export type { FormField, FormFieldOption } from './FormGroup.vue'

// 页面提示组件
export { default as PageAlert } from './PageAlert.vue'

// 服务标签页组件
export { default as ServiceTabs } from './ServiceTabs.vue'
export type { TabItem } from './ServiceTabs.vue'

// 空状态组件
export { default as EmptyState } from './EmptyState.vue'
