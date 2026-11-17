<script setup lang="ts">
import zhCn from 'element-plus/es/locale/lang/zh-cn'

/** 全局根组件：仅负责 Element Plus 中文语言与消息弹层作用域配置。 */
</script>

<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
