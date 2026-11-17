<script setup lang="ts">
import { ref, watch } from 'vue'
import { getLocationOptions } from '@/api/location'
import type { LocationOption } from '@/types/entity'

/**
 * 货位选择器（API-LOC-005）：按仓库过滤，切换仓库时自动重置。
 * value=货位 id；@change 回传 (id, option)。
 */
defineOptions({ name: 'LocationSelect' })

const props = withDefaults(
  defineProps<{
    modelValue?: number | null
    /** 必填：货位选项依赖仓库上下文 */
    warehouseId?: number | null
    /** 过滤货位类型（存储位/拣货位/暂存位/收货位） */
    locationType?: string
    /** 过滤状态（空闲/占用/锁定/禁用） */
    currentStatus?: string
    clearable?: boolean
    placeholder?: string
    disabled?: boolean
    size?: 'large' | 'default' | 'small'
  }>(),
  {
    modelValue: null,
    warehouseId: null,
    locationType: undefined,
    currentStatus: undefined,
    clearable: true,
    placeholder: '选择货位',
    disabled: false,
    size: 'default',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number | null]
  change: [value: number | null, option?: LocationOption]
}>()

const options = ref<LocationOption[]>([])
const loading = ref(false)

async function loadOptions(keyword?: string) {
  if (!props.warehouseId) {
    options.value = []
    return
  }
  loading.value = true
  try {
    options.value =
      (await getLocationOptions({
        warehouseId: props.warehouseId,
        locationType: props.locationType,
        currentStatus: props.currentStatus,
        keyword,
      })) ?? []
  } finally {
    loading.value = false
  }
}

watch(
  () => props.warehouseId,
  () => {
    options.value = []
    emit('update:modelValue', null)
    loadOptions()
  },
  { immediate: false },
)

function onVisible(visible: boolean) {
  if (visible) loadOptions()
}

function onChange(value: number | null) {
  emit('update:modelValue', value)
  emit('change', value, options.value.find((o) => o.id === value))
}
</script>

<template>
  <el-select
    :model-value="modelValue ?? undefined"
    :clearable="clearable"
    :disabled="disabled || !warehouseId"
    :placeholder="warehouseId ? placeholder : '请先选择仓库'"
    :size="size"
    :loading="loading"
    filterable
    :persistent="false"
    @visible-change="onVisible"
    @update:model-value="onChange"
  >
    <el-option
      v-for="opt in options"
      :key="opt.id"
      :label="`${opt.locationCode}${opt.locationName ? ` ${opt.locationName}` : ''}`"
      :value="opt.id"
    >
      <span>{{ opt.locationCode }}</span>
      <span class="option-meta">{{ opt.locationType }} · {{ opt.currentStatus }}</span>
    </el-option>
  </el-select>
</template>

<style scoped>
.option-meta {
  float: right;
  color: var(--c-text-3);
  font-size: var(--fs-xs);
}
</style>
