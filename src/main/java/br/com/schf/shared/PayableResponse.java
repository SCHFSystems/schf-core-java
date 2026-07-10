package br.com.schf.shared;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PayableResponse(
    UUID id, UUID organizationId, UUID supplierId, UUID categoryId,
    UUID financialAccountId, String description, String documentNumber,
    LocalDate issueDate, LocalDate dueDate, BigDecimal amount,
    String status, BigDecimal paidAmount
) {}