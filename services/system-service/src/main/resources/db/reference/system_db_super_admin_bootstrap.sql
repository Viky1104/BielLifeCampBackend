-- 超级管理员初始化脚本。
-- MySQL 8.0+，可在 IDEA、Navicat 或其他数据库脚本执行器中整段执行。
-- 固定目标数据库：camp_system_test_db。
-- 固定管理员工号：admin。
-- admin 不存在时创建本地引导员工；该记录没有微信身份绑定。
-- 先运行 AdminPasswordHashTool 生成哈希，再替换下一行占位符；脚本不会保存明文密码。

USE camp_system_test_db;
SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET @admin_password_hash = '<replace-with-AdminPasswordHashTool-output>';

-- 清理由旧版脚本执行失败后可能遗留的同名存储过程；新版不再使用存储过程。
DROP PROCEDURE IF EXISTS bootstrap_super_admin;

START TRANSACTION;

-- 1. 创建本地超级管理员用户。
-- sys_employee.id 已由 V2 调整为数据库自增，因此不需要人工填写主键。
INSERT INTO sys_employee
    (ehr_person_id, employee_no, display_name, mobile_hash,
     primary_org_id, employment_status, binding_status, account_status,
     authz_version, ehr_source_version, source_type)
SELECT
    'LOCAL_SUPER_ADMIN',
    'admin',
    '系统管理员',
    1,
    NULL,
    'ACTIVE',
    'UNBOUND',
    'ACTIVE',
    1,
    'LOCAL_BOOTSTRAP',
    'LOCAL_BOOTSTRAP'
WHERE NOT EXISTS (
    SELECT 1
    FROM sys_employee
    WHERE employee_no = 'admin'
);
SET @admin_employee_changed = ROW_COUNT();

-- 仅允许脚本恢复自己创建的本地引导用户，不覆盖同工号的真实 EHR 员工资料。
UPDATE sys_employee
SET employment_status = 'ACTIVE',
    account_status = 'ACTIVE',
    primary_org_id = COALESCE(primary_org_id, 1),
    source_type = 'LOCAL_BOOTSTRAP',
    updated_at = CURRENT_TIMESTAMP
WHERE employee_no = 'admin'
  AND ehr_person_id = 'LOCAL_SUPER_ADMIN'
  AND (
      employment_status <> 'ACTIVE'
      OR account_status <> 'ACTIVE'
      OR primary_org_id IS NULL
      OR source_type <> 'LOCAL_BOOTSTRAP'
  );
SET @admin_employee_changed = @admin_employee_changed + ROW_COUNT();

-- 2. 仅在填写了合法的 DelegatingPasswordEncoder BCrypt 哈希后写入本地凭据。
INSERT INTO sys_local_credential
    (employee_id, password_hash, status, must_change_password,
     password_changed_at, created_at, updated_at)
SELECT
    id,
    @admin_password_hash,
    'ACTIVE',
    FALSE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM sys_employee
WHERE employee_no = 'admin'
  AND ehr_person_id = 'LOCAL_SUPER_ADMIN'
  AND @admin_password_hash LIKE '{bcrypt}$%'
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    status = 'ACTIVE',
    must_change_password = FALSE,
    password_changed_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP;

-- 3. 初始化超级管理员内置角色。
INSERT INTO sys_role
    (id, role_code, role_name, status, built_in)
VALUES
    (2, 'SUPER_ADMIN', '超级管理员', 'ACTIVE', TRUE)
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    status = VALUES(status),
    built_in = VALUES(built_in);
SET @catalog_changed = ROW_COUNT();

SET @super_admin_role_id = (
    SELECT id
    FROM sys_role
    WHERE role_code = 'SUPER_ADMIN'
    LIMIT 1
);

-- 3. 初始化权限矩阵中的 40 个权限码。
-- INSERT IGNORE 用于兼容已经存在的权限码；固定主键冲突时不会覆盖其他权限记录。
INSERT IGNORE INTO sys_permission
    (id, permission_code, target_service, status)
VALUES
    (10001, 'system:self:read', 'system-service', 'ACTIVE'),
    (10002, 'system:employee:read', 'system-service', 'ACTIVE'),
    (10003, 'system:employee:freeze', 'system-service', 'ACTIVE'),
    (10004, 'system:employee:unfreeze', 'system-service', 'ACTIVE'),
    (10005, 'system:identity-binding:manage', 'system-service', 'ACTIVE'),
    (10006, 'system:organization:read', 'system-service', 'ACTIVE'),
    (10007, 'system:ehr-sync:read', 'system-service', 'ACTIVE'),
    (10008, 'system:ehr-sync:execute', 'system-service', 'ACTIVE'),
    (10009, 'system:role:read', 'system-service', 'ACTIVE'),
    (10010, 'system:role:manage', 'system-service', 'ACTIVE'),
    (10011, 'system:role:assign', 'system-service', 'ACTIVE'),
    (10012, 'system:integration:read', 'system-service', 'ACTIVE'),
    (10013, 'system:integration:manage', 'system-service', 'ACTIVE'),
    (10014, 'system:integration:credential-rotate', 'system-service', 'ACTIVE'),
    (10015, 'system:integration:test', 'system-service', 'ACTIVE'),
    (10016, 'system:audit:read', 'system-service', 'ACTIVE'),
    (10017, 'system:audit:export', 'system-service', 'ACTIVE'),
    (10101, 'workbench:task:read', 'workbench-service', 'ACTIVE'),
    (10102, 'workbench:task:claim', 'workbench-service', 'ACTIVE'),
    (10103, 'workbench:task:release', 'workbench-service', 'ACTIVE'),
    (10104, 'workbench:task:decide', 'workbench-service', 'ACTIVE'),
    (10105, 'workbench:task:history-read', 'workbench-service', 'ACTIVE'),
    (10201, 'points:account:read-self', 'points-service', 'ACTIVE'),
    (10202, 'points:ledger:read-self', 'points-service', 'ACTIVE'),
    (10203, 'points:account:read', 'points-service', 'ACTIVE'),
    (10204, 'points:ledger:read', 'points-service', 'ACTIVE'),
    (10205, 'points:adjustment:create', 'points-service', 'ACTIVE'),
    (10206, 'points:adjustment:approve', 'points-service', 'ACTIVE'),
    (10207, 'points:rule:read', 'points-service', 'ACTIVE'),
    (10208, 'points:rule:manage', 'points-service', 'ACTIVE'),
    (10301, 'mall:product:read', 'mall-service', 'ACTIVE'),
    (10302, 'mall:product:manage', 'mall-service', 'ACTIVE'),
    (10303, 'mall:product:publish', 'mall-service', 'ACTIVE'),
    (10304, 'mall:inventory:read', 'mall-service', 'ACTIVE'),
    (10305, 'mall:inventory:adjust', 'mall-service', 'ACTIVE'),
    (10306, 'mall:order:read-self', 'mall-service', 'ACTIVE'),
    (10307, 'mall:order:read', 'mall-service', 'ACTIVE'),
    (10308, 'mall:verification:execute', 'mall-service', 'ACTIVE'),
    (10309, 'mall:refund:create', 'mall-service', 'ACTIVE'),
    (10310, 'mall:refund:approve', 'mall-service', 'ACTIVE');
SET @catalog_changed = @catalog_changed + ROW_COUNT();

-- 已存在的同名权限按冻结权限矩阵恢复所属服务和启用状态。
UPDATE sys_permission
SET target_service = CASE
        WHEN permission_code LIKE 'system:%' THEN 'system-service'
        WHEN permission_code LIKE 'workbench:%' THEN 'workbench-service'
        WHEN permission_code LIKE 'points:%' THEN 'points-service'
        WHEN permission_code LIKE 'mall:%' THEN 'mall-service'
        ELSE target_service
    END,
    status = 'ACTIVE'
WHERE permission_code IN (
    'system:self:read',
    'system:employee:read',
    'system:employee:freeze',
    'system:employee:unfreeze',
    'system:identity-binding:manage',
    'system:organization:read',
    'system:ehr-sync:read',
    'system:ehr-sync:execute',
    'system:role:read',
    'system:role:manage',
    'system:role:assign',
    'system:integration:read',
    'system:integration:manage',
    'system:integration:credential-rotate',
    'system:integration:test',
    'system:audit:read',
    'system:audit:export',
    'workbench:task:read',
    'workbench:task:claim',
    'workbench:task:release',
    'workbench:task:decide',
    'workbench:task:history-read',
    'points:account:read-self',
    'points:ledger:read-self',
    'points:account:read',
    'points:ledger:read',
    'points:adjustment:create',
    'points:adjustment:approve',
    'points:rule:read',
    'points:rule:manage',
    'mall:product:read',
    'mall:product:manage',
    'mall:product:publish',
    'mall:inventory:read',
    'mall:inventory:adjust',
    'mall:order:read-self',
    'mall:order:read',
    'mall:verification:execute',
    'mall:refund:create',
    'mall:refund:approve'
);
SET @catalog_changed = @catalog_changed + ROW_COUNT();

-- 4. 给超级管理员角色关联全部冻结权限。
INSERT IGNORE INTO sys_role_permission
    (role_id, permission_id)
SELECT
    @super_admin_role_id,
    permission_record.id
FROM sys_permission permission_record
WHERE permission_record.permission_code IN (
    'system:self:read',
    'system:employee:read',
    'system:employee:freeze',
    'system:employee:unfreeze',
    'system:identity-binding:manage',
    'system:organization:read',
    'system:ehr-sync:read',
    'system:ehr-sync:execute',
    'system:role:read',
    'system:role:manage',
    'system:role:assign',
    'system:integration:read',
    'system:integration:manage',
    'system:integration:credential-rotate',
    'system:integration:test',
    'system:audit:read',
    'system:audit:export',
    'workbench:task:read',
    'workbench:task:claim',
    'workbench:task:release',
    'workbench:task:decide',
    'workbench:task:history-read',
    'points:account:read-self',
    'points:ledger:read-self',
    'points:account:read',
    'points:ledger:read',
    'points:adjustment:create',
    'points:adjustment:approve',
    'points:rule:read',
    'points:rule:manage',
    'mall:product:read',
    'mall:product:manage',
    'mall:product:publish',
    'mall:inventory:read',
    'mall:inventory:adjust',
    'mall:order:read-self',
    'mall:order:read',
    'mall:verification:execute',
    'mall:refund:create',
    'mall:refund:approve'
);
SET @catalog_changed = @catalog_changed + ROW_COUNT();

-- 5. 查找工号 admin 对应的在职、启用员工。
SET @admin_employee_id = (
    SELECT id
    FROM sys_employee
    WHERE employee_no = 'admin'
      AND employment_status = 'ACTIVE'
      AND account_status = 'ACTIVE'
    LIMIT 1
);

-- 已有授权记录时恢复为启用的全集团权限。
UPDATE sys_role_assignment
SET scope_type = 'ALL_ORGS',
    scope_value = '*',
    status = 'ACTIVE'
WHERE employee_id = @admin_employee_id
  AND role_id = @super_admin_role_id;
SET @assignment_changed = ROW_COUNT();

-- 没有授权记录时新增。人工初始化记录使用负数主键，避免与应用正数主键冲突。
SET @role_assignment_id = -CAST(
    CONV(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 15), 16, 10)
    AS SIGNED
);
INSERT INTO sys_role_assignment
    (id, employee_id, role_id, scope_type, scope_value, status)
SELECT
    @role_assignment_id,
    @admin_employee_id,
    @super_admin_role_id,
    'ALL_ORGS',
    '*',
    'ACTIVE'
WHERE @admin_employee_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_assignment
      WHERE employee_id = @admin_employee_id
        AND role_id = @super_admin_role_id
  );
SET @assignment_changed = @assignment_changed + ROW_COUNT();

-- 6. 用户、角色或权限变化时更新现有超级管理员权限版本。
UPDATE sys_employee employee_record
INNER JOIN sys_role_assignment role_assignment
        ON role_assignment.employee_id = employee_record.id
       AND role_assignment.role_id = @super_admin_role_id
       AND role_assignment.status = 'ACTIVE'
SET employee_record.authz_version = employee_record.authz_version + 1,
    employee_record.updated_at = CURRENT_TIMESTAMP
WHERE @catalog_changed > 0
   OR (
       employee_record.id = @admin_employee_id
       AND (
           @assignment_changed > 0
           OR @admin_employee_changed > 0
       )
   );

-- 7. 成功找到 admin 并完成授权时写入操作审计。
SET @audit_id = -CAST(
    CONV(SUBSTRING(REPLACE(UUID(), '-', ''), 1, 15), 16, 10)
    AS SIGNED
);
INSERT INTO sys_operation_audit
    (id, occurred_at, actor_employee_id, module, action,
     result, detail_code, request_id)
SELECT
    @audit_id,
    CURRENT_TIMESTAMP,
    NULL,
    'AUTH',
    'SUPER_ADMIN_BOOTSTRAP',
    'SUCCESS',
    IF(@assignment_changed > 0,
       'SUPER_ADMIN_BOOTSTRAP_GRANTED',
       'SUPER_ADMIN_BOOTSTRAP_NO_CHANGE'),
    CONCAT('DB-', REPLACE(UUID(), '-', ''))
WHERE @admin_employee_id IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM sys_role_assignment
      WHERE employee_id = @admin_employee_id
        AND role_id = @super_admin_role_id
        AND scope_type = 'ALL_ORGS'
        AND scope_value = '*'
        AND status = 'ACTIVE'
  );

COMMIT;

-- 9. 执行结果。result 必须为 SUCCESS，active_permission_count 必须为 40。
SELECT
    CASE
        WHEN @admin_employee_id IS NULL
            THEN 'FAILED_ADMIN_EMPLOYEE_NOT_FOUND_OR_INACTIVE'
        WHEN NOT EXISTS (
            SELECT 1
            FROM sys_local_credential
            WHERE employee_id = @admin_employee_id
              AND status = 'ACTIVE'
        )
            THEN 'FAILED_ADMIN_PASSWORD_NOT_CONFIGURED'
        WHEN (
            SELECT COUNT(DISTINCT permission_record.permission_code)
            FROM sys_role_permission role_permission
            INNER JOIN sys_permission permission_record
                    ON permission_record.id = role_permission.permission_id
                   AND permission_record.status = 'ACTIVE'
            WHERE role_permission.role_id = @super_admin_role_id
        ) < 40
            THEN 'FAILED_PERMISSION_INITIALIZATION_INCOMPLETE'
        WHEN NOT EXISTS (
            SELECT 1
            FROM sys_role_assignment
            WHERE employee_id = @admin_employee_id
              AND role_id = @super_admin_role_id
              AND scope_type = 'ALL_ORGS'
              AND scope_value = '*'
              AND status = 'ACTIVE'
        )
            THEN 'FAILED_ROLE_ASSIGNMENT_INCOMPLETE'
        ELSE 'SUCCESS'
    END AS result,
    @admin_employee_id AS employee_id,
    'admin' AS employee_no,
    @super_admin_role_id AS role_id,
    (
        SELECT authz_version
        FROM sys_employee
        WHERE id = @admin_employee_id
    ) AS authz_version,
    (
        SELECT COUNT(DISTINCT permission_record.permission_code)
        FROM sys_role_permission role_permission
        INNER JOIN sys_permission permission_record
                ON permission_record.id = role_permission.permission_id
               AND permission_record.status = 'ACTIVE'
        WHERE role_permission.role_id = @super_admin_role_id
    ) AS active_permission_count;

-- Redis 授权缓存已启用时，删除：
-- biel:security:authz-version:v1:{<employee_id>}
-- 不需要删除登录会话；旧 JWT 收到 AUTHZ_STALE 后使用刷新令牌获取新 JWT。
