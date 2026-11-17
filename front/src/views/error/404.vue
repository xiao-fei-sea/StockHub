<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

defineOptions({ name: 'NotFound' })

const router = useRouter()

function goBack() {
  router.push('/dashboard')
}
</script>

<template>
  <div class="not-found">
    <p class="not-found__code">404</p>
    <p class="not-found__title">页面不存在或没有访问权限</p>
    <p class="not-found__desc">你访问的页面可能已被移除、从未创建，或你的账号没有对应菜单权限。</p>
    <el-button type="primary" :icon="ArrowLeft" @click="goBack">返回首页</el-button>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 70vh;
  text-align: center;
  padding: var(--sp-8);
}
.not-found__code {
  font-size: 96px;
  line-height: 1;
  font-weight: var(--fw-semibold);
  letter-spacing: -0.04em;
  color: var(--c-border-strong);
}
.not-found__title {
  margin-top: var(--sp-5);
  font-size: var(--fs-lg);
  font-weight: var(--fw-semibold);
  color: var(--c-text);
}
.not-found__desc {
  margin-top: var(--sp-2);
  margin-bottom: var(--sp-6);
  max-width: 420px;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}
</style>
