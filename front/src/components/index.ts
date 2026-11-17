/**
 * 公共组件统一导出（前端开发规范 §4）。
 * 页面统一使用 `import { AppTable, StatusTag } from '@/components'`。
 */

export { default as StatusTag } from './StatusTag.vue'
export { default as PageHeader } from './PageHeader.vue'
export { default as AppTable } from './AppTable.vue'
export type { TableColumn, TablePagination } from './AppTable.vue'
export { default as SearchForm } from './SearchForm.vue'
export type { SearchField } from './SearchForm.vue'
export { default as OrderDetailDrawer } from './OrderDetailDrawer.vue'
export type { DetailField } from './OrderDetailDrawer.vue'
export { default as ItemEditTable } from './ItemEditTable.vue'
export { default as WarehouseSelect } from './selects/WarehouseSelect.vue'
export { default as ProductSelect } from './selects/ProductSelect.vue'
export { default as LocationSelect } from './selects/LocationSelect.vue'
export { default as AppChart } from './AppChart.vue'
export { default as LoadState } from './LoadState.vue'
export { confirmDialog } from './ConfirmDialog'
export type { ConfirmOptions } from './ConfirmDialog'
