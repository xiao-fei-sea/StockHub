import request from '@/utils/request'
import type { MenuForm, MenuTreeNode } from '@/types/entity'

/** API-MENU-001 菜单树查询（含目录 C / 菜单 M / 按钮 B 三层节点）。 */
export function getMenuTree(params?: { menuType?: 'C' | 'M' | 'B'; status?: number }): Promise<
  MenuTreeNode[]
> {
  return request.get('/menus/tree', { params })
}

/** API-MENU-002 新增菜单节点，返回新菜单 id。 */
export function createMenu(data: MenuForm): Promise<number> {
  return request.post('/menus', data)
}

/** API-MENU-003 修改菜单节点（移动到自身/子孙时后端 40901 拦截）。 */
export function updateMenu(id: number, data: MenuForm): Promise<void> {
  return request.put(`/menus/${id}`, data)
}

/** API-MENU-004 删除菜单节点（存在下级节点时后端 40901 拦截，同时清理角色关联）。 */
export function deleteMenu(id: number): Promise<void> {
  return request.delete(`/menus/${id}`)
}
