ALTER TABLE app_users
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD COLUMN password_changed_at TIMESTAMPTZ;

CREATE INDEX idx_app_users_organization_active
    ON app_users (organization_id, active);
CREATE INDEX idx_app_users_locked_until
    ON app_users (locked_until);
CREATE INDEX idx_audit_logs_organization_occurred_at
    ON audit_logs (organization_id, occurred_at DESC);
CREATE INDEX idx_audit_logs_organization_resource_type
    ON audit_logs (organization_id, resource_type);
CREATE INDEX idx_audit_logs_organization_user_id
    ON audit_logs (organization_id, user_id);

INSERT INTO permissions (id, code, name, description, created_at)
VALUES (gen_random_uuid(), 'AUDIT_READ', 'Audit log read',
        'Allows tenant-scoped audit log queries', CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (id, role_id, permission_id, created_at)
SELECT gen_random_uuid(), r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code = 'AUDIT_READ'
WHERE r.code IN ('OWNER', 'ADMIN')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
