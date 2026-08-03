# ACK infrastructure templates

These manifests are reviewable baselines. The current non-production target is ACK cluster `biel-ai`, namespace `biel-life-camp`. Create the following Secrets with the environment delivery system before applying Nacos:

- `nacos-rds-secret`: `MYSQL_SERVICE_HOST`, `MYSQL_SERVICE_PORT`, `MYSQL_SERVICE_DB_NAME` (must be `nacos_config`), `MYSQL_SERVICE_USER`, `MYSQL_SERVICE_PASSWORD`, `MYSQL_DATABASE_NUM` (must be `1`) and `MYSQL_SERVICE_DB_PARAM`. Use the private RDS endpoint and a dedicated least-privilege account.
- `nacos-auth-secret`: `NACOS_AUTH_ENABLE` (must be `true`), `NACOS_AUTH_SYSTEM_TYPE` (must be `nacos`), `NACOS_AUTH_TOKEN`, `NACOS_AUTH_IDENTITY_KEY`, `NACOS_AUTH_IDENTITY_VALUE`. The token must be Base64 for at least 32 raw characters, and all three cluster nodes must use the same token and identity values.
- `sentinel-dashboard-secret`: dashboard credentials/JVM options required by the internally approved image.
- `xxl-job-admin-secret`: Spring datasource URL/user/password for `xxl_job_db`, access token and mail/alert settings.

Import and checksum `distribution/conf/mysql-schema.sql` from the exact Nacos 3.1.1 tag before starting Nacos. Nacos does not create or migrate its schema automatically. Initialize the first administrator password through `POST /nacos/v3/auth/user/admin` after the cluster is healthy and keep the password outside Git.

Store the initialized administrator username/password in `nacos-admin-secret` with keys `NACOS_ADMIN_USERNAME` and `NACOS_ADMIN_PASSWORD`. This Secret is for controlled operations and smoke tests only; do not inject it into the long-running Nacos Pods.

The Nacos image is mirrored by ACR Enterprise artifact subscription into `boen/biel_rep/nacos:v3.1.1`; the manifest pins digest `sha256:cc5a4dca5cd2e637b9efc734205fe4d820a4dcfb089f411167a87ee3acf79e6f` through the VPC endpoint. Keep all Services as ClusterIP. `nacos:8848` is the in-cluster client endpoint, and `nacos-console:8080` is for private operations access only. Do not create a public Ingress for either Service.

Before applying, verify that the two Secrets exist without printing their values:

```powershell
kubectl -n biel-life-camp get secret nacos-rds-secret nacos-auth-secret
kubectl apply --dry-run=server -f .\server\deploy\kubernetes\infrastructure\nacos.yaml
kubectl apply -f .\server\deploy\kubernetes\infrastructure\nacos.yaml
kubectl -n biel-life-camp rollout status statefulset/nacos --timeout=10m
```

After rollout, verify all three pods, the Nacos 3.x readiness/liveness endpoints under `/nacos/v3/admin/core/state/`, `/nacos/actuator/prometheus`, authenticated login, config publish/read, service register/discover and one-pod restart recovery. The application endpoint is `nacos.biel-life-camp.svc.cluster.local:8848`.

The upstream Nacos 3.1.1 image declares root as its runtime user and its startup script writes `${BASE_DIR}/conf/cluster.conf`. The current non-production manifest therefore cannot set `runAsNonRoot` without a derived image, but still uses `RuntimeDefault` seccomp, drops all Linux capabilities, disables privilege escalation and ServiceAccount token mounting, and exposes only ClusterIP Services. Replace it with a reviewed non-root derived image before treating this baseline as production-hardened.

Sentinel rules live in Nacos, not in Dashboard memory. Replace the placeholder Sentinel image with an internally built image from the 1.8.9 source tag because Sentinel does not publish a production Docker image for this project.

## Development Redis

`redis-dev.yaml` deploys one Redis 7.4.9 StatefulSet only for the current non-production development environment. It is not the planned production or shared non-production Tair master-replica service and must not be treated as highly available. The in-cluster endpoint is `redis.biel-life-camp.svc.cluster.local:6379`.

The manifest pins the reviewed ACR image digest, runs as UID 999, uses a read-only root filesystem, persists AOF and RDB data on a 20 GiB ESSD volume, and retains the underlying PV if the PVC is deleted. It expects `redis-dev-secret` to be created outside Git with `REDIS_USERNAME`, `REDIS_PASSWORD`, and `users.acl` keys. The password-protected default user is limited to `PING` and `AUTH`; applications must authenticate as the generated `app` user.

For local development tools, start the authenticated loopback-only tunnel from the repository root:

```powershell
.\server\scripts\Connect-AckRedisDev.ps1
```

IDEA Database, RedisInsight, and redis-cli can then use `127.0.0.1:6379`, username `app`, and database `0`. The script copies the current password to the clipboard and sets `REDISCLI_AUTH` in the current PowerShell without printing or persisting the password. Stop the managed tunnel with `.\server\scripts\Connect-AckRedisDev.ps1 -Stop`. This local endpoint does not make Redis externally reachable.

Create or rotate the Secret without printing its value, then validate and apply:

```powershell
$bytes = New-Object byte[] 36
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$password = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$sha256 = [Security.Cryptography.SHA256]::Create()
try {
  $hashBytes = $sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes($password))
} finally {
  $sha256.Dispose()
}
$hash = ([BitConverter]::ToString($hashBytes)).Replace('-', '').ToLowerInvariant()
$acl = "user default reset on #$hash ~* &* -@all +ping +auth`nuser app reset on #$hash ~* &* +@all`n"
$aclFile = New-TemporaryFile
try {
  [IO.File]::WriteAllText($aclFile.FullName, $acl, (New-Object Text.UTF8Encoding($false)))
  kubectl -n biel-life-camp create secret generic redis-dev-secret `
    --from-literal=REDIS_USERNAME=app `
    --from-literal=REDIS_PASSWORD=$password `
    --from-file=users.acl=$($aclFile.FullName) `
    --dry-run=client -o yaml | kubectl apply -f -
} finally {
  Remove-Item -LiteralPath $aclFile.FullName -Force
  $password = $null
}

kubectl apply --dry-run=server -f .\server\deploy\kubernetes\infrastructure\redis-dev.yaml
kubectl apply -f .\server\deploy\kubernetes\infrastructure\redis-dev.yaml
kubectl -n biel-life-camp rollout status statefulset/redis --timeout=10m
```

The ACK Terway installation currently has NetworkPolicy enforcement disabled. The manifest records the intended namespace-local ingress policy, but this policy is not an effective isolation boundary until Terway NetworkPolicy is enabled and verified. Until then, rely on the Redis ACL and ClusterIP-only Service, and do not expose Redis through Ingress, LoadBalancer, or NodePort.
