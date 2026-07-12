CREATE TABLE migration_jobs (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    bundle_id VARCHAR(64) NOT NULL,
    bundle_version VARCHAR(20) NOT NULL,
    source_system VARCHAR(120) NOT NULL,
    status VARCHAR(40) NOT NULL,
    dry_run BOOLEAN NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    total_records BIGINT NOT NULL DEFAULT 0,
    imported_records BIGINT NOT NULL DEFAULT 0,
    skipped_records BIGINT NOT NULL DEFAULT 0,
    failed_records BIGINT NOT NULL DEFAULT 0,
    created_by UUID REFERENCES app_users (id),
    correlation_id UUID NOT NULL,
    error_summary VARCHAR(500),
    last_completed_phase VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organization_id, bundle_id, dry_run)
);

CREATE TABLE migration_bundle_files (
    id UUID PRIMARY KEY,
    migration_job_id UUID NOT NULL REFERENCES migration_jobs (id) ON DELETE CASCADE,
    file_name VARCHAR(180) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    record_count BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (migration_job_id, file_name)
);

CREATE TABLE migration_record_results (
    id UUID PRIMARY KEY,
    migration_job_id UUID NOT NULL REFERENCES migration_jobs (id) ON DELETE CASCADE,
    entity_type VARCHAR(80) NOT NULL,
    external_id UUID NOT NULL,
    result VARCHAR(40) NOT NULL,
    error_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE migration_external_ids (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    source_system VARCHAR(120) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    external_id UUID NOT NULL,
    internal_id UUID NOT NULL,
    migration_job_id UUID NOT NULL REFERENCES migration_jobs (id),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (organization_id, source_system, entity_type, external_id)
);

CREATE TABLE migration_errors (
    id UUID PRIMARY KEY,
    migration_job_id UUID NOT NULL REFERENCES migration_jobs (id) ON DELETE CASCADE,
    entity_type VARCHAR(80),
    external_id UUID,
    error_code VARCHAR(80) NOT NULL,
    sanitized_message VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_migration_jobs_organization_started
    ON migration_jobs (organization_id, started_at DESC);
CREATE INDEX idx_migration_jobs_status ON migration_jobs (status);
CREATE INDEX idx_migration_external_lookup
    ON migration_external_ids (organization_id, source_system, entity_type, external_id);
CREATE INDEX idx_migration_errors_job ON migration_errors (migration_job_id);
CREATE INDEX idx_migration_record_results_job ON migration_record_results (migration_job_id);

INSERT INTO permissions (id, code, name, description, created_at)
VALUES
    (gen_random_uuid(), 'MIGRATION_READ', 'Migration read',
     'Allows tenant-scoped migration job and report queries', CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'MIGRATION_IMPORT', 'Migration import',
     'Allows canonical bundle validation, dry-run and import', CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (id, role_id, permission_id, created_at)
SELECT gen_random_uuid(), r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code IN ('MIGRATION_READ', 'MIGRATION_IMPORT')
WHERE r.code IN ('OWNER', 'ADMIN')
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
