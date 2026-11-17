import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { SysUser, SysUserForm, UserOption, UserWarehouseInfo } from '@/types/entity'

/** 用户分页查询参数（API-USR-001）。 */
export interface UserQuery {
  pageNum?: number
  pageSize?: number
  /** 模糊查询：用户名、姓名、手机号 */
  keyword?: string
  /** 0 停用 / 1 正常 */
  status?: number
  /** 部门 id，精确匹配 */
  deptId?: number
}

/** 用户下拉选项（API-USR-009）。 */
export function getUserOptions(keyword?: string): Promise<UserOption[]> {
  return request.get('/users/options', { params: keyword ? { keyword } : undefined })
}

/** API-USR-001 用户分页查询。 */
export function getUsers(params?: UserQuery): Promise<PageResult<SysUser>> {
  return request.get('/users', { params })
}

/** API-USR-002 新增用户（可同时携带 roleIds / warehouseIds），返回新用户 id。 */
export function createUser(
  data: SysUserForm & { roleIds?: number[]; warehouseIds?: number[] },
): Promise<number> {
  return request.post('/users', data)
}

/** API-USR-003 用户详情（含 roleIds / warehouseIds，供编辑与分配回显）。 */
export function getUserDetail(id: number): Promise<SysUser> {
  return request.get(`/users/${id}`)
}

/** API-USR-004 修改用户基础信息（不影响密码、角色、授权仓库）。 */
export function updateUser(id: number, data: SysUserForm): Promise<void> {
  return request.put(`/users/${id}`, data)
}

/** API-USR-005 删除用户（同时清理角色与仓库关联，admin/当前账号不可删）。 */
export function deleteUser(id: number): Promise<void> {
  return request.delete(`/users/${id}`)
}

/** API-USR-006 启用/停用用户。 */
export function updateUserStatus(id: number, status: 0 | 1): Promise<void> {
  return request.put(`/users/${id}/status`, { status })
}

/** API-USR-007 重置用户密码。 */
export function resetUserPassword(id: number, password: string): Promise<void> {
  return request.put(`/users/${id}/password`, { password })
}

/** API-USR-008 分配用户角色（整体替换 sys_user_role）。 */
export function assignUserRoles(id: number, roleIds: number[]): Promise<void> {
  return request.put(`/users/${id}/roles`, { roleIds })
}

/** API-USR-010 查询用户仓库数据权限。 */
export function getUserWarehouses(userId: number): Promise<UserWarehouseInfo> {
  return request.get(`/user-warehouses/${userId}`)
}

/** API-USR-011 分配用户仓库数据权限（整体替换 sys_user_warehouse）。 */
export function assignUserWarehouses(id: number, warehouseIds: number[]): Promise<void> {
  return request.put(`/users/${id}/warehouses`, { warehouseIds })
}
