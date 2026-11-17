<script setup lang="ts">
import { Plus, Delete } from '@element-plus/icons-vue'
import type { TableColumn } from '@/components/AppTable.vue'

/**
 * 单据明细行编辑表格（入库/出库/调拨/盘点明细通用）。
 * - 行数据由父页面 v-model 管理；单元格编辑控件用 #col-<prop> 插槽（作用域 { row, index }）
 * - 自动行号列 + 行删除按钮；#footer 插槽放合计/备注等
 */
defineOptions({ name: 'ItemEditTable' })

const props = withDefaults(
  defineProps<{
    modelValue: Record<string, unknown>[]
    columns: TableColumn[]
    /** 行号字段名（参与 v-model 数据结构） */
    lineNoKey?: string
    disabled?: boolean
    addable?: boolean
    addText?: string
    emptyText?: string
    maxRows?: number
  }>(),
  {
    lineNoKey: 'lineNo',
    disabled: false,
    addable: true,
    addText: '添加明细',
    emptyText: '暂无明细，请添加',
    maxRows: 500,
  },
)

const emit = defineEmits<{
  'update:modelValue': [rows: Record<string, unknown>[]]
  add: []
  remove: [index: number]
}>()

function removeRow(index: number) {
  emit('update:modelValue', props.modelValue.filter((_, i) => i !== index))
  emit('remove', index)
}

function canAdd(): boolean {
  return !props.disabled && props.modelValue.length < props.maxRows
}
</script>

<template>
  <div class="item-edit">
    <el-table :data="modelValue" row-key="lineNo" size="small" class="item-edit__table">
      <el-table-column type="index" width="52" align="center" header-align="center">
        <template #header>#</template>
        <template #default="{ $index }">
          <span class="num">{{ $index + 1 }}</span>
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
        :show-overflow-tooltip="col.showOverflowTooltip"
        :formatter="col.formatter"
      >
        <template v-if="$slots[`col-${col.prop}`]" #default="scope">
          <slot :name="`col-${col.prop}`" v-bind="scope" />
        </template>
      </el-table-column>

      <el-table-column v-if="!disabled" label="" width="52" align="center" fixed="right">
        <template #default="{ $index }">
          <el-tooltip content="删除该行" placement="top">
            <button class="row-remove" type="button" @click.stop="removeRow($index)">
              <el-icon><Delete /></el-icon>
            </button>
          </el-tooltip>
        </template>
      </el-table-column>

      <template #empty>
        <div class="item-edit__empty">{{ emptyText }}</div>
      </template>
    </el-table>

    <div class="item-edit__foot">
      <el-button
        v-if="addable"
        :disabled="!canAdd()"
        :icon="Plus"
        size="small"
        @click="emit('add')"
      >
        {{ addText }}
      </el-button>
      <div class="item-edit__foot-right">
        <slot name="footer" />
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.item-edit {
  border: 1px solid var(--c-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  background-color: var(--c-bg-surface);

  &__table {
    :deep(.el-table__header th.el-table__cell) {
      background-color: var(--c-bg-subtle);
    }
  }

  &__empty {
    padding: var(--sp-6) 0;
    color: var(--c-text-3);
    font-size: var(--fs-sm);
  }

  &__foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-3);
    padding: var(--sp-2) var(--sp-3);
    border-top: 1px solid var(--c-border);
    background-color: var(--c-bg-subtle);
  }

  &__foot-right {
    display: flex;
    align-items: center;
    gap: var(--sp-4);
    font-size: var(--fs-sm);
    color: var(--c-text-2);
  }
}

.row-remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--c-text-3);
  cursor: pointer;

  &:hover {
    background-color: var(--c-danger-soft);
    color: var(--c-danger);
  }
}
</style>
