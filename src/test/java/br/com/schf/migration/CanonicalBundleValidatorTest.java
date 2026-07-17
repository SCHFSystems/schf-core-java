package br.com.schf.migration;

import static br.com.schf.migration.domain.BundlePaths.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.schf.migration.validation.BundleValidationException;
import br.com.schf.migration.validation.CanonicalBundleValidator;
import br.com.schf.migration.validation.MigrationProperties;
import br.com.schf.migration.validation.SecureBundleArchiveReader;
import br.com.schf.migration.validation.ValidationIssue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CanonicalBundleValidatorTest {
    private MigrationProperties properties;
    private CanonicalBundleValidator validator;

    @BeforeEach
    void setUp() {
        properties = new MigrationProperties();
        validator = new CanonicalBundleValidator(new SecureBundleArchiveReader(properties),
            new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test void validBundlePasses() {
        var result = validator.validate(SyntheticBundleFactory.validArchive());
        assertThat(result.report().valid()).isTrue();
        assertThat(result.bundle().totalRecords()).isEqualTo(9);
    }

    @Test void missingManifestFails() {
        var entries = SyntheticBundleFactory.entries(SyntheticBundleFactory.validData(), "1.0");
        entries.remove(MANIFEST);
        assertCode(SyntheticBundleFactory.zip(entries), "MISSING_FILE");
    }

    @Test void incompatibleVersionFails() {
        assertCode(SyntheticBundleFactory.zip(
            SyntheticBundleFactory.entries(SyntheticBundleFactory.validData(), "99.0")),
            "INCOMPATIBLE_BUNDLE_VERSION");
    }

    @Test void tamperedChecksumFails() {
        var entries = SyntheticBundleFactory.entries(SyntheticBundleFactory.validData(), "1.0");
        entries.put(SUPPLIERS, "{}\n".getBytes(StandardCharsets.UTF_8));
        assertCode(SyntheticBundleFactory.zip(entries), "CHECKSUM_MISMATCH");
    }

    @Test void invalidNdjsonFails() {
        var data = SyntheticBundleFactory.validData();
        data.get(SUPPLIERS).set(0, "{invalid-json}");
        assertCode(SyntheticBundleFactory.zip(SyntheticBundleFactory.entries(data, "1.0")), "INVALID_NDJSON");
    }

    @Test void zipSlipFails() {
        var entries = SyntheticBundleFactory.entries(SyntheticBundleFactory.validData(), "1.0");
        entries.put("../outside.txt", new byte[] {1});
        assertCode(SyntheticBundleFactory.zip(entries), "ZIP_SLIP_REJECTED");
    }

    @Test void uncompressedLimitStopsZipBombShape() {
        properties.setMaximumEntryBytes(2048);
        properties.setMaximumUncompressedBytes(16384);
        var entries = SyntheticBundleFactory.entries(SyntheticBundleFactory.validData(), "1.0");
        entries.put(SUMMARY, new byte[4096]);
        assertCode(SyntheticBundleFactory.zip(entries), "ENTRY_LIMIT_EXCEEDED");
    }

    @Test void duplicateExternalIdFails() {
        var data = SyntheticBundleFactory.validData();
        data.get(SUPPLIERS).add(data.get(SUPPLIERS).getFirst());
        assertDataIssue(SyntheticBundleFactory.zip(SyntheticBundleFactory.entries(data, "1.0")), "DUPLICATE_EXTERNAL_ID");
    }

    @Test void missingReferenceFails() {
        var data = SyntheticBundleFactory.validData();
        data.get(PAYMENTS).set(0, data.get(PAYMENTS).getFirst().replace(
            SyntheticBundleFactory.ACCOUNT_ID.toString(), UUID.randomUUID().toString()));
        assertDataIssue(SyntheticBundleFactory.zip(SyntheticBundleFactory.entries(data, "1.0")), "REFERENCE_NOT_FOUND");
    }

    @Test void invalidMoneyFails() {
        var data = SyntheticBundleFactory.validData();
        data.get(PAYMENTS).set(0, data.get(PAYMENTS).getFirst().replace("50.25", "-1.00"));
        assertDataIssue(SyntheticBundleFactory.zip(SyntheticBundleFactory.entries(data, "1.0")), "INVALID_MONEY");
    }

    @Test void counterpartyIdInSuppliersResolves() {
        var data = SyntheticBundleFactory.validData();
        data.put(COUNTERPARTIES, List.of());
        var viaCp = SyntheticBundleFactory.json(Map.of("externalId",
            UUID.fromString("60000000-0000-0000-0000-000000000010"), "counterpartyExternalId",
            SyntheticBundleFactory.SUPPLIER_ID, "categoryExternalId",
            SyntheticBundleFactory.CATEGORY_ID, "financialAccountExternalId",
            SyntheticBundleFactory.ACCOUNT_ID, "description", "Via counterparty supplier alias",
            "documentNumber", "SYN-VIACP", "issueDate", "2026-06-01", "dueDate", "2026-06-30",
            "amount", "100.00", "status", "OPEN"));
        data.get(PAYABLES).add(viaCp);
        var result = validator.validate(SyntheticBundleFactory.zip(
            SyntheticBundleFactory.entries(data, "1.0")));
        assertThat(result.report().issues())
            .filteredOn(i -> i.code().equals("REFERENCE_NOT_FOUND")).isEmpty();
    }

    @Test void nullIssueDateProducesWarning() {
        var data = SyntheticBundleFactory.validData();
        var nullIssue = new LinkedHashMap<String, Object>();
        nullIssue.put("externalId", SyntheticBundleFactory.PAYABLE_OPEN_ID);
        nullIssue.put("supplierExternalId", SyntheticBundleFactory.SUPPLIER_ID);
        nullIssue.put("categoryExternalId", SyntheticBundleFactory.CATEGORY_ID);
        nullIssue.put("financialAccountExternalId", SyntheticBundleFactory.ACCOUNT_ID);
        nullIssue.put("description", "Null issue date");
        nullIssue.put("documentNumber", "SYN-NULLISSUE");
        nullIssue.put("issueDate", null);
        nullIssue.put("dueDate", "2026-01-31");
        nullIssue.put("amount", "100.00");
        nullIssue.put("status", "OPEN");
        data.get(PAYABLES).set(0, SyntheticBundleFactory.json(nullIssue));
        var result = validator.validate(SyntheticBundleFactory.zip(
            SyntheticBundleFactory.entries(data, "1.0")));
        assertThat(result.report().issues())
            .anyMatch(i -> i.code().equals("MISSING_ISSUE_DATE")
                && i.severity().equals(ValidationIssue.WARNING));
    }

    @Test void nullDueDateProducesWarning() {
        var data = SyntheticBundleFactory.validData();
        var nullDue = new LinkedHashMap<String, Object>();
        nullDue.put("externalId", SyntheticBundleFactory.PAYABLE_OPEN_ID);
        nullDue.put("supplierExternalId", SyntheticBundleFactory.SUPPLIER_ID);
        nullDue.put("categoryExternalId", SyntheticBundleFactory.CATEGORY_ID);
        nullDue.put("financialAccountExternalId", SyntheticBundleFactory.ACCOUNT_ID);
        nullDue.put("description", "Null due date");
        nullDue.put("documentNumber", "SYN-NULLDUE");
        nullDue.put("issueDate", "2026-01-01");
        nullDue.put("dueDate", null);
        nullDue.put("amount", "100.00");
        nullDue.put("status", "OPEN");
        data.get(PAYABLES).set(0, SyntheticBundleFactory.json(nullDue));
        var result = validator.validate(SyntheticBundleFactory.zip(
            SyntheticBundleFactory.entries(data, "1.0")));
        assertThat(result.report().issues())
            .anyMatch(i -> i.code().equals("MISSING_DUE_DATE")
                && i.severity().equals(ValidationIssue.WARNING));
    }

    @Test void nullPaymentDateProducesWarning() {
        var data = SyntheticBundleFactory.validData();
        var nullPayDate = new LinkedHashMap<String, Object>();
        nullPayDate.put("externalId", SyntheticBundleFactory.PAYMENT_ID);
        nullPayDate.put("payableExternalId", SyntheticBundleFactory.PAYABLE_PAID_ID);
        nullPayDate.put("financialAccountExternalId", SyntheticBundleFactory.ACCOUNT_ID);
        nullPayDate.put("paymentDate", null);
        nullPayDate.put("amount", "50.25");
        nullPayDate.put("notes", "Null date payment");
        data.get(PAYMENTS).set(0, SyntheticBundleFactory.json(nullPayDate));
        var result = validator.validate(SyntheticBundleFactory.zip(
            SyntheticBundleFactory.entries(data, "1.0")));
        assertThat(result.report().issues())
            .anyMatch(i -> i.code().equals("MISSING_PAYMENT_DATE")
                && i.severity().equals(ValidationIssue.WARNING));
    }

    @Test void nullPaymentFinancialAccountDoesNotBlock() {
        var data = SyntheticBundleFactory.validData();
        var nullFa = new LinkedHashMap<String, Object>();
        nullFa.put("externalId", SyntheticBundleFactory.PAYMENT_ID);
        nullFa.put("payableExternalId", SyntheticBundleFactory.PAYABLE_PAID_ID);
        nullFa.put("financialAccountExternalId", null);
        nullFa.put("paymentDate", "2026-02-20");
        nullFa.put("amount", "50.25");
        nullFa.put("notes", "No FA payment");
        data.get(PAYMENTS).set(0, SyntheticBundleFactory.json(nullFa));
        var result = validator.validate(SyntheticBundleFactory.zip(
            SyntheticBundleFactory.entries(data, "1.0")));
        assertThat(result.report().issues())
            .filteredOn(i -> i.code().equals("REFERENCE_NOT_FOUND")).isEmpty();
    }

    @Test void overpaymentProducesWarning() {
        var data = SyntheticBundleFactory.validData();
        var overpaid = new LinkedHashMap<String, Object>();
        overpaid.put("externalId", SyntheticBundleFactory.PAYMENT_ID);
        overpaid.put("payableExternalId", SyntheticBundleFactory.PAYABLE_PAID_ID);
        overpaid.put("financialAccountExternalId", SyntheticBundleFactory.ACCOUNT_ID);
        overpaid.put("paymentDate", "2026-02-20");
        overpaid.put("amount", "9999.99");
        overpaid.put("notes", "Overpayment");
        data.get(PAYMENTS).set(0, SyntheticBundleFactory.json(overpaid));
        var result = validator.validate(SyntheticBundleFactory.zip(
            SyntheticBundleFactory.entries(data, "1.0")));
        assertThat(result.report().issues())
            .anyMatch(i -> i.code().equals("PAYMENT_TOTAL_EXCEEDED")
                && i.severity().equals(ValidationIssue.WARNING));
    }

    @Test void nullCategoryDoesNotBlock() {
        var data = SyntheticBundleFactory.validData();
        var noCat = new LinkedHashMap<String, Object>();
        noCat.put("externalId", SyntheticBundleFactory.PAYABLE_PAID_ID);
        noCat.put("supplierExternalId", SyntheticBundleFactory.SUPPLIER_ID);
        noCat.put("categoryExternalId", null);
        noCat.put("financialAccountExternalId", SyntheticBundleFactory.ACCOUNT_ID);
        noCat.put("description", "No category");
        noCat.put("documentNumber", "SYN-NOCAT");
        noCat.put("issueDate", "2026-02-01");
        noCat.put("dueDate", "2026-02-28");
        noCat.put("amount", "50.25");
        noCat.put("status", "PAID");
        data.get(PAYABLES).set(1, SyntheticBundleFactory.json(noCat));
        var result = validator.validate(SyntheticBundleFactory.zip(
            SyntheticBundleFactory.entries(data, "1.0")));
        assertThat(result.report().issues())
            .filteredOn(i -> i.code().equals("REFERENCE_NOT_FOUND")).isEmpty();
    }

    @Test void genuinelyMissingPayableReferenceBlocks() {
        var data = SyntheticBundleFactory.validData();
        var badRef = new LinkedHashMap<String, Object>();
        badRef.put("externalId", SyntheticBundleFactory.PAYABLE_OPEN_ID);
        badRef.put("supplierExternalId", UUID.randomUUID());
        badRef.put("categoryExternalId", SyntheticBundleFactory.CATEGORY_ID);
        badRef.put("financialAccountExternalId", SyntheticBundleFactory.ACCOUNT_ID);
        badRef.put("description", "Bad supplier ref");
        badRef.put("documentNumber", "SYN-BAD");
        badRef.put("issueDate", "2026-01-01");
        badRef.put("dueDate", "2026-01-31");
        badRef.put("amount", "100.00");
        badRef.put("status", "OPEN");
        data.get(PAYABLES).set(0, SyntheticBundleFactory.json(badRef));
        var result = validator.validate(SyntheticBundleFactory.zip(
            SyntheticBundleFactory.entries(data, "1.0")));
        assertThat(result.report().issues())
            .anyMatch(i -> i.code().equals("REFERENCE_NOT_FOUND")
                && i.severity().equals(ValidationIssue.ERROR));
        assertThat(result.report().valid()).isFalse();
    }

    @Test void nullUserDisplayNameDoesNotBlock() {
        var data = SyntheticBundleFactory.validData();
        var noDisplay = new LinkedHashMap<String, Object>();
        noDisplay.put("externalId", SyntheticBundleFactory.USER_ID);
        noDisplay.put("username", "no-display");
        noDisplay.put("email", "no-display@example.invalid");
        noDisplay.put("displayName", null);
        noDisplay.put("active", true);
        noDisplay.put("roleCodes", List.of("VIEWER"));
        data.get(USERS).set(0, SyntheticBundleFactory.json(noDisplay));
        var result = validator.validate(SyntheticBundleFactory.zip(
            SyntheticBundleFactory.entries(data, "1.0")));
        assertThat(result.report().issues())
            .anyMatch(i -> i.code().equals("INVALID_USER"));
        assertThat(result.report().valid()).isTrue();
    }

    @Test void seriousWarningsDoNotInvalidateBundle() {
        var data = SyntheticBundleFactory.validData();
        var nullablePayable = new LinkedHashMap<String, Object>();
        nullablePayable.put("externalId", SyntheticBundleFactory.PAYABLE_OPEN_ID);
        nullablePayable.put("supplierExternalId", SyntheticBundleFactory.SUPPLIER_ID);
        nullablePayable.put("categoryExternalId", SyntheticBundleFactory.CATEGORY_ID);
        nullablePayable.put("financialAccountExternalId", SyntheticBundleFactory.ACCOUNT_ID);
        nullablePayable.put("description", "Nullable dates");
        nullablePayable.put("documentNumber", "SYN-NULLABLE");
        nullablePayable.put("issueDate", null);
        nullablePayable.put("dueDate", null);
        nullablePayable.put("amount", "100.00");
        nullablePayable.put("status", "OPEN");
        data.get(PAYABLES).set(0, SyntheticBundleFactory.json(nullablePayable));
        var result = validator.validate(SyntheticBundleFactory.zip(
            SyntheticBundleFactory.entries(data, "1.0")));
        assertThat(result.report().valid()).isTrue();
        assertThat(result.report().errors()).isEmpty();
        assertThat(result.report().warnings()).isNotEmpty();
    }

    @Test void format12BundlePasses() {
        var data = SyntheticBundleFactory.validData();
        var result = validator.validate(SyntheticBundleFactory.zip(
            SyntheticBundleFactory.entries(data, "1.2")));
        assertThat(result.report().valid()).isTrue();
    }

    @Test void unresolvedCounterpartyProducesWarning() {
        var data = SyntheticBundleFactory.validData();
        data.get(COUNTERPARTIES).add(SyntheticBundleFactory.json(Map.of("externalId",
            SyntheticBundleFactory.UNRESOLVED_COUNTERPARTY_ID, "name", "Unresolved Legacy",
            "type", "GOVERNMENT", "sourceReference", "7|999",
            "resolutionStatus", "UNRESOLVED_LEGACY_REFERENCE", "active", false)));
        var unresolvedPayable = new LinkedHashMap<String, Object>();
        unresolvedPayable.put("externalId", UUID.randomUUID());
        unresolvedPayable.put("counterpartyExternalId", SyntheticBundleFactory.UNRESOLVED_COUNTERPARTY_ID);
        unresolvedPayable.put("categoryExternalId", SyntheticBundleFactory.CATEGORY_ID);
        unresolvedPayable.put("financialAccountExternalId", SyntheticBundleFactory.ACCOUNT_ID);
        unresolvedPayable.put("description", "Via unresolved CP");
        unresolvedPayable.put("documentNumber", "SYN-UNRES");
        unresolvedPayable.put("issueDate", "2026-06-01");
        unresolvedPayable.put("dueDate", "2026-06-30");
        unresolvedPayable.put("amount", "100.00");
        unresolvedPayable.put("status", "OPEN");
        data.get(PAYABLES).add(SyntheticBundleFactory.json(unresolvedPayable));
        var result = validator.validate(SyntheticBundleFactory.zip(
            SyntheticBundleFactory.entries(data, "1.2")));
        assertThat(result.report().valid()).isTrue();
        assertThat(result.report().issues())
            .anyMatch(i -> i.code().equals("LEGACY_COUNTERPARTY_ORPHAN")
                && i.severity().equals(ValidationIssue.WARNING));
    }

    @Test void unresolvedCounterpartyMissingReferenceStillBlocks() {
        var data = SyntheticBundleFactory.validData();
        var badRef = new LinkedHashMap<String, Object>();
        badRef.put("externalId", UUID.randomUUID());
        badRef.put("counterpartyExternalId", UUID.randomUUID());
        badRef.put("categoryExternalId", SyntheticBundleFactory.CATEGORY_ID);
        badRef.put("financialAccountExternalId", SyntheticBundleFactory.ACCOUNT_ID);
        badRef.put("description", "Missing counterparty");
        badRef.put("documentNumber", "SYN-MISS");
        badRef.put("issueDate", "2026-06-01");
        badRef.put("dueDate", "2026-06-30");
        badRef.put("amount", "100.00");
        badRef.put("status", "OPEN");
        data.get(PAYABLES).add(SyntheticBundleFactory.json(badRef));
        var result = validator.validate(SyntheticBundleFactory.zip(
            SyntheticBundleFactory.entries(data, "1.2")));
        assertThat(result.report().valid()).isFalse();
        assertThat(result.report().issues())
            .anyMatch(i -> i.code().equals("REFERENCE_NOT_FOUND"));
    }

    @Test void invalidDateFails() {
        var data = SyntheticBundleFactory.validData();
        data.get(PAYMENTS).set(0, data.get(PAYMENTS).getFirst().replace("2026-02-20", "not-a-date"));
        assertCode(SyntheticBundleFactory.zip(SyntheticBundleFactory.entries(data, "1.0")), "INVALID_NDJSON");
    }

    private void assertCode(byte[] archive, String code) {
        assertThatThrownBy(() -> validator.validate(archive))
            .isInstanceOf(BundleValidationException.class)
            .satisfies(error -> assertThat(((BundleValidationException) error).getIssues())
                .extracting(issue -> issue.code()).contains(code));
    }

    private void assertDataIssue(byte[] archive, String code) {
        var result = validator.validate(archive);
        assertThat(result.report().issues()).extracting(ValidationIssue::code).contains(code);
    }
}
