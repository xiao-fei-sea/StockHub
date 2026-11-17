<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowDown, ArrowUp, Refresh, Search } from '@element-plus/icons-vue'

/** 查询字段配置。 */
export interface SearchField {
  key: string
  /** 左侧标签，纯关键字场景可省略 */
  label?: string
  type: 'input' | 'select' | 'date' | 'daterange' | 'number'
  placeholder?: string
  options?: { label: string; value: string | number }[]
  clearable?: boolean
  /** 控件宽度（px 数字或 CSS 长度） */
  width?: number | string
}

/**
 * 通用查询区（默认布局 §3 第 1 层）。
 * v-model 传入查询对象；@search 点击查询或回车触发；@reset 重置后自动触发一次查询。
 * 自定义控件用 #field-<key> 插槽（如仓库/商品选择器）。
 */
defineOptions({ name: 'SearchForm' })

const props = withDefaults(
  defineProps<{
    fields: SearchField[]
    modelValue: Record<string, unknown>
    loading?: boolean
    /** 字段超过 COLLAPSE_AT 个时显示展开/收起（交互设计 §1.4） */
    collapsible?: boolean
  }>(),
  { loading: false, collapsible: true },
)

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
  search: []
  reset: []
}>()

const COLLAPSE_AT = 4
const expanded = ref(false)

const showCollapse = computed(
  () => props.collapsible && props.fields.length > COLLAPSE_AT,
)

const visibleFields = computed(() => {
  if (showCollapse.value && !expanded.value) return props.fields.slice(0, COLLAPSE_AT)
  return props.fields
})

function patch(key: string, value: unknown) {
  emit('update:modelValue', { ...props.modelValue, [key]: value })
}

function controlWidth(f: SearchField): string | undefined {
  if (f.width === undefined) return undefined
  return typeof f.width === 'number' ? `${f.width}px` : f.width
}

function handleSearch() {
  emit('search')
}

function handleReset() {
  const resetValues: Record<string, unknown> = {}
  for (const f of props.fields) {
    resetValues[f.key] = f.type === 'daterange' ? [] : undefined
  }
  emit('update:modelValue', { ...props.modelValue, ...resetValues })
  emit('reset')
  emit('search')
}
</script>

<template>
  <div class="search-panel">
    <el-form inline class="search-form" label-position="left" @submit.prevent>
      <template v-for="f in visibleFields" :key="f.key">
        <el-form-item :label="f.label">
          <!-- 自定义控件插槽 -->
          <slot :name="`field-${f.key}`" :value="modelValue[f.key]" :patch="patch">
            <el-input
              v-if="f.type === 'input'"
              :model-value="(modelValue[f.key] as string) ?? ''"
              :placeholder="f.placeholder ?? '请输入'"
              :clearable="f.clearable ?? true"
              :style="{ width: controlWidth(f) }"
              @update:model-value="patch(f.key, $event)"
              @keyup.enter="handleSearch"
            />
            <el-select
              v-else-if="f.type === 'select'"
              :model-value="modelValue[f.key]"
              :placeholder="f.placeholder ?? '请选择'"
              :clearable="f.clearable ?? true"
              :style="{ width: controlWidth(f) }"
              @update:model-value="patch(f.key, $event)"
            >
              <el-option
                v-for="opt in f.options ?? []"
                :key="String(opt.value)"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <el-date-picker
              v-else-if="f.type === 'daterange'"
              :model-value="(modelValue[f.key] as string[]) ?? []"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              :clearable="f.clearable ?? true"
              :style="{ width: controlWidth(f) ?? '240px' }"
              @update:model-value="patch(f.key, $event)"
            />
            <el-date-picker
              v-else-if="f.type === 'date'"
              :model-value="(modelValue[f.key] as string) ?? ''"
              type="date"
              placeholder="选择日期"
              value-format="YYYY-MM-DD"
              :clearable="f.clearable ?? true"
              :style="{ width: controlWidth(f) }"
              @update:model-value="patch(f.key, $event)"
            />
            <el-input-number
              v-else-if="f.type === 'number'"
              :model-value="(modelValue[f.key] as number) ?? undefined"
              :placeholder="f.placeholder"
              :controls="false"
              :style="{ width: controlWidth(f) ?? '140px' }"
              @update:model-value="patch(f.key, $event)"
            />
          </slot>
        </el-form-item>
      </template>

      <div class="search-actions">
        <el-button type="primary" :icon="Search" :loading="loading" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        <el-button v-if="showCollapse" link type="primary" @click="expanded = !expanded">
          {{ expanded ? '收起' : '展开' }}
          <el-icon class="search-actions__arrow">
            <ArrowUp v-if="expanded" />
            <ArrowDown v-else />
          </el-icon>
        </el-button>
      </div>
    </el-form>
  </div>
</template>

<style scoped lang="scss">
.search-panel {
  padding: var(--sp-4) var(--sp-4) 0;
  background-color: var(--c-bg-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xs);
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 0 var(--sp-4);

  :deep(.el-form-item) {
    margin-right: 0;
    margin-bottom: var(--sp-4);
  }

  :deep(.el-form-item__label) {
    font-size: var(--fs-sm);
    height: auto;
    line-height: normal;
    padding-bottom: 0;
    color: var(--c-text-2);
  }

  :deep(.el-form-item__content) {
    line-height: normal;
  }
}

.search-actions {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  margin-left: auto;
  padding-bottom: var(--sp-4);

  &__arrow {
    margin-left: 4px;
  }
}
</style>
