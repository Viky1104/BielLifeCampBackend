# 管理后台本地密码登录任务清单

- [x] 1. 冻结管理员登录、Token 声明和错误码契约
- [x] 2. 新增 `source_type` 与 `sys_local_credential` Flyway 迁移
- [x] 3. 调整 `admin`、`SUPER_ADMIN` 和本地凭据初始化流程
- [x] 4. 实现管理员密码登录完整链路
- [x] 5. 去除 Token 中写死的客户端类型和认证方式
- [x] 6. 将 `client_type` 传播到内部 JWS、`LoginUser` 和 `SecurityUtils`
- [x] 7. 已实现的 EHR 管理接口增加 `ADMIN_WEB` 与权限码双重鉴权
- [x] 8. 增加 Redis 登录失败限流和安全日志
- [x] 9. EHR 全量同步排除 `LOCAL_BOOTSTRAP` 账号
- [x] 10. 更新配置、OpenAPI、ER 图、运行手册和项目状态
- [x] 11. 安装 OpenSSL、生成本地外部 JWT RSA 密钥并验证 system-service 使用 `nacos` Profile 启动

## 检查点

- [x] A. 数据模型、契约和初始化脚本完成，Flyway 在 H2 MySQL 模式验证通过
- [x] B. 微信/管理员 Token、刷新和客户端类型传播测试通过
- [x] C. 管理接口隔离、限流、EHR 同步和全量 Maven `verify` 通过

## 部署环境待办

- [ ] 在 `camp_system_test_db` 执行 V4 迁移
- [ ] 在 IDEA 运行 `AdminPasswordHashTool`，把哈希填入超管初始化脚本并执行
- [ ] 在 Nacos 开启管理员密码登录并使用真实 Redis 完成登录、刷新、EHR 管理接口、退出联调
- [ ] 从 `application.yml` 移除本机秘密默认值，改由 IDEA 环境变量或 Kubernetes Secret/KMS 注入
- [ ] 为 `platform-service` Helm Chart 增加并验证两套 RSA Secret 的只读文件卷挂载
- [ ] 生成独立的 Gateway 内部 JWS 密钥对，完成 system-service、Gateway、业务服务端到端验签
