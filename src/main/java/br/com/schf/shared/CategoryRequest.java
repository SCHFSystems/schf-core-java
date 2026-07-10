package br.com.schf.shared;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 40) String type
) {}