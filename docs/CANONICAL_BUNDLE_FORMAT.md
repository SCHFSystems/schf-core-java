# Canonical Bundle Format

Version `1.0` is a ZIP-compatible `.schf` archive rooted at `bundle/`. It contains `manifest.json`, `checksums.sha256`, seven NDJSON data files and an optional sanitized summary.

The Core contract contains only organization, user, supplier, category, financial-account, payable and payment records. Every record has a UUID `externalId`; references use external UUIDs. Money is positive `NUMERIC(19,4)` compatible data and dates use ISO-8601.

The manifest declares format/schema versions, source identifier, source instance UUID, generation time, generator/core versions, organization external UUID, counts, SHA-256 checksums, anonymization flag and correlation UUID. Credentials, source paths, connection data, passwords and tokens are prohibited.

Users contain role codes but no password. Imported users receive an unguessable generated password and `mustChangePassword=true`.
