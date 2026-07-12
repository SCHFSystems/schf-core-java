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
    public static final UUID SUPPLIER_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    public static final UUID CATEGORY_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    public static final UUID ACCOUNT_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    public static final UUID PAYABLE_OPEN_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    public static final UUID PAYABLE_PAID_ID = UUID.fromString("60000000-0000-0000-0000-000000000002");
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
        if (path.equals(ORGANIZATIONS)) return "organizations";
        if (path.equals(USERS)) return "users";
        if (path.equals(SUPPLIERS)) return "suppliers";
        if (path.equals(CATEGORIES)) return "categories";
        if (path.equals(ACCOUNTS)) return "financialAccounts";
        if (path.equals(PAYABLES)) return "payables";
        return "payments";
    }

    private static ArrayList<String> list(String... values) { return new ArrayList<>(List.of(values)); }
    private static String json(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
