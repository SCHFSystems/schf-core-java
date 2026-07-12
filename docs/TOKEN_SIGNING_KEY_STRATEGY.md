# Token Signing Key Strategy

## Current Baseline

SCHF currently signs short-lived access tokens with HS256. The secret:

- is supplied only through `SCHF_JWT_SECRET`;
- has no production fallback in source code;
- must contain at least 32 bytes;
- is never logged or returned;
- is validated together with issuer, audience and expiration.

HS256 is acceptable for the current single-backend foundation when the secret is generated randomly, stored outside Git and limited to trusted backend processes.

## Rotation Limitation

The current implementation has one active symmetric key and no `kid`. Changing the secret immediately invalidates every access token. Refresh tokens remain independently revocable and can issue access tokens under the new key after deployment.

No complex key rotation is implemented in Sprint 22E.

## Future Direction

Before multiple token issuers, external verifiers or internet-scale deployment, evaluate RS256 or EdDSA with:

- a key identifier (`kid`) in JWT headers;
- one active signing key and a bounded set of verification keys;
- overlap between old and new public keys for at least the access-token TTL;
- private keys stored in a secret manager or KMS;
- a documented emergency revocation procedure;
- automated rotation tests and observability that never emits key material.

EdDSA offers small keys and modern primitives. RS256 has broader ecosystem and HSM compatibility. The final choice depends on infrastructure and external verifier requirements.
