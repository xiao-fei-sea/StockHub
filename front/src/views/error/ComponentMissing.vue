<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { Warning } from '@element-plus/icons-vue'

defineOptions({ name: 'ComponentMissing' })

const route = useRoute()
const router = useRouter()

/** 菜单在 sys_menu 中配置的组件路径（permission store 解析失败时写入 meta）。 */
const component = String(route.meta.componentMissing ?? '')

function goBack() {
  router.push('/dashboard')
}
</script>

<template>
  <div class="missing">
    <el-icon class="missing__icon" :size="72"><Warning /></el-icon>
    <p class="missing__title">菜单组件未实现</p>
    <p class="missing__desc">
      该菜单对应的前端页面不存在或路径配置错误，请联系管理员核对
      <code>sys_menu.component</code>。
    </p>
    <el-alert v-if="component" type="error" :closable="false" class="missing__code">
      <template #title>component：{{ component }}.vue</template>
    </el-alert>
    <el-button type="primary" @click="goBack">返回首页</el-button>
  </div>
</template>

<style scoped>
.missing {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 70vh;
  text-align: center;
  padding: var(--sp-8);
}
.missing__icon {
  color: var(--c-danger, #f56c6c);
}
.missing__title {
  margin-top: var(--sp-5);
  font-size: var(--fs-lg);
  font-weight: var(--fw-semibold);
  color: var(--c-text);
}
.missing__desc {
  margin-top: var(--sp-2);
  margin-bottom: var(--sp-5);
  max-width: 460px;
  font-size: var(--fs-sm);
  color: var(--c-text-3);
}
.missing__code {
  margin-bottom: var(--sp-6);
  max-width: 460px;
  text-align: left;
}
</style>
