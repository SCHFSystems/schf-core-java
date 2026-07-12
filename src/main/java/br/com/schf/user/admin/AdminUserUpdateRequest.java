package br.com.schf.user.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserUpdateRequest(
    @NotBlank @Email @Size(max = 180) String email,
    @NotBlank @Size(max = 160) String displayName
) {
}
