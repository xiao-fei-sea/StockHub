# 智仓云 SmartWMS 全系统 API 接口汇总清单

> 本清单与《17. API接口汇总清单》接口条目完全一致（134 条 = 基线 133 + 编码阶段新增 API-AUTH-005，逐条核对一致），按模块分组、末尾附统计。
> 对应 OpenAPI 文件：`接口文档/OpenAPI接口定义.yaml`（OpenAPI 3.0.3，tags 分组按 04 文档归并为 18 个：认证、用户管理、角色权限、菜单管理、部门管理、仓库管理、库位管理、商品管理、供应商管理、客户管理、入库管理、出库管理、库存查询、库存盘点、库存调拨、库存预警、数据统计、操作日志）。

## 一、口径说明

1. 接口路径均为前端请求路径，已统一以 `/api` 开头（网关转发前）；`?xxx=` 为示例查询参数（可选，非路径组成部分）。
2. 统一响应：`{code, message, data, timestamp}`，分页为 `data = {total, pageNum, pageSize, list}`，时间格式 `yyyy-MM-dd HH:mm:ss`。
3. 认证：JWT Bearer（`Authorization: Bearer <accessToken>`）；仅 `POST /api/auth/login`、`POST /api/auth/refresh`、`POST /api/auth/register` 免认证。
4. 权限：菜单权限 + 按钮权限 `perms`（下表）+ 数据仓库权限；`系统预置` 表示内置角色 admin/ROLE_ADMIN/ROLE_VIEWER 能力，`待确认：` 为待与后端核对项。

## 二、接口明细（按模块分组）

### 1. 认证（5 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-AUTH-001 | 登录 | POST | /api/auth/login | 认证（免认证） | 免认证 | 否 |
| API-AUTH-002 | 刷新令牌 | POST | /api/auth/refresh | 认证（免认证） | 免认证 | 否 |
| API-AUTH-003 | 退出登录 | POST | /api/auth/logout | 认证 | 认证即可 | 否 |
| API-AUTH-004 | 获取当前用户信息与菜单权限 | GET | /api/auth/info | 认证 | 认证即可 | 否 |
| API-AUTH-005 | 用户注册（编码阶段新增，基线外） | POST | /api/auth/register | 认证（免认证） | 免认证 | 否 |

### 2. 用户管理（11 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-USR-001 | 用户分页查询 | GET | /api/users | 用户管理 | sys:user:list | 是 |
| API-USR-002 | 创建用户 | POST | /api/users | 用户管理 | sys:user:add | 否 |
| API-USR-003 | 编辑用户 | PUT | /api/users/{id} | 用户管理 | sys:user:edit | 否 |
| API-USR-004 | 删除用户 | DELETE | /api/users/{id} | 用户管理 | sys:user:delete | 否 |
| API-USR-005 | 用户详情 | GET | /api/users/{id} | 用户管理 | sys:user:list | 否 |
| API-USR-006 | 启用/停用用户 | PUT | /api/users/{id}/status | 用户管理 | sys:user:status | 否 |
| API-USR-007 | 重置用户密码 | PUT | /api/users/{id}/password | 用户管理 | sys:user:reset | 否 |
| API-USR-008 | 分配用户角色 | PUT | /api/users/{id}/roles | 用户管理 | sys:user:role | 否 |
| API-USR-009 | 用户下拉选项 | GET | /api/users/options | 用户管理 | sys:user:list | 否 |
| API-USR-010 | 分配用户数据仓库权限 | PUT | /api/users/{id}/warehouses | 用户管理 | sys:user:assignWarehouse | 否 |
| API-USR-011 | 查询用户仓库权限 | GET | /api/user-warehouses/{userId} | 用户管理 | sys:user:list | 否 |

### 3. 部门管理（4 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-DEPT-001 | 部门树查询 | GET | /api/depts/tree | 部门管理 | sys:dept:list | 否 |
| API-DEPT-002 | 创建部门 | POST | /api/depts | 部门管理 | sys:dept:add | 否 |
| API-DEPT-003 | 编辑部门 | PUT | /api/depts/{id} | 部门管理 | sys:dept:edit | 否 |
| API-DEPT-004 | 删除部门 | DELETE | /api/depts/{id} | 部门管理 | sys:dept:delete | 否 |

### 4. 角色权限（6 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-ROLE-001 | 角色分页查询 | GET | /api/roles | 角色权限 | sys:role:list | 是 |
| API-ROLE-002 | 创建角色 | POST | /api/roles | 角色权限 | sys:role:add | 否 |
| API-ROLE-003 | 编辑角色 | PUT | /api/roles/{id} | 角色权限 | sys:role:edit | 否 |
| API-ROLE-004 | 删除角色 | DELETE | /api/roles/{id} | 角色权限 | sys:role:delete | 否 |
| API-ROLE-005 | 角色详情 | GET | /api/roles/{id} | 角色权限 | sys:role:list | 否 |
| API-ROLE-006 | 分配角色菜单权限 | PUT | /api/roles/{id}/menus | 角色权限 | sys:role:assign | 否 |

### 5. 菜单权限（4 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-MENU-001 | 菜单树查询 | GET | /api/menus/tree | 菜单权限 | sys:menu:list | 否 |
| API-MENU-002 | 创建菜单 | POST | /api/menus | 菜单权限 | sys:menu:add | 否 |
| API-MENU-003 | 编辑菜单 | PUT | /api/menus/{id} | 菜单权限 | sys:menu:edit | 否 |
| API-MENU-004 | 删除菜单 | DELETE | /api/menus/{id} | 菜单权限 | sys:menu:delete | 否 |

### 6. 仓库管理（6 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-WH-001 | 仓库分页查询 | GET | /api/warehouses | 仓库管理 | sys:warehouse:list | 是 |
| API-WH-002 | 创建仓库 | POST | /api/warehouses | 仓库管理 | sys:warehouse:add | 否 |
| API-WH-003 | 编辑仓库 | PUT | /api/warehouses/{id} | 仓库管理 | sys:warehouse:edit | 否 |
| API-WH-004 | 删除仓库 | DELETE | /api/warehouses/{id} | 仓库管理 | sys:warehouse:delete | 否 |
| API-WH-005 | 仓库详情 | GET | /api/warehouses/{id} | 仓库管理 | sys:warehouse:list | 否 |
| API-WH-006 | 仓库下拉选项 | GET | /api/warehouses/options | 仓库管理 | sys:warehouse:list | 否 |

### 7. 库区管理（5 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-AREA-001 | 库区分页查询 | GET | /api/areas | 库区管理 | sys:area:list | 是 |
| API-AREA-002 | 创建库区 | POST | /api/areas | 库区管理 | sys:area:add | 否 |
| API-AREA-003 | 编辑库区 | PUT | /api/areas/{id} | 库区管理 | sys:area:edit | 否 |
| API-AREA-004 | 删除库区 | DELETE | /api/areas/{id} | 库区管理 | sys:area:delete | 否 |
| API-AREA-005 | 库区下拉选项 | GET | /api/areas/options?warehouseId= | 库区管理 | sys:area:list | 否 |

### 8. 货架管理（4 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-RACK-001 | 货架分页查询 | GET | /api/racks | 货架管理 | sys:rack:list | 是 |
| API-RACK-002 | 创建货架 | POST | /api/racks | 货架管理 | sys:rack:add | 否 |
| API-RACK-003 | 编辑货架 | PUT | /api/racks/{id} | 货架管理 | sys:rack:edit | 否 |
| API-RACK-004 | 删除货架 | DELETE | /api/racks/{id} | 货架管理 | sys:rack:delete | 否 |

### 9. 货位管理（6 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-LOC-001 | 货位分页查询 | GET | /api/locations | 货位管理 | sys:location:list | 是 |
| API-LOC-002 | 创建货位 | POST | /api/locations | 货位管理 | sys:location:add | 否 |
| API-LOC-003 | 编辑货位 | PUT | /api/locations/{id} | 货位管理 | sys:location:edit | 否 |
| API-LOC-004 | 删除货位 | DELETE | /api/locations/{id} | 货位管理 | sys:location:delete | 否 |
| API-LOC-005 | 货位下拉选项 | GET | /api/locations/options | 货位管理 | sys:location:list | 否 |
| API-LOC-006 | 批量生成货位 | POST | /api/locations/batch | 货位管理 | sys:location:add | 否 |

### 10. 商品分类（4 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-CAT-001 | 商品分类树查询 | GET | /api/categories/tree | 商品分类 | sys:category:list | 否 |
| API-CAT-002 | 创建分类 | POST | /api/categories | 商品分类 | sys:category:add | 否 |
| API-CAT-003 | 编辑分类 | PUT | /api/categories/{id} | 商品分类 | sys:category:edit | 否 |
| API-CAT-004 | 删除分类 | DELETE | /api/categories/{id} | 商品分类 | sys:category:delete | 否 |

### 11. 商品管理（6 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-PROD-001 | 商品分页查询 | GET | /api/products | 商品管理 | sys:product:list | 是 |
| API-PROD-002 | 创建商品 | POST | /api/products | 商品管理 | sys:product:add | 否 |
| API-PROD-003 | 编辑商品 | PUT | /api/products/{id} | 商品管理 | sys:product:edit | 否 |
| API-PROD-004 | 删除商品 | DELETE | /api/products/{id} | 商品管理 | sys:product:delete | 否 |
| API-PROD-005 | 商品详情 | GET | /api/products/{id} | 商品管理 | sys:product:list | 否 |
| API-PROD-006 | 商品下拉选项 | GET | /api/products/options | 商品管理 | sys:product:list | 否 |

### 12. 供应商管理（5 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-SUP-001 | 供应商分页查询 | GET | /api/suppliers | 供应商管理 | sys:supplier:list | 是 |
| API-SUP-002 | 创建供应商 | POST | /api/suppliers | 供应商管理 | sys:supplier:add | 否 |
| API-SUP-003 | 编辑供应商 | PUT | /api/suppliers/{id} | 供应商管理 | sys:supplier:edit | 否 |
| API-SUP-004 | 删除供应商 | DELETE | /api/suppliers/{id} | 供应商管理 | sys:supplier:delete | 否 |
| API-SUP-005 | 供应商下拉选项 | GET | /api/suppliers/options | 供应商管理 | sys:supplier:list | 否 |

### 13. 客户管理（5 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-CUS-001 | 客户分页查询 | GET | /api/customers | 客户管理 | sys:customer:list | 是 |
| API-CUS-002 | 创建客户 | POST | /api/customers | 客户管理 | sys:customer:add | 否 |
| API-CUS-003 | 编辑客户 | PUT | /api/customers/{id} | 客户管理 | sys:customer:edit | 否 |
| API-CUS-004 | 删除客户 | DELETE | /api/customers/{id} | 客户管理 | sys:customer:delete | 否 |
| API-CUS-005 | 客户下拉选项 | GET | /api/customers/options | 客户管理 | sys:customer:list | 否 |

### 14. 入库管理（11 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-INB-001 | 入库单分页查询 | GET | /api/inbounds | 入库管理 | sys:inbound:list | 是 |
| API-INB-002 | 创建入库单 | POST | /api/inbounds | 入库管理 | sys:inbound:add | 否 |
| API-INB-003 | 入库单详情 | GET | /api/inbounds/{id} | 入库管理 | sys:inbound:list | 否 |
| API-INB-004 | 编辑入库单 | PUT | /api/inbounds/{id} | 入库管理 | sys:inbound:edit | 否 |
| API-INB-005 | 删除入库单 | DELETE | /api/inbounds/{id} | 入库管理 | sys:inbound:delete | 否 |
| API-INB-006 | 入库单提交 | PUT | /api/inbounds/{id}/submit | 入库管理 | sys:inbound:submit | 否 |
| API-INB-007 | 入库单审核 | PUT | /api/inbounds/{id}/audit | 入库管理 | sys:inbound:audit | 否 |
| API-INB-008 | 入库单驳回 | PUT | /api/inbounds/{id}/reject | 入库管理 | sys:inbound:reject | 否 |
| API-INB-009 | 入库单过账 | PUT | /api/inbounds/{id}/post | 入库管理 | sys:inbound:post | 否 |
| API-INB-010 | 入库单取消 | PUT | /api/inbounds/{id}/cancel | 入库管理 | sys:inbound:cancel | 否 |
| API-INB-011 | 入库单撤回 | PUT | /api/inbounds/{id}/withdraw | 入库管理 | sys:inbound:withdraw | 否 |

### 15. 出库管理（11 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-OUT-001 | 出库单分页查询 | GET | /api/outbounds | 出库管理 | sys:outbound:list | 是 |
| API-OUT-002 | 创建出库单 | POST | /api/outbounds | 出库管理 | sys:outbound:add | 否 |
| API-OUT-003 | 出库单详情 | GET | /api/outbounds/{id} | 出库管理 | sys:outbound:list | 否 |
| API-OUT-004 | 编辑出库单 | PUT | /api/outbounds/{id} | 出库管理 | sys:outbound:edit | 否 |
| API-OUT-005 | 删除出库单 | DELETE | /api/outbounds/{id} | 出库管理 | sys:outbound:delete | 否 |
| API-OUT-006 | 出库单提交 | PUT | /api/outbounds/{id}/submit | 出库管理 | sys:outbound:submit | 否 |
| API-OUT-007 | 出库单审核 | PUT | /api/outbounds/{id}/audit | 出库管理 | sys:outbound:audit | 否 |
| API-OUT-008 | 出库单驳回 | PUT | /api/outbounds/{id}/reject | 出库管理 | sys:outbound:reject | 否 |
| API-OUT-009 | 出库单过账（FIFO 分配批次） | PUT | /api/outbounds/{id}/post | 出库管理 | sys:outbound:post | 否 |
| API-OUT-010 | 出库单取消 | PUT | /api/outbounds/{id}/cancel | 出库管理 | sys:outbound:cancel | 否 |
| API-OUT-011 | 出库单撤回 | PUT | /api/outbounds/{id}/withdraw | 出库管理 | sys:outbound:withdraw | 否 |

### 16. 库存管理（3 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-INV-001 | 库存查询 | GET | /api/inventory | 库存管理 | sys:inventory:list | 是 |
| API-INV-002 | 库存流水分页查询 | GET | /api/inventory/flows | 库存管理 | sys:inventory:list | 是 |
| API-INV-003 | 库存汇总 | GET | /api/inventory/summary | 库存管理 | sys:inventory:list | 否 |

### 17. 库存调拨（11 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-TRF-001 | 调拨单分页查询 | GET | /api/transfers | 库存调拨 | sys:transfer:list | 是 |
| API-TRF-002 | 创建调拨单 | POST | /api/transfers | 库存调拨 | sys:transfer:add | 否 |
| API-TRF-003 | 调拨单详情 | GET | /api/transfers/{id} | 库存调拨 | sys:transfer:list | 否 |
| API-TRF-004 | 编辑调拨单 | PUT | /api/transfers/{id} | 库存调拨 | sys:transfer:edit | 否 |
| API-TRF-005 | 删除调拨单 | DELETE | /api/transfers/{id} | 库存调拨 | sys:transfer:delete | 否 |
| API-TRF-006 | 调拨单提交 | PUT | /api/transfers/{id}/submit | 库存调拨 | sys:transfer:submit | 否 |
| API-TRF-007 | 调拨单审核 | PUT | /api/transfers/{id}/audit | 库存调拨 | sys:transfer:audit | 否 |
| API-TRF-008 | 调拨单驳回 | PUT | /api/transfers/{id}/reject | 库存调拨 | sys:transfer:reject | 否 |
| API-TRF-009 | 调拨单过账（源减目标增） | PUT | /api/transfers/{id}/post | 库存调拨 | sys:transfer:post | 否 |
| API-TRF-010 | 调拨单取消 | PUT | /api/transfers/{id}/cancel | 库存调拨 | sys:transfer:cancel | 否 |
| API-TRF-011 | 调拨单撤回 | PUT | /api/transfers/{id}/withdraw | 库存调拨 | sys:transfer:withdraw | 否 |

### 18. 库存盘点（11 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-STK-001 | 盘点单分页查询 | GET | /api/stocktakes | 库存盘点 | sys:stocktake:list | 是 |
| API-STK-002 | 创建盘点单（自动生成明细） | POST | /api/stocktakes | 库存盘点 | sys:stocktake:add | 否 |
| API-STK-003 | 盘点单详情 | GET | /api/stocktakes/{id} | 库存盘点 | sys:stocktake:list | 否 |
| API-STK-004 | 录入/更新实盘数 | PUT | /api/stocktakes/{id} | 库存盘点 | sys:stocktake:edit | 否 |
| API-STK-005 | 删除盘点单 | DELETE | /api/stocktakes/{id} | 库存盘点 | sys:stocktake:delete | 否 |
| API-STK-006 | 盘点单提交 | PUT | /api/stocktakes/{id}/submit | 库存盘点 | sys:stocktake:submit | 否 |
| API-STK-007 | 盘点单审核（差异授权） | PUT | /api/stocktakes/{id}/audit | 库存盘点 | sys:stocktake:audit | 否 |
| API-STK-008 | 盘点单驳回 | PUT | /api/stocktakes/{id}/reject | 库存盘点 | sys:stocktake:reject | 否 |
| API-STK-009 | 盘点单过账（按差异调整库存） | PUT | /api/stocktakes/{id}/post | 库存盘点 | sys:stocktake:post | 否 |
| API-STK-010 | 盘点单取消 | PUT | /api/stocktakes/{id}/cancel | 库存盘点 | sys:stocktake:cancel | 否 |
| API-STK-011 | 盘点单撤回 | PUT | /api/stocktakes/{id}/withdraw | 库存盘点 | sys:stocktake:withdraw | 否 |

### 19. 库存预警（8 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-ALR-001 | 预警规则分页查询 | GET | /api/alert-rules | 库存预警 | sys:alert:rule:list | 是 |
| API-ALR-002 | 创建预警规则 | POST | /api/alert-rules | 库存预警 | sys:alert:rule:add | 否 |
| API-ALR-003 | 编辑预警规则 | PUT | /api/alert-rules/{id} | 库存预警 | sys:alert:rule:edit | 否 |
| API-ALR-004 | 删除预警规则 | DELETE | /api/alert-rules/{id} | 库存预警 | sys:alert:rule:delete | 否 |
| API-ALR-005 | 预警规则详情 | GET | /api/alert-rules/{id} | 库存预警 | sys:alert:rule:list | 否 |
| API-ALR-006 | 预警记录分页查询 | GET | /api/alerts | 库存预警 | sys:alert:list | 是 |
| API-ALR-007 | 处理预警 | PUT | /api/alerts/{id}/handle | 库存预警 | sys:alert:handle | 否 |
| API-ALR-008 | 手动触发预警扫描 | POST | /api/alerts/scan | 库存预警 | sys:alert:scan | 否 |

### 20. 数据统计（6 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-STS-001 | 首页看板汇总 | GET | /api/stats/dashboard | 数据统计 | sys:stats:list | 否 |
| API-STS-002 | 出入库趋势 | GET | /api/stats/inbound-outbound-trend?days=30 | 数据统计 | sys:stats:list | 否 |
| API-STS-003 | 库存分类分布 | GET | /api/stats/category-distribution | 数据统计 | sys:stats:list | 否 |
| API-STS-004 | 仓库货位利用率 | GET | /api/stats/warehouse-occupancy | 数据统计 | sys:stats:list | 否 |
| API-STS-005 | 出入库 Top 商品 | GET | /api/stats/top-products?limit=10 | 数据统计 | sys:stats:list | 否 |
| API-STS-006 | 库存账龄/滞销 | GET | /api/stats/stock-aging | 数据统计 | sys:stats:list | 否 |

### 21. 操作日志（2 个）

| 接口编号 | 接口名称 | 方法 | 路径 | 模块 | 权限标识 | 是否分页 |
|---|---|---|---|---|---|---|
| API-OPL-001 | 操作日志分页查询 | GET | /api/logs | 操作日志 | sys:log:list | 是 |
| API-OPL-002 | 操作日志详情 | GET | /api/logs/{id} | 操作日志 | sys:log:list | 否 |

## 三、统计

### 1. 分模块数量

| 模块 | 接口数 |
|---|---|
| 认证 | 5 |
| 用户管理 | 11 |
| 部门管理 | 4 |
| 角色权限 | 6 |
| 菜单权限 | 4 |
| 仓库管理 | 6 |
| 库区管理 | 5 |
| 货架管理 | 4 |
| 货位管理 | 6 |
| 商品分类 | 4 |
| 商品管理 | 6 |
| 供应商管理 | 5 |
| 客户管理 | 5 |
| 入库管理 | 11 |
| 出库管理 | 11 |
| 库存管理 | 3 |
| 库存调拨 | 11 |
| 库存盘点 | 11 |
| 库存预警 | 8 |
| 数据统计 | 6 |
| 操作日志 | 2 |
| **合计** | **134** |

### 2. 按方法与分页统计

| 维度 | 数量 |
|---|---|
| GET | 47 |
| POST | 23 |
| PUT | 47 |
| DELETE | 17 |
| 分页接口 | 18 |
| 非分页接口 | 116 |
| **合计** | **134** |

> 合计 **134 个接口**（与《17. API接口汇总清单》逐条核对一致：134 = 134，方法+路径组合零差异；其中基线 133 + 编码阶段新增 API-AUTH-005）。
