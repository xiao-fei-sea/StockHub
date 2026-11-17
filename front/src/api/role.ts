import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { RoleDetail, RoleForm, SysRole } from '@/types/entity'

/** 角色分页查询参数（API-ROLE-001）。 */
export interface RoleQuery {
  pageNum?: number
  pageSize?: number
  /** 模糊匹配 roleName / roleKey */
  keyword?: string
  /** 0 停用 / 1 正常 */
  status?: number
}

/** API-ROLE-001 角色分页查询。 */
export function getRoles(params?: RoleQuery): Promise<PageResult<SysRole>> {
  return request.get('/roles', { params })
}

/** API-ROLE-002 新增角色（可携带 menuIds 初始化菜单权限），返回新角色 id。 */
export function createRole(data: RoleForm): Promise<number> {
  return request.post('/roles', data)
}

/** API-ROLE-005 角色详情（含已授权 menuIds，供权限树回显）。 */
export function getRoleDetail(id: number): Promise<RoleDetail> {
  return request.get(`/roles/${id}`)
}

/** API-ROLE-003 修改角色基本信息（权限集合修改走 assignRoleMenus）。 */
export function updateRole(id: number, data: RoleForm): Promise<void> {
  return request.put(`/roles/${id}`, data)
}

/** API-ROLE-004 删除角色（存在用户引用或内置角色时后端 40901 拦截，同时清理菜单关联）。 */
export function deleteRole(id: number): Promise<void> {
  return request.delete(`/roles/${id}`)
}

/** API-ROLE-006 分配角色菜单权限（整体替换 sys_role_menu，含目录/菜单/按钮）。 */
export function assignRoleMenus(id: number, menuIds: number[]): Promise<void> {
  return request.put(`/roles/${id}/menus`, { menuIds })
}
