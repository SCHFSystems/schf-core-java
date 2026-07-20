package br.com.schf.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PayableDetailResponse(
    UUID id, UUID organizationId,
    UUID supplierId, String supplierName,
    UUID counterpartyId, String counterpartyName,
    UUID categoryId, UUID financialAccountId,
    String description, String documentNumber,
    LocalDate issueDate, LocalDate dueDate,
    BigDecimal amount, String status, BigDecimal paidAmount,
    boolean unresolved
) {}
