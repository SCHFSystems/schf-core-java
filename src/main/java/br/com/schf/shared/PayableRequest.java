package br.com.schf.shared;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PayableRequest(
    @NotNull UUID supplierId,
    @NotNull UUID categoryId,
    UUID financialAccountId,
    @NotBlank @Size(max = 255) String description,
    @Size(max = 80) String documentNumber,
    @NotNull LocalDate issueDate,
    @NotNull LocalDate dueDate,
    @NotNull @Positive BigDecimal amount
) {}