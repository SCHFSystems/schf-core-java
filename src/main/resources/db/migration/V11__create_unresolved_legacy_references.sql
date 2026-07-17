CREATE TABLE unresolved_legacy_references (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations (id),
    external_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(60) NOT NULL,
    source_reference VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT false,
    resolution_status VARCHAR(60) NOT NULL DEFAULT 'UNRESOLVED_LEGACY_REFERENCE',
    migration_job_id UUID REFERENCES migration_jobs (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_unresolved_legacy_ref_org_ext ON unresolved_legacy_references (organization_id, external_id);
CREATE INDEX idx_unresolved_legacy_ref_job ON unresolved_legacy_references (migration_job_id);
