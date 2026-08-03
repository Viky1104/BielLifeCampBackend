# 伯恩生活营地后端运行与部署手册

更新时间：2026-07-31

本文给出当前代码基线的本地运行、Nacos 配置、认证密钥准备和 ACK 部署检查步骤。当前已经验证
`system-service` 可在 Windows、JDK 21、`nacos` Profile 和固定 RSA 密钥下启动；Gateway 与业务服务
尚未部署到 ACK，Helm 模板仍是待环境化的基线，不能直接视为生产发布方案。

## 1. 环境边界

| 环境 | 配置中心 | 密钥与凭据 | 当前状态 |
|---|---|---|---|
| Windows 本地开发 | ACK Nacos `dev/LIFECAMP`，通过本机隧道访问 | IDEA 进程环境变量与本机 PEM 文件 | `system-service` 启动已验证 |
| ACK 开发环境 | `nacos.biel-life-camp.svc.cluster.local:8848` | Kubernetes Secret 挂载文件及 Secret 环境变量 | Nacos、Redis 已部署，业务服务未部署 |
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

当前 [`platform-service` Helm Chart](helm/platform-service) 只支持 `envFrom.secretRef`，尚未提供 RSA
Secret 文件卷挂载。认证开启前必须先补充并验证只读 Secret volume/volumeMount；不得把 PEM 正文改成
普通环境变量或写入 Nacos 来绕过该缺口。

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
