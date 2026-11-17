<script setup lang="ts">
import type { TableColumnCtx } from 'element-plus'

/** 表格列配置。 */
export interface TableColumn {
  prop: string
  label: string
  width?: number | string
  minWidth?: number | string
  align?: 'left' | 'center' | 'right'
  headerAlign?: 'left' | 'center' | 'right'
  fixed?: boolean | 'left' | 'right'
  showOverflowTooltip?: boolean
  /** 单元格纯文本格式化；复杂内容请用 #col-<prop> 插槽 */
  formatter?: (row: Record<string, unknown>, column: TableColumnCtx<Record<string, unknown>>, cellValue: unknown, index: number) => string
}

/** 分页入参。 */
export interface TablePagination {
  total: number
  pageNum: number
  pageSize: number
  pageSizes?: number[]
}

/**
 * 通用列表表格（默认布局 §3 第 2/4 层）：
 * 单元格插槽 #col-<prop>，表头插槽 #h-<prop>，空状态插槽 #empty。
 * pagination 传 null/false 表示不分页（详情/静态数据场景）。
 */
defineOptions({ name: 'AppTable' })

const props = withDefaults(
  defineProps<{
    columns: TableColumn[]
    data: Record<string, unknown>[]
    loading?: boolean
    /** 显示行序号列 */
    serial?: boolean
    /** 显示多选列 */
    selectable?: boolean
    rowKey?: string
    size?: 'large' | 'default' | 'small'
    stripe?: boolean
    border?: boolean
    height?: string | number
    emptyText?: string
    pagination?: TablePagination | null | false
  }>(),
  {
    loading: false,
    serial: false,
    selectable: false,
    rowKey: 'id',
    size: 'default',
    stripe: false,
    border: false,
    emptyText: '暂无数据',
    pagination: null,
  },
)

const emit = defineEmits<{
  'page-change': [{ pageNum: number; pageSize: number }]
  'selection-change': [rows: Record<string, unknown>[]]
  'row-click': [row: Record<string, unknown>]
}>()

function serialIndex(index: number): number {
  const p = props.pagination
  if (!p) return index + 1
  return (p.pageNum - 1) * p.pageSize + index + 1
}

function onPageChange(pageNum: number) {
  const p = props.pagination as TablePagination
  emit('page-change', { pageNum, pageSize: p.pageSize })
}

function onSizeChange(pageSize: number) {
  emit('page-change', { pageNum: 1, pageSize })
}

function onSelect(rows: Record<string, unknown>[]) {
  emit('selection-change', rows)
}
</script>

<template>
  <div class="app-table" :class="{ 'app-table--boxed': $slots.toolbar }">
    <div v-if="$slots.toolbar" class="app-table__toolbar">
      <slot name="toolbar" />
    </div>

    <el-table
      v-loading="loading"
      :data="data"
      :row-key="rowKey"
      :size="size"
      :stripe="stripe"
      :border="border"
      :height="height"
      :empty-text="emptyText"
      highlight-current-row
      @selection-change="onSelect"
      @row-click="(row: Record<string, unknown>) => emit('row-click', row)"
    >
      <el-table-column v-if="selectable" type="selection" width="44" reserve-selection />
      <el-table-column v-if="serial" type="index" width="56" align="center" header-align="center">
        <template #header>序号</template>
        <template #default="{ $index }">
          <span class="num t-sm">{{ serialIndex($index) }}</span>
        </template>
      </el-table-column>

      <el-table-column
        v-for="col in columns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :align="col.align ?? 'left'"
        :header-align="col.headerAlign ?? col.align ?? 'left'"
        :fixed="col.fixed"
        :show-overflow-tooltip="col.showOverflowTooltip"
        :formatter="col.formatter"
      >
        <template v-if="$slots[`h-${col.prop}`]" #header>
          <slot :name="`h-${col.prop}`" />
        </template>
        <template v-if="$slots[`col-${col.prop}`]" #default="scope">
          <slot :name="`col-${col.prop}`" v-bind="scope" />
        </template>
      </el-table-column>

      <template #empty>
        <slot name="empty" />
      </template>
    </el-table>

    <div v-if="pagination" class="app-table__pager">
      <span class="app-table__total">共 {{ pagination.total }} 条</span>
      <el-pagination
        :current-page="pagination.pageNum"
        :page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="pagination.pageSizes ?? [10, 20, 50, 100]"
        layout="sizes, prev, pager, next, jumper"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.app-table {
  background-color: var(--c-bg-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
  overflow: hidden;

  &__toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-3);
    padding: var(--sp-3) var(--sp-4);
    border-bottom: 1px solid var(--c-border);
  }

  &__pager {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: var(--sp-4);
    padding: var(--sp-3) var(--sp-4);
    border-top: 1px solid var(--c-border);
    background-color: var(--c-bg-surface);
  }

  &__total {
    font-size: var(--fs-sm);
    color: var(--c-text-3);
  }

  :deep(.el-table__inner-wrapper::before) {
    display: none;
  }

  :deep(.el-table__row) {
    cursor: default;
  }
}
</style>
