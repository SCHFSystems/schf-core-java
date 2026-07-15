package br.com.schf.migration;

import static br.com.schf.migration.domain.BundlePaths.*;

import br.com.schf.migration.domain.BundleManifest;
import br.com.schf.migration.validation.SecureBundleArchiveReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class SyntheticBundleFactory {
    public static final UUID ORGANIZATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    public static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    public static final UUID USER_NULL_DISPLAYNAME_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    public static final UUID SUPPLIER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    public static final UUID SUPPLIER_ALIAS_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    public static final UUID CATEGORY_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    public static final UUID ACCOUNT_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    public static final UUID PAYABLE_OPEN_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    public static final UUID PAYABLE_PAID_ID = UUID.fromString("60000000-0000-0000-0000-000000000002");
    public static final UUID PAYABLE_NO_DESC_ID = UUID.fromString("60000000-0000-0000-0000-000000000003");
    public static final UUID PAYABLE_THROUGH_CP_ID = UUID.fromString("60000000-0000-0000-0000-000000000004");
    public static final UUID PAYABLE_NO_FA_ID = UUID.fromString("60000000-0000-0000-0000-000000000005");
    public static final UUID PAYABLE_NULL_DATES_ID = UUID.fromString("60000000-0000-0000-0000-000000000006");
    public static final UUID COUNTERPARTY_ID = UUID.fromString("25000000-0000-0000-0000-000000000001");
    public static final UUID COUNTERPARTY_ALIAS_ID = SUPPLIER_ALIAS_ID;
    public static final UUID PAYMENT_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private SyntheticBundleFactory() {}

    public static Map<String, List<String>> validData() {
        var data = new LinkedHashMap<String, List<String>>();
        data.put(ORGANIZATIONS, list(json(Map.of("externalId", ORGANIZATION_ID, "code", "SYNTH", "name", "Synthetic Organization"))));
        data.put(USERS, list(json(Map.of("externalId", USER_ID, "username", "synthetic-user",
            "email", "synthetic-user@example.invalid", "displayName", "Synthetic User",
            "active", true, "roleCodes", List.of("VIEWER")))));
        data.put(SUPPLIERS, list(json(Map.of("externalId", SUPPLIER_ID, "name", "Synthetic Supplier",
            "document", "SYNTHETIC", "email", "supplier@example.invalid", "phone", "0000", "active", true))));
        data.put(CATEGORIES, list(json(Map.of("externalId", CATEGORY_ID, "name", "Synthetic Category",
            "type", "EXPENSE", "active", true))));
        data.put(ACCOUNTS, list(json(Map.of("externalId", ACCOUNT_ID, "name", "Synthetic Account",
            "type", "BANK", "bankName", "Synthetic Bank", "agency", "0001", "accountNumber", "00001", "active", true))));
        data.put(COUNTERPARTIES, list(json(Map.of("externalId", COUNTERPARTY_ID, "name", "Synthetic Counterparty",
            "type", "SUPPLIER", "sourceReference", "3|1"))));
        data.put(PAYABLES, list(
            json(Map.of("externalId", PAYABLE_OPEN_ID, "supplierExternalId", SUPPLIER_ID,
                "categoryExternalId", CATEGORY_ID, "financialAccountExternalId", ACCOUNT_ID,
                "description", "Synthetic overdue payable", "documentNumber", "SYN-OPEN",
                "issueDate", "2026-01-01", "dueDate", "2026-01-31", "amount", "123.45", "status", "OPEN")),
            json(Map.of("externalId", PAYABLE_PAID_ID, "supplierExternalId", SUPPLIER_ID,
                "categoryExternalId", CATEGORY_ID, "financialAccountExternalId", ACCOUNT_ID,
                "description", "Synthetic paid payable", "documentNumber", "SYN-PAID",
                "issueDate", "2026-02-01", "dueDate", "2026-02-28", "amount", "50.25", "status", "PAID"))));
        data.put(PAYMENTS, list(json(Map.of("externalId", PAYMENT_ID, "payableExternalId", PAYABLE_PAID_ID,
            "financialAccountExternalId", ACCOUNT_ID, "paymentDate", "2026-02-20", "amount", "50.25",
            "notes", "Synthetic payment"))));
        return data;
    }

    public static Map<String, List<String>> validData(String suffix) {
        var data = validData();
        data.get(USERS).set(0, data.get(USERS).getFirst()
            .replace("synthetic-user@example.invalid", "synthetic-user-" + suffix + "@example.invalid")
            .replace("synthetic-user", "synthetic-user-" + suffix));
        return data;
    }

    public static Map<String, List<String>> richData(String suffix) {
        var data = validData(suffix);
        var nullDisplayUser = new LinkedHashMap<String, Object>();
        nullDisplayUser.put("externalId", USER_NULL_DISPLAYNAME_ID);
        nullDisplayUser.put("username", "no-display-" + suffix);
        nullDisplayUser.put("email", "no-display-" + suffix + "@example.invalid");
        nullDisplayUser.put("displayName", null);
        nullDisplayUser.put("active", true);
        nullDisplayUser.put("roleCodes", List.of("VIEWER"));
        data.get(USERS).add(json(nullDisplayUser));
        var aliasSupplier = new LinkedHashMap<String, Object>();
        aliasSupplier.put("externalId", SUPPLIER_ALIAS_ID);
        aliasSupplier.put("name", "Alias Supplier");
        aliasSupplier.put("document", "ALIAS");
        aliasSupplier.put("email", "alias@example.invalid");
        aliasSupplier.put("phone", "0001");
        aliasSupplier.put("active", true);
        data.get(SUPPLIERS).add(json(aliasSupplier));
        var aliasCounterparty = new LinkedHashMap<String, Object>();
        aliasCounterparty.put("externalId", COUNTERPARTY_ALIAS_ID);
        aliasCounterparty.put("name", "Alias Counterparty");
        aliasCounterparty.put("type", "SUPPLIER");
        aliasCounterparty.put("sourceReference", "3|2");
        data.get(COUNTERPARTIES).add(json(aliasCounterparty));
        var noDescPayable = new LinkedHashMap<String, Object>();
        noDescPayable.put("externalId", PAYABLE_NO_DESC_ID);
        noDescPayable.put("supplierExternalId", SUPPLIER_ID);
        noDescPayable.put("categoryExternalId", null);
        noDescPayable.put("financialAccountExternalId", null);
        noDescPayable.put("description", null);
        noDescPayable.put("documentNumber", "SYN-NODESC");
        noDescPayable.put("issueDate", "2026-03-01");
        noDescPayable.put("dueDate", "2026-03-31");
        noDescPayable.put("amount", "75.00");
        noDescPayable.put("status", "OPEN");
        data.get(PAYABLES).add(json(noDescPayable));
        var cpPayable = new LinkedHashMap<String, Object>();
        cpPayable.put("externalId", PAYABLE_THROUGH_CP_ID);
        cpPayable.put("supplierExternalId", null);
        cpPayable.put("counterpartyExternalId", COUNTERPARTY_ALIAS_ID);
        cpPayable.put("categoryExternalId", CATEGORY_ID);
        cpPayable.put("financialAccountExternalId", ACCOUNT_ID);
        cpPayable.put("description", "Through counterparty");
        cpPayable.put("documentNumber", "SYN-CP");
        cpPayable.put("issueDate", "2026-04-01");
        cpPayable.put("dueDate", "2026-04-30");
        cpPayable.put("amount", "200.00");
        cpPayable.put("status", "OPEN");
        data.get(PAYABLES).add(json(cpPayable));
        var noFaPayable = new LinkedHashMap<String, Object>();
        noFaPayable.put("externalId", PAYABLE_NO_FA_ID);
        noFaPayable.put("supplierExternalId", SUPPLIER_ID);
        noFaPayable.put("categoryExternalId", CATEGORY_ID);
        noFaPayable.put("financialAccountExternalId", null);
        noFaPayable.put("description", "No financial account");
        noFaPayable.put("documentNumber", "SYN-NOFA");
        noFaPayable.put("issueDate", "2026-05-01");
        noFaPayable.put("dueDate", "2026-05-31");
        noFaPayable.put("amount", "150.00");
        noFaPayable.put("status", "OPEN");
        data.get(PAYABLES).add(json(noFaPayable));
        var nullDatesPayable = new LinkedHashMap<String, Object>();
        nullDatesPayable.put("externalId", PAYABLE_NULL_DATES_ID);
        nullDatesPayable.put("supplierExternalId", SUPPLIER_ID);
        nullDatesPayable.put("categoryExternalId", null);
        nullDatesPayable.put("financialAccountExternalId", null);
        nullDatesPayable.put("description", "Nullable dates");
        nullDatesPayable.put("documentNumber", "SYN-NULLDATE");
        nullDatesPayable.put("issueDate", null);
        nullDatesPayable.put("dueDate", null);
        nullDatesPayable.put("amount", "50.00");
        nullDatesPayable.put("status", "OPEN");
        data.get(PAYABLES).add(json(nullDatesPayable));
        return data;
    }

    public static byte[] richArchive(String suffix) { return zip(entries(richData(suffix), "1.0")); }

    public static Map<String, byte[]> entries(Map<String, List<String>> data, String formatVersion) {
        var result = new LinkedHashMap<String, byte[]>();
        var checksums = new LinkedHashMap<String, String>();
        var counts = new LinkedHashMap<String, Long>();
        for (var entry : data.entrySet()) {
            var bytes = String.join("\n", entry.getValue()).concat("\n").getBytes(StandardCharsets.UTF_8);
            result.put(entry.getKey(), bytes);
            checksums.put(entry.getKey(), SecureBundleArchiveReader.sha256(bytes));
            counts.put(countKey(entry.getKey()), (long) entry.getValue().size());
        }
        var manifest = new BundleManifest(formatVersion, "1", "SYNTHETIC", UUID.fromString("80000000-0000-0000-0000-000000000001"),
            OffsetDateTime.parse("2026-07-12T12:00:00Z"), "schf-migration-java-test", "0.1.0",
            ORGANIZATION_ID, counts, checksums, true, UUID.fromString("90000000-0000-0000-0000-000000000001"));
        result.put(MANIFEST, json(manifest).getBytes(StandardCharsets.UTF_8));
        var checksumText = new StringBuilder();
        checksums.forEach((path, checksum) -> checksumText.append(checksum).append("  ").append(path).append('\n'));
        result.put(CHECKSUMS, checksumText.toString().getBytes(StandardCharsets.UTF_8));
        result.put(SUMMARY, "{\"synthetic\":true}\n".getBytes(StandardCharsets.UTF_8));
        return result;
    }

    public static byte[] validArchive() { return zip(entries(validData(), "1.0")); }
    public static byte[] validArchive(String suffix) { return zip(entries(validData(suffix), "1.0")); }

    public static byte[] zip(Map<String, byte[]> entries) {
        try {
            var output = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(output)) {
                for (var entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String countKey(String path) {
        if (path.equals(ORGANIZATIONS)) return "organizations.ndjson";
        if (path.equals(USERS)) return "users.ndjson";
        if (path.equals(SUPPLIERS)) return "suppliers.ndjson";
        if (path.equals(CATEGORIES)) return "categories.ndjson";
        if (path.equals(ACCOUNTS)) return "financial-accounts.ndjson";
        if (path.equals(COUNTERPARTIES)) return "counterparties.ndjson";
        if (path.equals(PAYABLES)) return "payables.ndjson";
        return "payments.ndjson";
    }

    private static ArrayList<String> list(String... values) { return new ArrayList<>(List.of(values)); }
    public static String json(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
