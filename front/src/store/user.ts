import { defineStore } from 'pinia'
import { getAuthInfo, login as loginApi, logout as logoutApi } from '@/api/auth'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/utils/auth'
import type { AuthInfoVO, AuthUser, AuthWarehouse, LoginDTO } from '@/types/entity'

interface UserState {
  token: string
  userInfo: AuthUser | null
  roles: string[]
  permissions: string[]
  warehouses: AuthWarehouse[]
  /** 是否已成功拉取 /api/auth/info */
  infoLoaded: boolean
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: getAccessToken(),
    userInfo: null,
    roles: [],
    permissions: [],
    warehouses: [],
    infoLoaded: false,
  }),

  getters: {
    /** 超级管理员：后端对 admin 返回全量权限，前端仍以后端返回为准。 */
    isAdmin: (state): boolean => state.roles.includes('ROLE_ADMIN'),
    realName: (state): string => state.userInfo?.realName ?? state.userInfo?.username ?? '',
    /** 数据权限内仓库（admin 返回全量）。 */
    dataScopeWarehouses: (state): AuthWarehouse[] => state.warehouses,
  },

  actions: {
    async login(payload: LoginDTO, opts?: { silent?: boolean }) {
      const data = await loginApi(payload, opts?.silent)
      setTokens(data.accessToken, data.refreshToken)
      this.token = data.accessToken
      this.infoLoaded = false
    },

    /** 拉取当前用户信息、菜单与权限（菜单树交给 permission store 消费，本 store 不保存）。 */
    async fetchInfo(): Promise<AuthInfoVO> {
      const info = await getAuthInfo()
      this.userInfo = info.user
      this.roles = info.roles ?? []
      this.permissions = info.permissions ?? []
      this.warehouses = info.warehouses ?? []
      this.infoLoaded = true
      return info
    },

    /** 退出登录：清令牌、清用户状态（动态路由由 permission store 清理）。 */
    async logout() {
      const refreshToken = getRefreshToken() || undefined
      try {
        await logoutApi(refreshToken ? { refreshToken } : undefined)
      } catch {
        // 登出接口失败不阻断本地清理
      }
      this.reset()
    },

    reset() {
      clearTokens()
      this.token = ''
      this.userInfo = null
      this.roles = []
      this.permissions = []
      this.warehouses = []
      this.infoLoaded = false
    },

    /** 判断权限标识（admin 全量放行由后端返回保证，此处按集合精确匹配）。 */
    hasPerm(code: string | undefined | null): boolean {
      if (!code) return true
      const perms = this.permissions
      if (perms.includes('*') || perms.includes('*:*:*')) return true
      return perms.includes(code)
    },
  },
})
