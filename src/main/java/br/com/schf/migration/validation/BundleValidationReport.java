package br.com.schf.migration.validation;

import java.util.List;
import java.util.Map;

public record BundleValidationReport(boolean valid, String bundleId, long totalRecords,
                                     Map<String, Long> recordCounts,
                                     List<ValidationIssue> issues) {
}
