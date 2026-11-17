import request from '@/utils/request'
import type { CategoryForm, CategoryNode } from '@/types/entity'

/** API-CAT-001 分类树查询（全量树，含 level 与 children 嵌套）。 */
export function getCategoryTree(params?: { status?: number; keyword?: string }): Promise<CategoryNode[]> {
  return request.get('/categories/tree', { params })
}

/** API-CAT-002 新增分类（根分类 parentId=0），返回新分类 id。 */
export function createCategory(data: CategoryForm): Promise<number> {
  return request.post('/categories', data)
}

/** API-CAT-003 修改分类（不允许移动到自身或子孙，后端 40901）。 */
export function updateCategory(id: number, data: CategoryForm): Promise<void> {
  return request.put(`/categories/${id}`, data)
}

/** API-CAT-004 删除分类（存在下级分类或关联商品时后端返回 40901 拦截）。 */
export function deleteCategory(id: number): Promise<void> {
  return request.delete(`/categories/${id}`)
}
