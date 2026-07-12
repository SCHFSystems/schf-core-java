# Migration Recovery

The complete bundle is validated before a job enters `IMPORTING`. Each dependency phase runs in its own transaction and commits its external-ID checkpoint. The job stores `last_completed_phase` plus imported/skipped/failed counters.

Unexpected phase failure marks the job `FAILED` in an independent transaction and writes a sanitized migration error. Previously committed phases remain explicit and idempotent; the job is never reported as completed. Re-upload can resume because mapped external IDs are skipped.

The initial batch configuration is 500 records. Current phase implementation uses repository writes within the phase transaction; explicit chunk flushing is the next scaling step before large real bundles. Dry-run validates and records a job without financial persistence.
