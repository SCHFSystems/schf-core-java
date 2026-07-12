package br.com.schf.migration.api;

import java.util.Map;
import java.util.UUID;

public record MigrationReportResponse(UUID migrationJobId, String status, String lastCompletedPhase,
                                      long totalRecords, long importedRecords, long skippedRecords,
                                      long failedRecords, Map<String, Long> persistedCounts) {
}
