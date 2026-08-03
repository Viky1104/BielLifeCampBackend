-- 员工、身份、权限、会话和同步运行之间只保留逻辑关联，不创建数据库外键。
ALTER TABLE sys_employee
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '本地员工主键，由数据库自增生成';
ALTER TABLE sys_employee
    MODIFY COLUMN primary_org_id BIGINT NULL COMMENT '主组织标识';
ALTER TABLE sys_employee
    MODIFY COLUMN mobile_hash CHAR(64) NULL COMMENT '登录手机号的HMAC-SHA256摘要，不存储手机号明文';

ALTER TABLE sys_employee ADD COLUMN birthday DATE COMMENT '员工生日';
ALTER TABLE sys_employee
    ADD COLUMN gender_code VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN' COMMENT '标准化性别编码';
ALTER TABLE sys_employee ADD COLUMN gender_source_value VARCHAR(32) COMMENT 'EHR返回的原始性别值';
ALTER TABLE sys_employee ADD COLUMN email VARCHAR(256) COMMENT '员工工作邮箱';
ALTER TABLE sys_employee ADD COLUMN primary_org_code VARCHAR(128) COMMENT '主组织编码';
ALTER TABLE sys_employee ADD COLUMN primary_org_name VARCHAR(256) COMMENT '主组织名称快照';
ALTER TABLE sys_employee ADD COLUMN legal_company_code VARCHAR(128) COMMENT '法人公司编码';
ALTER TABLE sys_employee ADD COLUMN legal_company_name VARCHAR(256) COMMENT '法人公司名称快照';
ALTER TABLE sys_employee ADD COLUMN supervisor_employee_no VARCHAR(64) COMMENT '直属上级工号快照';
ALTER TABLE sys_employee ADD COLUMN supervisor_employee_id BIGINT COMMENT '解析后的直属上级本地员工主键';
ALTER TABLE sys_employee ADD COLUMN job_grade VARCHAR(128) COMMENT '职级';
ALTER TABLE sys_employee ADD COLUMN professional_title VARCHAR(256) COMMENT '职称';
ALTER TABLE sys_employee ADD COLUMN job_code VARCHAR(128) COMMENT '职位或职务编码';
ALTER TABLE sys_employee ADD COLUMN job_name VARCHAR(256) COMMENT '职位或职务名称';
ALTER TABLE sys_employee ADD COLUMN position_code VARCHAR(128) COMMENT '岗位编码';
ALTER TABLE sys_employee ADD COLUMN position_name VARCHAR(256) COMMENT '岗位名称';
ALTER TABLE sys_employee ADD COLUMN hire_date DATE COMMENT '入职日期';
ALTER TABLE sys_employee ADD COLUMN termination_date DATE COMMENT '离职日期';
ALTER TABLE sys_employee ADD COLUMN ehr_created_at TIMESTAMP COMMENT '人员记录在EHR中的创建时间';
ALTER TABLE sys_employee ADD COLUMN ehr_modified_at TIMESTAMP COMMENT '人员记录在EHR中的最近修改时间';
CREATE INDEX idx_sys_employee_supervisor_no ON sys_employee (supervisor_employee_no);
CREATE INDEX idx_sys_employee_supervisor_id ON sys_employee (supervisor_employee_id);
CREATE INDEX idx_sys_employee_org_code_status
    ON sys_employee (primary_org_code, employment_status, account_status);

ALTER TABLE sys_external_identity
    ADD COLUMN provider_subject_ciphertext VARBINARY(1024)
        COMMENT '外部主体标识密文，例如加密后的OpenID';
ALTER TABLE sys_external_identity
    ADD COLUMN union_subject_ciphertext VARBINARY(1024)
        COMMENT '跨应用主体标识密文，例如加密后的UnionID';

CREATE TABLE sys_wechat_profile (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '微信资料记录主键',
    employee_id BIGINT NOT NULL COMMENT '关联的本地员工主键',
    external_identity_id BIGINT NOT NULL COMMENT '关联的微信外部身份记录主键',
    nickname VARCHAR(128) COMMENT '微信昵称',
    avatar_url VARCHAR(1024) COMMENT '微信头像地址',
    profile_updated_at TIMESTAMP COMMENT '微信资料来源端的最近更新时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录最近更新时间',
    CONSTRAINT uk_sys_wechat_profile_employee UNIQUE (employee_id),
    CONSTRAINT uk_sys_wechat_profile_identity UNIQUE (external_identity_id)
) COMMENT = '微信昵称头像等非EHR资料';

CREATE TABLE sys_ehr_sync_run (
    id BIGINT NOT NULL PRIMARY KEY COMMENT 'EHR同步运行主键',
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
    started_at TIMESTAMP COMMENT '同步开始时间',
    completed_at TIMESTAMP COMMENT '同步完成时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '运行记录创建时间',
    CONSTRAINT uk_sys_ehr_sync_idempotency UNIQUE (idempotency_key)
) COMMENT = 'EHR人员全量同步运行';
CREATE INDEX idx_sys_ehr_sync_run_created ON sys_ehr_sync_run (created_at, id);

CREATE TABLE sys_ehr_employee_stage (
    run_id BIGINT NOT NULL COMMENT '所属EHR同步运行主键',
    ehr_person_id VARCHAR(128) NOT NULL COMMENT 'EHR人员稳定唯一标识',
    employee_no VARCHAR(64) NOT NULL COMMENT '员工工号',
    payload_digest CHAR(64) NOT NULL COMMENT '人员快照内容摘要，用于一致性校验',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '暂存记录创建时间',
    PRIMARY KEY (run_id, ehr_person_id),
    CONSTRAINT uk_sys_ehr_stage_employee_no UNIQUE (run_id, employee_no)
) COMMENT = 'EHR完整快照身份键暂存';

CREATE TABLE sys_ehr_sync_issue (
    id BIGINT NOT NULL PRIMARY KEY COMMENT '同步问题记录主键',
    run_id BIGINT NOT NULL COMMENT '所属EHR同步运行主键',
    severity VARCHAR(16) NOT NULL COMMENT '问题严重级别',
    issue_code VARCHAR(64) NOT NULL COMMENT '问题类型编码',
    ehr_person_id VARCHAR(128) COMMENT '涉及的EHR人员稳定唯一标识',
    employee_no VARCHAR(64) COMMENT '涉及的员工工号',
    detail_digest VARCHAR(500) NOT NULL COMMENT '脱敏后的问题详情摘要',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '问题记录创建时间'
) COMMENT = 'EHR同步差异和问题摘要';
CREATE INDEX idx_sys_ehr_issue_run ON sys_ehr_sync_issue (run_id, severity);

CREATE TABLE sys_task_lease (
    task_type VARCHAR(80) NOT NULL PRIMARY KEY
        COMMENT '集群任务类型，同一类型同一时刻只允许一个持有者',
    task_id VARCHAR(64) NOT NULL COMMENT '当前任务实例标识',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '当前任务执行幂等键',
    attempt INT NOT NULL COMMENT '当前任务尝试次数',
    lease_expires_at TIMESTAMP NOT NULL COMMENT '租约过期时间',
    status VARCHAR(24) NOT NULL COMMENT '任务租约状态',
    failure_digest VARCHAR(500) COMMENT '脱敏后的最近失败摘要',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '租约最近更新时间'
) COMMENT = '集群任务数据库租约';
