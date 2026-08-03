# 管理后台本地密码登录实施计划

## 1. 目标

在不改变现有微信小程序登录流程的前提下，为管理后台补充最小可用的本地密码登录能力：

- 小程序继续使用微信认证，签发 `client_type=MINI_PROGRAM` 的访问令牌。
- 管理后台使用工号和密码认证，签发 `client_type=ADMIN_WEB` 的访问令牌。
- 两类客户端共用员工、角色、权限、会话、刷新令牌和 Redis 在线会话体系。
- 管理接口同时校验 `ADMIN_WEB` 客户端类型和细粒度权限。
- 本地 `admin` 只作为开发期引导账号或生产应急账号，生产主路径最终迁移到企业 SSO。

## 2. 架构决策

1. 不新增第二套用户体系。`sys_employee` 仍是统一登录主体，管理员通过
   `sys_role_assignment` 获得 `SUPER_ADMIN` 等角色。
2. 密码不加密、不保存明文，使用 Spring Security `DelegatingPasswordEncoder`，
   当前编码算法采用 BCrypt、强度 12，数据库保存 `{bcrypt}...` 格式。
3. 本地凭据与 EHR 员工资料分表存储，新增最小表 `sys_local_credential`。
4. 新增 `sys_employee.source_type`，取值至少包含 `EHR`、`LOCAL_BOOTSTRAP`；
   EHR 全量同步只停用 `EHR` 来源记录。
5. 外部访问 JWT 使用相同的 RS256 密钥、签发方和 Gateway audience，通过
   `client_type` 与 `amr` 区分登录渠道。
6. 外部 JWT 继续只保存主体、会话和权限版本，不直接固化角色和权限；
   Gateway 实时取得授权后签发目标服务内部 JWS。
7. 密码登录默认关闭，通过配置显式开启；初始密码不得写入 SQL、代码、Git 或 Nacos。

## 3. 依赖关系

```text
数据库迁移与本地账号来源标记
        │
        ├── 密码摘要生成与初始化
        │
        └── 管理员登录接口
                │
                ├── ADMIN_WEB 会话与访问令牌
                │       │
                │       └── 刷新令牌保持客户端类型
                │
                └── Gateway / 内部 JWS 传播 client_type
                        │
                        └── 管理接口双重鉴权
```

## 4. 实施任务

### 任务 1：冻结后台认证契约

说明：先补充 OpenAPI 和身份协议，明确管理员登录请求、响应、错误码以及 Token 声明，避免数据库和代码各自演进。

验收标准：

- 新增 `POST /api/system/v1/auth/admin/login`，请求只包含 `employeeNo`、`password`。
- 登录失败统一返回 `AUTH_INVALID_CREDENTIALS`，不暴露账号是否存在。
- `client_type` 固定支持 `MINI_PROGRAM`、`ADMIN_WEB`；`amr` 支持 `wechat`、`password`，为未来 `sso`、`mfa` 预留。

验证：

- OpenAPI 校验通过。
- 契约示例不包含真实密码、密码摘要或密钥。

依赖：无。

预计范围：小。

### 任务 2：新增本地凭据和账号来源迁移

说明：通过新的 Flyway 迁移增加 `sys_employee.source_type` 和最小化的
`sys_local_credential`，不修改已经执行的 V1～V3。

建议字段：

- `sys_employee.source_type`：`EHR` 或 `LOCAL_BOOTSTRAP`，现有记录回填为 `EHR`。
- `sys_local_credential.employee_id`：员工主键，逻辑关联，不创建数据库外键。
- `password_hash`：`VARCHAR(255)`，保存带算法前缀的摘要。
- `status`：`ACTIVE`、`DISABLED`。
- `must_change_password`：首次登录是否必须修改密码。
- `password_changed_at`、`created_at`、`updated_at`。

验收标准：

- 迁移可在 MySQL 8 空库和 V1～V3 已执行数据库上成功运行。
- 不存在明文密码字段。
- 同一员工最多一条本地凭据。

验证：

- MySQL 8 迁移集成测试通过。
- 表、字段、唯一约束和注释核对通过。

依赖：任务 1。

预计范围：小。

### 任务 3：调整超级管理员初始化

说明：更新现有超级管理员脚本，将 `admin` 标记为 `LOCAL_BOOTSTRAP`，创建角色和授权；密码摘要通过一次性本地工具生成后单独写入凭据表。

验收标准：

- 脚本可重复执行，只创建一个 `admin`、一个有效 `SUPER_ADMIN` 授权和一条本地凭据。
- 初始化过程不包含默认密码或固定密码摘要。
- 工具从控制台安全读取密码，只输出 BCrypt 摘要，不打印原始密码。

验证：

- 首次执行、重复执行和已有同工号 EHR 员工冲突测试通过。
- 仓库秘密扫描不出现测试密码或真实摘要。

依赖：任务 2。

预计范围：中。

### 任务 4：实现管理员登录完整链路

说明：在 system-service 增加管理员密码登录，从 DAO 查询本地凭据，使用
`PasswordEncoder.matches` 校验，然后复用现有会话、刷新令牌和授权快照流程。

验收标准：

- 只有在职、账号启用、凭据启用且密码正确的员工可以登录。
- `admin` 登录创建 `client_type=ADMIN_WEB`、`auth_method=PASSWORD` 会话。
- 返回值继续使用现有 `TokenPairDTO`，不建设第二套令牌模型。

验证：

- 正确密码、错误密码、未知工号、冻结账号、禁用凭据测试通过。
- 错误响应和日志不泄露账号存在性、密码或密码摘要。

依赖：任务 2、任务 3。

预计范围：中。

### 任务 5：改造 Token 和刷新会话

说明：去掉 `AuthTokenManager` 中写死的 `MINI_PROGRAM` 和 `wechat`，由会话携带客户端类型和认证方式；刷新令牌沿用原会话属性。

验收标准：

- 微信登录仍签发 `MINI_PROGRAM`、`amr=[wechat]`。
- 管理员登录签发 `ADMIN_WEB`、`amr=[password]`。
- 刷新后 `client_type` 和 `amr` 不发生改变，客户端不能通过请求参数切换。

验证：

- 两类登录和刷新 Token 的声明测试通过。
- 伪造客户端类型或跨会话刷新测试被拒绝。

依赖：任务 4。

预计范围：中。

### 任务 6：传播客户端类型到业务服务

说明：扩展 system-service 会话上下文、Gateway 内部 JWS、公共契约、
`LoginUser` 和 `SecurityUtils`，让业务服务能够可靠判断请求来源。

验收标准：

- 内部 JWS 增加受签名保护的 `client_type`。
- `LoginUser` 可读取客户端类型。
- `SecurityUtils.requireClientType("ADMIN_WEB")` 或等价能力可用。
- 客户端提交的普通 HTTP Header 不能覆盖服务端认证上下文。

验证：

- Gateway、system-service 和 starter-security-context 测试通过。
- 缺失、非法或与会话不一致的 `client_type` 失败关闭。

依赖：任务 5。

预计范围：中。

### 任务 7：管理接口实施双重鉴权

说明：为角色、员工冻结、EHR 手工同步、第三方配置和审计导出等管理接口增加客户端类型与权限码双重校验。

验收标准：

- `MINI_PROGRAM` Token 即使属于 `SUPER_ADMIN` 也不能调用管理接口。
- `ADMIN_WEB` Token 缺少目标权限时返回 `AUTH_PERMISSION_DENIED`。
- `ADMIN_WEB` Token 具备权限时仍需经过数据范围、状态机、职责分离和操作审计。

验证：

- 每类管理接口至少包含允许、错误客户端和缺少权限三个测试。
- 超级管理员不能绕过本人审批本人、审计不可修改等硬约束。

依赖：任务 6。

预计范围：中。

### 任务 8：增加密码登录防护

说明：密码登录接口增加 Redis 失败限流和统一安全日志。限流以账号摘要和来源 IP 两个维度组合，Redis 故障时失败关闭。

验收标准：

- 连续失败达到阈值后临时拒绝登录，成功登录清理账号维度失败计数。
- 限流参数可配置但默认安全，真实密码和完整账号不进入 Redis Key 或日志。
- 不在数据库保存高频失败计数。

验证：

- 并发失败、过期恢复、Redis 异常和成功清理测试通过。
- 日志检查不包含密码、摘要、Token 或认证请求正文。

依赖：任务 4。

预计范围：中。

### 任务 9：修正 EHR 同步边界

说明：人员全量同步仅管理 `source_type=EHR` 的员工，不能把本地应急管理员当作“快照缺失员工”停用。

验收标准：

- EHR 快照缺失时，EHR 员工按原规则离职/停用。
- `LOCAL_BOOTSTRAP` 管理员不参与 EHR 缺失判定。
- EHR 数据不能覆盖本地密码摘要或本地凭据状态。

验证：

- 全量同步中包含、缺失和冲突场景测试通过。
- 同步日志不输出管理员密码、摘要或完整个人信息。

依赖：任务 2。

预计范围：小。

### 任务 10：配置、文档和发布

说明：补充 Nacos 非秘密配置、Kubernetes Secret 约束、运行手册、OpenAPI、ER 图和项目状态。

验收标准：

- `ADMIN_PASSWORD_AUTH_ENABLED` 默认 `false`。
- Nacos 只保存开关、限流阈值等非秘密配置，不保存初始密码。
- 文档说明本地管理员只用于引导/应急，生产管理员优先使用企业 SSO。

验证：

- system-service 模块测试、Gateway 测试和全量 Maven `verify` 通过。
- 关闭开关时原微信登录行为完全不变。
- 开启开关后完成一次 `admin` 登录、刷新、管理接口访问和退出的端到端测试。

依赖：任务 1～9。

预计范围：中。

## 5. 检查点

### 检查点 A：任务 1～3

- 数据模型和认证契约评审通过。
- 初始化脚本不包含密码。
- MySQL 8 迁移和脚本验证通过。

### 检查点 B：任务 4～6

- 微信和管理员登录均能签发正确 Token。
- 刷新保持原客户端类型。
- Gateway 到业务服务可以可信传播 `client_type`。

### 检查点 C：任务 7～10

- 小程序 Token 无法调用管理接口。
- 管理权限、数据范围和硬约束同时生效。
- 密码限流、日志脱敏、EHR 同步和全量构建通过。

## 6. 风险与控制

| 风险 | 影响 | 控制 |
| --- | --- | --- |
| 本地 admin 被 EHR 同步停用 | 管理后台不可登录 | 增加 `source_type`，同步只处理 EHR 来源 |
| 小程序 Token 调用管理接口 | 权限提升 | `ADMIN_WEB` 与权限码双重校验 |
| 密码写入脚本或 Nacos | 凭据泄露 | 本地安全生成摘要，密码只进入进程内存 |
| Token 刷新改变客户端类型 | 跨端权限提升 | 客户端类型取自服务端会话，不接受请求参数 |
| 暴力破解 | 管理账号失陷 | BCrypt 12、Redis 账号/IP 双维度限流、统一错误 |
| 本地账号长期替代 SSO | 身份治理分裂 | 标记为引导/应急账号，生产路线保留 SSO 迁移 |

## 7. 建议实施顺序

严格按任务 1→10 实施。任务 8 和任务 9在任务 2、4完成后可以分别推进，但合并前必须经过检查点 B，不能先开放管理后台登录再补客户端隔离。

## 8. 实施结果（2026-07-31）

- 任务 1～10 已完成代码、SQL、契约和项目内运行文档实现。
- 当前真实管理接口只有 EHR 同步运行接口，因此双重鉴权先覆盖该组接口；后续角色、
  员工冻结、第三方配置和审计接口落地时必须复用相同边界。
- `must_change_password` 字段已经预留，但本版没有修改密码接口，因此暂不强制阻断登录，
  防止初始化管理员在没有闭环操作时被锁死。
- 全量 Maven `verify` 的 22 个 Reactor 模块全部通过。真实 MySQL、Redis 和 Gateway
  端到端验证需要在部署环境执行 `tasks/todo.md` 的三项部署待办。
- 开发机已安装 OpenSSL 4.0.1，并在仓库外生成 3072 位外部 JWT RSA 密钥对；密钥格式为
  PKCS#8 私钥和 X.509 公钥。IDEA 的 system-service 启动配置使用 `file:D:/...` 资源位置，
  已验证 `nacos` Profile 下 Tomcat 在 8081 启动成功。
- 新增 `deploy/README.md`，统一记录本地运行、Nacos 配置、RSA 密钥、常见故障、ACK 部署顺序和
  回滚检查。当前 Helm Chart 尚无 Secret 文件挂载能力，认证服务部署 ACK 前必须补齐。
