-- Biel Life Camp system_db authentication, login and EHR employee schema.
-- Target: MySQL 8.0+
-- Usage: initialize a NEW, EMPTY system_db for schema review or controlled bootstrap.
-- Warning: Flyway-managed environments must execute db/migration/V1 and V2 instead.
-- Do not execute this consolidated script after Flyway migrations have created the tables.
-- Relationship strategy: no database foreign keys; application transactions maintain logical references.

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ============================================================
-- 1. EHR employee projection
-- ============================================================
use camp_system_test_db;
CREATE TABLE sys_employee (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '本地员工主键，由数据库自增生成',
    ehr_person_id VARCHAR(128) NOT NULL COMMENT 'EHR人员稳定唯一标识',
    employee_no VARCHAR(64) NOT NULL COMMENT '员工工号',
    display_name VARCHAR(128) NOT NULL COMMENT '员工姓名',
    mobile_hash CHAR(64) COMMENT '登录手机号的HMAC-SHA256摘要，不存储手机号明文',
    primary_org_id BIGINT COMMENT '主组织标识',
    source_type VARCHAR(32) NOT NULL DEFAULT 'EHR' COMMENT '人员来源：EHR或LOCAL_BOOTSTRAP',
    employment_status VARCHAR(24) NOT NULL COMMENT '人员在职或可用状态',
    binding_status VARCHAR(24) NOT NULL DEFAULT 'UNBOUND' COMMENT '微信身份绑定状态',
    account_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '本地账号状态',
    authz_version BIGINT NOT NULL DEFAULT 1 COMMENT '权限版本，变更后使旧访问令牌失效',
    ehr_source_version VARCHAR(128) COMMENT 'EHR源数据版本或水位',
    last_ehr_synced_at TIMESTAMP NULL COMMENT '最近一次成功同步EHR数据的时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    birthday DATE COMMENT '员工生日',
    gender_code VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '标准化性别编码',
    gender_source_value VARCHAR(32) COMMENT 'EHR返回的原始性别值',
    email VARCHAR(256) COMMENT '员工工作邮箱',
    primary_org_code VARCHAR(128) COMMENT '主组织编码',
    primary_org_name VARCHAR(256) COMMENT '主组织名称快照',
    legal_company_code VARCHAR(128) COMMENT '法人公司编码',
    legal_company_name VARCHAR(256) COMMENT '法人公司名称快照',
    supervisor_employee_no VARCHAR(64) COMMENT '直属上级工号快照',
    supervisor_employee_id BIGINT COMMENT '解析后的直属上级本地员工主键',
    job_grade VARCHAR(128) COMMENT '职级',
    professional_title VARCHAR(256) COMMENT '职称',
    job_code VARCHAR(128) COMMENT '职位或职务编码',
    job_name VARCHAR(256) COMMENT '职位或职务名称',
    position_code VARCHAR(128) COMMENT '岗位编码',
    position_name VARCHAR(256) COMMENT '岗位名称',
    hire_date DATE COMMENT '入职日期',
    termination_date DATE COMMENT '离职日期',
    ehr_created_at TIMESTAMP NULL COMMENT '人员记录在EHR中的创建时间',
    ehr_modified_at TIMESTAMP NULL COMMENT '人员记录在EHR中的最近修改时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_employee_ehr_person UNIQUE (ehr_person_id),
    CONSTRAINT uk_sys_employee_no UNIQUE (employee_no),
    KEY idx_sys_employee_mobile_status (mobile_hash, employment_status),
    KEY idx_sys_employee_org_status
        (primary_org_id, employment_status, account_status),
    KEY idx_sys_employee_supervisor_no (supervisor_employee_no),
    KEY idx_sys_employee_supervisor_id (supervisor_employee_id),
    KEY idx_sys_employee_org_code_status
        (primary_org_code, employment_status, account_status),
    KEY idx_sys_employee_source_status
        (source_type, employment_status, account_status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '统一员工主体及EHR人员本地投影';

CREATE TABLE sys_local_credential (
    employee_id BIGINT NOT NULL COMMENT '关联的本地员工主键',
    password_hash VARCHAR(255) NOT NULL COMMENT '带算法标识的不可逆密码哈希',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '凭据状态：ACTIVE或DISABLED',
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE COMMENT '下次登录是否要求修改密码',
    password_changed_at TIMESTAMP NULL COMMENT '密码最近修改时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
    PRIMARY KEY (employee_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '管理后台本地密码凭据';

-- ============================================================
-- 2. WeChat external identity and profile
-- ============================================================

CREATE TABLE sys_external_identity (
    id BIGINT NOT NULL COMMENT '外部身份记录主键',
    employee_id BIGINT NOT NULL COMMENT '关联的本地员工主键',
    provider_type VARCHAR(32) NOT NULL COMMENT '身份提供方类型，例如WECHAT_MINI_PROGRAM',
    provider_tenant VARCHAR(128) NOT NULL COMMENT '身份提供方租户标识，例如微信AppID',
    provider_subject_hash CHAR(64) NOT NULL COMMENT '外部主体标识摘要，例如OpenID的HMAC-SHA256',
    union_subject_hash CHAR(64) COMMENT '跨应用主体标识摘要，例如UnionID的HMAC-SHA256',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '外部身份绑定状态',
    bound_mobile_hash CHAR(64) COMMENT '绑定时使用的登录手机号摘要',
    last_login_at TIMESTAMP NULL COMMENT '该外部身份最近登录时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
    provider_subject_ciphertext VARBINARY(1024) COMMENT '外部主体标识密文，例如加密后的OpenID',
    union_subject_ciphertext VARBINARY(1024) COMMENT '跨应用主体标识密文，例如加密后的UnionID',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_external_subject
        UNIQUE (provider_type, provider_tenant, provider_subject_hash),
    CONSTRAINT uk_sys_employee_provider
        UNIQUE (employee_id, provider_type, provider_tenant)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '微信等外部身份绑定';

CREATE TABLE sys_wechat_profile (
    id BIGINT NOT NULL COMMENT '微信资料记录主键',
    employee_id BIGINT NOT NULL COMMENT '关联的本地员工主键',
    external_identity_id BIGINT NOT NULL COMMENT '关联的微信外部身份记录主键',
    nickname VARCHAR(128) COMMENT '微信昵称',
    avatar_url VARCHAR(1024) COMMENT '微信头像地址',
    profile_updated_at TIMESTAMP NULL COMMENT '微信资料来源端的最近更新时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_wechat_profile_employee UNIQUE (employee_id),
    CONSTRAINT uk_sys_wechat_profile_identity UNIQUE (external_identity_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '微信昵称头像等非EHR资料';

-- ============================================================
-- 3. RBAC and default employee role
-- ============================================================

CREATE TABLE sys_role (
    id BIGINT NOT NULL COMMENT '角色主键',
    role_code VARCHAR(80) NOT NULL COMMENT '角色唯一编码',
    role_name VARCHAR(128) NOT NULL COMMENT '角色名称',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '角色状态',
    built_in BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否系统内置角色',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_code UNIQUE (role_code)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '角色定义';

CREATE TABLE sys_permission (
    id BIGINT NOT NULL COMMENT '权限主键',
    permission_code VARCHAR(160) NOT NULL COMMENT '权限唯一编码',
    target_service VARCHAR(80) NOT NULL COMMENT '权限所属目标服务',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '权限状态',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_permission_code UNIQUE (permission_code)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '权限码定义';

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL COMMENT '角色主键',
    permission_id BIGINT NOT NULL COMMENT '权限主键',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联创建时间',
    PRIMARY KEY (role_id, permission_id),
    KEY idx_sys_role_permission_permission (permission_id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '角色权限关联';

CREATE TABLE sys_role_assignment (
    id BIGINT NOT NULL COMMENT '员工角色分配主键',
    employee_id BIGINT NOT NULL COMMENT '员工主键',
    role_id BIGINT NOT NULL COMMENT '角色主键',
    scope_type VARCHAR(40) NOT NULL DEFAULT 'SELF' COMMENT '数据权限范围类型，例如SELF或ORG',
    scope_value VARCHAR(256) COMMENT '数据权限范围值',
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' COMMENT '角色分配状态',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_role_assignment
        UNIQUE (employee_id, role_id, scope_type, scope_value),
    KEY idx_sys_role_assignment_role (role_id, status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '员工角色和数据范围分配';

-- ============================================================
-- 4. Login session and rotating refresh token
-- ============================================================

CREATE TABLE sys_user_session (
    id VARCHAR(36) NOT NULL COMMENT '登录会话唯一标识',
    employee_id BIGINT NOT NULL COMMENT '登录员工主键',
    client_type VARCHAR(32) NOT NULL COMMENT '客户端类型，例如WECHAT_MINI_PROGRAM',
    auth_method VARCHAR(32) NOT NULL COMMENT '本次会话使用的认证方式',
    status VARCHAR(24) NOT NULL COMMENT '会话状态',
    authz_version_at_issue BIGINT NOT NULL COMMENT '签发会话时的员工权限版本',
    absolute_expires_at DATETIME(3) NOT NULL COMMENT '会话绝对过期时间',
    idle_expires_at DATETIME(3) NOT NULL COMMENT '会话空闲过期时间',
    last_seen_at DATETIME(3) NOT NULL COMMENT '最近一次访问时间',
    revoked_at DATETIME(3) NULL COMMENT '会话吊销时间',
    revoke_reason VARCHAR(128) COMMENT '会话吊销原因',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '会话创建时间',
    PRIMARY KEY (id),
    KEY idx_sys_session_employee_status (employee_id, status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '用户登录会话';

CREATE TABLE sys_refresh_token (
    id VARCHAR(36) NOT NULL COMMENT '刷新令牌记录唯一标识',
    session_id VARCHAR(36) NOT NULL COMMENT '所属登录会话标识',
    token_family_id VARCHAR(36) NOT NULL COMMENT '轮换令牌族标识，用于重放检测',
    token_hash CHAR(64) NOT NULL COMMENT '刷新令牌SHA-256摘要，不存储令牌明文',
    parent_token_id VARCHAR(36) COMMENT '轮换前的父刷新令牌标识',
    status VARCHAR(24) NOT NULL COMMENT '刷新令牌状态',
    expires_at TIMESTAMP NOT NULL COMMENT '刷新令牌过期时间',
    consumed_at TIMESTAMP NULL COMMENT '刷新令牌被消费的时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_refresh_hash UNIQUE (token_hash),
    KEY idx_sys_refresh_session (session_id),
    KEY idx_sys_refresh_family (token_family_id, status)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '轮换式刷新令牌摘要';

-- ============================================================
-- 5. Authentication and administration audit
-- ============================================================

CREATE TABLE sys_operation_audit (
    id BIGINT NOT NULL COMMENT '操作审计主键',
    occurred_at TIMESTAMP NOT NULL COMMENT '业务操作发生时间',
    actor_employee_id BIGINT COMMENT '操作员工主键，系统操作时可为空',
    module VARCHAR(80) NOT NULL COMMENT '操作所属模块',
    action VARCHAR(80) NOT NULL COMMENT '操作类型',
    result VARCHAR(24) NOT NULL COMMENT '操作结果',
    detail_code VARCHAR(128) COMMENT '结果或失败原因编码，不记录敏感明文',
    request_id VARCHAR(64) NOT NULL COMMENT '请求链路标识',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审计记录入库时间',
    PRIMARY KEY (id),
    KEY idx_sys_audit_actor_time (actor_employee_id, occurred_at),
    KEY idx_sys_audit_action_time (module, action, occurred_at)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '认证和管理员操作审计';

-- ============================================================
-- 6. EHR integration gate and full synchronization
-- ============================================================

CREATE TABLE sys_integration_state (
    connection_code VARCHAR(40) NOT NULL COMMENT '外部系统连接编码，例如EHR',
    initial_sync_completed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已完成首次全量同步',
    last_successful_watermark VARCHAR(256) COMMENT '最近成功同步的数据水位或版本',
    last_successful_at TIMESTAMP NULL COMMENT '最近一次同步成功时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
    PRIMARY KEY (connection_code)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '外部系统集成状态和登录门禁';

CREATE TABLE sys_ehr_sync_run (
    id BIGINT NOT NULL COMMENT 'EHR同步运行主键',
    idempotency_key VARCHAR(128) COMMENT '同步任务幂等键',
    run_type VARCHAR(32) NOT NULL COMMENT '同步类型，例如FULL',
    trigger_type VARCHAR(32) NOT NULL COMMENT '触发方式，例如SCHEDULED或MANUAL',
    status VARCHAR(32) NOT NULL COMMENT '同步运行状态',
    fetched_count BIGINT NOT NULL DEFAULT 0 COMMENT '从EHR拉取的人员数量',
    inserted_count BIGINT NOT NULL DEFAULT 0 COMMENT '本次新增员工数量',
    updated_count BIGINT NOT NULL DEFAULT 0 COMMENT '本次更新员工数量',
    resigned_count BIGINT NOT NULL DEFAULT 0 COMMENT '本次识别并停用的离职员工数量',
    role_initialized_count BIGINT NOT NULL DEFAULT 0 COMMENT '本次初始化普通员工角色的数量',
    issue_count BIGINT NOT NULL DEFAULT 0 COMMENT '本次同步产生的问题数量',
    failure_code VARCHAR(64) COMMENT '同步失败原因编码',
    failure_digest VARCHAR(500) COMMENT '脱敏后的失败摘要',
    started_at TIMESTAMP NULL COMMENT '同步开始时间',
    completed_at TIMESTAMP NULL COMMENT '同步完成时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '运行记录创建时间',
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_ehr_sync_idempotency UNIQUE (idempotency_key),
    KEY idx_sys_ehr_sync_run_created (created_at, id)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'EHR人员全量同步运行';

CREATE TABLE sys_ehr_employee_stage (
    run_id BIGINT NOT NULL COMMENT '所属EHR同步运行主键',
    ehr_person_id VARCHAR(128) NOT NULL COMMENT 'EHR人员稳定唯一标识',
    employee_no VARCHAR(64) NOT NULL COMMENT '员工工号',
    payload_digest CHAR(64) NOT NULL COMMENT '人员快照内容摘要，用于一致性校验',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '暂存记录创建时间',
    PRIMARY KEY (run_id, ehr_person_id),
    CONSTRAINT uk_sys_ehr_stage_employee_no UNIQUE (run_id, employee_no)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'EHR完整快照身份键暂存';

CREATE TABLE sys_ehr_sync_issue (
    id BIGINT NOT NULL COMMENT '同步问题记录主键',
    run_id BIGINT NOT NULL COMMENT '所属EHR同步运行主键',
    severity VARCHAR(16) NOT NULL COMMENT '问题严重级别',
    issue_code VARCHAR(64) NOT NULL COMMENT '问题类型编码',
    ehr_person_id VARCHAR(128) COMMENT '涉及的EHR人员稳定唯一标识',
    employee_no VARCHAR(64) COMMENT '涉及的员工工号',
    detail_digest VARCHAR(500) NOT NULL COMMENT '脱敏后的问题详情摘要',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '问题记录创建时间',
    PRIMARY KEY (id),
    KEY idx_sys_ehr_issue_run (run_id, severity)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'EHR同步差异和问题摘要';

CREATE TABLE sys_task_lease (
    task_type VARCHAR(80) NOT NULL COMMENT '集群任务类型，同一类型同一时刻只允许一个持有者',
    task_id VARCHAR(64) NOT NULL COMMENT '当前任务实例标识',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '当前任务执行幂等键',
    attempt INT NOT NULL COMMENT '当前任务尝试次数',
    lease_expires_at TIMESTAMP NOT NULL COMMENT '租约过期时间',
    status VARCHAR(24) NOT NULL COMMENT '任务租约状态',
    failure_digest VARCHAR(500) COMMENT '脱敏后的最近失败摘要',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '租约最近更新时间',
    PRIMARY KEY (task_type)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '集群任务数据库租约';

-- ============================================================
-- 7. Required built-in data
-- ============================================================

INSERT INTO sys_role
    (id, role_code, role_name, status, built_in)
VALUES
    (1, 'EMPLOYEE', '普通员工', 'ACTIVE', TRUE),
    (2, 'SUPER_ADMIN', '超级管理员', 'ACTIVE', TRUE),
    (3, 'OPS_ADMIN', '运营管理员', 'ACTIVE', TRUE),
    (4, 'HR_ADMIN', 'HR管理员', 'ACTIVE', TRUE),
    (5, 'CONTENT_REVIEWER', '内容审核员', 'ACTIVE', TRUE),
    (6, 'MALL_VERIFIER', '商城核销员', 'ACTIVE', TRUE),
    (7, 'AUDITOR', '审计员', 'ACTIVE', TRUE),
    (8, 'READ_ONLY', '只读观察员', 'ACTIVE', TRUE);

INSERT INTO sys_integration_state
    (connection_code, initial_sync_completed)
VALUES
    ('EHR', FALSE);
