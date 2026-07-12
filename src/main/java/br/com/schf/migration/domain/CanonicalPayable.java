package br.com.schf.migration.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CanonicalPayable(
    UUID externalId,
    UUID supplierExternalId,
    UUID categoryExternalId,
    UUID financialAccountExternalId,
    String description,
    String documentNumber,
    LocalDate issueDate,
    LocalDate dueDate,
    BigDecimal amount,
    String status
) {
}
