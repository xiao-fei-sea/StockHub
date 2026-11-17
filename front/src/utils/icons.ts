import { computed, type Component } from 'vue'
import * as ElementPlusIcons from '@element-plus/icons-vue'

/**
 * 按名称解析 Element Plus 图标组件（菜单 icon 字段来自 sys_menu.icon）。
 * 未匹配时回退到 Menu 图标，避免运行时报错。
 */
export function resolveIcon(name?: string | null): Component {
  if (!name) return ElementPlusIcons.Menu
  const icons = ElementPlusIcons as unknown as Record<string, Component>
  return icons[name] ?? ElementPlusIcons.Menu
}

/** 响应式图标解析（模板中使用）。 */
export function useIcon(name: () => string | null | undefined) {
  return computed(() => resolveIcon(name()))
}
