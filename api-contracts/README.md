# API Contracts

Each service owns its OpenAPI contract. This module aggregates reviewed contracts only; it must not contain shared domain entities or generated DTOs before an API is approved.

## P0 frozen baseline

The first reviewed design baseline is under `p0/`:

- `common.yaml`: technical schemas and security conventions only.
- `system-service.openapi.yaml`: EHR synchronization, identity, organization, RBAC, integrations, and audit.
- `workbench-service.openapi.yaml`: work item claim and decision flow.
- `points-service.openapi.yaml`: accounts, immutable ledger, adjustments, and internal posting.
- `mall-service.openapi.yaml`: catalog, inventory, exchange orders, verification, and refunds.

The accompanying Chinese design documents are under `docs/04-技术设计/01-身份与权限/`.

These files remain the reviewed design target. The mini-program authentication subset in `system-service.openapi.yaml` is implemented; other operations can still be contract-only. Breaking changes require a new path version; common files must never become a shared domain model package.

## Unified response envelope

All frontend-facing and internal JSON APIs use the same outer structure:

```json
{
  "code": "0",
  "errorMsg": "",
  "data": {}
}
```

- `code` is always a string. Success is `"0"`; failures use a stable business code such as `AUTH_TOKEN_INVALID`.
- `errorMsg` is an empty string on success and a caller-facing message on failure.
- `data` contains the successful business payload and is `null` on failure.
- HTTP status codes retain their protocol meaning; the response envelope does not replace `4xx` or `5xx`.
- `X-Request-Id` remains in the response header and logs rather than becoming a fourth body field.
- New contract-only operations must wrap their domain response schema before implementation.
