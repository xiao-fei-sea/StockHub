import request from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { Product, ProductForm, ProductOption } from '@/types/entity'

/** 商品分页查询参数（API-PROD-001）。 */
export interface ProductQuery {
  pageNum?: number
  pageSize?: number
  /** 匹配 productCode / productName / brand */
  keyword?: string
  categoryId?: number
  status?: number
  /** 是否保质期管理：0/1 */
  shelfLifeFlag?: number
  /** 是否批次管理：0/1 */
  batchFlag?: number
}

/** API-PROD-001 商品分页查询。 */
export function getProducts(params?: ProductQuery): Promise<PageResult<Product>> {
  return request.get('/products', { params })
}

/** API-PROD-002 新增商品，返回新商品 id。 */
export function createProduct(data: ProductForm): Promise<number> {
  return request.post('/products', data)
}

/** API-PROD-003 商品详情。 */
export function getProductDetail(id: number): Promise<Product> {
  return request.get(`/products/${id}`)
}

/** API-PROD-004 修改商品。 */
export function updateProduct(id: number, data: ProductForm): Promise<void> {
  return request.put(`/products/${id}`, data)
}

/** API-PROD-005 删除商品（存在库存/批次/流水/单据引用时后端返回 40901 拦截）。 */
export function deleteProduct(id: number): Promise<void> {
  return request.delete(`/products/${id}`)
}

/** 商品下拉选项（API-PROD-006，远程搜索）。 */
export function getProductOptions(keyword?: string): Promise<ProductOption[]> {
  return request.get('/products/options', { params: { keyword } })
}
