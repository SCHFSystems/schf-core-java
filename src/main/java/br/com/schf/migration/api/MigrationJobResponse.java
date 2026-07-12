package br.com.schf.migration.api;

import br.com.schf.migration.domain.MigrationJob;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MigrationJobResponse(
    UUID id, String bundleId, String bundleVersion, String sourceSystem, String status,
    boolean dryRun, OffsetDateTime startedAt, OffsetDateTime completedAt,
    long totalRecords, long importedRecords, long skippedRecords, long failedRecords,
    UUID correlationId, String errorSummary, String lastCompletedPhase
) {
    public static MigrationJobResponse from(MigrationJob job) {
        return new MigrationJobResponse(job.getId(), job.getBundleId(), job.getBundleVersion(),
            job.getSourceSystem(), job.getStatus().name(), job.isDryRun(), job.getStartedAt(),
            job.getCompletedAt(), job.getTotalRecords(), job.getImportedRecords(),
            job.getSkippedRecords(), job.getFailedRecords(), job.getCorrelationId(),
            job.getErrorSummary(), job.getLastCompletedPhase());
    }
}
