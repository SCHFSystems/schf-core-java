package br.com.schf.migration.domain;

import java.util.List;
import java.util.Map;

public record CanonicalBundle(
    String bundleId,
    BundleManifest manifest,
    List<CanonicalOrganization> organizations,
    List<CanonicalUser> users,
    List<CanonicalSupplier> suppliers,
    List<CanonicalCategory> categories,
    List<CanonicalFinancialAccount> financialAccounts,
    List<CanonicalPayable> payables,
    List<CanonicalPayment> payments,
    Map<String, String> verifiedChecksums
) {
    public long totalRecords() {
        return (long) organizations.size() + users.size() + suppliers.size() + categories.size()
            + financialAccounts.size() + payables.size() + payments.size();
    }
}
