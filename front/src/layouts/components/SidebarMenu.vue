<script setup lang="ts">
import { resolveIcon } from '@/utils/icons'
import type { MenuNodeVO } from '@/types/entity'

defineOptions({ name: 'SidebarItem' })

const props = defineProps<{
  item: MenuNodeVO
  basePath: string
}>()

function resolvePath(base: string, path: string): string {
  if (path.startsWith('/')) return path
  const b = base === '/' ? '' : base
  return `${b}/${path}`.replace(/\/+/g, '/')
}

const fullPath = () => resolvePath(props.basePath, props.item.path)
</script>

<template>
  <!-- 目录：仅展开/收起，不产生路由 -->
  <el-sub-menu v-if="item.menuType === 'C' && (item.children?.length ?? 0) > 0" :index="fullPath()">
    <template #title>
      <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
      <span>{{ item.menuName }}</span>
    </template>
    <SidebarItem
      v-for="child in item.children"
      :key="child.id"
      :item="child"
      :base-path="fullPath()"
    />
  </el-sub-menu>

  <!-- 菜单：el-menu router 模式直接跳转 -->
  <el-menu-item v-else-if="item.menuType === 'M'" :index="fullPath()">
    <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
    <template #title>{{ item.menuName }}</template>
  </el-menu-item>
</template>
