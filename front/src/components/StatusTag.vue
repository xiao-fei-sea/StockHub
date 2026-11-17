<script setup lang="ts">
import { computed } from 'vue'
import { orderStatusInfo } from '@/utils/dict'

/** 单据状态标签：全局唯一状态配色来源（系统UI设计规范 §7）。 */
defineOptions({ name: 'StatusTag' })

const props = withDefaults(
  defineProps<{
    /** 单据状态 0-5 */
    status: number | null | undefined
    /** 需要覆盖文案时传入（如调拨确认状态等自定义场景） */
    label?: string
    size?: 'large' | 'default' | 'small'
  }>(),
  { size: 'default' },
)

const info = computed(() => {
  const base = orderStatusInfo(props.status)
  return { ...base, label: props.label ?? base.label }
})
</script>

<template>
  <el-tag :type="info.type" :size="size" effect="light">{{ info.label }}</el-tag>
</template>
