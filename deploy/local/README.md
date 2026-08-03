# Local infrastructure

Copy `.env.example` to `.env`, replace every placeholder, then run `docker compose up -d` for MySQL, Redis and Nacos. Add `--profile governance` for Sentinel Dashboard and `--profile scheduler` for XXL-JOB Admin.

Nacos uses its embedded database locally for a low-friction developer setup. ACK environments must use the dedicated `nacos_config` schema in RDS. Before enabling the scheduler profile, import the SQL shipped with the exact XXL-JOB 3.4.0 release into `xxl_job_db`; this repository does not copy or guess vendor tables.

The local SQL creates empty service databases only. Business tables must be introduced by reviewed, service-owned Flyway migrations. The broad `lifecamp` account is local-only; every ACK service uses a separate least-privilege RDS account.
