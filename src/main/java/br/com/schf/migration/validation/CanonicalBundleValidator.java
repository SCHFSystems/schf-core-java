package br.com.schf.migration.validation;

import br.com.schf.account.FinancialAccountType;
import br.com.schf.migration.domain.BundleManifest;
import br.com.schf.migration.domain.BundlePaths;
import br.com.schf.migration.domain.CanonicalBundle;
import br.com.schf.migration.domain.CanonicalCategory;
import br.com.schf.migration.domain.CanonicalCounterparty;
import br.com.schf.migration.domain.CanonicalFinancialAccount;
import br.com.schf.migration.domain.CanonicalOrganization;
import br.com.schf.migration.domain.CanonicalPayable;
import br.com.schf.migration.domain.CanonicalPayment;
import br.com.schf.migration.domain.CanonicalSupplier;
import br.com.schf.migration.domain.CanonicalUser;
import br.com.schf.payable.PayableStatus;
import br.com.schf.supplier.CategoryType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CanonicalBundleValidator {
    public static final String FORMAT_VERSION = "1.0";
    public static final String FORMAT_VERSION_1_1 = "1.1";
    public static final Set<String> SUPPORTED_FORMATS = Set.of(FORMAT_VERSION, FORMAT_VERSION_1_1);
    public static final String SCHEMA_VERSION = "1";

    private final SecureBundleArchiveReader archiveReader;
    private final ObjectMapper mapper;

    public CanonicalBundleValidator(SecureBundleArchiveReader archiveReader, ObjectMapper mapper) {
        this.archiveReader = archiveReader;
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public ValidatedBundle validate(byte[] archive) {
        var content = archiveReader.read(archive);
        var issues = new ArrayList<ValidationIssue>();
        for (String required : BundlePaths.REQUIRED_FILES) {
            if (!content.files().containsKey(required)) {
                issues.add(issue("MISSING_FILE", required, null, "Required bundle file is missing"));
            }
        }
        failIfAny(issues);

        var manifest = parseJson(content.files().get(BundlePaths.MANIFEST), BundleManifest.class,
            BundlePaths.MANIFEST, issues);
        var declaredChecksums = parseChecksumFile(content.files().get(BundlePaths.CHECKSUMS), issues);
        failIfAny(issues);
        validateManifest(manifest, issues);
        validateChecksums(content.files(), manifest, declaredChecksums, issues);
        failIfAny(issues);

        var organizations = parseNdjson(content.files().get(BundlePaths.ORGANIZATIONS),
            CanonicalOrganization.class, BundlePaths.ORGANIZATIONS, issues);
        var users = parseNdjson(content.files().get(BundlePaths.USERS),
            CanonicalUser.class, BundlePaths.USERS, issues);
        var suppliers = parseNdjson(content.files().get(BundlePaths.SUPPLIERS),
            CanonicalSupplier.class, BundlePaths.SUPPLIERS, issues);
        var categories = parseNdjson(content.files().get(BundlePaths.CATEGORIES),
            CanonicalCategory.class, BundlePaths.CATEGORIES, issues);
        var accounts = parseNdjson(content.files().get(BundlePaths.ACCOUNTS),
            CanonicalFinancialAccount.class, BundlePaths.ACCOUNTS, issues);
        var counterparties = parseNdjson(content.files().get(BundlePaths.COUNTERPARTIES),
            CanonicalCounterparty.class, BundlePaths.COUNTERPARTIES, issues);
        var payables = parseNdjson(content.files().get(BundlePaths.PAYABLES),
            CanonicalPayable.class, BundlePaths.PAYABLES, issues);
        var payments = parseNdjson(content.files().get(BundlePaths.PAYMENTS),
            CanonicalPayment.class, BundlePaths.PAYMENTS, issues);

        var bundle = new CanonicalBundle(content.bundleId(), manifest, organizations, users, suppliers,
            categories, accounts, counterparties, payables, payments, declaredChecksums);
        failIfAny(issues);

        var dataIssues = new ArrayList<ValidationIssue>();
        validateCounts(manifest, Map.of(
            "organizations.ndjson", (long) organizations.size(), "users.ndjson", (long) users.size(),
            "suppliers.ndjson", (long) suppliers.size(), "categories.ndjson", (long) categories.size(),
            "financial-accounts.ndjson", (long) accounts.size(), "counterparties.ndjson", (long) counterparties.size(),
            "payables.ndjson", (long) payables.size(), "payments.ndjson", (long) payments.size()), dataIssues);
        validateDuplicates("ORGANIZATION", organizations.stream().map(CanonicalOrganization::externalId).toList(), dataIssues);
        validateDuplicates("USER", users.stream().map(CanonicalUser::externalId).toList(), dataIssues);
        validateDuplicates("SUPPLIER", suppliers.stream().map(CanonicalSupplier::externalId).toList(), dataIssues);
        validateDuplicates("CATEGORY", categories.stream().map(CanonicalCategory::externalId).toList(), dataIssues);
        validateDuplicates("FINANCIAL_ACCOUNT", accounts.stream().map(CanonicalFinancialAccount::externalId).toList(), dataIssues);
        validateDuplicates("COUNTERPARTY", counterparties.stream().map(CanonicalCounterparty::externalId).toList(), dataIssues);
        validateDuplicates("PAYABLE", payables.stream().map(CanonicalPayable::externalId).toList(), dataIssues);
        validateDuplicates("PAYMENT", payments.stream().map(CanonicalPayment::externalId).toList(), dataIssues);
        validateValues(manifest, organizations, users, suppliers, categories, accounts, counterparties, payables, payments, dataIssues);
        var hasDataIssues = !dataIssues.isEmpty();
        issues.addAll(dataIssues);

        var valid = !hasDataIssues;
        var report = new BundleValidationReport(valid, content.bundleId(), bundle.totalRecords(),
            Map.copyOf(manifest.recordCounts()), List.copyOf(issues));
        return new ValidatedBundle(bundle, report);
    }

    private void validateManifest(BundleManifest manifest, List<ValidationIssue> issues) {
        if (manifest == null) return;
        if (!SUPPORTED_FORMATS.contains(manifest.bundleFormatVersion()))
            issues.add(issue("INCOMPATIBLE_BUNDLE_VERSION", BundlePaths.MANIFEST, null, "Bundle format version is unsupported"));
        if (!SCHEMA_VERSION.equals(manifest.schemaVersion()))
            issues.add(issue("INCOMPATIBLE_SCHEMA_VERSION", BundlePaths.MANIFEST, null, "Bundle schema version is unsupported"));
        if (blank(manifest.sourceSystem()) || manifest.sourceSystem().length() > 120)
            issues.add(issue("INVALID_SOURCE_SYSTEM", BundlePaths.MANIFEST, null, "Source system identifier is invalid"));
        if (manifest.sourceInstanceId() == null || manifest.organizationExternalId() == null
            || manifest.correlationId() == null || manifest.generatedAt() == null)
            issues.add(issue("INVALID_MANIFEST_IDENTITY", BundlePaths.MANIFEST, null, "Manifest identity fields are required"));
        if (manifest.recordCounts() == null || manifest.fileChecksums() == null)
            issues.add(issue("INVALID_MANIFEST_MAPS", BundlePaths.MANIFEST, null, "Manifest counts and checksums are required"));
    }

    private void validateChecksums(Map<String, byte[]> files, BundleManifest manifest,
                                   Map<String, String> checksumFile, List<ValidationIssue> issues) {
        for (String path : BundlePaths.DATA_FILES) {
            var actual = SecureBundleArchiveReader.sha256(files.get(path));
            var listed = checksumFile.get(path);
            var declared = manifest.fileChecksums().get(path);
            if (!actual.equalsIgnoreCase(listed == null ? "" : listed)
                || !actual.equalsIgnoreCase(declared == null ? "" : declared)) {
                issues.add(issue("CHECKSUM_MISMATCH", path, null, "Bundle file checksum does not match"));
            }
        }
    }

    private Map<String, String> parseChecksumFile(byte[] bytes, List<ValidationIssue> issues) {
        var result = new LinkedHashMap<String, String>();
        var lines = new String(bytes, StandardCharsets.UTF_8).split("\\R");
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].isBlank()) continue;
            var parts = lines[index].split("  ", 2);
            if (parts.length != 2 || !parts[0].matches("[a-fA-F0-9]{64}")
                || !BundlePaths.DATA_FILES.contains(parts[1]) || result.put(parts[1], parts[0].toLowerCase()) != null) {
                issues.add(issue("INVALID_CHECKSUM_FILE", BundlePaths.CHECKSUMS, (long) index + 1,
                    "Checksum entry is invalid"));
            }
        }
        if (!result.keySet().containsAll(BundlePaths.DATA_FILES)) {
            issues.add(issue("INCOMPLETE_CHECKSUM_FILE", BundlePaths.CHECKSUMS, null,
                "Checksum file does not cover every data file"));
        }
        return Map.copyOf(result);
    }

    private <T> T parseJson(byte[] bytes, Class<T> type, String file, List<ValidationIssue> issues) {
        try {
            return mapper.readValue(bytes, type);
        } catch (IOException ex) {
            issues.add(issue("INVALID_JSON", file, null, "JSON document is invalid"));
            return null;
        }
    }

    private <T> List<T> parseNdjson(byte[] bytes, Class<T> type, String file, List<ValidationIssue> issues) {
        var records = new ArrayList<T>();
        var lines = new String(bytes, StandardCharsets.UTF_8).split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].isBlank()) continue;
            try {
                records.add(mapper.readValue(lines[index], type));
            } catch (JsonProcessingException ex) {
                issues.add(issue("INVALID_NDJSON", file, (long) index + 1, "NDJSON record is invalid"));
            }
        }
        return List.copyOf(records);
    }

    private void validateCounts(BundleManifest manifest, Map<String, Long> actual,
                                List<ValidationIssue> issues) {
        if (manifest == null || manifest.recordCounts() == null) return;
        for (var entry : actual.entrySet()) {
            var declared = manifest.recordCounts().get(entry.getKey());
            if (declared == null) {
                issues.add(issue("COUNT_MISMATCH", BundlePaths.MANIFEST, null,
                    "Manifest missing count for " + entry.getKey()));
                continue;
            }
            if (!entry.getValue().equals(declared)) {
                issues.add(issue("COUNT_MISMATCH", BundlePaths.MANIFEST, null,
                    "Declared record count does not match " + entry.getKey()));
            }
        }
    }

    private void validateDuplicates(String type, List<UUID> ids, List<ValidationIssue> issues) {
        var seen = new HashSet<UUID>();
        for (UUID id : ids) {
            if (id == null || !seen.add(id)) {
                issues.add(issue("DUPLICATE_EXTERNAL_ID", null, null,
                    type + " contains a missing or duplicate externalId"));
            }
        }
    }

    private void validateValues(BundleManifest manifest, List<CanonicalOrganization> organizations,
                                List<CanonicalUser> users, List<CanonicalSupplier> suppliers,
                                List<CanonicalCategory> categories, List<CanonicalFinancialAccount> accounts,
                                List<CanonicalCounterparty> counterparties, List<CanonicalPayable> payables,
                                List<CanonicalPayment> payments, List<ValidationIssue> issues) {
        if (organizations.size() != 1 || manifest == null || organizations.stream().noneMatch(
            organization -> organization.externalId().equals(manifest.organizationExternalId()))) {
            issues.add(issue("ORGANIZATION_MAPPING_INVALID", BundlePaths.ORGANIZATIONS, null,
                "Bundle must contain the manifest organization exactly once"));
        }
        categories.forEach(category -> validateEnum(CategoryType.class, category.type(), "INVALID_CATEGORY_TYPE", issues));
        accounts.forEach(account -> validateEnum(FinancialAccountType.class, account.type(), "INVALID_ACCOUNT_TYPE", issues));
        payables.forEach(payable -> {
            validateEnum(PayableStatus.class, payable.status(), "INVALID_PAYABLE_STATUS", issues);
            validateMoney(payable.amount(), "PAYABLE", payable.externalId(), issues);
        });
        payments.forEach(payment -> validateMoney(payment.amount(), "PAYMENT", payment.externalId(), issues));

        var supplierIds = ids(suppliers.stream().map(CanonicalSupplier::externalId).toList());
        var counterpartyIds = ids(counterparties.stream().map(CanonicalCounterparty::externalId).toList());
        var categoryIds = ids(categories.stream().map(CanonicalCategory::externalId).toList());
        var accountIds = ids(accounts.stream().map(CanonicalFinancialAccount::externalId).toList());
        var payableIds = ids(payables.stream().map(CanonicalPayable::externalId).toList());
        for (CanonicalPayable payable : payables) {
            var counterpartyResolved = payable.counterpartyExternalId() != null
                && counterpartyIds.contains(payable.counterpartyExternalId());
            var supplierResolved = payable.supplierExternalId() != null
                && supplierIds.contains(payable.supplierExternalId());
            if ((!counterpartyResolved && !supplierResolved)
                || !categoryIds.contains(payable.categoryExternalId())
                || payable.financialAccountExternalId() != null && !accountIds.contains(payable.financialAccountExternalId())) {
                issues.add(issue("REFERENCE_NOT_FOUND", BundlePaths.PAYABLES, null,
                    "Payable contains an unresolved canonical reference"));
            }
            if (payable.issueDate() == null || payable.dueDate() == null) {
                issues.add(issue("INVALID_DATE", BundlePaths.PAYABLES, null, "Payable dates are required"));
            }
        }
        var totals = new HashMap<UUID, BigDecimal>();
        for (CanonicalPayment payment : payments) {
            if (!payableIds.contains(payment.payableExternalId())
                || !accountIds.contains(payment.financialAccountExternalId())) {
                issues.add(issue("REFERENCE_NOT_FOUND", BundlePaths.PAYMENTS, null,
                    "Payment contains an unresolved canonical reference"));
            }
            if (payment.paymentDate() == null) {
                issues.add(issue("INVALID_DATE", BundlePaths.PAYMENTS, null, "Payment date is required"));
            }
            if (payment.amount() != null)
                totals.merge(payment.payableExternalId(), payment.amount(), BigDecimal::add);
        }
        for (CanonicalPayable payable : payables) {
            if (payable.amount() != null && totals.getOrDefault(payable.externalId(), BigDecimal.ZERO)
                .compareTo(payable.amount()) > 0) {
                issues.add(issue("PAYMENT_TOTAL_EXCEEDED", BundlePaths.PAYMENTS, null,
                    "Payment total exceeds the payable amount"));
            }
        }
        users.forEach(user -> {
            if (blank(user.username()) || blank(user.email()) || blank(user.displayName()))
                issues.add(issue("INVALID_USER", BundlePaths.USERS, null, "Canonical user fields are required"));
        });
    }

    private Set<UUID> ids(List<UUID> values) { return new HashSet<>(values); }

    private void validateMoney(BigDecimal amount, String type, UUID id, List<ValidationIssue> issues) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 4 || amount.precision() > 19) {
            issues.add(issue("INVALID_MONEY", null, null, type + " has an invalid monetary value"));
        }
    }

    private <E extends Enum<E>> void validateEnum(Class<E> type, String value, String code,
                                                   List<ValidationIssue> issues) {
        try {
            Enum.valueOf(type, value == null ? "" : value);
        } catch (IllegalArgumentException ex) {
            issues.add(issue(code, null, null, "Canonical enum value is invalid"));
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private ValidationIssue issue(String code, String file, Long line, String message) {
        return new ValidationIssue(code, file, line, message);
    }
    private void failIfAny(List<ValidationIssue> issues) {
        if (!issues.isEmpty()) throw new BundleValidationException(issues);
    }
}
