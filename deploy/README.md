# 伯恩生活营地后端运行与部署手册

更新时间：2026-08-06

本文给出当前代码基线的本地运行、Nacos 配置、认证密钥准备和 ACK 部署检查步骤。Gateway 与
`system-service` 的环境运行配置已经迁移到 Nacos；应用包只保留服务名、端口、配置中心连接、代码
结构扫描规则以及关闭自动路由、自动治理和 Flyway 的安全兜底。Gateway 与 `system-service` 已部署到
ACK 开发环境；仓库中的环境 values 仅适用于该开发环境，不能直接视为生产发布方案。

## 1. 环境边界

| 环境 | 配置中心 | 密钥与凭据 | 当前状态 |
|---|---|---|---|
| Windows 本地开发 | ACK Nacos `dev/LIFECAMP`，通过本机隧道访问 | IDEA 进程环境变量与本机 PEM 文件 | `system-service` 启动已验证 |
| ACK 开发环境 | `nacos.biel-life-camp.svc.cluster.local:8848` | Kubernetes Secret 挂载文件及 Secret 环境变量 | Gateway、system-service、Nacos、Redis 已部署 |
| 共享非生产/生产 | 环境独立的 Nacos、RDS、Tair、KMS | KMS/Kubernetes Secret，不使用开发密钥 | 尚未建设 |

Nacos 只保存开关、超时、阈值、路由和 Secret 文件位置等非秘密配置。数据库密码、Redis ACL、EHR
凭据、微信 AppSecret、pepper、AES 密钥、Gateway 服务凭据和 RSA 私钥不得写入 Nacos、Git、镜像或
日志。

## 2. 本地前置条件

- JDK：`D:\DevelopEnviroment\jdk\openjdk-21`
- Maven：仓库自带 `mvnw.cmd`，不依赖全局 Maven
- OpenSSL：`C:\Program Files\OpenSSL-Win64\bin\openssl.exe`
- kubectl：用于连接 ACK Nacos 和开发 Redis
- MySQL：已执行 system-service 当前迁移并可被本机访问

在新 PowerShell 中确认：

```powershell
& 'D:\DevelopEnviroment\jdk\openjdk-21\bin\java.exe' -version
& 'C:\Program Files\OpenSSL-Win64\bin\openssl.exe' version
```

## 3. 准备外部 JWT RSA 密钥

外部密钥由 `system-service` 使用私钥签发访问 JWT，Gateway 使用同一对密钥的公钥验签。私钥必须是
PKCS#8 PEM，公钥必须是 X.509 SubjectPublicKeyInfo PEM。

```powershell
New-Item -ItemType Directory -Force 'D:\DevelopEnviroment\keys\biel-life-camp'

& 'C:\Program Files\OpenSSL-Win64\bin\openssl.exe' genpkey `
  -algorithm RSA -pkeyopt rsa_keygen_bits:3072 `
  -out 'D:\DevelopEnviroment\keys\biel-life-camp\auth-private.pem'

& 'C:\Program Files\OpenSSL-Win64\bin\openssl.exe' pkey `
  -in 'D:\DevelopEnviroment\keys\biel-life-camp\auth-private.pem' `
  -pubout `
  -out 'D:\DevelopEnviroment\keys\biel-life-camp\auth-public.pem'
```

Spring 配置接收的是资源位置，不是 PEM 正文。Windows 必须带 `file:` 并使用正斜杠：

```text
AUTH_PRIVATE_KEY=file:D:/DevelopEnviroment/keys/biel-life-camp/auth-private.pem
AUTH_PUBLIC_KEY=file:D:/DevelopEnviroment/keys/biel-life-camp/auth-public.pem
```

`AUTH_ALLOW_EPHEMERAL_KEYS=true` 只允许用于自动化测试。临时密钥每次重启都会变化，不能用于 Gateway
联调或任何部署环境。

## 4. 本地连接 ACK 基础设施

在 `server/` 目录执行：

```powershell
.\scripts\Connect-AckNacosDev.ps1
.\scripts\Connect-AckRedisDev.ps1
```

第一个脚本维护本机 `8848/9848/18000` 到 ACK Nacos 的通道，并为当前 PowerShell 注入
`SPRING_PROFILES_ACTIVE=nacos`、Namespace `dev`、Group `LIFECAMP` 和受控客户端凭据。第二个脚本把
ACK 开发 Redis 映射到 `127.0.0.1:6379` 并注入 ACL 环境变量。

IDEA 不会继承另一个已打开 PowerShell 的进程变量。推荐完全退出 IDEA，在执行连接脚本的同一
PowerShell 中重新启动 IDEA；或者在本地 `SystemServiceApplication` Run Configuration 中配置相同
环境变量，并把 `.\scripts\Start-AckNacosTunnel.ps1` 设为 Before launch。

## 5. Nacos 配置约定

当前开发环境使用：

| 项目 | 值 |
|---|---|
| ACK 集群/命名空间 | `biel-ai` / `biel-life-camp` |
| Nacos Namespace | `dev` |
| Group | `LIFECAMP` |
| system-service Data ID | `system-service.yaml` |
| gateway Data ID | `gateway.yaml` |

仓库中的可审查配置基线是：

- [`gateway.yaml`](nacos/dev/gateway.yaml)：显式路由、CORS、HTTP/LB 超时与缓存、监控、文档和网关认证参数。
- [`system-service.yaml`](nacos/dev/system-service.yaml)：数据源/Redis 引用、上传限制、监控、OSS、EHR、认证、微信和 XXL-Job 参数。

激活 `nacos` Profile 后使用非 `optional` 的 `spring.config.import`。Data ID 不存在、无权限或 Nacos
不可达时应用直接启动失败，避免 Gateway 在无路由或弱认证状态下运行。仓库基线发布到 Nacos 后，
以 Nacos 的配置历史作为配置回滚点；运行参数变更后仍应滚动重启并执行健康检查与冒烟测试。

`system-service.yaml` 只保存非秘密配置和环境变量引用，例如：

```yaml
platform:
  auth:
    enabled: true
    private-key-location: ${AUTH_PRIVATE_KEY:}
    public-key-location: ${AUTH_PUBLIC_KEY:}
    allow-ephemeral-keys: false
    admin-password:
      enabled: true
      rate-limit-enabled: true
      max-attempts: 5
      window: 15m
  ehr:
    enabled: false
```

本地运行时，环境变量优先级高于 Nacos。Nacos 中不要把密钥位置配置成裸 Windows 路径；正确格式是
`file:D:/.../auth-private.pem`。ACK 中使用容器路径，例如
`file:/run/secrets/lifecamp-auth/auth-private.pem`。

数据库及认证链路至少需要按实际启用范围注入以下 Secret 环境变量：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_USERNAME
REDIS_PASSWORD
AUTH_IDENTIFIER_PEPPER
AUTH_IDENTITY_ENCRYPTION_KEY
AUTH_TOKEN_PEPPER
AUTH_GATEWAY_SERVICE_TOKEN
EHR_ESB_AUTH
WECHAT_APP_SECRET
XXL_JOB_ACCESS_TOKEN
```

OSS 不在 Spring/Nacos 中配置 AccessKey 字段。SDK 默认凭证链从运行环境读取；本地联调使用
`ALIBABA_CLOUD_ACCESS_KEY_ID` 和 `ALIBABA_CLOUD_ACCESS_KEY_SECRET`，临时凭据同时注入
`ALIBABA_CLOUD_SECURITY_TOKEN`。ACK 优先配置 RRSA 的 `ALIBABA_CLOUD_ROLE_ARN`、
`ALIBABA_CLOUD_OIDC_PROVIDER_ARN`、`ALIBABA_CLOUD_OIDC_TOKEN_FILE` 和
`ALIBABA_CLOUD_ROLE_SESSION_NAME`，避免长期 AccessKey。

## 6. IDEA 启动 system-service

Run Configuration 至少确认：

```text
Active profiles: nacos
AUTH_PRIVATE_KEY=file:D:/DevelopEnviroment/keys/biel-life-camp/auth-private.pem
AUTH_PUBLIC_KEY=file:D:/DevelopEnviroment/keys/biel-life-camp/auth-public.pem
```

数据库、Nacos、Redis、EHR 和认证秘密值通过本地环境变量或受控 Secret 注入。启动成功的关键日志：

```text
Tomcat started on port 8081 (http)
Started SystemServiceApplication
```

验证：

```powershell
Invoke-RestMethod 'http://127.0.0.1:8081/actuator/health/readiness'
Invoke-RestMethod 'http://127.0.0.1:8081/v3/api-docs'
```

首次 EHR 人员同步不会在应用启动时自动执行。准备完成后由管理员携带唯一 `Idempotency-Key` 调用
`POST /api/system/v1/ehr-sync-runs`，具体见
[`system_db_ehr_full_sync_runbook.md`](../services/system-service/src/main/resources/db/reference/system_db_ehr_full_sync_runbook.md)。

## 7. 常见启动异常

| 错误 | 根因 | 处理 |
|---|---|---|
| `RSA key location is required` | 未传入 `AUTH_PRIVATE_KEY` 或 `AUTH_PUBLIC_KEY` | 在实际启动进程/IDEA Run Configuration 注入两项变量 |
| `Unable to load RSA key from ...` | 路径缺少 `file:`、文件不存在或 PEM 格式错误 | 使用 `file:D:/...`，检查文件并重新生成 PKCS#8/X.509 PEM |
| `AUTH_DISABLED` / dependency unavailable | 认证依赖未完整配置 | 从最底层 `Caused by` 检查 RSA、pepper、AES、Redis、数据库 |
| Nacos 401/403 或落到 `tenant=public` | 客户端凭据错误或 Namespace 没有注入 | 重新运行连接脚本，确认 `NACOS_NAMESPACE=dev` |
| `url attribute is not specified` | Nacos 未加载数据库配置，且本地未注入 `DB_URL` | 先修复 Nacos，再确认数据库环境变量 |
| 8081 端口占用 | 旧进程仍运行 | 停止 IDEA 旧进程或调整 `SERVER_PORT` |

排错必须保留最底层 `Caused by`。上层 `UnsatisfiedDependencyException` 通常只是依赖链结果，不是根因。

## 8. Gateway 密钥关系

外部 JWT 与内部身份 JWS 必须使用两套 RSA 密钥：

| 变量 | 使用方 | 用途 |
|---|---|---|
| `AUTH_PRIVATE_KEY` | system-service | 签发外部访问 JWT |
| `AUTH_PUBLIC_KEY` | system-service、Gateway | system-service 构造编码器；Gateway 验证外部 JWT |
| `INTERNAL_IDENTITY_PRIVATE_KEY` | Gateway | 签发面向目标服务的短时内部 JWS |
| `INTERNAL_IDENTITY_PUBLIC_KEY` | Gateway、业务服务 | Gateway 构造编码器；业务服务验证内部 JWS |

轮换时先分发新公钥并保留旧公钥兼容窗口，再切换签名私钥，最后在最长令牌有效期后移除旧公钥。
当前实现以单个 key location 配置为主，正式自动轮换与多公钥兼容仍待实现。

## 9. ACK 部署顺序

1. 在独立迁移 Job 中执行已评审的 Flyway，确认数据库备份和回滚路径。
2. 构建不可变 JAR 和镜像，以镜像 digest 发布，禁止使用 `latest`。
3. 创建最小权限 Kubernetes Secret：数据库/Redis/Nacos/EHR/微信凭据及 pepper/AES/Gateway token。
4. 将两套 RSA 密钥分别作为 Secret 文件挂载到只读目录，并通过环境变量传入 `file:/...` 位置。
5. 发布 Nacos 非秘密配置，核对 Namespace、Group、Data ID 和配置版本。
6. 先部署 system-service，再部署 Gateway 和一个启用内部 JWS 验签的业务服务。
7. 检查 startup/readiness/liveness、Prometheus 指标、结构化日志和 Nacos 实例状态。
8. 完成登录、刷新、会话撤销、权限版本、Gateway 转发和业务服务二次验签冒烟。
9. 人工执行首次全量人员同步并核对数据库结果；通过前不开放小程序认证。

当前 [`platform-service` Helm Chart](helm/platform-service) 同时支持 `envFrom.secretRef` 和只读 RSA
Secret 文件卷挂载。Pod 固定以 UID/GID `10001` 运行，RSA 文件以 `0440` 挂载并通过 `fsGroup=10001`
读取；不得把 PEM 正文改成普通环境变量或写入 Nacos。

### ACK 开发环境当前发布

- Namespace：`biel-life-camp`；Helm release：`system-service`、`gateway`。
- ACK 服务注册到 Nacos `ACK` cluster，并显式启用 Nacos LoadBalancer；本机开发实例保留在 `DEFAULT`
  cluster，避免网关误路由到本机地址。
- system-service：2 个副本，HPA 2–4；镜像 digest
  `sha256:f9cd0db839f4ac9c60c50559ed10779fcf9ade822231751cdafd769ebee2cc72`。
- Gateway：2 个副本，HPA 2–4；镜像 digest
  `sha256:9c93881f72acdcc0a89afdcea33084f01bde40cda520f693320fe6278ed5ae93`。
- 环境 values：[`values-dev-system-service.yaml`](helm/platform-service/values-dev-system-service.yaml) 和
  [`values-dev-gateway.yaml`](helm/platform-service/values-dev-gateway.yaml)。
- 数据库现有结构已只读核对 V1–V6，并建立 Flyway 版本 6 基线；后续迁移由独立纯 Flyway Job 执行。
- OSS 头像存储已在 ACK 开发环境启用：私有 Bucket `biel-life-camp-test`、Region `cn-shenzhen`、
  公网签名 Endpoint `oss-cn-shenzhen.aliyuncs.com`、对象前缀 `profiles/avatars`。昵称修改、头像上传、
  签名下载、头像替换和旧对象删除已经通过隔离测试用户完成端到端验证。EHR 同步仍显式关闭。
- OSS AccessKey 只存在于 `system-service-oss-dev-secret`，不进入 Nacos 或仓库。当前 ACK 未启用 RRSA，
  且操作账号没有 RAM 管理权限，因此开发环境暂时使用长期 AccessKey；应由 RAM 管理员创建仅允许
  `biel-life-camp-test/profiles/avatars/*` Put/Get/Delete 的专用身份，或启用 RRSA 后替换并轮换当前密钥。
- Gateway 到 system-service 的鉴权调用使用集群内 HTTP、服务令牌和 NetworkPolicy；system-service
  保持 ClusterIP，不直接暴露公网。
- 小程序 API 入口为 `https://lifecamp-test.bielcrystal.com`，通过 Nginx Ingress 复用现有公网 NLB，
  HTTP 强制跳转 HTTPS，TLS 使用覆盖 `*.bielcrystal.com` 的证书。DNS 管理员需创建：
  `lifecamp-test.bielcrystal.com CNAME nlb-pkheesf2fnmqr7yil7.cn-shenzhen.nlb.aliyuncsslb.com`。
- 当前通配符证书有效期至 2026-11-22；证书续签后需同步更新 `biel-life-camp` Namespace 中的
  `20261122bielcrystal.com` TLS Secret。
- DNS 生效后，在微信小程序后台把 `https://lifecamp-test.bielcrystal.com` 加入 `request` 合法域名；
  头像通过 `wx.uploadFile` 上传时还需把该域名加入 `uploadFile` 合法域名。签名头像 URL 使用
  `https://biel-life-camp-test.oss-cn-shenzhen.aliyuncs.com`，需将此 OSS 域名加入 `downloadFile`
  合法域名。

查看和回滚 Helm 版本：

```powershell
helm history system-service -n biel-life-camp
helm history gateway -n biel-life-camp
helm rollback system-service <revision> -n biel-life-camp --wait
helm rollback gateway <revision> -n biel-life-camp --wait
```

## 10. 回滚与停机

- 配置回滚：恢复上一版 Nacos 配置并重启受影响服务；关键密钥配置不依赖热刷新。
- 应用回滚：使用上一不可变镜像 digest，数据库迁移优先采用向前修复，禁止直接破坏性回滚。
- 密钥回滚：恢复上一私钥和兼容公钥集合；确认已签发 Token 的最长有效窗口。
- 人员同步失败：保留系统日志和运行结果，不自动重试整批，不执行快照缺失人员的离职处理。
- 停机：先从入口摘流，等待在途请求结束，再停止 Pod；不得通过删除数据库或 Redis 数据处理启动故障。

## 11. 发布检查清单

- [ ] JDK、Maven、镜像 digest 和依赖版本与仓库基线一致
- [ ] Nacos Namespace/Group/Data ID 正确，配置中没有明文秘密
- [ ] RSA 私钥仅存在于受控本机或 Secret/KMS，文件权限已检查
- [ ] 数据库迁移、备份、Redis ACL 和网络访问验证通过
- [ ] startup/readiness/liveness、指标、日志和告警可用
- [ ] system-service、Gateway、业务服务认证链路端到端通过
- [ ] 首次 EHR 全量同步经人工核对，失败人员只记录脱敏日志
- [ ] 回滚版本、配置版本和责任人明确
