<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getWarehouseOptions } from '@/api/warehouse'
import type { WarehouseOption } from '@/types/entity'

/**
 * 仓库选择器（数据权限内）。
 * value=仓库 id；@change 回传 (id, option) 供父级读取仓库名等信息。
 */
defineOptions({ name: 'WarehouseSelect' })

withDefaults(
  defineProps<{
    modelValue?: number | null
    multiple?: boolean
    clearable?: boolean
    placeholder?: string
    disabled?: boolean
    size?: 'large' | 'default' | 'small'
  }>(),
  {
    modelValue: null,
    multiple: false,
    clearable: true,
    placeholder: '选择仓库',
    disabled: false,
    size: 'default',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number | null]
  change: [value: number | null, option?: WarehouseOption]
}>()

const options = ref<WarehouseOption[]>([])
const loaded = ref(false)

async function loadOptions() {
  if (loaded.value) return
  try {
    options.value = (await getWarehouseOptions()) ?? []
    loaded.value = true
  } catch {
    options.value = []
  }
}
onMounted(loadOptions)

function onChange(value: number | null) {
  emit('update:modelValue', value)
  emit('change', value, options.value.find((o) => o.id === value))
}
</script>

<template>
  <el-select
    :model-value="modelValue ?? undefined"
    :multiple="multiple"
    :clearable="clearable"
    :disabled="disabled"
    :placeholder="placeholder"
    :size="size"
    :loading="!loaded"
    filterable
    @update:model-value="onChange"
  >
    <el-option v-for="opt in options" :key="opt.id" :label="opt.warehouseName" :value="opt.id">
      <span>{{ opt.warehouseName }}</span>
      <span class="option-code num">{{ opt.warehouseCode }}</span>
    </el-option>
  </el-select>
</template>

<style scoped>
.option-code {
  float: right;
  color: var(--c-text-3);
  font-size: var(--fs-xs);
}
</style>
