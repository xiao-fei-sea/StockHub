import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type {
  CategoryNode,
  InventoryFlow,
  InventoryFlowQuery,
  InventoryQuery,
  InventoryItem,
  InventorySummary,
} from '@/types/entities/inventory'

/** API-INV-001 库存多条件分页查询（GET /api/inventory，perms=sys:inventory:list）。 */
export function getInventory(params?: InventoryQuery): Promise<PageResult<InventoryItem>> {
  return request.get('/inventory', { params })
}

/** API-INV-002 库存变动流水分页查询（GET /api/inventory/flows）。 */
export function getInventoryFlows(
  params?: InventoryFlowQuery,
): Promise<PageResult<InventoryFlow>> {
  return request.get('/inventory/flows', { params })
}

/** API-INV-003 库存汇总（GET /api/inventory/summary，看板指标条数据源）。 */
export function getInventorySummary(params?: {
  groupBy?: 'warehouse' | 'category' | 'product'
}): Promise<InventorySummary> {
  return request.get('/inventory/summary', { params })
}

/** API-CAT-001 商品分类树（库存查询分类筛选与预警规则适用分类数据源）。 */
export function getCategoryTree(params?: {
  status?: number
  keyword?: string
}): Promise<CategoryNode[]> {
  return request.get('/categories/tree', { params })
}
