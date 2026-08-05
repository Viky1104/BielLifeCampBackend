# EHR 人员首次全量同步执行手册

## 当前结论

代码和数据库结构已具备全量同步能力，但在取得并核对“真实 EHR 响应样例、人员覆盖范围、ESB 鉴权信息和稳定手机号摘要密钥”前，不建议直接触发生产数据同步。

首次同步采用“在职人员完整快照”语义：快照中的人员新增或更新，当前库中处于在职但不在快照中的人员会被标记为离职并禁用账号。因此，接口覆盖范围是本次上线的最高风险项。

## 触发前需要补齐的信息

| 类别 | 需要确认的信息 | 当前代码默认值或要求 | 阻断级别 |
|---|---|---|---|
| EHR 地址 | ACK 内访问的 ESB URL、DNS、端口及网络白名单 | `http://esb.biel.com/api/esb/EHR_GetData` | 必须 |
| ESB 鉴权 | `EsbAuth` 的正式值及 Secret 保存位置 | `EHR_ESB_AUTH`，不可为空 | 必须 |
| 调用头 | `sourceSystem`、`targetSystem`、`serviceName`、`routeId` | `WeChat`、`EHR-Micro`、`ehr-micro-getpsninfo`、`HZ` | 必须 |
| 查询语义 | `state=N` 是否代表本系统所需的全部在职人员 | 当前请求固定携带 `state=N` | 必须 |
| 人员范围 | 是否包含所有公司、园区及越南人员，是否排除派遣工 | 参考旧任务曾额外合并越南 EHR 1.0 数据 | 必须 |
| 分页响应 | 脱敏后的第一页和最后一页 JSON 样例 | 要求 `code=0`、`data.totalRecords`、`data.totalPages`、`data.data[]` | 必须 |
| 并发限额 | ESB 允许的单实例分页并发数及整体 QPS | 默认并发 `4`，允许范围 `1～16` | 必须 |
| 人数上限 | 全部在职人员的合理峰值 | 默认最多 `200000` 人，超出立即终止 | 必须 |
| 唯一标识 | `pkPsndoc` 是否全员存在且永久稳定，工号是否可能复用或变更 | `pkPsndoc`、`code`、`name` 均为必填且全快照唯一 | 必须 |
| 手机号 | 空手机号、短号、境外号码和异常格式的处理口径 | 空值允许；非法格式会隔离当前人员并记录问题 | 必须 |
| 直属上级 | `jobglbdef29` 返回的是上级工号还是其他编码 | 当前按上级工号解析 | 必须 |
| 字段口径 | 生日、性别、职级、职称、职位、岗位的实际样例 | 见下方字段清单 | 建议 |
| 数据库 | RDS URL、账号、密码及 `camp_system_test_db` 权限 | 需要读写 15 张系统表 | 必须 |
| 摘要密钥 | 稳定的 `AUTH_IDENTIFIER_PEPPER` | 至少 32 个字符，首次同步后不得更换 | 必须 |
| Nacos | 应用配置命名空间、Group、Data ID、账号密码 | Data ID `system-service.yaml`，Group 默认 `LIFECAMP` | 必须 |
| 触发链路 | 内网直连地址或 XXL-JOB 执行器 | 当前 XXL-JOB 默认关闭 | 必须 |

## EHR 字段清单

至少提供一份脱敏响应样例，并确认以下字段的类型、最大长度、空值比例和业务含义：

| EHR 字段 | 本地字段 | 校验或处理 |
|---|---|---|
| `pkPsndoc` | `ehr_person_id` | 必填，全快照唯一 |
| `code` | `employee_no` | 必填，全快照唯一 |
| `name` | `display_name` | 必填 |
| `mobile` | `mobile_hash` | 规范化后 HMAC，不保存明文 |
| `birthdate` | `birthday` | 取前 10 位并按 `yyyy-MM-dd` 解析 |
| `sex` | `gender_code`、`gender_source_value` | 映射男/女，未知值保存为 `UNKNOWN` |
| `glbdef8` | `email` | 可空 |
| `pkDeptCode`、`pkDeptName` | 主组织编码、名称 | 当前只保存组织快照，不生成组织树 |
| `jobglbdef21code`、`jobglbdef21name` | 法人公司编码、名称 | 名称兼容读取 `jobglbdef21` |
| `jobglbdef29` | `supervisor_employee_no` | 按员工工号解析本地上级 ID |
| `jobglbdef27` | `job_grade` | 职级 |
| `titletechpost` | `professional_title` | 职称 |
| `pkJobcode`、`pkJobname` | `job_code`、`job_name` | 职位或职务 |
| `pkPostCode`、`pkPostName` | `position_code`、`position_name` | 岗位 |
| `begindate`、`enddate` | 入职、离职日期 | 非法日期当前会降级为空 |
| `creationtime`、`modifiedtime` | EHR 创建、修改时间 | 要求 `yyyy-MM-dd HH:mm:ss` |

## Nacos 配置模板

正式密钥应放 Kubernetes Secret，不要直接写入 Nacos 明文配置。Nacos 配置可引用容器环境变量：

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

platform:
  auth:
    enabled: ${AUTH_ENABLED:false}
    identifier-pepper: ${AUTH_IDENTIFIER_PEPPER}
    private-key-location: ${AUTH_PRIVATE_KEY:}
    public-key-location: ${AUTH_PUBLIC_KEY:}
    allow-ephemeral-keys: false
  ehr:
    enabled: true
    url: ${EHR_URL}
    source-system: ${EHR_SOURCE_SYSTEM:WeChat}
    target-system: ${EHR_TARGET_SYSTEM:EHR-Micro}
    service-name: ${EHR_SERVICE_NAME:ehr-micro-getpsninfo}
    route-id: ${EHR_ROUTE_ID:HZ}
    auth: ${EHR_ESB_AUTH}
    full-since: ${EHR_FULL_SINCE:2000-01-01 00:00:00}
    page-size: ${EHR_PAGE_SIZE:1000}
    max-pages: ${EHR_MAX_PAGES:10000}
    page-concurrency: ${EHR_PAGE_CONCURRENCY:4}
    max-records: ${EHR_MAX_RECORDS:200000}
    persistence-batch-size: ${EHR_PERSISTENCE_BATCH_SIZE:500}
    connect-timeout: ${EHR_CONNECT_TIMEOUT:5s}
    read-timeout: ${EHR_READ_TIMEOUT:30s}
```

ACK 集群名 `biel-ai` 和 Kubernetes 命名空间 `biel-life-camp` 不能替代 Nacos 的逻辑命名空间配置。
当前开发环境的 Nacos Namespace ID 为 `dev`，应用配置 Data ID 为 `system-service.yaml`，Group 为
`LIFECAMP`。未注入 `NACOS_NAMESPACE=dev` 时客户端会落到 `public`，可能表现为配置缺失、数据库未配置
或认证依赖不可用。

EHR 同步启用时会强制校验 `AUTH_IDENTIFIER_PEPPER` 至少为 32 个字符；即使 `AUTH_ENABLED=false`，人员手机号摘要也会使用该稳定密钥。该密钥首次同步后不得更换。

RSA 配置是 Spring Resource 位置而不是 PEM 正文。Windows 使用 `file:D:/...`，ACK 使用只读
Kubernetes Secret 文件挂载后的 `file:/...`。密钥生成、IDEA 启动和 ACK 部署步骤见
[`deploy/README.md`](../../../../../../../deploy/README.md)。

## 数据库准备

1. 如果 8 个内置角色尚未录入，执行 `system_db_basic_roles_seed.sql`。
2. 执行 `system_db_ehr_sync_readiness.sql`。
3. 只有最终的 `pre_sync_readiness` 返回 `READY` 才继续。
4. 首次同步前记录员工总数；若不是 0，先确认覆盖语义并建立 RDS 快照或备份。

## 小流量连通性验证

正式触发前，先从 system-service 的实际运行网络发起一次只读请求：

1. 使用 `pageSize=1&pageNo=1`，确认 DNS、网络白名单、HTTP 状态和 ESB 鉴权。
2. 只记录 `code`、`totalRecords`、`totalPages` 和字段名，不在日志保存人员明文或 `EsbAuth`。
3. 确认 `totalRecords > 0`，并与 HR 给出的在职人数处于同一数量级。
4. 抽查第一页和最后一页，确认分页元数据稳定。
5. 抽查总部、不同园区、越南、无手机号和有直属上级人员是否在范围内。

当前 EHR 客户端没有自动重试。第一页同步取得分页元数据，后续页使用固定大小线程池和
滑动窗口并发拉取，但仍按页码顺序汇总。单次运行最多只有 `page-concurrency` 个后续页面
处于在途或待汇总状态，不会一次性创建全部分页任务。任一页超时、格式异常或分页总数变化
都会取消窗口内剩余任务并终止同步，业务人员表不会执行生效事务；失败运行和任务租约仍会
留下审计记录。

`max-records` 会在第一页返回后、分配全量人员集合前检查。EHR 声明人数超过上限时返回
`EHR_RECORD_LIMIT_EXCEEDED`，避免异常分页元数据导致超大数组分配。调整该值前必须结合
system-service 的 JVM 堆上限、单页大小和一次同步期间来源 DTO/持久化 DTO 并存情况评估，
不能只为了绕过错误而无限调大。`page-concurrency` 应先以 `2～4` 小流量验证，只有 ESB
明确允许且延迟、错误率和 Pod 内存稳定时才逐步提高，最大允许值为 `16`。

人员快照完成校验后，以 `persistence-batch-size` 为单位批量写入暂存表和员工投影，
默认每批 `500` 人，允许范围为 `1～1000`。任一批量 SQL 失败时，系统会先回滚到该批
保存点，再逐人重试并隔离具体问题人员；不会因为一个超长字段或唯一约束异常回滚其他
合法人员。直属上级解析和普通角色初始化仍在人员投影全部写入后执行。分页并发数和
持久化批次大小在线程池与事务开始前确定，修改 Nacos 配置后需要重启 system-service。

人员字段缺失、手机号异常、批次内身份重复、工号归属冲突或单人数据库写入失败属于人员级问题。批量 SQL 失败后系统会回退为逐人短事务，只隔离问题人员并继续提交其他合法人员。脱敏问题明细写入 `sys_ehr_sync_issue`，失败人数写入 `sys_ehr_sync_run.issue_count`。存在人员问题的运行状态为 `PARTIAL_SUCCEEDED`，用于防止把不完整批次误认为完整成功；该批次不会执行缺失人员离职对账，但只要至少一名员工成功投影，就会开放登录门禁，问题人员不会阻断其他员工登录。登录检查同时兼容历史 `SUCCEEDED` 或 `PARTIAL_SUCCEEDED` 运行，只要其中至少一名员工已成功新增或更新，即使旧版本没有正确写入 `sys_integration_state`，其他已落库员工也可以正常登录。

仓库提供了 `scripts/Test-EhrEmployeeEndpoint.ps1`。先通过受控环境变量注入 `EHR_URL` 和 `EHR_ESB_AUTH`，再运行该脚本；它只请求第一页和最后一页的单条记录，只输出分页元数据与字段名，不输出人员字段值或鉴权值。

## 首次同步触发

当前人工接口：

```text
POST /api/system/v1/ehr-sync-runs
Idempotency-Key: ehr-full-20260730-0001
Content-Type: application/json

{
  "runType": "FULL_RECONCILIATION",
  "reason": "首次上线人员全量同步",
  "confirmationToken": "<至少16字符的运维确认串>"
}
```

同一个 `Idempotency-Key` 重复提交只会返回原运行记录。人工接口先创建 `PENDING` 运行并立即返回 `202`，后台单线程执行完整拉取和入库。通过响应中的 `runId` 轮询运行详情，不需要延长 HTTP 客户端超时。

安全提醒：当前 `confirmationToken` 只校验非空和长度，未参与真实授权；内部身份过滤器也不会强制每个请求必须携带身份。因此在补充运维授权校验前，只允许通过端口转发或受控内网直接访问 system-service，不得创建公网入口。

PowerShell 内网触发示例：

```powershell
$syncHeaders = @{
    'Idempotency-Key' = 'ehr-full-20260730-0001'
}
$syncBody = @{
    runType = 'FULL_RECONCILIATION'
    reason = '首次上线人员全量同步'
    confirmationToken = '<受控运维确认串>'
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri 'http://127.0.0.1:8081/api/system/v1/ehr-sync-runs' `
    -Headers $syncHeaders `
    -ContentType 'application/json' `
    -Body $syncBody `
    -TimeoutSec 30
```

## 同步后验收

再次执行 `system_db_ehr_sync_readiness.sql`，按顺序确认：

1. 最新运行状态为 `SUCCEEDED`，`fetched_count` 与暂存记录数一致。
2. `sys_integration_state.initial_sync_completed = 1`。
3. 在职员工数与 EHR `totalRecords` 一致。
4. 所有在职员工均拥有启用的 `EMPLOYEE` 角色。
5. 必填字段缺失数为 0。
6. 抽查直属上级未解析记录；它是警告，但数量异常时应暂停登录开放。
7. 最终 `post_sync_readiness` 返回 `PASS`。

若状态为 `PARTIAL_SUCCEEDED`，调用 `GET /api/system/v1/ehr-sync-runs/{runId}/issues?afterId=0&pageSize=100` 分页查询脱敏工号、失败阶段、问题编码和摘要；必要时再按 `runId` 检索失败日志。修复问题人员后使用新的幂等键执行全量同步。问题批次中的合法人员已经提交，不需要人工回滚。

日志事件：

- `ehr_sync_stage_started` / `ehr_sync_stage_progress` / `ehr_sync_stage_completed`：按拉取、校验、员工入库、直属上级、默认角色、离职对账和收尾七个阶段记录线程、批次、已处理量、剩余量、速率和预计剩余时间。
- `ehr_sync_issue_summary`：汇总校验、入库、直属上级和角色失败人数。
- `ehr_employee_sync_item_failed`：记录不可逆 `personRef`、脱敏工号、失败阶段、稳定失败码、失败原因和同步时间。
- `ehr_employee_sync_succeeded`：同时覆盖 `SUCCEEDED` 和 `PARTIAL_SUCCEEDED`，以 `status` 和 `issueCount` 区分。
- `ehr_employee_sync_failed`：只表示分页、空快照、数量不一致或同步基础设施等批次级失败。

若整批同步失败，不要改用新的幂等键盲目重试。先根据 `failure_code` 排查 EHR 响应、分页变化、网络或数据库基础设施；确认原因后再使用新的幂等键重试。
