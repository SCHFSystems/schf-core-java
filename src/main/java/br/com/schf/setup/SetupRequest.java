package br.com.schf.setup;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetupRequest(
    @NotBlank @Size(min = 2, max = 64) String organizationCode,
    @NotBlank @Size(min = 2, max = 160) String organizationName,
    @NotBlank @Size(min = 2, max = 80) String adminUsername,
    @NotBlank @Email @Size(max = 180) String adminEmail,
    @NotBlank @Size(min = 12, max = 120) String adminPassword
) {
}
