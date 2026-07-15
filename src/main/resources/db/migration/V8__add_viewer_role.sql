INSERT INTO permissions (id, code, name, description, created_at)
VALUES
    (gen_random_uuid(), 'SUPPLIER_READ', 'Supplier read', 'Allows supplier queries', CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'CATEGORY_READ', 'Category read', 'Allows category queries', CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'ACCOUNT_READ', 'Financial account read', 'Allows financial account queries', CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'PAYABLE_READ', 'Payable read', 'Allows payable queries', CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'REPORT_READ', 'Report read', 'Allows report queries', CURRENT_TIMESTAMP),
    (gen_random_uuid(), 'USER_READ', 'User read', 'Allows user queries', CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

INSERT INTO roles (id, organization_id, code, name, description, active, created_at, updated_at)
SELECT gen_random_uuid(), o.id, 'VIEWER', 'Viewer', 'Read-only user', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM roles r WHERE r.organization_id = o.id AND r.code = 'VIEWER'
);

INSERT INTO role_permissions (id, role_id, permission_id, created_at)
SELECT gen_random_uuid(), r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code IN ('SUPPLIER_READ', 'CATEGORY_READ', 'ACCOUNT_READ',
                                  'PAYABLE_READ', 'REPORT_READ', 'USER_READ')
WHERE r.code = 'VIEWER'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
