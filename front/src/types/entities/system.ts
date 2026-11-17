/**
 * 系统管理模块（用户 / 部门 / 角色 / 菜单 / 操作日志）实体类型。
 * 字段与《用户管理接口文档》《角色权限接口文档》《操作日志接口文档》逐字对应（camelCase）。
 */
import type { MenuType } from '@/types/enum'
import type { WarehouseOption } from './base'

/* ---------------- 用户（/api/users、/api/user-warehouses） ---------------- */

/** 用户（sys_user 分页行 / 详情，API-USR-001/003）。 */
export interface SysUser {
  id: number
  username: string
  realName: string
  phone?: string | null
  email?: string | null
  deptId?: number | null
  deptName?: string | null
  /** 0 停用 / 1 正常 */
  status: number
  /** 最近登录时间 */
  loginDate?: string | null
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  /** 仅详情返回：已分配角色 id 集合 */
  roleIds?: number[]
  /** 仅详情返回：已授权仓库 id 集合 */
  warehouseIds?: number[]
}

/** 用户表单（新增含初始密码，修改不含密码/角色/仓库，API-USR-002/004）。 */
export interface SysUserForm {
  username: string
  /** 仅新增必填（8-20 位且含字母与数字） */
  password?: string
  realName: string
  phone?: string
  email?: string
  deptId?: number | null
  /** 0 停用 / 1 正常 */
  status: number
}

/** 用户下拉选项（API-USR-009）。 */
export interface UserOption {
  id: number
  username: string
  realName: string
}

/** 用户仓库数据权限（API-USR-010 响应）。 */
export interface UserWarehouseInfo {
  userId: number
  warehouseIds: number[]
  warehouses: WarehouseOption[]
}

/* ---------------- 部门（/api/depts） ---------------- */

/** 部门树节点（API-DEPT-001）。 */
export interface DeptNode {
  id: number
  parentId: number
  deptName: string
  sort?: number
  /** 祖先 id 路径（逗号分隔，根为 0） */
  ancestors?: string
  /** 0 停用 / 1 正常（停用部门前端置灰） */
  status: number
  children?: DeptNode[] | null
}

/** 部门表单（API-DEPT-002/003，ancestors 由服务端生成）。 */
export interface DeptForm {
  parentId: number
  deptName: string
  sort?: number
  status?: number
}

/* ---------------- 角色（/api/roles） ---------------- */

/** 角色（sys_role 分页行，API-ROLE-001）。 */
export interface SysRole {
  id: number
  roleName: string
  roleKey: string
  roleSort?: number
  /** 0 停用 / 1 正常 */
  status: number
  remark?: string | null
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
}

/** 角色详情（API-ROLE-005，含已授权菜单 id 集合）。 */
export interface RoleDetail extends SysRole {
  menuIds?: number[]
}

/** 角色表单（API-ROLE-002/003；新增可携带 menuIds 初始化权限）。 */
export interface RoleForm {
  roleName: string
  roleKey: string
  roleSort?: number
  status: number
  remark?: string
  menuIds?: number[]
}

/** 角色下拉选项（用户分配角色数据源，来自 GET /roles 分页行）。 */
export interface RoleOption {
  id: number
  roleName: string
  roleKey: string
  status?: number
}

/* ---------------- 菜单权限（/api/menus） ---------------- */

/** 菜单树节点（C 目录 / M 菜单 / B 按钮，API-MENU-001）。 */
export interface MenuTreeNode {
  id: number
  parentId: number
  menuName: string
  menuType: MenuType
  /** 按钮必填，形如 sys:user:add */
  perms?: string | null
  path?: string | null
  component?: string | null
  icon?: string | null
  sort?: number
  /** 0 隐藏 / 1 显示 */
  visible?: number
  /** 0 停用 / 1 正常 */
  status?: number
  children?: MenuTreeNode[] | null
}

/** 菜单表单（API-MENU-002/003，必填项按 menuType 联动）。 */
export interface MenuForm {
  parentId: number
  menuName: string
  menuType: MenuType
  perms?: string
  path?: string
  component?: string
  icon?: string
  sort?: number
  visible?: number
  status?: number
}

/* ---------------- 操作日志（/api/logs，只读） ---------------- */

/** 操作日志（sys_oper_log 行 / 详情，API-OPL-001/002）。 */
export interface OperLog {
  id: number
  /** 业务模块（如：入库管理、用户管理） */
  module: string
  /** 操作标题（如：审核入库单） */
  title: string
  /** 后端方法签名 类#方法 */
  method?: string | null
  /** HTTP 方法 GET/POST/PUT/DELETE */
  requestMethod?: string | null
  requestUrl?: string | null
  /** 请求参数（服务端已脱敏，敏感字段为 ******） */
  requestParams?: string | null
  /** 响应结果摘要（超长截断） */
  responseResult?: string | null
  /** 0 失败 / 1 成功 */
  status: number
  errorMsg?: string | null
  ip?: string | null
  userId?: number | null
  username?: string | null
  /** 耗时（毫秒） */
  costTime?: number | null
  operTime?: string
}
