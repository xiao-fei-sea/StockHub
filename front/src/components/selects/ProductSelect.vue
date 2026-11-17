<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getProductOptions } from '@/api/product'
import type { ProductOption } from '@/types/entity'

/**
 * 商品选择器（远程搜索，API-PROD-006）。
 * value=商品 id；@change 回传 (id, option)，单据明细可用 option.unit/batchFlag 等联动。
 */
defineOptions({ name: 'ProductSelect' })

withDefaults(
  defineProps<{
    modelValue?: number | null
    clearable?: boolean
    placeholder?: string
    disabled?: boolean
    size?: 'large' | 'default' | 'small'
  }>(),
  {
    modelValue: null,
    clearable: true,
    placeholder: '搜索商品编码 / 名称',
    disabled: false,
    size: 'default',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number | null]
  change: [value: number | null, option?: ProductOption]
}>()

const options = ref<ProductOption[]>([])
const searching = ref(false)

/** 预载全量选项：保证外部赋值的 modelValue 能回显标签。 */
async function preload() {
  try {
    options.value = (await getProductOptions()) ?? []
  } catch {
    options.value = []
  }
}
onMounted(preload)

async function remoteSearch(keyword: string) {
  searching.value = true
  try {
    options.value = (await getProductOptions(keyword || undefined)) ?? []
  } finally {
    searching.value = false
  }
}

function onChange(value: number | null) {
  const option = options.value.find((o) => o.id === value)
  emit('update:modelValue', value)
  emit('change', value, option)
}
</script>

<template>
  <el-select
    :model-value="modelValue ?? undefined"
    :clearable="clearable"
    :disabled="disabled"
    :placeholder="placeholder"
    :size="size"
    :loading="searching"
    filterable
    remote
    :remote-method="remoteSearch"
    :persistent="false"
    @visible-change="(visible: boolean) => visible && remoteSearch('')"
    @update:model-value="onChange"
  >
    <el-option
      v-for="opt in options"
      :key="opt.id"
      :label="`${opt.productCode} ${opt.productName}`"
      :value="opt.id"
    >
      <span class="option-name">{{ opt.productName }}</span>
      <span class="option-code num">{{ opt.productCode }}</span>
    </el-option>
  </el-select>
</template>

<style scoped>
.option-name {
  display: inline-block;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}
.option-code {
  float: right;
  color: var(--c-text-3);
  font-size: var(--fs-xs);
  margin-left: var(--sp-4);
}
</style>
