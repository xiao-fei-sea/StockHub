# 智仓云 SmartWMS 后端项目

> Spring Boot 3.2.5 + Java 17 + MyBatis-Plus 3.5.5 + JWT (jjwt 0.12.5)，单体架构。
> 结构依据《系统项目目录结构说明》第 2 节；依赖依据《系统技术依赖清单》第 1 节。

## 1. 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17+（本机 21 亦可编译，release=17） | Spring Boot 3 最低要求 |
| Maven | 3.9.x | 本机未全局安装时可用 `%LOCALAPPDATA%\apache-maven-3.9.9\bin\mvn` |
| MySQL | 8.0.x | 需先执行 `..\数据库脚本\` 下 5 个 SQL 完成搭建 |

## 2. 构建与运行

```powershell
# 编译
mvn -f 后端项目\pom.xml clean compile

# 单元测试
mvn -f 后端项目\pom.xml test

# 打包（可执行 fat JAR）
mvn -f 后端项目\pom.xml clean package -DskipTests

# 运行（开发环境，默认读取 application-dev.yml）
java -jar 后端项目\target\smartwms-backend-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```

## 3. 环境变量

| 变量 | 说明 | 开发环境缺省 |
| --- | --- | --- |
| DB_URL | JDBC 连接串 | jdbc:mysql://localhost:3306/smartwms?... |
| DB_USER | 数据库账号 | root |
| DB_PASSWORD | 数据库密码（禁止入库明文） | 空 |
| JWT_SECRET | JWT 签名密钥（生产必须覆盖） | dev 占位值 |

## 4. 包结构（com.smartwms）

| 包 | 职责 |
| --- | --- |
| config | SecurityConfig、MybatisPlusConfig（分页/乐观锁插件）、Jackson/WebMvc/Async 配置 |
| security | JwtAuthenticationFilter、LoginUser、JwtUtil |
| interceptor | 数据权限拦截器（warehouse_id 注入） |
| aspect | OperationLogAspect 操作日志切面 |
| controller | REST 路由 /api/**，仅路由+校验+调用，返回统一 Result |
| service / service.impl | 业务接口与实现（状态机、FIFO、单号生成、事务） |
| mapper / mapper.xml | 数据访问（BaseMapper + 手写 XML） |
| entity | 29 张表实体 |
| dto / vo | 请求入参 / 响应出参 |
| enums | OrderStatus(0-5)、AlertType、ErrorCode 等 |
| exception | BizException、GlobalExceptionHandler |
| util | NoUtil 单号、TreeUtil、SensitiveUtil |

依赖方向：`controller → service(接口) → impl → mapper → entity` 单向，跨模块只经 Service 接口。
库存唯一写入口为 `InventoryService`，禁止绕过直改 inventory 表。

## 5. 当前状态（骨架阶段）

- ✅ 工程骨架：pom、启动类、三环境配置、全部包目录
- ⬜ 业务代码：待按《07_开发规范与测试设计》迭代计划编写（先做登录认证与仓库管理模块）
- ⬜ 可选依赖 Knife4j / actuator 未引入（待确认，见《系统技术依赖清单》5）
