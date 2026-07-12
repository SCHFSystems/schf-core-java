# Migration Security

Validation occurs before domain persistence. The reader enforces compressed, per-entry, aggregate uncompressed and file-count limits. It rejects absolute/traversal/backslash paths, duplicate names, unexpected files and Unix symbolic links.

Every data file must match both the manifest checksum and `checksums.sha256`. JSON uses strict fields; NDJSON, UUIDs, counts, enums, money, dates, duplicates and cross-record references are validated.

Logs and versioned reports contain only job/correlation IDs, entity type, counts, status and sanitized error codes. Bundle content, financial rows, documents, full email addresses, credentials and tokens are never logged.

Real data is not accepted into Git, CI fixtures, reports or the vault. Local workbench and detailed reports remain runtime-only.
