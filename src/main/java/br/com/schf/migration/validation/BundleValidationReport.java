package br.com.schf.migration.validation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Map;

public record BundleValidationReport(boolean valid, String bundleId, long totalRecords,
                                     Map<String, Long> recordCounts,
                                     List<ValidationIssue> issues,
                                     List<ValidationIssue> errors,
                                     List<ValidationIssue> warnings) {

    public BundleValidationReport(boolean valid, String bundleId, long totalRecords,
                                   Map<String, Long> recordCounts,
                                   List<ValidationIssue> issues) {
        this(valid, bundleId, totalRecords, recordCounts, issues,
            issues.stream().filter(ValidationIssue::isError).toList(),
            issues.stream().filter(ValidationIssue::isWarning).toList());
    }

    public BundleValidationReport(String bundleId, long totalRecords,
                                   Map<String, Long> recordCounts,
                                   List<ValidationIssue> issues) {
        this(issues.stream().noneMatch(ValidationIssue::isError),
            bundleId, totalRecords, recordCounts, issues,
            issues.stream().filter(ValidationIssue::isError).toList(),
            issues.stream().filter(ValidationIssue::isWarning).toList());
    }

    @JsonIgnore
    public long errorCount() { return errors.size(); }

    @JsonIgnore
    public long warningCount() { return warnings.size(); }
}
