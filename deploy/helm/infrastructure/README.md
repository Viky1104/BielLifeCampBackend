# ACK infrastructure integration

Nacos 3.1.1, Sentinel Dashboard 1.8.9 and XXL-JOB Admin 3.4.0 run only on private ACK networks. Create Kubernetes Secrets outside Git and inject RDS endpoints, service accounts, Nacos auth material and XXL-JOB access tokens.

- Nacos persists metadata in the dedicated `nacos_config` RDS schema and exposes an internal headless Service.
- Sentinel clients load versioned flow/degrade rules from Nacos; Dashboard is an internal operations UI, not a rule source of truth.
- XXL-JOB Admin uses the dedicated `xxl_job_db`; every service owns its Executor and private task table.
- Filebeat runs as a DaemonSet and writes structured stdout logs to environment-specific Elasticsearch Data Streams.

The repository intentionally does not embed vendor database schemas. Import the exact SQL shipped with the pinned Nacos and XXL-JOB releases, checksum it in the environment repository, and run it before the workloads.
