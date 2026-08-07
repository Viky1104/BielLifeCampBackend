# system_db migrations

`V1__identity_and_session.sql` is the reviewed P0 identity baseline for employee projections, external identities, RBAC, sessions, rotating refresh tokens, and the EHR initial-sync gate. Future changes must be additive versioned migrations; never edit an applied migration.

`V2__ehr_employee_full_sync.sql` adds the EHR full-snapshot personnel synchronization model:
database-generated employee IDs, the stable EHR person key, birthday/gender/supervisor/grade/title/job/position fields,
encrypted WeChat identity values and profile storage, synchronization run/stage/issue tables, and the database task lease.
Every successful full snapshot atomically updates current employees, initializes the `EMPLOYEE` role, resolves supervisors,
and disables active employees missing from the complete snapshot.

`V3__basic_roles.sql` initializes the protected built-in role catalog from the frozen permission matrix:
`EMPLOYEE`, `SUPER_ADMIN`, `OPS_ADMIN`, `HR_ADMIN`, `CONTENT_REVIEWER`, `MALL_VERIFIER`, `AUDITOR`, and `READ_ONLY`.
It does not assign administrator roles to any employee and does not initialize permissions.

`V4__local_admin_authentication.sql` adds the transitional local administrator credential table and
the employee `source_type`. Passwords are stored only as `{bcrypt}` hashes; EHR missing-snapshot
reconciliation only disables `EHR` employees and preserves `LOCAL_BOOTSTRAP` subjects.

`V6__user_profile_avatar_storage.sql` adds the private object-storage key to the existing
`sys_wechat_profile` table. Apply V6 before deploying the profile-enabled system-service version.
Production keeps application Flyway disabled and runs the migration through a controlled migration job.
Deployment, verification, monitoring, and rollback procedures are documented in
`../reference/system_user_profile_api_and_oss_runbook.md`.

`../reference/system_db_auth_login_ehr_schema.sql` is the consolidated MySQL 8 schema for a new, empty database.
It is intended for design review and controlled fresh initialization. It must not be executed after Flyway V1 through V4,
and it is not an alternative upgrade path for an existing database.

`../reference/system_db_basic_roles_seed.sql` can be rerun against `camp_system_test_db` to align the eight built-in
role records after the tables have been created.

`../reference/system_db_super_admin_bootstrap.sql` is a manually executed MySQL 8 bootstrap for the initial
super administrator. It targets `camp_system_test_db`, creates a local bootstrap employee whose `employee_no`
is `admin` when missing, creates or enables `SUPER_ADMIN`, grants `ALL_ORGS`, seeds the 40 frozen permission
codes, installs the locally generated BCrypt credential, increments affected authorization versions, and appends an
operation-audit record. The local employee has no WeChat binding and is explicitly outside EHR missing-snapshot
reconciliation. If Redis authorization caching is enabled, delete
`biel:security:authz-version:v1:{<employee-id>}` after the transaction; do not revoke the login session.

The consolidated schema contains 16 persistent tables:

- Employee and identity: `sys_employee`, `sys_local_credential`, `sys_external_identity`,
  `sys_wechat_profile`
- RBAC: `sys_role`, `sys_permission`, `sys_role_permission`, `sys_role_assignment`
- Login and audit: `sys_user_session`, `sys_refresh_token`, `sys_operation_audit`
- EHR integration and full sync: `sys_integration_state`, `sys_ehr_sync_run`,
  `sys_ehr_employee_stage`, `sys_ehr_sync_issue`, `sys_task_lease`

Short-lived WeChat login state and first-auth verification tickets stay in Tair/Redis with an expiry.
They are intentionally excluded from the persistent MySQL schema.

The schema intentionally does not create database foreign keys. Cross-table relationships are enforced by
application transactions, existence checks, idempotent writes, and reconciliation during EHR synchronization.
Primary keys, unique constraints, and explicit relationship indexes remain in MySQL to protect uniqueness and
query performance without coupling batch synchronization to database-level reference ordering.

The table catalog, logical relationship ER diagram, application-level reference rules, and unique-constraint
checklist are documented in `../reference/system_db_auth_login_ehr_er.md`.
