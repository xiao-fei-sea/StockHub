/** 统一响应信封（API 接口统一规范 第 4 节），全局唯一声明。 */
export interface Result<T = unknown> {
  /** 业务码：200 成功；400/401/403/404/500 对应 HTTP；5 位数为业务错误码 */
  code: number
  /** 面向最终用户的提示信息 */
  message: string
  /** 业务数据，无数据时为 null */
  data: T
  /** 服务端处理时间 yyyy-MM-dd HH:mm:ss */
  timestamp: string
}

/** 分页响应结构（API 接口统一规范 第 5 节），data 字段内容。 */
export interface PageResult<T = unknown> {
  total: number
  pageNum: number
  pageSize: number
  list: T[]
}

/** 通用分页/条件查询参数（各接口按需组合）。 */
export interface PageQuery {
  pageNum?: number
  pageSize?: number
  keyword?: string
  status?: number
  warehouseId?: number
  startDate?: string
  endDate?: string
}
