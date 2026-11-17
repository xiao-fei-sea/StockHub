/**
 * 全局实体/DTO/VO 类型汇总入口（前端开发规范 §7）。
 * 各业务模块实体拆分在 ./entities/ 下，按模块归属维护，避免共享文件冲突。
 */
import type { MenuType } from '@/types/enum'

export * from './entities/base'
export * from './entities/system'
export * from './entities/order'
export * from './entities/inventory'
export * from './entities/stats'

/* ---------------- 认证模块（API-AUTH-001 ~ 004） ---------------- */

export interface LoginDTO {
  username: string
  password: string
}

/** 自助注册（后端 RegisterDTO 校验：账号 2-50 位；密码 ≥8 位且含字母与数字；手机号可选）。 */
export interface RegisterDTO {
  username: string
  password: string
  realName: string
  phone?: string
}

export interface TokenUser {
  id: number
  username: string
  realName: string
  deptId?: number | null
  status: number
}

export interface LoginVO {
  accessToken: string
  refreshToken: string
  /** 形如 Bearer */
  tokenType: string
  /** access token 有效期（秒） */
  expiresIn: number
  user: TokenUser
}

/** 动态菜单树节点（C/M 两级）。 */
export interface MenuNodeVO {
  id: number
  parentId: number
  menuName: string
  menuType: MenuType
  /** 顶级存 '/dashboard' 形式的全量路径 */
  path: string
  /** 如 system/user/index，对应 src/views/{component}.vue */
  component?: string | null
  icon?: string | null
  sort: number
  visible: number
  /** 页面级权限标识（M 节点），如 sys:user:list；用于路由 meta.perms 拦截 */
  perms?: string | null
  children?: MenuNodeVO[] | null
}

export interface AuthUser {
  id: number
  username: string
  realName: string
  phone?: string | null
  email?: string | null
  deptId?: number | null
  deptName?: string | null
  status: number
  loginDate?: string | null
}

export interface AuthWarehouse {
  id: number
  warehouseName: string
}

/** GET /api/auth/info 返回结构。 */
export interface AuthInfoVO {
  user: AuthUser
  roles: string[]
  /** 按钮/菜单权限标识集合，如 sys:user:add */
  permissions: string[]
  menus: MenuNodeVO[]
  /** 当前用户数据权限内的仓库 */
  warehouses: AuthWarehouse[]
}
