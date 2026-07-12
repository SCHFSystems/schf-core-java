# Audit Log Query

`GET /api/admin/audit-logs` provides a tenant-scoped, protected and paginated audit view.

## Authorization

The endpoint requires `AUDIT_READ` or `ADMIN_ACCESS`. `AUDIT_READ` is assigned to development `OWNER` and `ADMIN` roles. Migration V4 also assigns it to existing OWNER and ADMIN roles.

## Filters

- `action`: exact action code;
- `actor`: user UUID;
- `resourceType`: exact resource type;
- `from`: inclusive ISO-8601 timestamp;
- `to`: inclusive ISO-8601 timestamp;
- `page`: zero-based, default 0;
- `size`: default 20, constrained to 1 through 100.

Results are sorted by `occurredAt` descending.

## Response Safety

The API returns only:

- audit ID;
- organization ID;
- actor ID;
- action;
- resource type and ID;
- outcome;
- timestamp.

It deliberately excludes `details`, IP address and user-agent. Passwords, hashes, JWTs, refresh tokens, reset tokens and raw request payloads are never returned.

Every query has a mandatory organization predicate derived from the authenticated tenant context.
