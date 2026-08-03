CREATE TABLE sys_employee (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '本地员工主键',
    ehr_person_id VARCHAR(128) NOT NULL COMMENT 'EHR人员稳定唯一标识',
    employee_no VARCHAR(64) NOT NULL COMMENT '员工工号',
    display_name VARCHAR(128) NOT NULL COMMENT '员工姓名',
    mobile_hash CHAR(64) NOT NULL COMMENT '登录手机号的HMAC-SHA256摘要，不存储手机号明文',
    primary_org_id BIGINT NOT NULL COMMENT '主组织标识',
    employment_status VARCHAR(24) NOT NULL COMMENT 'EHR在职状态',
    binding_status VARCHAR(24) NOT NULL DEFAULT 'UNBOUND' COMMENT '微信身份绑定状态',
    account_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '本地账号状态',
    authz_version BIGINT NOT NULL DEFAULT 1 COMMENT '权限版本，变更后使旧访问令牌失效',
    ehr_source_version VARCHAR(128) COMMENT 'EHR源数据版本或水位',
    last_ehr_synced_at TIMESTAMP COMMENT '最近一次成功同步EHR数据的时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    CONSTRAINT uk_sys_employee_ehr_person UNIQUE (ehr_person_id),
    CONSTRAINT uk_sys_employee_no UNIQUE (employee_no)
) COMMENT = 'EHR权威员工本地投影';
CREATE INDEX idx_sys_employee_mobile_status ON sys_employee (mobile_hash, employment_status);
CREATE INDEX idx_sys_employee_org_status ON sys_employee (primary_org_id, employment_status, account_status);

CREATE TABLE sys_external_identity (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '外部身份记录主键',
    employee_id BIGINT NOT NULL COMMENT '关联的本地员工主键',
    provider_type VARCHAR(32) NOT NULL COMMENT '身份提供方类型，例如WECHAT_MINI_PROGRAM',
    provider_tenant VARCHAR(128) NOT NULL COMMENT '身份提供方租户标识，例如微信AppID',
    provider_subject_hash CHAR(64) NOT NULL COMMENT '外部主体标识摘要，例如OpenID的HMAC-SHA256',
    union_subject_hash CHAR(64) COMMENT '跨应用主体标识摘要，例如UnionID的HMAC-SHA256',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '外部身份绑定状态',
    bound_mobile_hash CHAR(64) COMMENT '绑定时使用的登录手机号摘要',
    last_login_at TIMESTAMP COMMENT '该外部身份最近登录时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
    CONSTRAINT uk_sys_external_subject UNIQUE (provider_type, provider_tenant, provider_subject_hash),
    CONSTRAINT uk_sys_employee_provider UNIQUE (employee_id, provider_type, provider_tenant)
) COMMENT = '微信等外部身份绑定';

CREATE TABLE sys_role (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '角色主键',
    role_code VARCHAR(80) NOT NULL COMMENT '角色唯一编码',
    role_name VARCHAR(128) NOT NULL COMMENT '角色名称',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '角色状态',
    built_in BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否系统内置角色',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    CONSTRAINT uk_sys_role_code UNIQUE (role_code)
) COMMENT = '角色定义';

CREATE TABLE sys_permission (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '权限主键',
    permission_code VARCHAR(160) NOT NULL COMMENT '权限唯一编码',
    target_service VARCHAR(80) NOT NULL COMMENT '权限所属目标服务',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '权限状态',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    CONSTRAINT uk_sys_permission_code UNIQUE (permission_code)
) COMMENT = '权限码定义';

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL COMMENT '角色主键',
    permission_id BIGINT NOT NULL COMMENT '权限主键',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联创建时间',
    PRIMARY KEY (role_id, permission_id),
    KEY idx_sys_role_permission_permission (permission_id)
) COMMENT = '角色权限关联';

CREATE TABLE sys_role_assignment (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '员工角色分配主键',
    employee_id BIGINT NOT NULL COMMENT '员工主键',
    role_id BIGINT NOT NULL COMMENT '角色主键',
    scope_type VARCHAR(40) NOT NULL DEFAULT 'SELF' COMMENT '数据权限范围类型，例如SELF或ORG',
    scope_value VARCHAR(256) COMMENT '数据权限范围值',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '角色分配状态',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    CONSTRAINT uk_sys_role_assignment UNIQUE (employee_id, role_id, scope_type, scope_value),
    KEY idx_sys_role_assignment_role (role_id, status)
) COMMENT = '员工角色和数据范围分配';

CREATE TABLE sys_user_session (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '登录会话唯一标识',
    employee_id BIGINT NOT NULL COMMENT '登录员工主键',
    client_type VARCHAR(32) NOT NULL COMMENT '客户端类型，例如WECHAT_MINI_PROGRAM',
    auth_method VARCHAR(32) NOT NULL COMMENT '本次会话使用的认证方式',
    status VARCHAR(24) NOT NULL COMMENT '会话状态',
    authz_version_at_issue BIGINT NOT NULL COMMENT '签发会话时的员工权限版本',
    absolute_expires_at TIMESTAMP NOT NULL COMMENT '会话绝对过期时间',
    idle_expires_at TIMESTAMP NOT NULL COMMENT '会话空闲过期时间',
    last_seen_at TIMESTAMP NOT NULL COMMENT '最近一次访问时间',
    revoked_at TIMESTAMP COMMENT '会话吊销时间',
    revoke_reason VARCHAR(128) COMMENT '会话吊销原因',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '会话创建时间'
) COMMENT = '用户登录会话';
CREATE INDEX idx_sys_session_employee_status ON sys_user_session (employee_id, status);

CREATE TABLE sys_refresh_token (
    id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '刷新令牌记录唯一标识',
    session_id VARCHAR(36) NOT NULL COMMENT '所属登录会话标识',
    token_family_id VARCHAR(36) NOT NULL COMMENT '轮换令牌族标识，用于重放检测',
    token_hash CHAR(64) NOT NULL COMMENT '刷新令牌SHA-256摘要，不存储令牌明文',
    parent_token_id VARCHAR(36) COMMENT '轮换前的父刷新令牌标识',
    status VARCHAR(24) NOT NULL COMMENT '刷新令牌状态',
    expires_at TIMESTAMP NOT NULL COMMENT '刷新令牌过期时间',
    consumed_at TIMESTAMP COMMENT '刷新令牌被消费的时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    CONSTRAINT uk_sys_refresh_hash UNIQUE (token_hash)
) COMMENT = '轮换式刷新令牌摘要';
CREATE INDEX idx_sys_refresh_session ON sys_refresh_token (session_id);
CREATE INDEX idx_sys_refresh_family ON sys_refresh_token (token_family_id, status);

CREATE TABLE sys_operation_audit (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '操作审计主键',
    occurred_at TIMESTAMP NOT NULL COMMENT '业务操作发生时间',
    actor_employee_id BIGINT COMMENT '操作员工主键，系统操作时可为空',
    module VARCHAR(80) NOT NULL COMMENT '操作所属模块',
    action VARCHAR(80) NOT NULL COMMENT '操作类型',
    result VARCHAR(24) NOT NULL COMMENT '操作结果',
    detail_code VARCHAR(128) COMMENT '结果或失败原因编码，不记录敏感明文',
    request_id VARCHAR(64) NOT NULL COMMENT '请求链路标识',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审计记录入库时间'
) COMMENT = '认证和管理员操作审计';
CREATE INDEX idx_sys_audit_actor_time ON sys_operation_audit (actor_employee_id, occurred_at);
CREATE INDEX idx_sys_audit_action_time ON sys_operation_audit (module, action, occurred_at);

CREATE TABLE sys_integration_state (
    connection_code VARCHAR(40) NOT NULL PRIMARY KEY COMMENT '外部系统连接编码，例如EHR',
    initial_sync_completed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已完成首次全量同步',
    last_successful_watermark VARCHAR(256) COMMENT '最近成功同步的数据水位或版本',
    last_successful_at TIMESTAMP COMMENT '最近一次同步成功时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录最近更新时间'
) COMMENT = '外部系统集成状态和登录门禁';

INSERT INTO sys_role (id, role_code, role_name, status, built_in) VALUES (1, 'EMPLOYEE', '员工', 'ACTIVE', TRUE);
INSERT INTO sys_integration_state (connection_code, initial_sync_completed) VALUES ('EHR', FALSE);
