# 用户资料与头像 OSS 运维手册

## 1. 文档目的

本文用于指导 `system-service` 用户资料接口和私有头像 OSS 能力的上线、配置、验收、监控、故障处理与回滚。

适用接口：

- `GET /api/system/v1/me`
- `PATCH /api/system/v1/me/profile`
- `POST /api/system/v1/me/profile/avatar`

本期只提供昵称和头像维护，不启用昵称审核、头像审核或内容审核流程。

## 2. 运行机制

### 2.1 数据流

1. 昵称保存在现有 `sys_wechat_profile.nickname`。
2. 头像文件保存在私有 OSS Bucket。
3. 数据库只保存 `avatar_object_key`，不保存带签名参数的临时 URL。
4. `GET /me` 和头像上传响应按需生成默认一小时有效的签名 URL。
5. 签名失败不会阻止用户登录或查询资料，接口会返回 `avatarUrl: null` 并记录告警。

### 2.2 一致性与补偿

头像上传和 OSS 签名在数据库事务之外执行，数据库只承担短事务：

- OSS 上传或签名失败：不更新数据库，返回 `503 PROFILE_STORAGE_UNAVAILABLE`。
- OSS 成功、数据库失败：尽力删除刚上传的新对象，数据库继续引用旧头像。
- 数据库提交成功：再尽力删除被替换的旧对象。
- 旧对象删除失败：新头像仍然生效，日志记录清理失败，后续按孤儿对象流程处理。

## 3. 上线前检查

| 检查项 | 要求 | 阻断级别 |
| --- | --- | --- |
| 数据库备份 | 上线窗口前完成 RDS 快照或可恢复备份 | 必须 |
| Flyway V6 | `V6__user_profile_avatar_storage.sql` 已评审 | 必须 |
| OSS Bucket | 与应用部署地域一致或网络可达，保持私有读写 | 必须 |
| Endpoint 和 Region | 与 Bucket 实际地域完全一致 | 必须 |
| RAM 身份 | 已通过 ACK RRSA、ECS/ECI RAM 角色或受控本地凭证授权 | 必须 |
| 最小权限 | 仅允许操作指定 Bucket 的头像前缀 | 必须 |
| 网络 | Pod 能通过 HTTPS 访问 OSS Endpoint | 必须 |
| 系统时间 | 节点开启 NTP，同步误差处于可接受范围 | 必须 |
| 微信域名 | OSS 下载域名已加入小程序合法下载域名 | 必须 |
| 网关上传限制 | Ingress/Gateway 请求体上限不低于 3 MB | 必须 |
| 验收图片 | 准备小于 2 MB 的 JPEG、PNG、WebP 测试文件 | 建议 |

### 3.1 Bucket 要求

- Bucket ACL 必须为私有，禁止为了头像展示改成公共读。
- 建议单独使用用户资料 Bucket，或至少使用独立前缀 `profiles/avatars/`。
- 应用通过后端上传，当前方案不需要为前端开放匿名上传权限。
- 签名 URL 可能出现在客户端网络请求中，应按临时敏感凭证管理，不写入业务数据库和普通访问日志。

### 3.2 微信小程序域名

签名 URL 通常使用以下形式的域名：

```text
https://<bucket>.<oss-endpoint>/<object-key>?<signature-query>
```

必须在微信小程序后台将实际 HTTPS 域名配置为合法下载域名。签名 URL 默认一小时失效属于正常行为，客户端应重新调用 `GET /api/system/v1/me` 获取新地址，不能永久缓存旧地址。

## 4. RAM 最小权限

以下策略示例仅允许操作指定 Bucket 的头像前缀。部署前替换 Bucket 和前缀，禁止直接使用通配全部 OSS 资源的管理员权限。

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "oss:PutObject",
        "oss:GetObject",
        "oss:DeleteObject"
      ],
      "Resource": [
        "acs:oss:*:*:<bucket-name>/profiles/avatars/*"
      ]
    }
  ]
}
```

推荐凭证来源顺序：

1. ACK 使用 RRSA，将 RAM 角色绑定到 `system-service` 的 ServiceAccount。
2. ECS/ECI 使用实例 RAM 角色。
3. 本地联调使用受控环境变量，禁止把 AccessKey 写入 Git、Nacos 明文或镜像。

默认凭证链可识别的常用环境变量包括：

```text
ALIBABA_CLOUD_ROLE_ARN
ALIBABA_CLOUD_OIDC_PROVIDER_ARN
ALIBABA_CLOUD_OIDC_TOKEN_FILE
ALIBABA_CLOUD_ROLE_SESSION_NAME
ALIBABA_CLOUD_ACCESS_KEY_ID
ALIBABA_CLOUD_ACCESS_KEY_SECRET
ALIBABA_CLOUD_SECURITY_TOKEN
```

生产环境优先使用前三项所代表的 RRSA/OIDC 方式，不建议配置长期 AccessKey。

## 5. 应用配置

### 5.1 环境变量

| 环境变量 | 示例 | 是否必须 | 说明 |
| --- | --- | --- | --- |
| `PROFILE_STORAGE_ENABLED` | `true` | 是 | 开启头像 OSS；默认 `false` |
| `PROFILE_OSS_ENDPOINT` | `oss-cn-shenzhen.aliyuncs.com` | 是 | OSS Endpoint |
| `PROFILE_OSS_REGION` | `cn-shenzhen` | 是 | Signature V4 使用的 Region |
| `PROFILE_OSS_BUCKET` | `biel-life-camp-private` | 是 | 私有 Bucket 名称 |
| `PROFILE_OSS_AVATAR_PREFIX` | `profiles/avatars` | 否 | 对象前缀，默认 `profiles/avatars` |
| `PROFILE_OSS_SIGNED_URL_TTL` | `1h` | 否 | 签名 URL 有效期，默认一小时 |

修改以上配置后必须重启 `system-service`。当 `PROFILE_STORAGE_ENABLED=true` 时，Endpoint、Region、Bucket 任一缺失都会导致应用启动失败。

### 5.2 Kubernetes 配置示例

非敏感配置可放 ConfigMap 或受控 Nacos 配置；凭证由 RRSA 或 Secret 注入，不能写入 ConfigMap。

```yaml
env:
  - name: PROFILE_STORAGE_ENABLED
    value: "true"
  - name: PROFILE_OSS_ENDPOINT
    value: "oss-cn-shenzhen.aliyuncs.com"
  - name: PROFILE_OSS_REGION
    value: "cn-shenzhen"
  - name: PROFILE_OSS_BUCKET
    value: "<private-bucket-name>"
  - name: PROFILE_OSS_AVATAR_PREFIX
    value: "profiles/avatars"
  - name: PROFILE_OSS_SIGNED_URL_TTL
    value: "1h"
```

## 6. 数据库变更

### 6.1 迁移内容

迁移文件：

```text
services/system-service/src/main/resources/db/migration/
V6__user_profile_avatar_storage.sql
```

迁移在现有 `sys_wechat_profile` 境加：

```sql
avatar_object_key VARCHAR(512) NULL
```

该变更为向后兼容的新增列，不创建第二套资料表，不修改现有昵称和历史头像 URL。

### 6.2 执行要求

生产环境默认 `FLYWAY_ENABLED=false`，应通过独立数据库迁移 Job 或受控 DBA 流程执行。不能依赖应用启动自动迁移。

正确上线顺序：

1. 创建 RDS 快照或备份。
2. 执行 V6。
3. 验证字段存在。
4. 部署新版本应用，首次可保持 `PROFILE_STORAGE_ENABLED=false`。
5. 验证 `/me` 和昵称接口。
6. 注入 OSS/RAM 配置并开启 `PROFILE_STORAGE_ENABLED=true`。
7. 滚动重启后执行头像功能验收。

禁止先部署新应用再执行 V6。新版本 `/me` 会查询 `avatar_object_key`，字段缺失时会产生数据库异常。

### 6.3 迁移后检查

```sql
SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'sys_wechat_profile'
  AND COLUMN_NAME = 'avatar_object_key';
```

预期结果：一行，类型为 `varchar`，长度为 `512`，允许为空。

业务数据概览：

```sql
SELECT COUNT(*) AS profile_count,
       SUM(CASE WHEN avatar_object_key IS NOT NULL THEN 1 ELSE 0 END)
           AS object_avatar_count,
       SUM(CASE WHEN avatar_url IS NOT NULL THEN 1 ELSE 0 END)
           AS legacy_avatar_count
FROM sys_wechat_profile;
```

禁止在工单、群聊和普通日志中输出完整 `avatar_object_key` 或签名 URL。

## 7. 发布步骤

### 7.1 灰度发布

1. 确认数据库 V6 已完成。
2. 使用 `PROFILE_STORAGE_ENABLED=false` 部署一个灰度 Pod。
3. 检查启动日志、数据库连接和 `/actuator/health/readiness`。
4. 验证 `GET /me` 新增字段存在，未上传头像的用户返回 `avatarUrl: null`。
5. 验证昵称更新和清空。
6. 配置 OSS/RAM 后将灰度 Pod 的 `PROFILE_STORAGE_ENABLED` 改为 `true` 并重启。
7. 上传测试头像，检查响应、数据库对象键和 OSS 对象。
8. 验证新签名 URL 可访问，且微信小程序能正常显示。
9. 扩大到全部 Pod，观察至少一个签名 URL 有效周期。

`/actuator/health/readiness` 当前不主动访问 OSS，因此健康检查通过不代表 OSS 一定可用，必须执行头像上传和读取的功能性验证。

### 7.2 健康检查

```text
GET /actuator/health/readiness
GET /actuator/health/liveness
GET /actuator/prometheus
```

发布期间关注：

- Pod 重启次数和启动失败原因。
- HTTP 4xx、5xx，尤其是资料接口的 413、422、503。
- 数据库连接池和 SQL 异常。
- OSS 请求错误、RAM 拒绝、签名错误和网络超时。

## 8. 功能验收

以下请求应通过网关地址执行，不能绕过网关直接伪造内部身份。示例中的令牌必须通过受控方式注入，不能写入脚本仓库。

```powershell
$profileBaseUrl = 'https://<gateway-domain>'
$profileAccessToken = '<temporary-access-token>'
$profileHeaders = @{
    Authorization = "Bearer $profileAccessToken"
}
```

### 8.1 查询当前资料

```powershell
Invoke-RestMethod `
    -Method Get `
    -Uri "$profileBaseUrl/api/system/v1/me" `
    -Headers $profileHeaders
```

确认响应包含：

```text
nickname
avatarUrl
organizationId
organizationName
positionName
```

组织主数据尚未映射时，`organizationId` 保持兼容值 `"0"`；这不应阻止资料查询。

### 8.2 更新昵称

```powershell
$nicknameBody = @{
    nickname = '小营友'
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Patch `
    -Uri "$profileBaseUrl/api/system/v1/me/profile" `
    -Headers $profileHeaders `
    -ContentType 'application/json' `
    -Body $nicknameBody
```

清空昵称：

```powershell
Invoke-RestMethod `
    -Method Patch `
    -Uri "$profileBaseUrl/api/system/v1/me/profile" `
    -Headers $profileHeaders `
    -ContentType 'application/json' `
    -Body '{"nickname":null}'
```

验收规则：非空昵称去除首尾空白后包含 1～32 个 Unicode 字符，不能包含控制字符。

### 8.3 上传头像

使用 `curl.exe` 可兼容 Windows PowerShell 5：

```powershell
$profileAvatarPath = 'D:\temp\profile-avatar.png'

curl.exe `
    --request POST `
    "$profileBaseUrl/api/system/v1/me/profile/avatar" `
    --header "Authorization: Bearer $profileAccessToken" `
    --form "avatar=@$profileAvatarPath;type=image/png"
```

验收要求：

1. JPEG、PNG、WebP 小文件分别成功一次。
2. 响应包含非空 `avatarUrl` 和 `updatedAt`。
3. `avatarUrl` 使用 HTTPS，访问后返回正确图片。
4. 数据库 `avatar_object_key` 非空，但不包含签名查询参数。
5. OSS 对象位于配置前缀下。
6. 再次上传后新头像生效；旧对象删除失败时出现 cleanup failed 日志，并按孤儿对象流程处理。
7. 大于 2 MB 的文件返回 `413 PROFILE_AVATAR_TOO_LARGE`。
8. 伪造扩展名或损坏图片返回 `422 PROFILE_AVATAR_INVALID`。

## 9. 监控与告警

### 9.1 建议指标

| 指标 | 建议告警条件 | 说明 |
| --- | --- | --- |
| 头像上传 503 比例 | 5 分钟内连续出现或比例超过 1% | OSS、凭证、网络或配置异常 |
| 资料接口 5xx | 5 分钟内非零并持续增长 | 应用或数据库异常 |
| `PROFILE_AVATAR_TOO_LARGE` | 突然明显升高 | 客户端压缩策略异常 |
| `PROFILE_AVATAR_INVALID` | 突然明显升高 | 客户端格式或恶意请求异常 |
| OSS 4xx | 连续出现 | RAM 权限、Region、Endpoint、时间漂移 |
| OSS 5xx/超时 | 连续出现 | OSS 或网络异常 |
| Bucket 容量和对象数 | 超过容量预算或异常增长 | 旧对象清理失败或滥用 |

### 9.2 关键日志

```text
Profile avatar signing unavailable, employeeId=...
Profile avatar cleanup failed, employeeId=..., reason=...
```

第一类日志表示 `/me` 仍会成功，但 `avatarUrl` 为 `null`。第二类日志表示业务更新已经完成，但可能产生孤儿对象。

日志和链路系统禁止记录：

- 完整签名 URL。
- OSS AccessKey、SecurityToken、OIDC Token。
- 上传文件内容。
- 用户提交的原始头像二进制。

## 10. 故障排查

| 现象或错误 | 常见原因 | 排查与处理 |
| --- | --- | --- |
| 启动时报 OSS 配置缺失 | 开启存储但 Endpoint、Region、Bucket 为空 | 补齐配置并重启 |
| `503 PROFILE_STORAGE_UNAVAILABLE` | 存储未开启、凭证链失败、网络不通、RAM 拒绝、Endpoint/Region 错误 | 依次检查开关、Pod 环境变量、RRSA、HTTPS 连通性、RAM 策略和地域 |
| `/me` 成功但 `avatarUrl=null` | 无头像、存储关闭或签名失败 | 查询对象键是否存在，再检索 signing unavailable 告警 |
| `422 PROFILE_AVATAR_INVALID` | 空文件、损坏图片、非 JPEG/PNG/WebP、尺寸或像素异常 | 用标准图片重试，不能只修改扩展名或 Content-Type |
| `413 PROFILE_AVATAR_TOO_LARGE` | 文件超过 2 MB | 客户端压缩后重试；不要提高限制绕过业务约束 |
| `409 PROFILE_IDENTITY_MISSING` | 当前员工没有有效的 WECHAT 外部身份 | 检查登录绑定和 `sys_external_identity`，不要人工伪造资料记录 |
| 500 且提示 `avatar_object_key` 不存在 | 新应用先于 V6 部署 | 立即应用 V6，或回滚应用；不要临时修改 Mapper |
| OSS 返回 AccessDenied | RAM 动作或资源前缀不匹配 | 检查角色绑定、Bucket 名和策略 Resource |
| OSS 返回签名错误 | Region/Endpoint 不一致、节点时间漂移、临时凭证过期 | 核对地域、NTP 和凭证链刷新 |
| 浏览器可访问但小程序不显示 | 微信合法下载域名未配置 | 将实际 OSS HTTPS 域名加入小程序后台白名单 |
| 签名 URL 一段时间后失效 | 默认一小时 TTL 到期 | 重新调用 `/me` 获取新 URL，属于正常行为 |
| 旧头像对象持续增加 | 删除权限缺失或 OSS 删除失败 | 检索 cleanup failed 日志并执行孤儿对象核对 |

## 11. 孤儿对象处理

头像对象采用随机键，清理失败不会破坏新头像，但会增加 OSS 存储量。建议每天或每周执行只读核对：

1. 导出指定 OSS 前缀下的对象清单和创建时间。
2. 导出 `sys_wechat_profile.avatar_object_key` 的非空集合。
3. 只将“OSS 存在、数据库不存在、且创建时间超过 24 小时”的对象列为待清理对象。
4. 先生成审计清单并人工复核，再批量删除。
5. 禁止直接删除数据库仍在引用的对象。

不要以签名 URL 作为比对键；签名参数会变化，只能使用数据库中的对象键。

## 12. 回滚方案

### 12.1 功能降级

最快降级方式：

```text
PROFILE_STORAGE_ENABLED=false
```

修改后滚动重启 `system-service`。结果：

- 登录、刷新令牌、退出登录不受影响。
- `/me` 继续返回资料和授权信息；已有对象头像返回 `avatarUrl: null`。
- 昵称查询和更新继续可用。
- 新头像上传返回 `503 PROFILE_STORAGE_UNAVAILABLE`。

### 12.2 应用版本回滚

1. 先关闭 `PROFILE_STORAGE_ENABLED`。
2. 回滚 `system-service` 应用版本。
3. 保留 V6 新增列，不执行删除列回滚。
4. 保留已上传 OSS 对象和数据库对象键，等待新版本恢复后继续使用。

V6 是向后兼容新增列，旧应用会忽略该字段。紧急回滚时删除列会造成不可恢复的头像引用丢失，因此禁止执行 `DROP COLUMN avatar_object_key`。

### 12.3 Bucket 或 RAM 回滚

- 可以撤销应用 RAM 权限实现强制停用，但必须先关闭应用存储开关，避免持续产生 503 和告警。
- 禁止把 Bucket 改为公共读作为故障规避方案。
- 禁止直接删除整个头像前缀。

## 13. 发布验收清单

- [ ] RDS 快照或备份已完成。
- [ ] V6 已执行且字段检查通过。
- [ ] 私有 Bucket、Endpoint、Region 已核对。
- [ ] RAM 最小权限和 RRSA/实例角色已生效。
- [ ] Pod 能通过 HTTPS 访问 OSS。
- [ ] 微信小程序合法下载域名已配置。
- [ ] Ingress/Gateway 请求体上限不低于 3 MB。
- [ ] `system-service` readiness/liveness 正常。
- [ ] `/me` 新增字段正常。
- [ ] 昵称更新和清空正常。
- [ ] JPEG、PNG、WebP 上传正常。
- [ ] 超限和非法图片错误码正确。
- [ ] 数据库只保存对象键，不保存签名 URL。
- [ ] 小程序能够显示签名头像。
- [ ] 替换头像后旧对象清理正常。
- [ ] 资料接口 5xx、OSS 错误和 cleanup failed 日志无异常。
- [ ] 回滚开关和负责人已经确认。

## 14. 相关文件

- `services/system-service/src/main/resources/application.yml`
- `services/system-service/src/main/resources/db/migration/V6__user_profile_avatar_storage.sql`
- `services/system-service/src/main/resources/mapper/system/ProfileMapper.xml`
- `services/system-service/src/main/java/com/biel/lifecamp/system/config/ProfileConfiguration.java`
- `services/system-service/src/main/java/com/biel/lifecamp/system/service/impl/ProfileServiceImpl.java`
