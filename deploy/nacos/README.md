# Nacos 配置基线

本目录保存可审查、可发布的 Nacos 非秘密配置。目录名对应 Nacos Namespace，文件名对应 Data ID；
所有环境统一使用 Group `LIFECAMP`。

| 路径 | Namespace | Group | Data ID |
|---|---|---|---|
| `dev/gateway.yaml` | `dev` | `LIFECAMP` | `gateway.yaml` |
| `dev/system-service.yaml` | `dev` | `LIFECAMP` | `system-service.yaml` |

发布前必须确认配置中只有环境变量占位符，没有数据库密码、Redis ACL、OSS AccessKey、EHR/微信凭据、
pepper、AES 密钥、Gateway 服务凭据或 RSA 私钥正文。发布后比较 Nacos 返回内容，并滚动重启受影响
服务完成 readiness 与业务冒烟；回滚使用 Nacos 配置历史恢复上一版本后再次重启验证。
