import {
  createRouter,
  createWebHistory,
  type LocationQueryRaw,
  type RouteRecordRaw,
} from 'vue-router'
import { useUserStore } from '@/store/user'
import { usePermissionStore } from '@/store/permission'
import { getAccessToken } from '@/utils/auth'

declare module 'vue-router' {
  interface RouteMeta {
    /** 菜单/页签/面包屑显示名（取 sys_menu.menu_name） */
    title?: string
    /** 页面级权限标识（守卫二次校验用，可选） */
    perms?: string
    /** Element Plus 图标名 */
    icon?: string
    /** 是否纳入 keep-alive，默认 true */
    keepAlive?: boolean
    /** 页签固定不可关闭（仅 /dashboard） */
    affix?: boolean
    /** 菜单组件解析失败时的兜底标记：值为 sys_menu.component 原始配置 */
    componentMissing?: string
    /** 不参与菜单/面包屑渲染 */
    hidden?: boolean
  }
}

const LoginLayout = () => import('@/layouts/LoginLayout.vue')
const MainLayout = () => import('@/layouts/MainLayout.vue')

/** 静态路由（与用户权限无关），动态业务路由经 permission store addRoute('MainLayout', ...) 挂入。 */
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: LoginLayout,
    meta: { hidden: true },
    children: [
      {
        path: '',
        name: 'Login',
        component: () => import('@/views/login/index.vue'),
        meta: { title: '登录页', hidden: true },
      },
    ],
  },
  {
    path: '/register',
    component: LoginLayout,
    meta: { hidden: true },
    children: [
      {
        path: '',
        name: 'Register',
        component: () => import('@/views/register/index.vue'),
        meta: { title: '注册页', hidden: true },
      },
    ],
  },
  {
    path: '/',
    name: 'MainLayout',
    component: MainLayout,
    redirect: '/dashboard',
    meta: { hidden: true },
    children: [],
  },
  {
    // 页签“刷新当前”中转（路由设计 §7/§9），不计入业务页面
    path: '/redirect/:path(.*)',
    name: 'Redirect',
    component: () => import('@/views/redirect/index.vue'),
    meta: { hidden: true },
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '页面不存在', hidden: true },
  },
  // 兜底：Vue Router 4 按路径评分匹配，具体路由始终优先于通配
  {
    path: '/:pathMatch(.*)*',
    name: 'CatchAll',
    redirect: '/404',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
  scrollBehavior: () => ({ top: 0 }),
})

/** 重置为静态路由（登出时调用，清除上一用户的动态路由与菜单缓存）。 */
export function resetRouter(): void {
  const permStore = usePermissionStore()
  permStore.resetRoutes()
}

router.beforeEach(async (to) => {
  const title = to.meta.title
  document.title = title ? `${title} - 智仓云 SmartWMS` : '智仓云 SmartWMS'

  const token = getAccessToken()

  // 已登录访问登录/注册页 → 回首页
  if (to.path === '/login' || to.path === '/register') {
    return token ? '/' : true
  }

  // 未登录 → 登录页并携带回跳地址
  if (!token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const permStore = usePermissionStore()
  if (!permStore.routesGenerated) {
    try {
      await permStore.generateRoutes()
    } catch {
      // 令牌失效且续签失败：request 层已统一跳转，这里兜底回登录页
      return { path: '/login', query: { redirect: to.fullPath }, replace: true }
    }
    // 动态路由注册后重放本次导航，防止直达 URL 落 404。
    // 硬刷新直达业务路径时，路由尚未注册，CatchAll 会先于守卫把 to 解析为 /404，
    // 原始地址保存在 redirectedFrom；它可能带 name=CatchAll，直接返回会再次命中
    // CatchAll 形成 404 死循环，故只取 path/query/hash 重建（保留看板下钻等查询参数）。
    const origin = to.redirectedFrom
    if (typeof origin === 'string') return origin
    if (origin && 'path' in origin && origin.path) {
      const loc: { path: string; query?: LocationQueryRaw; hash?: string; replace: boolean } = {
        path: String(origin.path),
        replace: true,
      }
      const query = (origin as { query?: LocationQueryRaw }).query
      const hash = (origin as { hash?: string }).hash
      if (query && Object.keys(query).length) loc.query = query
      if (hash) loc.hash = hash
      return loc
    }
    return to.fullPath
  }

  // 权限回收后的页面级二次校验：无权限 = 404
  if (to.meta.perms && !useUserStore().hasPerm(to.meta.perms)) {
    return '/404'
  }

  return true
})

export default router
