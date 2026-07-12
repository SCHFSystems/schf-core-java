# Security Foundation

Sprint 22D establishes the first security baseline for SCHF v2. It is not the final security architecture.

## Authentication

- Login uses email and password at `POST /api/auth/login`.
- Passwords are stored using BCrypt with cost 12.
- Access tokens are signed JWTs using HS256, with issuer and audience validation.
- The JWT secret is supplied only through `SCHF_JWT_SECRET` and must have at least 32 bytes.
- Access tokens default to 15 minutes.
- Refresh tokens default to 14 days, are random 256-bit values and are stored only as SHA-256 hashes.
- Refresh rotates the token. The used token is revoked immediately.
- Logout revokes the supplied refresh token.
- Password reset tokens are random, hashed at rest, single-use and expire after 30 minutes.
- Forgot-password always returns a generic response to avoid account enumeration. Delivery is intentionally not implemented in this sprint.

## Public Endpoints

- `GET /api/health`
- `GET /actuator/health`
- `GET /actuator/info`
- `GET /actuator/prometheus`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `POST /api/auth/password/forgot`
- `POST /api/auth/password/reset`

`GET /api/auth/me` and every financial endpoint require authentication.

## Tenant Guard

The organization is loaded from the authenticated database user, not from a request field or JWT organization claim. `TenantContextFilter` binds that organization to the request after JWT authentication and clears it in a `finally` block.

Repository operations that receive resource IDs also include the organization ID. Payable creation validates supplier, category and financial account ownership. Payment validates both payable and financial account ownership.

The old automatic first-organization tenant strategy is restricted to the `dev` profile and requires `schf.tenant.strategy=auto`. The default strategy is `authenticated`.

## Audit

Audit records contain organization, user, action, resource, outcome, timestamp, IP and user-agent when a request is available. No password, access token, refresh token or reset token is logged.

Current audited events include:

- login success and failure;
- refresh and logout;
- password reset request and completion;
- supplier, category, financial account and payable creation;
- payable cancellation;
- payment creation/settlement.

## Development Bootstrap

`DevSeed` runs only under the `dev` profile. It creates a development organization, baseline permissions, roles and an owner account from environment-backed bootstrap properties. It never runs in production unless the production process is incorrectly started with the `dev` profile.

## Remaining Evolution

- External identity providers, MFA, SSO and device/session management are outside Sprint 22D.
- Password reset delivery needs a notification provider.
- Key rotation and asymmetric JWT signing should be evaluated before internet exposure.
- Rate limits, brute-force controls and account lockout need a later security sprint.
- User and role management APIs are not part of this baseline.
