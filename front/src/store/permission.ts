import { defineStore } from 'pinia'
import type { Component } from 'vue'
import router from '@/router'
import { useUserStore } from '@/store/user'
import type { MenuNodeVO } from '@/types/entity'

/** Vite 视图组件映射表：component 字段 → 动态 import。 */
const viewModules = import.meta.glob('../views/**/*.vue') as Record<string, () => Promise<Component>>

interface PermissionState {
  /** 当前用户可见菜单树（C/M，来自 /api/auth/info） */
  menus: MenuNodeVO[]
  /** 已注册的动态路由 name（登出时按此移除） */
  dynamicNames: string[]
  /** 动态路由是否已生成 */
  routesGenerated: boolean
}

/** 由 path 派生路由 name：/inventory/stocktake → InventoryStocktake（与页签/keep-alive 一致）。 */
export function pathToRouteName(path: string): string {
  const segments = path.replace(/^\//, '').split('/').filter(Boolean)
  if (segments.length === 0) return 'Index'
  return segments.map((s) => s.charAt(0).toUpperCase() + s.slice(1)).join('')
}

/** 拼接父子路径：绝对路径直接采用，相对路径按父级拼接。 */
function joinPath(parentPath: string, path: string): string {
  if (path.startsWith('/')) return path
  const base = parentPath === '/' ? '' : parentPath
  return `${base}/${path}`.replace(/\/+/g, '/')
}

function resolveView(component?: string | null): (() => Promise<Component>) | null {
  if (!component) return null
  const key = `../views/${component}.vue`
  const loader = viewModules[key]
  if (!loader) {
    console.warn(`[SmartWMS] 菜单组件未实现：${component}`)
    return null
  }
  return loader
}

/** 组件解析失败时的兜底占位页：菜单仍可点击，页面上直接显示缺失的组件路径。 */
const missingView = () => import('@/views/error/ComponentMissing.vue')

export const usePermissionStore = defineStore('permission', {
  state: (): PermissionState => ({
    menus: [],
    dynamicNames: [],
    routesGenerated: false,
  }),

  getters: {
    /** 侧栏可见菜单树：visible=0 过滤、同级按 sort 升序、无可见子菜单的目录隐藏。 */
    sidebarMenus: (state): MenuNodeVO[] => {
      const filter = (nodes: MenuNodeVO[]): MenuNodeVO[] =>
        nodes
          .filter((n) => n.visible === 1)
          .sort((a, b) => a.sort - b.sort)
          .map((n) => ({ ...n, children: n.children ? filter(n.children) : [] }))
          .filter((n) => n.menuType === 'M' || (n.children?.length ?? 0) > 0)
      return filter(state.menus)
    },
  },

  actions: {
    /** 生成动态路由（守卫在首次导航时调用），请求失败向上抛出由守卫跳转登录。 */
    async generateRoutes(): Promise<void> {
      const userStore = useUserStore()
      const info = await userStore.fetchInfo()
      this.menus = info.menus ?? []
      this.addRoutesFromMenus(this.menus, '/')
      this.routesGenerated = true
    },

    addRoutesFromMenus(menus: MenuNodeVO[], basePath: string): void {
      const sorted = [...menus].sort((a, b) => a.sort - b.sort)
      for (const menu of sorted) {
        const fullPath = joinPath(basePath, menu.path)
        if (menu.menuType === 'C') {
          this.addRoutesFromMenus(menu.children ?? [], fullPath)
        } else if (menu.menuType === 'M') {
          if (menu.visible === 0) continue
          const configured = menu.component
          const loader = resolveView(configured)
          const name = pathToRouteName(fullPath)
          const route = {
            path: fullPath,
            name,
            component: loader ?? missingView,
            meta: {
              title: menu.menuName,
              icon: menu.icon ?? undefined,
              perms: menu.perms ?? undefined,
              keepAlive: true,
              affix: fullPath === '/dashboard',
              // 解析失败时保留原始配置，占位页据此提示排查方向
              componentMissing: loader ? undefined : configured ?? '(未配置)',
            },
          }
          if (router.hasRoute(name)) router.removeRoute(name)
          router.addRoute('MainLayout', route)
          this.dynamicNames.push(name)
        }
      }
    },

    /** 登出时移除全部动态路由，防止下个用户看到上一用户的菜单结构。 */
    resetRoutes(): void {
      this.dynamicNames.forEach((name) => {
        if (router.hasRoute(name)) router.removeRoute(name)
      })
      this.menus = []
      this.dynamicNames = []
      this.routesGenerated = false
    },
  },
})
