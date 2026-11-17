# 智仓云 SmartWMS

> 智能仓库库存管理系统 —— 面向中小型综合仓储企业的前后端分离 WMS 单体应用。

## 项目简介

SmartWMS 覆盖仓储业务的核心链路：基础资料、入库、出库、库存、调拨、盘点、预警与统计分析。支持**仓库 → 库区 → 货架 → 货位**四级库位结构、批次与保质期管理、出库 FIFO 自动分配、单据"草稿 → 待审核 → 已审核 → 过账"状态机，以及菜单/按钮/仓库数据范围三级权限体系。

## 功能模块

| 模块 | 说明 |
| ---- | ---- |
| 登录认证 | 登录/登出、JWT 双令牌（access 2h / refresh 7d） |
| 数据看板 | 库存汇总、出入库趋势、预警提示 |
| 系统管理 | 用户、角色与菜单权限、数据权限、操作日志 |
| 基础资料 | 仓库、库区/货架/货位、商品与分类、供应商、客户 |
| 入库管理 | 采购/调拨/退货/盘盈入库，提交 → 审核 → 过账 |
| 出库管理 | 销售/调拨/退货/报损出库，FIFO 自动分配批次 |
| 库存管理 | 库存查询、库存流水、库存调拨 |
| 库存盘点 | 盘点单、差异确认与过账调整 |
| 库存预警 | 低于下限/高于上限/临近保质期/已过期四类规则与记录处理 |
| 数据统计 | 出入库趋势、分类分布、货位利用率、Top 商品 |

## 技术栈

| 层 | 技术 |
| ---- | ---- |
| 前端 | Vue 3 · TypeScript · Vite 5 · Element Plus · Pinia · Vue Router · ECharts · SCSS |
| 后端 | Java 17 · Spring Boot 3.2 · Spring Security · MyBatis-Plus · JWT (jjwt) · Maven |
| 数据库 | MySQL 8.0（utf8mb4，29 张表，逻辑外键 + 索引） |
| 部署 | Nginx 反代 `/api` → 8080，单机可运行 |

## 目录结构

```
.
├── WmsCore/            # 后端 Spring Boot 工程（Maven）
│   └── src/main/java/com/smartwms/
│       ├── common/     # 统一响应、分页、异常、枚举等公共类
│       └── module/     # 业务域：system / warehouse / product / partner
│                       #        inbound / outbound / transfer / stocktake
│                       #        inventory / alert / stats / log
├── front/              # 前端 Vue3 + Vite 工程
│   └── src/
│       ├── api/        # 各模块接口封装
│       ├── views/      # 页面（按业务域分组）
│       ├── components/ # 通用组件
│       ├── router/     # 路由与权限守卫
│       └── store/      # Pinia 状态
└── README.md
```

> 设计文档、数据库 SQL 脚本与接口文档保存在本地 `项目文档/`、`数据库脚本/`、`接口文档/` 目录（未纳入版本库）。

## 快速开始

### 环境要求

- JDK 17+、Maven 3.8+
- Node.js 18+（npm）
- MySQL 8.0+

### 1. 初始化数据库

创建数据库 `smartwms`，依次执行 `数据库脚本/` 下的 SQL（可重复执行、无 DROP）：

1. `数据库创建脚本.sql`
2. `数据表结构脚本.sql`（29 张表）
3. `数据库索引脚本.sql`
4. `系统初始化数据.sql`（菜单、角色、管理员）
5. `测试基础数据.sql`（可选：仓库/商品/库存等测试数据）

初始管理员：`admin` / `Admin@123456`（仅限测试环境，生产必须更换）。

### 2. 启动后端

```powershell
# 数据库密码通过环境变量注入（不再硬编码在配置中）
$env:DB_PASSWORD = "你的MySQL密码"

cd WmsCore
mvn spring-boot:run        # 或 mvn package 后 java -jar target/*.jar
# 默认使用 dev 配置：localhost:3306/smartwms，端口 8080
```

### 3. 启动前端

```powershell
cd front
npm install
npm run dev                # http://localhost:5173，/api 已代理至 8080
```

构建生产包：`npm run build`（产物 `front/dist/`）。

## 配置说明

| 配置项 | 位置 | 说明 |
| ---- | ---- | ---- |
| `DB_PASSWORD` | 环境变量 | MySQL 密码，`application-dev.yml` 通过 `${DB_PASSWORD}` 读取 |
| 数据源 | `WmsCore/src/main/resources/application-dev.yml` | 开发环境连接与 DEBUG 日志 |
| 生产配置 | `application-prod.yml` | 生产环境部署时使用 |
| API 代理 | `front/vite.config.ts` | 开发环境 `/api` → `http://localhost:8080` |

## 常用命令

```powershell
# 前端
npm run dev            # 开发服务器
npm run type-check     # vue-tsc 类型检查
npm run build          # 类型检查 + 构建

# 后端
mvn spring-boot:run    # 启动
mvn package -DskipTests # 打包
```
