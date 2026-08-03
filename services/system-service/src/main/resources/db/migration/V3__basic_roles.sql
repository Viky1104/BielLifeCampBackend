UPDATE sys_role
SET role_name = '普通员工',
    status = 'ACTIVE',
    built_in = TRUE
WHERE role_code = 'EMPLOYEE';

INSERT INTO sys_role
    (id, role_code, role_name, status, built_in)
VALUES
    (2, 'SUPER_ADMIN', '超级管理员', 'ACTIVE', TRUE),
    (3, 'OPS_ADMIN', '运营管理员', 'ACTIVE', TRUE),
    (4, 'HR_ADMIN', 'HR管理员', 'ACTIVE', TRUE),
    (5, 'CONTENT_REVIEWER', '内容审核员', 'ACTIVE', TRUE),
    (6, 'MALL_VERIFIER', '商城核销员', 'ACTIVE', TRUE),
    (7, 'AUDITOR', '审计员', 'ACTIVE', TRUE),
    (8, 'READ_ONLY', '只读观察员', 'ACTIVE', TRUE);
