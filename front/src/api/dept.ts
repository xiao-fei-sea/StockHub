import request from '@/utils/request'
import type { DeptForm, DeptNode } from '@/types/entity'

/** API-DEPT-001 部门树查询（停用部门默认返回并标记 status=0，前端置灰）。 */
export function getDeptTree(params?: { status?: number }): Promise<DeptNode[]> {
  return request.get('/depts/tree', { params })
}

/** API-DEPT-002 新增部门（ancestors 由服务端生成），返回新部门 id。 */
export function createDept(data: DeptForm): Promise<number> {
  return request.post('/depts', data)
}

/** API-DEPT-003 修改部门（父级变更时服务端重建子树 ancestors）。 */
export function updateDept(id: number, data: DeptForm): Promise<void> {
  return request.put(`/depts/${id}`, data)
}

/** API-DEPT-004 删除部门（存在下级部门或用户引用时后端 40901 拦截）。 */
export function deleteDept(id: number): Promise<void> {
  return request.delete(`/depts/${id}`)
}
