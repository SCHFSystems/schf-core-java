# User Administration

Sprint 22E adds tenant-scoped user and role administration. It does not expose password hashes, reset tokens or refresh tokens.

## Permissions

- Read operations require `USER_READ` or `ADMIN_ACCESS`.
- Write operations require `USER_WRITE` or `ADMIN_ACCESS`.
- Every user and role lookup includes the authenticated organization.

## Endpoints

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/admin/users` | List organization users |
| POST | `/api/admin/users` | Create a user with a temporary password |
| GET | `/api/admin/users/{id}` | Read one organization user |
| PATCH | `/api/admin/users/{id}` | Update display name and email |
| POST | `/api/admin/users/{id}/activate` | Activate a user |
| POST | `/api/admin/users/{id}/deactivate` | Deactivate a user and revoke refresh tokens |
| POST | `/api/admin/users/{id}/roles` | Assign a tenant role |
| DELETE | `/api/admin/users/{id}/roles/{roleId}` | Remove a tenant role |
| POST | `/api/admin/users/{id}/password-reset` | Request an administrative password reset |
| GET | `/api/admin/roles` | List tenant roles |

## Safety Rules

- Email and username remain globally unique under the current schema.
- Newly created users must change their temporary password.
- A user cannot deactivate their own account.
- The last active `OWNER` cannot be deactivated or lose the `OWNER` role.
- Role mutation locks the role row while counting owners to avoid concurrent removal races.
- Deactivation revokes all active refresh tokens.
- Administrative password reset sends a reset token through the delivery abstraction; it never returns a password or token in the API.

The legacy `app_users.role` column is still populated for schema continuity. Authorization uses `user_roles` and `role_permissions`.
