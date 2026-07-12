# Migration Importer

Administrative endpoints under `/api/admin/migrations` validate, dry-run, import and query canonical jobs. Uploads require `.schf` or `.zip`, are copied only to the configured controlled workbench and are deleted after processing. Clients cannot submit server paths.

POST operations require `MIGRATION_IMPORT`. GET operations require `MIGRATION_READ`, `MIGRATION_IMPORT` or `ADMIN_ACCESS`. The target organization and actor come from the authenticated principal.

Import order is organization mapping, users, suppliers, categories, financial accounts, payables and payments. The Core has no source connector or source schema dependency.
