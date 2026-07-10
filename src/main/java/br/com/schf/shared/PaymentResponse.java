package br.com.schf.shared;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentResponse(
    UUID id, UUID payableId, UUID financialAccountId,
    LocalDate paymentDate, BigDecimal amount, String notes
) {}