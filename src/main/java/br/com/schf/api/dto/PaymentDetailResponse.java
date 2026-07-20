package br.com.schf.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentDetailResponse(
    UUID id, UUID organizationId,
    UUID payableId, String payableDescription,
    UUID financialAccountId,
    LocalDate paymentDate, BigDecimal amount, String notes,
    boolean unresolvedPayable
) {}
