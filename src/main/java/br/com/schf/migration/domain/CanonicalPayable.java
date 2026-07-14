package br.com.schf.migration.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
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
    String status,
    UUID counterpartyExternalId,
    String counterpartyType
) {
}
