package br.com.schf.migration.validation;

import java.util.List;

public class BundleValidationException extends RuntimeException {
    private final List<ValidationIssue> issues;

    public BundleValidationException(List<ValidationIssue> issues) {
        super("Canonical migration bundle validation failed");
        this.issues = List.copyOf(issues);
    }

    public List<ValidationIssue> getIssues() { return issues; }
}
