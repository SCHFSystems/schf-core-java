CREATE TABLE instance_setup (
    id UUID PRIMARY KEY,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    organization_id UUID REFERENCES organizations(id),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO instance_setup (id, completed) VALUES (gen_random_uuid(), FALSE);
