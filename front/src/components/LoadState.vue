<script setup lang="ts">
import { RefreshRight } from '@element-plus/icons-vue'

/**
 * 图表/列表区块加载状态机：
 * loading → 骨架；error → 错误 + 重试（交互设计 ⑧ 图表 loading 与重试）；否则渲染默认插槽。
 */
defineOptions({ name: 'LoadState' })

defineProps<{
  loading?: boolean
  error?: boolean
  empty?: boolean
  height?: string
}>()

const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="loading" class="load-state" :style="{ minHeight: height ?? '220px' }">
    <el-skeleton :rows="4" animated />
  </div>

  <div v-else-if="error" class="load-state load-state--center" :style="{ minHeight: height ?? '220px' }">
    <p class="load-state__text">数据加载失败</p>
    <el-button size="small" :icon="RefreshRight" @click="emit('retry')">重试</el-button>
  </div>

  <div v-else-if="empty" class="load-state load-state--center" :style="{ minHeight: height ?? '220px' }">
    <p class="load-state__text">暂无数据</p>
  </div>

  <slot v-else />
</template>

<style scoped lang="scss">
.load-state {
  padding: var(--sp-4) 0;

  &--center {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: var(--sp-3);
  }

  &__text {
    font-size: var(--fs-sm);
    color: var(--c-text-3);
  }
}
</style>
