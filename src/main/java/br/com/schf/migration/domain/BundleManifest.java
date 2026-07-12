package br.com.schf.migration.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record BundleManifest(
    String bundleFormatVersion,
    String schemaVersion,
    String sourceSystem,
    UUID sourceInstanceId,
    OffsetDateTime generatedAt,
    String generatorVersion,
    String coreMinimumVersion,
    UUID organizationExternalId,
    Map<String, Long> recordCounts,
    Map<String, String> fileChecksums,
    boolean anonymized,
    UUID correlationId
) {
}
