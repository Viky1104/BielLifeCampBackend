-- Biel Life Camp EHR 人员全量同步前后只读检查脚本。
-- 适用环境：MySQL 8.0 / camp_system_test_db。
-- 本脚本只执行 SELECT，不会修改任何业务数据。

USE camp_system_test_db;

-- 01. 数据库与时间基线。应用使用 UTC 持久化时间，需重点关注数据库时区。
SELECT
    DATABASE() AS database_name,
    VERSION() AS mysql_version,
    @@session.time_zone AS session_time_zone,
    @@global.time_zone AS global_time_zone,
    UTC_TIMESTAMP() AS utc_now,
    NOW() AS database_now;

-- 02. 检查同步和认证依赖的 16 张表是否齐全。
WITH expected_table AS (
    SELECT 'sys_employee' AS table_name
    UNION ALL SELECT 'sys_local_credential'
    UNION ALL SELECT 'sys_external_identity'
    UNION ALL SELECT 'sys_wechat_profile'
    UNION ALL SELECT 'sys_role'
    UNION ALL SELECT 'sys_permission'
    UNION ALL SELECT 'sys_role_permission'
    UNION ALL SELECT 'sys_role_assignment'
    UNION ALL SELECT 'sys_user_session'
    UNION ALL SELECT 'sys_refresh_token'
    UNION ALL SELECT 'sys_operation_audit'
    UNION ALL SELECT 'sys_integration_state'
    UNION ALL SELECT 'sys_ehr_sync_run'
    UNION ALL SELECT 'sys_ehr_employee_stage'
    UNION ALL SELECT 'sys_ehr_sync_issue'
    UNION ALL SELECT 'sys_task_lease'
)
SELECT
    e.table_name,
    CASE WHEN t.table_name IS NULL THEN 'MISSING' ELSE 'READY' END AS check_status
FROM expected_table e
LEFT JOIN information_schema.tables t
       ON t.table_schema = DATABASE()
      AND t.table_name = e.table_name
ORDER BY e.table_name;

-- 03. 检查 8 个内置角色。首次同步至少要求 EMPLOYEE 唯一、启用且为内置角色。
SELECT
    role_code,
    role_name,
    status,
    built_in,
    id
FROM sys_role
WHERE role_code IN (
    'EMPLOYEE',
    'SUPER_ADMIN',
    'OPS_ADMIN',
    'HR_ADMIN',
    'CONTENT_REVIEWER',
    'MALL_VERIFIER',
    'AUDITOR',
    'READ_ONLY'
)
ORDER BY id;

SELECT
    CASE
        WHEN COUNT(*) = 1
         AND SUM(status = 'ACTIVE') = 1
         AND SUM(built_in = TRUE) = 1
        THEN 'PASS'
        ELSE 'STOP'
    END AS employee_role_check,
    COUNT(*) AS employee_role_count,
    SUM(status = 'ACTIVE') AS active_count,
    SUM(built_in = TRUE) AS built_in_count
FROM sys_role
WHERE role_code = 'EMPLOYEE';

-- 04. 首次同步门禁状态。首次同步前应存在 EHR 行且 initial_sync_completed = 0。
SELECT
    connection_code,
    initial_sync_completed,
    last_successful_watermark,
    last_successful_at,
    updated_at
FROM sys_integration_state
WHERE connection_code = 'EHR';

-- 05. 检查是否有仍有效的全量同步租约。存在结果时不要再次触发。
SELECT
    task_type,
    task_id,
    idempotency_key,
    attempt,
    lease_expires_at,
    status,
    failure_digest,
    updated_at
FROM sys_task_lease
WHERE task_type = 'EHR_EMPLOYEE_FULL_SYNC'
  AND status = 'RUNNING'
  AND lease_expires_at > UTC_TIMESTAMP();

-- 06. 同步前人员存量。首次同步通常应为 0；非 0 时必须确认本次是覆盖同步。
SELECT
    COUNT(*) AS total_employee_count,
    COALESCE(SUM(employment_status = 'ACTIVE'), 0) AS active_employee_count,
    COALESCE(SUM(employment_status = 'RESIGNED'), 0) AS resigned_employee_count,
    COALESCE(SUM(account_status = 'ACTIVE'), 0) AS active_account_count,
    COALESCE(SUM(binding_status = 'BOUND'), 0) AS bound_employee_count,
    COALESCE(SUM(mobile_hash IS NULL), 0) AS missing_mobile_count
FROM sys_employee;

-- 07. 同步前逻辑关系检查。项目不使用数据库外键，因此由本查询发现悬空关系。
SELECT 'role_assignment_missing_employee' AS issue_type, COUNT(*) AS issue_count
FROM sys_role_assignment a
LEFT JOIN sys_employee e ON e.id = a.employee_id
WHERE e.id IS NULL
UNION ALL
SELECT 'role_assignment_missing_role', COUNT(*)
FROM sys_role_assignment a
LEFT JOIN sys_role r ON r.id = a.role_id
WHERE r.id IS NULL
UNION ALL
SELECT 'supervisor_missing_employee', COUNT(*)
FROM sys_employee e
LEFT JOIN sys_employee s ON s.id = e.supervisor_employee_id
WHERE e.supervisor_employee_id IS NOT NULL
  AND s.id IS NULL;

-- 08. 最近同步记录及最近一次运行的暂存数量。
SELECT
    id,
    idempotency_key,
    run_type,
    trigger_type,
    status,
    fetched_count,
    inserted_count,
    updated_count,
    resigned_count,
    role_initialized_count,
    issue_count,
    failure_code,
    failure_digest,
    started_at,
    completed_at,
    created_at
FROM sys_ehr_sync_run
ORDER BY created_at DESC, id DESC
LIMIT 10;

SELECT
    r.id AS latest_run_id,
    r.status,
    r.fetched_count,
    COUNT(s.ehr_person_id) AS stage_count,
    r.failure_code,
    r.completed_at
FROM sys_ehr_sync_run r
LEFT JOIN sys_ehr_employee_stage s ON s.run_id = r.id
WHERE r.id = (
    SELECT id
    FROM sys_ehr_sync_run
    ORDER BY created_at DESC, id DESC
    LIMIT 1
)
GROUP BY r.id, r.status, r.fetched_count, r.failure_code, r.completed_at;

-- 09. 同步后人员字段质量。必填字段缺失必须为 0；可选字段用于评估 EHR 数据完整度。
SELECT
    COUNT(*) AS employee_count,
    COALESCE(SUM(ehr_person_id IS NULL OR TRIM(ehr_person_id) = ''), 0)
        AS missing_ehr_person_id,
    COALESCE(SUM(employee_no IS NULL OR TRIM(employee_no) = ''), 0)
        AS missing_employee_no,
    COALESCE(SUM(display_name IS NULL OR TRIM(display_name) = ''), 0)
        AS missing_display_name,
    COALESCE(SUM(mobile_hash IS NULL), 0) AS missing_mobile,
    COALESCE(SUM(primary_org_code IS NULL OR TRIM(primary_org_code) = ''), 0)
        AS missing_org_code,
    COALESCE(SUM(primary_org_name IS NULL OR TRIM(primary_org_name) = ''), 0)
        AS missing_org_name,
    COALESCE(SUM(birthday IS NULL), 0) AS missing_birthday,
    COALESCE(SUM(gender_code = 'UNKNOWN'), 0) AS unknown_gender,
    COALESCE(SUM(supervisor_employee_no IS NULL OR TRIM(supervisor_employee_no) = ''), 0)
        AS missing_supervisor_employee_no,
    COALESCE(SUM(job_grade IS NULL OR TRIM(job_grade) = ''), 0)
        AS missing_job_grade,
    COALESCE(SUM(professional_title IS NULL OR TRIM(professional_title) = ''), 0)
        AS missing_professional_title,
    COALESCE(SUM(job_code IS NULL OR TRIM(job_code) = ''), 0)
        AS missing_job_code,
    COALESCE(SUM(position_code IS NULL OR TRIM(position_code) = ''), 0)
        AS missing_position_code
FROM sys_employee
WHERE employment_status = 'ACTIVE';

-- 10. 直属上级解析情况。直属上级工号存在但本地 ID 为空属于警告，需抽样核实范围。
SELECT
    COUNT(*) AS active_employee_count,
    COALESCE(SUM(supervisor_employee_no IS NOT NULL), 0) AS supervisor_number_count,
    COALESCE(SUM(supervisor_employee_id IS NOT NULL), 0) AS resolved_supervisor_count,
    COALESCE(SUM(supervisor_employee_no IS NOT NULL
                 AND supervisor_employee_id IS NULL), 0)
        AS unresolved_supervisor_count
FROM sys_employee
WHERE employment_status = 'ACTIVE';

SELECT
    employee_no,
    display_name,
    supervisor_employee_no,
    primary_org_code,
    primary_org_name
FROM sys_employee
WHERE employment_status = 'ACTIVE'
  AND supervisor_employee_no IS NOT NULL
  AND supervisor_employee_id IS NULL
ORDER BY primary_org_code, employee_no
LIMIT 100;

-- 11. 普通员工角色覆盖率。同步成功后 missing_employee_role_count 必须为 0。
SELECT
    COUNT(*) AS active_employee_count,
    COALESCE(SUM(a.id IS NOT NULL), 0) AS assigned_employee_role_count,
    COALESCE(SUM(a.id IS NULL), 0) AS missing_employee_role_count
FROM sys_employee e
LEFT JOIN sys_role r
       ON r.role_code = 'EMPLOYEE'
      AND r.status = 'ACTIVE'
LEFT JOIN sys_role_assignment a
       ON a.employee_id = e.id
      AND a.role_id = r.id
      AND a.status = 'ACTIVE'
WHERE e.employment_status = 'ACTIVE';

-- 12. 总体同步前判定。返回 READY 才可以触发；表缺失时请先停止并修复数据库。
SELECT
    CASE
        WHEN (
            SELECT COUNT(*)
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN (
                   'sys_employee',
                   'sys_local_credential',
                  'sys_external_identity',
                  'sys_wechat_profile',
                  'sys_role',
                  'sys_permission',
                  'sys_role_permission',
                  'sys_role_assignment',
                  'sys_user_session',
                  'sys_refresh_token',
                  'sys_operation_audit',
                  'sys_integration_state',
                  'sys_ehr_sync_run',
                  'sys_ehr_employee_stage',
                  'sys_ehr_sync_issue',
                  'sys_task_lease'
              )
        ) <> 16 THEN 'STOP_SCHEMA_INCOMPLETE'
        WHEN (
            SELECT COUNT(*)
            FROM sys_role
            WHERE role_code = 'EMPLOYEE'
              AND status = 'ACTIVE'
              AND built_in = TRUE
        ) <> 1 THEN 'STOP_EMPLOYEE_ROLE_NOT_READY'
        WHEN (
            SELECT COUNT(*)
            FROM sys_integration_state
            WHERE connection_code = 'EHR'
        ) <> 1 THEN 'STOP_EHR_INTEGRATION_STATE_MISSING'
        WHEN EXISTS (
            SELECT 1
            FROM sys_task_lease
            WHERE task_type = 'EHR_EMPLOYEE_FULL_SYNC'
              AND status = 'RUNNING'
              AND lease_expires_at > UTC_TIMESTAMP()
        ) THEN 'STOP_SYNC_ALREADY_RUNNING'
        ELSE 'READY'
    END AS pre_sync_readiness;

-- 13. 同步后最终判定。首次同步执行完成后再次运行本脚本并查看本结果。
SELECT
    CASE
        WHEN COALESCE((
            SELECT status
            FROM sys_ehr_sync_run
            ORDER BY created_at DESC, id DESC
            LIMIT 1
        ), 'NONE') <> 'SUCCEEDED' THEN 'LATEST_SYNC_NOT_SUCCEEDED'
        WHEN NOT EXISTS (
            SELECT 1
            FROM sys_integration_state
            WHERE connection_code = 'EHR'
              AND initial_sync_completed = TRUE
        ) THEN 'STOP_INITIAL_SYNC_GATE_NOT_OPEN'
        WHEN NOT EXISTS (
            SELECT 1
            FROM sys_employee
            WHERE employment_status = 'ACTIVE'
        ) THEN 'STOP_NO_ACTIVE_EMPLOYEE'
        WHEN EXISTS (
            SELECT 1
            FROM sys_employee e
            LEFT JOIN sys_role r
                   ON r.role_code = 'EMPLOYEE'
                  AND r.status = 'ACTIVE'
            LEFT JOIN sys_role_assignment a
                   ON a.employee_id = e.id
                  AND a.role_id = r.id
                  AND a.status = 'ACTIVE'
            WHERE e.employment_status = 'ACTIVE'
              AND a.id IS NULL
        ) THEN 'STOP_EMPLOYEE_ROLE_INCOMPLETE'
        ELSE 'PASS'
    END AS post_sync_readiness;
