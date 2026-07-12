# Migration Idempotency

Bundle identity is the SHA-256 of the uploaded archive. Completed jobs are unique by organization, bundle ID and dry-run flag.

Record identity is unique by organization, source system, entity type and external UUID. `migration_external_ids` maps canonical identity to internal UUID. Existing mappings are skipped, never matched by names or descriptions and never silently overwrite domain data.

Re-uploading a completed bundle returns the existing job. A failed job can be submitted again and resumes through persisted external mappings/checkpoints.
