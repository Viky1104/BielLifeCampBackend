# 伯恩生活营地后端

更新时间：2026-07-31

`server/` 是企业积分商城的 Java 21 / Spring Boot 4 多模块后端工程。当前已在工程底座上实现首批身份能力：微信小程序登录、EHR 人员每日全量同步、RS256 JWT、旋转刷新令牌、Gateway 实时会话校验、60 秒内部身份 JWS，以及 system-service 身份/会话与人员同步 Flyway；组织同步和其他领域 API 仍待后续实现。

## 版本基线

| 组件 | 版本 |
|---|---:|
| Java | 21 |
| Spring Boot | 4.0.7 |
| Spring Cloud | 2025.1.2 |
| Spring Cloud Alibaba | 2025.1.0.0 |
| MyBatis Starter | 4.0.0 |
| SpringDoc | 3.0.3 |
| Knife4j | 4.5.0 |
| Nacos | 3.1.1 |
| Sentinel | 1.8.9 |
| XXL-JOB | 3.4.0 |
| Maven Wrapper | 3.9.16 |

版本集中在根 [pom.xml](pom.xml) 管理。Maven Enforcer 限定 JDK 21 和 Maven 3.9.x，并检查重复依赖声明。

## 模块结构

```text
server/
├── platform-bom/                 内部技术依赖 BOM
├── platform-starters/
│   ├── starter-web/              请求 ID、统一错误响应、异常处理
│   ├── starter-security-context/ 内部身份上下文
│   ├── starter-observability/    Actuator、Tracing、OTLP、共享 Logback
│   ├── starter-data-mybatis/     MyBatis、Flyway、MySQL
│   ├── starter-governance/       Nacos、Sentinel、LoadBalancer
│   ├── starter-task-lease/       任务租约 SPI、XXL-JOB Executor
│   └── starter-test/             Spring Test、ArchUnit、Testcontainers、H2
├── api-contracts/                经评审的跨服务契约交付位置
├── gateway/                      统一 API Gateway
├── services/                     九个服务模块
└── deploy/                       Local、Helm 和 ACK 模板
```

业务服务统一使用 MVC 三层及职责扩展包：

```text
controller/       HTTP 入口、参数校验和协议适配
service/          业务服务接口
service/impl/     业务实现和事务边界
manager/          可复用业务编排或第三方能力封装，按需创建
dao/              MyBatis Mapper 接口
model/entity/     持久化实体，按需创建
model/dto/        层间 DTO 及 request/response 子包
config/           配置类及 config/properties 属性类
common/           公共结果、异常和常量，按职责分子包
util/             无状态通用工具，按需创建
task/             私有任务处理、租约和重试
resources/mapper/ 与 dao 接口对应的 MyBatis XML
db/migration/     服务私有 Flyway 迁移
```

`system-service` 的认证实现已经按该边界落地：`controller` 承载 HTTP 适配，`service` 与 `service.impl` 承载接口、业务编排和事务，`manager` 封装 JWT 与微信外部能力，`dao` 放置 MyBatis Mapper 接口，`model.dto` 放置传输与查询投影，`config.properties`、`common.exception` 等按职责归类。模块内 ArchUnit 测试强制 Controller 不得绕过 Service 访问 DAO，DAO 也不得反向依赖 Controller、Service 或 Manager。

业务数据访问统一使用 MyBatis。Java 代码不得使用 `JdbcTemplate`、MyBatis SQL 注解或字符串内联业务 SQL；DAO 接口与 SQL 分离，SQL 统一维护在 `src/main/resources/mapper/**/*.xml`，查询必须显式列出字段，禁止 `SELECT *`。

Gateway 的配置与过滤器分别位于 `gateway.config`、`gateway.filter`；Web、安全上下文和任务租约 Starter 将自动配置、过滤器、异常处理按技术职责分包，对外上下文、常量和 SPI 保持原包兼容。已知 Java 规范差异见 [后端 Java 规范差异清单](../docs/代码规范/后端Java规范差异清单.md)。

Starter 只提供技术能力，不允许放入积分账户、订单、活动、工作项等领域模型。

## 构建与测试

在 PowerShell 中使用进程级 JDK 配置，不修改系统全局环境：

```powershell
$env:JAVA_HOME='D:\DevelopEnviroment\jdk\openjdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -ntp clean verify
```

验证单个服务及其依赖：

```powershell
.\mvnw.cmd --% -ntp -pl services/system-service -am verify
```

当前全量验证结果：22 个模块成功，79 个测试通过，0 失败、0 错误、0 跳过。

## 构建和运行服务

先生成所有可执行 Jar：

```powershell
.\mvnw.cmd --% -ntp -DskipTests package
```

准备可访问的 MySQL，并设置对应服务的连接信息：

```powershell
$env:DB_URL='jdbc:mysql://localhost:3306/system_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC'
$env:DB_USERNAME='lifecamp'
$env:DB_PASSWORD='<local-password>'
$env:AUTH_PRIVATE_KEY='file:D:/DevelopEnviroment/keys/biel-life-camp/auth-private.pem'
$env:AUTH_PUBLIC_KEY='file:D:/DevelopEnviroment/keys/biel-life-camp/auth-public.pem'
java -jar services\system-service\target\system-service-0.1.0-SNAPSHOT.jar
```

`<local-password>` 只是说明占位符，真实密码不得写入文档、代码或 Git。密钥配置是 Spring
Resource 位置；Windows 本地文件必须使用 `file:D:/...`，不能只写裸盘符路径。完整准备、IDEA
运行和 ACK 发布步骤见[运行与部署手册](deploy/README.md)。

## 默认端口

| 应用 | 端口 |
|---|---:|
| Gateway | 8080 |
| system-service | 8081 |
| communication-service | 8082 |
| workbench-service | 8083 |
| points-service | 8084 |
| activity-service | 8085 |
| community-service | 8086 |
| mall-service | 8087 |
| life-service | 8088 |
| order-view-service | 8089 |

端口都可通过 `SERVER_PORT` 覆盖。

## Gateway 路由

| 路径 | 目标服务 |
|---|---|
| `/api/system/**` | system-service |
| `/api/communications/**` | communication-service |
| `/api/workbench/**` | workbench-service |
| `/api/points/**` | points-service |
| `/api/activities/**` | activity-service |
| `/api/community/**` | community-service |
| `/api/mall/**` | mall-service |
| `/api/life/**` | life-service |
| `/api/order-views/**` | order-view-service |

路由统一显式维护在 `gateway/src/main/resources/application.yml` 的
`spring.cloud.gateway.server.webflux.routes` 节点中，不会因为服务注册到 Nacos 就自动
对外暴露。后续迁移配置中心时，将完整 `routes` 列表复制到 Nacos `gateway.yaml` 的相同
配置路径。YAML 列表覆盖时不会按路由 `id` 自动合并，因此不能只发布其中几条路由。

认证开关启用后，Gateway 会清除客户端提交的全部 `X-Internal-*` 头，校验外部 RS256 JWT，
向 system-service 实时确认会话、人员状态和 `authz_ver`，再签发 audience 绑定目标服务且
最长 60 秒的 `X-Internal-Identity` JWS。登录和刷新接口是匿名白名单；其他 `/api/**`
默认要求 Bearer JWT。

业务请求保留原始路径和查询参数，通过 `lb://<service-name>` 从 Nacos 可用实例中选择目标。
Spring Cloud LoadBalancer 默认使用轮询策略，九个固定服务在 Gateway 启动时预热，并使用
Caffeine 缓存实例列表；缓存默认有效 `10s`、容量 `256`。Gateway 不自动重试转发请求，
避免 POST 等非幂等操作被重复执行。没有可用实例时返回 `503`。

Gateway 下游建连超时默认 `3s`，响应超时默认 `30s`。人工 EHR 全量同步接口会先落库
`PENDING` 运行并立即返回 `202`，由 system-service 后台线程执行；客户端必须提交稳定的
`Idempotency-Key`，并使用返回的 `runId` 查询最终状态。

跨域预检统一在 Gateway 处理，不进入认证或下游转发。默认仅允许本地开发地址
`http://localhost:5173` 和 `http://127.0.0.1:5173`，允许
`GET/POST/PUT/PATCH/DELETE/OPTIONS`，并仅开放 `Authorization`、`Content-Type`、
`Idempotency-Key`、`X-Request-Id` 请求头。生产环境必须在 Nacos `gateway.yaml`
中替换为实际 HTTPS 前端域名，例如：

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          globalcors:
            cors-configurations:
              '[/**]':
                allowedOriginPatterns:
                  - https://camp.example.com
                allowCredentials: false
```

Bearer Token 放在 `Authorization` 头中不需要开启 CORS credentials。只有改成 Cookie
认证时才考虑 `allowCredentials: true`，并且不得把允许来源配置为 `*`。Gateway HTTP
客户端、负载均衡上下文和 CORS Handler 均在启动阶段装配，Nacos 修改这些配置后需要重启
Gateway。

## 全局登录认证上下文

数据库继续持久化会话、刷新令牌和 RBAC 权威事实，Redis 承担普通请求的在线会话主路径、
员工当前权限版本和版本化授权快照。在线会话缓存未命中时 system-service 查询数据库并回填；
Redis 连接、认证或反序列化故障时失败关闭，不把技术故障伪装成普通未命中。

在线会话键为 `biel:auth:session:v1:{<session-id>}`，TTL 不超过数据库绝对和空闲期限。
Redis 活跃时间默认最多每分钟改写一次，数据库会话活跃时间默认每 5 分钟持久化一次，避免每个
受保护请求都查询和更新数据库。

授权快照按
`biel:security:authorization:v1:{<employee-id>}:<target-service>:<authz-version>`
保存 15 分钟，同一员工的多个会话共享。普通角色、权限和数据范围变化只递增并发布
`authz_version`，不删除登录会话；旧 JWT 返回 `AUTHZ_STALE` 后，客户端使用刷新令牌取得
新 JWT，无需重新微信登录。离职、冻结、主动退出、强制下线和刷新令牌重放才删除在线会话。

Gateway 取得当前会话和目标服务授权后签发最长 60 秒的内部 JWS。业务服务同时校验该 JWS 和
同版本 Redis 授权快照，校验通过后才把 `LoginUser` 放入请求上下文。Redis 不保存访问令牌、
原始刷新令牌、手机号、OpenID 或其他认证秘密。

业务代码通过 `SecurityUtils` 获取当前用户，不读取客户端提交的身份头：

```java
LoginUser loginUser = SecurityUtils.getLoginUser();
long employeeId = SecurityUtils.getUserId();
String employeeNo = SecurityUtils.getUsername();
boolean canRead = SecurityUtils.hasPermission("employee:read");
```

`hasRole` 和 `hasPermission` 只判断当前目标服务的授权集合；涉及部门、本人或指定组织的数据访问
时，业务层仍需结合 `LoginUser.dataScopes()` 和资源归属执行对象级校验。当前请求没有经过完整
认证链时，`SecurityUtils` 会拒绝返回用户，而不会从普通请求头推导身份。

ACK Redis 已提供 `redis.biel-life-camp.svc.cluster.local:6379`。建议在 system-service 和全部
业务服务对应的 Nacos Data ID 中配置：

```yaml
spring:
  data:
    redis:
      host: redis.biel-life-camp.svc.cluster.local
      port: 6379
      username: ${REDIS_USERNAME}
      password: ${REDIS_PASSWORD}
      database: 0
      connect-timeout: 2s
      timeout: 2s

platform:
  auth:
    session-cache:
      enabled: true
      key-prefix: biel:auth:session:v1
      redis-touch-interval: 1m
      database-touch-interval: 5m
  security-context:
    authorization-cache:
      enabled: true
      key-prefix: biel:security:authorization:v1
      version-key-prefix: biel:security:authz-version:v1
      ttl: 15m
      version-ttl: 5m
```

Redis 用户名和密码必须由 Kubernetes Secret 注入 Pod，不得把真实值写入 Nacos。配置发布后
需要重启 system-service 和全部受保护业务服务；只有 system-service 配置 `session-cache`，
所有业务服务配置 `authorization-cache`。Gateway 不直接读写 Redis。启用后若授权快照不存在
或 Redis 暂时不可用，请求分别返回
`AUTH_LOGIN_CONTEXT_MISSING` 或 `AUTH_LOGIN_CONTEXT_UNAVAILABLE`（HTTP 503）；快照与内部身份
不一致时返回 `AUTH_LOGIN_CONTEXT_INVALID`（HTTP 401）。

当前实现的登录、Token、数据库与 Redis 存储分工、完整键模型、权限变化、并发和故障语义见
[`认证、Token、会话与 Redis 存储设计`](services/system-service/src/main/resources/db/reference/system_auth_token_session_storage_design.md)。
外部总体设计资料见
[`06-Redis会话与授权缓存设计-v1.md`](../docs/04-技术设计/01-身份与权限/06-Redis会话与授权缓存设计-v1.md)。
ACK 当前 Redis 是开发单实例，只允许开发联调；共享非生产和生产必须先切换到 Tair 主从高可用。

滚动升级时先发布全部新版本应用并保持两个缓存开关关闭，确认 system-service 和业务服务版本
一致后，再在 Nacos 同时开启 `AUTH_REDIS_SESSION_ENABLED` 和
`AUTHORIZATION_CACHE_ENABLED` 并重启。回滚时先关闭两个开关再回滚应用；新版本缓存键由 TTL
自动清理。不得让旧业务服务读取新版 system-service 授权，或让新版业务服务读取旧登录快照。

## API 接口文档

各业务服务由 SpringDoc 3.0.3 输出 OpenAPI 3 文档，Gateway 使用 Knife4j 4.5.0 UI 和
显式只读路由集中聚合，路由目标仍通过 Nacos 和 LoadBalancer 解析。本地开发默认开启
Knife4j、关闭 Basic Auth，启动 Gateway 后访问
`http://<gateway-host>:<gateway-port>/doc.html`。聚合配置接口为
`http://<gateway-host>:<gateway-port>/v3/api-docs/swagger-config`。

system-service 在线文档按“认证与会话”和“EHR 人员同步”分组，当前只展示已经实现并经过
Gateway 暴露的 8 个操作。每个操作包含唯一 `operationId`、鉴权要求、请求约束、字段示例、
成功与错误状态码，以及统一 `code/errorMsg/data` 响应结构。仅供 Gateway 调用的
`/internal/system/**` 不进入外部 Knife4j 文档。`api-contracts/p0` 中的冻结契约还包含尚未
实现的员工、组织和完整 RBAC 接口，不能将其误认为当前在线接口。

ACK 环境在 Nacos 的 `gateway.yaml`（Group `LIFECAMP`）中显式配置开关。需要开放在线
文档时推荐启用 Basic Auth：

```yaml
platform:
  api-docs:
    enabled: true
    basic-enabled: true
    username: ${KNIFE4J_BASIC_USERNAME}
    password: ${KNIFE4J_BASIC_PASSWORD}
```

`KNIFE4J_BASIC_USERNAME`、`KNIFE4J_BASIC_PASSWORD` 应由 Kubernetes Secret 注入 Gateway
Pod，不要将真实凭据直接写入 Nacos；用户名不能为空且不能包含冒号，密码至少 12 位。
开发环境需要临时免认证访问时可设置 `basic-enabled: false`，不得将该配置直接用于公开网络。
生产环境不需要在线调试文档时，应设置 `platform.api-docs.enabled=false`。

Gateway 始终预注册聚合配置端点，`enabled`、`basic-enabled` 和 Basic 凭据刷新后由访问过滤器
读取最新值，不会再因 Nacos 在启动后开启文档而缺失 Controller。升级到包含该修复的代码后
需要重启一次 Gateway。各业务服务需保持 `springdoc.api-docs.enabled=true`（默认值），
否则对应分组无法加载。Knife4j 只聚合显式批准且已注册的服务，不会改变现有业务路由。

访问异常可按状态码定位：

- `/doc.html` 返回统一格式的 `404 COMMON_RESOURCE_NOT_FOUND`：当前文档开关为关闭状态。
- `/doc.html` 正常、`/v3/api-docs/swagger-config` 返回 Spring 默认 404：Gateway 仍运行旧代码，
  需要重新构建并重启。
- 返回 `401 AUTH_BASIC_INVALID`：Basic Auth 已开启，需要提供正确用户名和密码。
- 返回 `503 API_DOCS_CONFIGURATION_INVALID`：Basic Auth 已开启，但用户名或密码不符合要求。
- 页面能够打开但某个服务分组加载失败：检查对应服务是否已注册到同一 Nacos
  Namespace/Group，以及服务自身的 `/v3/api-docs` 是否可用。

Knife4j 4.5.0 官方 Gateway starter 编译基线是 Spring Boot 3 / Spring Framework 6，
与本项目 Spring Framework 7 的 `HttpHeaders` 二进制接口不兼容。因此 Gateway 仅使用
官方 `knife4j-openapi3-ui` 静态资源，并由项目内控制器输出兼容的聚合配置；在 Knife4j
官方明确支持 Spring Boot 4 前，不要替换回 `knife4j-gateway-spring-boot-starter`。

## 配置方式

常用环境变量：

| 变量 | 作用 | 默认行为 |
|---|---|---|
| `DB_URL` | 服务私有 MySQL JDBC URL | 指向本地对应 Schema |
| `DB_USERNAME` / `DB_PASSWORD` | 服务数据库账号 | 用户名为本地开发值，密码为空 |
| `FLYWAY_ENABLED` | 应用内 Flyway | `false`；生产使用独立迁移 Job |
| `REDIS_HOST` / `REDIS_PORT` | Redis 地址 | `localhost` / `6379` |
| `REDIS_USERNAME` / `REDIS_PASSWORD` | Redis ACL 凭据 | 空；ACK 中必须由 Kubernetes Secret 注入 |
| `REDIS_DATABASE` | Redis 逻辑库 | `0` |
| `REDIS_CONNECT_TIMEOUT` / `REDIS_COMMAND_TIMEOUT` | Redis 建连与命令超时 | `2s` / `2s` |
| `NACOS_ENABLED` | Nacos 注册发现 | `false` |
| `NACOS_SERVER_ADDR` | Nacos 地址 | `127.0.0.1:8848` |
| `NACOS_NAMESPACE` / `NACOS_GROUP` | 环境隔离 | Group 默认为 `LIFECAMP` |
| `GATEWAY_CONNECT_TIMEOUT_MS` / `GATEWAY_RESPONSE_TIMEOUT` | Gateway 下游建连与普通响应超时 | `3000` / `30s` |
| `GATEWAY_LB_CACHE_TTL` / `GATEWAY_LB_CACHE_CAPACITY` | LoadBalancer 实例缓存 | `10s` / `256` |
| `GATEWAY_CORS_ALLOWED_ORIGIN_1` / `GATEWAY_CORS_ALLOWED_ORIGIN_2` | 本地跨域来源；生产推荐由 Nacos 列表覆盖 | `http://localhost:5173` / `http://127.0.0.1:5173` |
| `GATEWAY_CORS_ALLOW_CREDENTIALS` / `GATEWAY_CORS_MAX_AGE_SECONDS` | 是否允许跨域凭据与预检缓存秒数 | `false` / `3600` |
| `SENTINEL_ENABLED` | Sentinel 客户端 | `false` |
| `XXL_JOB_ENABLED` | XXL-JOB Executor | `false` |
| `XXL_JOB_ADMIN_ADDRESSES` | 调度中心地址 | 空 |
| `AUTH_ENABLED` / `GATEWAY_AUTH_ENABLED` | system-service 签发与 Gateway 校验开关 | 当前本地 system-service 与 Gateway 默认为 `true`；部署环境必须显式配置，缺少依赖时拒绝启动 |
| `AUTH_ISSUER` / `AUTH_AUDIENCE` | 外部 JWT issuer / Gateway audience | `biel-life-camp` / `biel-life-camp-gateway` |
| `AUTH_KEY_ID` | 外部 JWT 当前签名密钥 ID | 必须显式配置 |
| `AUTH_PRIVATE_KEY` / `AUTH_PUBLIC_KEY` | 外部 JWT PKCS#8 私钥、X.509 公钥资源位置 | 本地使用 `file:D:/...`；ACK 使用只读 Secret 文件挂载路径 |
| `AUTH_IDENTIFIER_PEPPER` / `AUTH_TOKEN_PEPPER` | 手机号/OpenID HMAC 与刷新令牌摘要 pepper | 空；启用时均至少 32 字符 |
| `AUTH_IDENTITY_ENCRYPTION_KEY` | OpenID/UnionID AES-GCM 密钥 | 空；启用认证时必须是 Base64 编码的 32 字节密钥 |
| `AUTH_ADMIN_PASSWORD_ENABLED` | 是否开放管理后台本地密码登录 | `false`；过渡或应急使用，生产优先接企业 SSO |
| `AUTH_ADMIN_PASSWORD_RATE_LIMIT_ENABLED` | 是否启用管理员登录 Redis 失败限流 | `true`；启用登录时不建议关闭 |
| `AUTH_ADMIN_PASSWORD_MAX_ATTEMPTS` / `AUTH_ADMIN_PASSWORD_WINDOW` | 单账号或来源地址失败阈值与计数窗口 | `5` / `15m` |
| `WECHAT_APP_ID` / `WECHAT_APP_SECRET` | 微信小程序服务端凭据 | 空；缺失时登录失败关闭 |
| `EHR_SYNC_ENABLED` / `EHR_URL` | EHR 人员全量同步开关与 ESB 地址 | 默认关闭；正式地址默认为企业内网 ESB |
| `EHR_ESB_AUTH` | EHR ESB 请求认证头 | 空；启用同步时必须由 Secret/KMS 注入 |
| `EHR_PAGE_SIZE` / `EHR_MAX_PAGES` | 全量人员分页大小与最大页数保护 | `1000` / `10000` |
| `EHR_PAGE_CONCURRENCY` | EHR 后续分页的单实例并发请求数 | `4`，允许 `1～16` |
| `EHR_MAX_RECORDS` | 单次全量快照最大人员数，防止异常元数据造成 OOM | `200000` |
| `EHR_PERSISTENCE_BATCH_SIZE` | 人员暂存和投影批量写入数量 | `500`，允许 `1～1000` |
| `AUTH_GATEWAY_SERVICE_TOKEN` | Gateway 调 system-service 的附加服务凭据 | 空；启用时至少 32 字符，且生产仍要求 HTTPS/mTLS |
| `SYSTEM_AUTHORIZATION_URI` | Gateway 实时会话校验地址 | 本地默认 `http://127.0.0.1:8081/internal/system/v1/auth/session-context`；生产必须是 HTTPS 内网地址 |
| `INTERNAL_IDENTITY_PRIVATE_KEY` / `INTERNAL_IDENTITY_PUBLIC_KEY` | Gateway 内部 JWS PKCS#8 私钥、X.509 公钥资源位置 | 本地使用 `file:D:/DevelopEnviroment/keys/biel-life-camp/internal-identity-*.pem`；不得与外部 JWT 密钥对共用 |
| `INTERNAL_IDENTITY_ENABLED` | 业务服务是否验签内部 JWS | 当前本地 system-service 默认为 `true`；部署环境必须显式配置公钥 |
| `INTERNAL_IDENTITY_ISSUER` / `INTERNAL_IDENTITY_KEY_ID` | 内部 JWS issuer / key ID | issuer 默认 `biel-life-camp-gateway` |
| `AUTH_REDIS_SESSION_ENABLED` | system-service 是否使用 Redis 在线会话主路径 | `false`；与授权缓存一起开启 |
| `AUTH_REDIS_SESSION_KEY_PREFIX` | 在线会话键前缀 | `biel:auth:session:v1` |
| `AUTH_REDIS_TOUCH_INTERVAL` / `AUTH_DATABASE_TOUCH_INTERVAL` | Redis 与数据库会话活跃时间更新间隔 | `1m` / `5m` |
| `AUTHORIZATION_CACHE_ENABLED` | 是否启用版本化授权缓存与业务服务双重校验 | `false`；与Redis会话一起开启 |
| `AUTHORIZATION_CACHE_KEY_PREFIX` / `AUTHORIZATION_VERSION_KEY_PREFIX` | 授权快照和当前权限版本键前缀 | `biel:security:authorization:v1` / `biel:security:authz-version:v1` |
| `AUTHORIZATION_CACHE_TTL` / `AUTHORIZATION_VERSION_TTL` | 授权快照和当前权限版本有效期 | `15m` / `5m` |
| `KNIFE4J_ENABLED` | Gateway 是否启用聚合接口文档 | `true`；生产环境由 Nacos 显式关闭或加认证 |
| `KNIFE4J_BASIC_ENABLED` | Knife4j 是否启用 Basic 访问保护 | `false`；ACK 启用文档时必须设置为 `true` |
| `KNIFE4J_BASIC_USERNAME` / `KNIFE4J_BASIC_PASSWORD` | Knife4j 访问凭据 | 空；由 Kubernetes Secret 注入 |
| `TRACING_SAMPLING_PROBABILITY` | Trace 采样率 | `0.1` |

## 日志输出管理

所有运行模块通过 `starter-observability` 共享
[`logback-spring.xml`](platform-starters/starter-observability/src/main/resources/logback-spring.xml)。
Spring Boot Starter 提供 `log4j-to-slf4j`、`jul-to-slf4j` 和 Commons Logging 路由，
因此 Log4j API、JUL、Spring 与业务日志最终都由 Logback 统一输出，不得再引入第二套
Log4j2 实现或额外的 `log4j2.xml`。

控制台始终输出 ECS JSON，包含时间、级别、线程、Logger、消息以及 MDC 中的
trace/request 字段；设置 `LOGGING_STRUCTURED_ECS_SERVICE_NAME` 后同时包含稳定的服务名。
ACK Filebeat 从 stdout 采集。默认日志级别如下：

| Logger | 默认级别 | 用途 |
|---|---:|---|
| `ROOT` | `INFO` | 全局默认 |
| `com.biel.lifecamp` | `INFO` | 平台业务代码 |
| `org.springframework` | `INFO` | Spring 组件 |
| `com.alibaba.nacos` / `com.alibaba.cloud.nacos` | `WARN` | Nacos 客户端 |
| `org.mybatis` / `org.apache.ibatis` | `WARN` | MyBatis |
| `io.netty` | `WARN` | Netty 网络组件 |

环境级别可在对应 Nacos Data ID 中配置，修改后应重启服务确认生效：

```yaml
logging:
  level:
    root: INFO
    com.biel.lifecamp: DEBUG
    com.alibaba.nacos: WARN
    org.mybatis: WARN
```

文件输出为显式启用能力。在 IDEA 本地调试时增加 `file-logging` Profile，例如：

```text
SPRING_PROFILES_ACTIVE=nacos,file-logging
LOGGING_FILE_NAME=D:/logs/biel-life-camp/system-service.log
LOGGING_STRUCTURED_ECS_SERVICE_NAME=system-service
```

启用后生成：

```text
D:/logs/biel-life-camp/system-service.log
D:/logs/biel-life-camp/system-service.log.error
D:/logs/biel-life-camp/system-service.log.yyyy-MM-dd.N.gz
D:/logs/biel-life-camp/system-service.log.error.yyyy-MM-dd.N.gz
```

普通文件接收该 Logger 有效级别允许的全部事件，错误文件只接收 `ERROR`。两个文件均按天和
单文件 `50MB` 滚动，默认保留 `30` 天；每类归档总量上限为 `5GB`（两类合计最多约
`10GB`），启动时清理过期归档。
共享日志目录中的每个进程必须使用唯一的 `logging.file.name`，不能依赖
`application.log` 回退名称。可用 Spring Boot 标准配置覆盖：

| 配置项 / 环境变量 | 默认值 |
|---|---:|
| `logging.file.name` / `LOGGING_FILE_NAME` | 空；启用文件日志时必须显式指定 |
| `logging.file.path` / `LOGGING_FILE_PATH` | 空；只设置路径时 Spring Boot 使用 `<path>/spring.log`，两项都未设置时共享配置回退到 `./logs/application.log` |
| `logging.structured.ecs.service.name` / `LOGGING_STRUCTURED_ECS_SERVICE_NAME` | 空；部署时设置为 Nacos 服务名 |
| `logging.logback.rollingpolicy.max-file-size` / `LOGGING_LOGBACK_ROLLINGPOLICY_MAX_FILE_SIZE` | `50MB` |
| `logging.logback.rollingpolicy.max-history` / `LOGGING_LOGBACK_ROLLINGPOLICY_MAX_HISTORY` | `30` |
| `logging.logback.rollingpolicy.total-size-cap` / `LOGGING_LOGBACK_ROLLINGPOLICY_TOTAL_SIZE_CAP` | `5GB` |
| `logging.logback.rollingpolicy.clean-history-on-start` | `true` |

当前 Helm 工作负载使用只读根文件系统，且 Filebeat 已采集 stdout，因此 ACK 默认不要启用
`file-logging`。如确需 Pod 内文件日志，必须先为 `LOGGING_FILE_PATH` 挂载有容量限制的可写卷，
同时评估 stdout 与文件的重复采集及 Pod 重建后的保留策略。

日志不得打印密码、Token、密钥、完整手机号、OpenID、身份证号或完整请求体。人员同步等批量
任务应记录稳定的员工标识、同步时间、结果和脱敏后的失败原因；数据库只持久化结构化、脱敏的
人员问题，不保存原始响应或异常堆栈。

认证配置中的密钥值是 Spring Resource 位置，例如 `file:/run/secrets/auth-private.pem`，不是 PEM 正文本身。外部 JWT 与内部 JWS 使用两套独立 RSA 密钥。`AUTH_ALLOW_EPHEMERAL_KEYS` 只供自动化测试，生产禁止开启。system-service 的 EHR 初始同步标志默认为未完成；在真实全量同步成功前，小程序登录返回 `AUTH_EHR_INITIAL_SYNC_REQUIRED`，不得手工绕过。

管理后台与小程序共用 `sys_employee`、JWT、Redis 会话和 RBAC，但会话明确区分
`ADMIN_WEB/password` 与 `MINI_PROGRAM/wechat`。本地密码登录默认关闭，可在 Nacos 的
system-service 配置中设置 `platform.auth.admin-password.enabled=true`；管理接口仍会同时
校验 `client_type=ADMIN_WEB` 和权限码。执行
`services/system-service/.../util/AdminPasswordHashTool` 生成 `{bcrypt}` 哈希，再替换
`system_db_super_admin_bootstrap.sql` 顶部占位符后整段执行。禁止把明文密码或哈希放入
Nacos；Nacos 只保存登录开关、阈值和窗口配置。

EHR 同步通过 XXL-JOB 处理器 `ehrEmployeeFullSyncJob` 触发，调度中心应配置
`0 0 2 * * ?`（`Asia/Shanghai`）。第一页同步取得分页元数据，后续分页通过有界线程池和
固定大小滑动窗口并发拉取，但仍按页码顺序汇总。最多只保留
`EHR_PAGE_CONCURRENCY` 个在途或待汇总页面；EHR 声明人数超过 `EHR_MAX_RECORDS` 时会在
创建全量集合前终止。完整快照的全部分页和关键字段校验通过后才进入人员生效阶段；
空快照、分页变化或关键字段异常会整批失败，不会执行离职标记。集群并发由
`sys_task_lease` 数据库租约保护。

人员生效阶段按 `EHR_PERSISTENCE_BATCH_SIZE` 使用短事务批量写入员工投影、直属上级和默认
角色，减少数据库往返；批量 SQL 失败时再逐人重试并隔离问题人员，因此不会改变
“单个人员失败不回滚其他合法人员”的规则。分页并发数和持久化批次大小变更后需要重启
system-service，使线程池与任务配置重新初始化。

首次上线通过人工接口 `POST /api/system/v1/ehr-sync-runs` 提交同步，请求头必须提供 16～128 字符的
`Idempotency-Key`。接口立即返回 `PENDING` 运行，后续通过 `GET /api/system/v1/ehr-sync-runs/{runId}`
查询结果；失败人员通过 `GET /api/system/v1/ehr-sync-runs/{runId}/issues` 分页查询。重复提交同一个
幂等键只返回已有运行；需要重新同步时应在确认上一运行结果后更换幂等键。
EHR 同步启用时，无论认证开关是否启用，
都必须配置至少 32 字符且长期稳定的 `AUTH_IDENTIFIER_PEPPER`。

激活 Nacos 配置：

```powershell
.\scripts\Connect-AckNacosDev.ps1
```

脚本通过阿里云 CLI 获取临时 ACK kubeconfig，从 `biel-life-camp/nacos-client-dev-secret`
读取客户端凭据，启动具备健康检查和自动重连能力的本机 `8848/9848/18000` ACK Nacos
通道，并为当前 PowerShell 设置 `nacos` Profile、`dev` Namespace 和 `LIFECAMP` Group。
凭据只存在于当前进程环境，不会写入仓库或脚本。当前 PowerShell 启动的 Java 服务会继承
这些变量，例如：

```powershell
java -jar gateway\target\gateway-0.1.0-SNAPSHOT.jar
```

脚本依赖已登录的阿里云 CLI 以及可用的 `kubectl`。应用使用 `spring.config.import`，不使用
旧式 `bootstrap.yml`。ACK 中的真实配置和凭据必须来自 Nacos、Kubernetes Secret 或 KMS。

IDEA 不会从脚本执行后的其他 PowerShell 窗口获取环境变量。使用 IDEA 调试时，必须先完全
退出已经运行的 IDEA，在执行脚本的同一个 PowerShell 中重新启动 IDEA；不要把 Nacos 密码
保存到共享 Run Configuration。若 IDEA 已经启动，直接点击 Run 会因为未激活 `nacos`
Profile 而触发 Nacos `spring.config.import` 检查失败。

如果 IDEA Run Configuration 已自行保存本地 Nacos 环境变量，可将以下命令配置为
`Before launch` External Tool，仅负责保证通道在线：

```powershell
.\scripts\Start-AckNacosTunnel.ps1
```

控制台地址为 `http://127.0.0.1:18000`。守护进程在网络切换、电脑休眠或 ACK 连接中断后
会自动重建端口转发。停止本地通道：

```powershell
.\scripts\Start-AckNacosTunnel.ps1 -Stop
```

连接 ACK 开发 Redis：

```powershell
.\scripts\Connect-AckRedisDev.ps1
```

脚本自动获取临时 ACK kubeconfig 和 `redis-dev-secret`，完成 ACL 认证检查，并将 Redis 映射到
本机 `127.0.0.1:6379`。IDEA Database、RedisInsight 和其他客户端统一填写：

| 配置项 | 值 |
|---|---|
| Host / Port | `127.0.0.1` / `6379` |
| Username | `app` |
| Password | 脚本已复制到剪贴板 |
| Database | `0` |

脚本同时为当前 PowerShell 设置应用使用的 `REDIS_*` 变量和 redis-cli 使用的
`REDISCLI_AUTH`，因此安装 redis-cli 后可直接运行：

```powershell
redis-cli -h 127.0.0.1 -p 6379 --user app
```

重复执行脚本会复用现有通道。若 `6379` 已被占用，可增加 `-LocalPort 16379`，并让客户端改连
对应端口。停止通道并清理当前 PowerShell 中的 Redis 变量：

```powershell
.\scripts\Connect-AckRedisDev.ps1 -Stop
```

该本机地址只在脚本运行并保持通道在线时有效；电脑休眠或网络切换后重新运行脚本即可。
ACK 集群内的服务仍应使用 `redis.biel-life-camp.svc.cluster.local:6379`，不得把开发 Redis
通过 Ingress、LoadBalancer 或 NodePort 暴露到公网。

## 日志与健康检查

- 控制台默认输出 ECS JSON，供 Pod 标准输出、Filebeat 和 Elasticsearch 采集；
- 请求使用 `X-Request-Id`，并将 `request_id` 写入 MDC；
- 健康检查：`/actuator/health/readiness`、`/actuator/health/liveness`；
- 指标入口：`/actuator/prometheus`；
- 日志不得包含令牌、密码、Cookie、完整手机号、证件号或未筛选请求正文。

## 本地基础设施与 ACK

- [运行与部署手册](deploy/README.md)
- [本地 Compose](deploy/local/README.md)
- [通用服务 Helm Chart](deploy/helm/platform-service)
- [ACK 基础设施清单](deploy/kubernetes/infrastructure/README.md)

当前开发机已安装 kubectl 1.36.1，但没有 Docker、Helm 和 yq。ACK Nacos 与开发 Redis 清单已完成服务端校验和实际运行验证；Compose 与 Helm 模板仍未完成本机运行或渲染验证。启用其他模板前必须替换占位镜像、Secret、RDS 地址和网络策略，并导入固定版本厂商提供的 Nacos/XXL-JOB SQL。

## 开发约束

- 不跨服务共享业务实体、Mapper、Repository 或数据库连接；
- 不凭原型和记忆推断接口、字段、错误码或状态机；
- 数据库变化必须带服务私有 Flyway 迁移；
- API 行为变化必须同步 OpenAPI 和架构文档；
- 定时任务仍由业务服务拥有，XXL-JOB 只提供控制面；
- 完成修改后必须运行受影响模块测试，架构或状态变化要同步 `docs/PROJECT_CONTEXT.md`、`docs/DECISIONS.md` 和 `docs/CURRENT_STATUS.md`。

返回项目总说明：[../README.md](../README.md)。
