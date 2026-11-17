<script setup lang="ts">
import { onBeforeMount } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/**
 * 页签“刷新当前”中转页：
 * 由 keep-alive 缓存中移除目标组件后经本页跳回，实现页面真实重新加载。
 */
defineOptions({ name: 'RedirectPage' })

const route = useRoute()
const router = useRouter()

onBeforeMount(() => {
  const path = '/' + (Array.isArray(route.params.path) ? route.params.path.join('/') : route.params.path ?? '')
  const { ...query } = route.query
  router.replace({ path, query })
})
</script>

<template>
  <div class="redirect-loading">
    <span class="spinner" />
  </div>
</template>

<style scoped>
.redirect-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200px;
}
.spinner {
  width: 22px;
  height: 22px;
  border: 2px solid var(--c-border);
  border-top-color: var(--c-primary);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
