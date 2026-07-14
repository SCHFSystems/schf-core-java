package br.com.schf.migration.domain;

import java.util.List;

public final class BundlePaths {
    public static final String MANIFEST = "bundle/manifest.json";
    public static final String CHECKSUMS = "bundle/checksums.sha256";
    public static final String ORGANIZATIONS = "bundle/data/organizations.ndjson";
    public static final String USERS = "bundle/data/users.ndjson";
    public static final String SUPPLIERS = "bundle/data/suppliers.ndjson";
    public static final String CATEGORIES = "bundle/data/categories.ndjson";
    public static final String ACCOUNTS = "bundle/data/financial-accounts.ndjson";
    public static final String COUNTERPARTIES = "bundle/data/counterparties.ndjson";
    public static final String PAYABLES = "bundle/data/payables.ndjson";
    public static final String PAYMENTS = "bundle/data/payments.ndjson";
    public static final String SUMMARY = "bundle/reports/migration-summary.json";
    public static final List<String> DATA_FILES = List.of(
        ORGANIZATIONS, USERS, SUPPLIERS, CATEGORIES, ACCOUNTS, COUNTERPARTIES, PAYABLES, PAYMENTS);
    public static final List<String> REQUIRED_FILES = List.of(
        MANIFEST, CHECKSUMS, ORGANIZATIONS, USERS, SUPPLIERS, CATEGORIES, ACCOUNTS, COUNTERPARTIES, PAYABLES, PAYMENTS);

    private BundlePaths() {
    }
}
