<script setup lang="ts">
/** 页面头部：标题 + 描述 + 右侧操作区（统一规范见 §3 默认布局结构）。 */
defineOptions({ name: 'PageHeader' })

defineProps<{
  title: string
  desc?: string
}>()
</script>

<template>
  <header class="page-header">
    <div class="page-header__main">
      <h1 class="page-header__title">{{ title }}</h1>
      <p v-if="desc" class="page-header__desc">{{ desc }}</p>
      <slot name="extra" />
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>
