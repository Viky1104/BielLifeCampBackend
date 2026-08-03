-- 管理后台本地密码仅作为过渡和应急认证方式；人员主数据仍保存在统一员工表。
-- 所有关联由应用事务维护，不创建数据库外键。
ALTER TABLE sys_employee
    ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'EHR'
        COMMENT '人员来源：EHR或LOCAL_BOOTSTRAP';
CREATE INDEX idx_sys_employee_source_status
    ON sys_employee (source_type, employment_status, account_status);

CREATE TABLE sys_local_credential (
    employee_id BIGINT NOT NULL PRIMARY KEY COMMENT '关联的本地员工主键',
    password_hash VARCHAR(255) NOT NULL COMMENT '带算法标识的不可逆密码哈希',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '凭据状态：ACTIVE或DISABLED',
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE COMMENT '下次登录是否要求修改密码',
    password_changed_at TIMESTAMP NULL COMMENT '密码最近修改时间',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录最近更新时间'
) COMMENT = '管理后台本地密码凭据';

