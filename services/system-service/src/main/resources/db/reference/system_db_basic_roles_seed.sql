-- Biel Life Camp system_db built-in role seed.
-- This script is safe to rerun after the role table has been created.

USE camp_system_test_db;

START TRANSACTION;

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
    (8, 'READ_ONLY', '只读观察员', 'ACTIVE', TRUE)
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    status = VALUES(status),
    built_in = VALUES(built_in);

COMMIT;

SELECT id, role_code, role_name, status, built_in
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
