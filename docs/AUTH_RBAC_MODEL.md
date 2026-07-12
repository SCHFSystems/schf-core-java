# Authentication and RBAC Model

## Data Model

Flyway migration `V3__security_foundation.sql` creates:

- `roles`, scoped by organization;
- `permissions`, globally identified by code;
- `user_roles`;
- `role_permissions`;
- `refresh_tokens`;
- `password_reset_tokens`;
- `audit_logs`.

It also adds `password_hash`, `last_login_at` and `must_change_password` to `app_users`.

The legacy `app_users.role` column remains temporarily for schema continuity. Authorization decisions use `user_roles` and `role_permissions` only.

## Roles

| Role | Intended use |
| --- | --- |
| `OWNER` | All organization permissions |
| `ADMIN` | All baseline permissions |
| `FINANCE` | Financial read/write and reports |
| `VIEWER` | Financial read and reports |

Roles are seeded only for the development organization. Production role provisioning must be explicit.

## Permissions

- `SUPPLIER_READ`, `SUPPLIER_WRITE`
- `CATEGORY_READ`, `CATEGORY_WRITE`
- `ACCOUNT_READ`, `ACCOUNT_WRITE`
- `PAYABLE_READ`, `PAYABLE_WRITE`
- `PAYMENT_WRITE`
- `REPORT_READ`
- `USER_READ`, `USER_WRITE`
- `ADMIN_ACCESS`

Spring Security authorities use the `PERM_` prefix internally, for example `PERM_PAYMENT_WRITE`. Database and JWT permission codes do not include this prefix.

## Financial Endpoint Matrix

| Endpoint | Permission |
| --- | --- |
| `GET /api/suppliers/**` | `SUPPLIER_READ` |
| `POST /api/suppliers/**` | `SUPPLIER_WRITE` |
| `GET /api/categories/**` | `CATEGORY_READ` |
| `POST /api/categories/**` | `CATEGORY_WRITE` |
| `GET /api/financial-accounts/**` | `ACCOUNT_READ` |
| `POST /api/financial-accounts/**` | `ACCOUNT_WRITE` |
| `GET /api/payables/**` | `PAYABLE_READ` |
| `POST /api/payables/**` | `PAYABLE_WRITE` |
| `POST /api/payables/{id}/payments` | `PAYMENT_WRITE` |

## Token Lifecycle

1. Login validates active user and BCrypt password.
2. The server loads current permissions from the database.
3. A short-lived access JWT and opaque refresh token are returned.
4. Refresh validates the hash, expiry, revocation, active user and organization match.
5. Refresh revokes the old token and issues a new pair.
6. Logout revokes the supplied refresh token.
7. Password reset revokes every active refresh token for the user.

The authentication filter reloads the user and permissions on every request. Deactivating a user or changing RBAC therefore takes effect without waiting for the access token to expire.

## Configuration

Required outside tests:

```text
SCHF_JWT_SECRET=<at-least-32-random-bytes>
```

Optional:

```text
SCHF_JWT_ISSUER=schf-core-java
SCHF_JWT_AUDIENCE=schf-clients
SCHF_ACCESS_TOKEN_TTL_SECONDS=900
SCHF_REFRESH_TOKEN_TTL_SECONDS=1209600
```

Never commit real values. `.env.example` contains development-only fake examples.
