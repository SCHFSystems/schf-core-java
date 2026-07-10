CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations (id),
    username VARCHAR(80) NOT NULL UNIQUE,
    email VARCHAR(180) NOT NULL UNIQUE,
    display_name VARCHAR(160) NOT NULL,
    role VARCHAR(40) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_app_users_organization_id ON app_users (organization_id);
CREATE INDEX idx_app_users_role ON app_users (role);
