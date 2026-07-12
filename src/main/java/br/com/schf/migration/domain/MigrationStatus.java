package br.com.schf.migration.domain;

public enum MigrationStatus {
    CREATED,
    VALIDATING,
    VALIDATED,
    IMPORTING,
    COMPLETED,
    COMPLETED_WITH_WARNINGS,
    FAILED,
    ROLLED_BACK
}
