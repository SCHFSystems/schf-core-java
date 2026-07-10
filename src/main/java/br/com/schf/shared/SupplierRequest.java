package br.com.schf.shared;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
    @NotBlank @Size(max = 160) String name,
    @Size(max = 40) String document,
    @Size(max = 180) String email,
    @Size(max = 40) String phone
) {}