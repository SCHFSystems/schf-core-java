package br.com.schf.migration;

import static br.com.schf.migration.domain.BundlePaths.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.schf.migration.validation.BundleValidationException;
import br.com.schf.migration.validation.CanonicalBundleValidator;
import br.com.schf.migration.validation.MigrationProperties;
import br.com.schf.migration.validation.SecureBundleArchiveReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
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
        assertThat(result.bundle().totalRecords()).isEqualTo(8);
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
        assertCode(SyntheticBundleFactory.zip(SyntheticBundleFactory.entries(data, "1.0")), "DUPLICATE_EXTERNAL_ID");
    }

    @Test void missingReferenceFails() {
        var data = SyntheticBundleFactory.validData();
        data.get(PAYMENTS).set(0, data.get(PAYMENTS).getFirst().replace(
            SyntheticBundleFactory.ACCOUNT_ID.toString(), UUID.randomUUID().toString()));
        assertCode(SyntheticBundleFactory.zip(SyntheticBundleFactory.entries(data, "1.0")), "REFERENCE_NOT_FOUND");
    }

    @Test void invalidMoneyFails() {
        var data = SyntheticBundleFactory.validData();
        data.get(PAYMENTS).set(0, data.get(PAYMENTS).getFirst().replace("50.25", "-1.00"));
        assertCode(SyntheticBundleFactory.zip(SyntheticBundleFactory.entries(data, "1.0")), "INVALID_MONEY");
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
}
