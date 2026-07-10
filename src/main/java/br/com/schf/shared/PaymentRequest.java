package br.com.schf.shared;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PaymentRequest(
    @NotNull UUID financialAccountId,
    @NotNull LocalDate paymentDate,
    @NotNull @Positive BigDecimal amount,
    @Size(max = 255) String notes
) {}