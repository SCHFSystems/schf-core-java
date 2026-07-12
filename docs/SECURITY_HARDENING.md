# Security Hardening

Sprint 22E extends the Sprint 22D foundation. It is still not a claim of final or complete security.

## Password Lifecycle

- `POST /api/auth/change-password` requires the authenticated user's current password.
- New passwords must contain between 12 and 72 characters.
- A successful password change clears `must_change_password` and revokes every refresh token.
- Public and administrative reset tokens expire after 30 minutes and are single-use.
- `password_changed_at` records password updates.

## Reset Delivery

`PasswordResetDeliveryService` isolates token creation from delivery.

- The default non-development implementation is a safe no-op until SMTP or another provider is configured.
- `CapturingPasswordResetDeliveryService` exists only in `dev` and `test` profiles.
- Capture is in memory, bounded to the latest delivery per normalized email and never logged.
- No HTTP endpoint exposes the captured token.
- No SMTP credential or provider secret is present in the repository.

## Brute Force and Lockout

The public authentication endpoints use an in-memory fixed-window rate limiter keyed by remote IP and endpoint:

- `/api/auth/login`;
- `/api/auth/refresh`;
- `/api/auth/password/forgot`;
- `/api/auth/password/reset`.

Defaults use a 60-second window. Limits are environment-configurable. This protects one application instance only; Redis-backed distributed limiting is required before horizontal scaling.

Known users are temporarily locked after five failed logins by default. Lockout state is persisted in `app_users`, failures are updated under a pessimistic row lock and responses remain the same generic 401 used for invalid credentials. IP-only rate limiting covers unknown credentials without creating account-enumeration signals.

## Metrics

Micrometer counters:

- `schf.auth.login.success`;
- `schf.auth.login.failure`;
- `schf.auth.lockout`;
- `schf.auth.password.reset.requested`;
- `schf.auth.refresh.denied`;
- `schf.auth.rate.limit.denied`.

These meters have no email, username, user ID, organization ID, IP, token or other PII labels.

## Configuration

```text
SCHF_MAXIMUM_FAILED_LOGINS=5
SCHF_LOCKOUT_SECONDS=900
SCHF_RATE_LIMIT_WINDOW_SECONDS=60
SCHF_LOGIN_RATE_LIMIT=10
SCHF_REFRESH_RATE_LIMIT=20
SCHF_FORGOT_PASSWORD_RATE_LIMIT=5
SCHF_RESET_PASSWORD_RATE_LIMIT=10
```

## Future Hardening

- Redis-backed distributed rate limiting;
- adaptive lockout and anomaly detection;
- SMTP/provider-backed reset delivery;
- device/session management;
- asymmetric signing keys and automated rotation;
- MFA and external identity providers after product prioritization.
