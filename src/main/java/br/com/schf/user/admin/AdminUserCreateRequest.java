package br.com.schf.user.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AdminUserCreateRequest(
    @NotBlank @Size(max = 80) String username,
    @NotBlank @Email @Size(max = 180) String email,
    @NotBlank @Size(max = 160) String displayName,
    @NotBlank @Size(min = 12, max = 72) String temporaryPassword,
    List<UUID> roleIds
) {
}
