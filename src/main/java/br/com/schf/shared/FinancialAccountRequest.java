package br.com.schf.shared;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FinancialAccountRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 40) String type,
    @Size(max = 120) String bankName,
    @Size(max = 40) String agency,
    @Size(max = 40) String accountNumber
) {}