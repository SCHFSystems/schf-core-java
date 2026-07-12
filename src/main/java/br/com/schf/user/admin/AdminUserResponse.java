package br.com.schf.user.admin;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminUserResponse(
    UUID id,
    String username,
    String email,
    String displayName,
    boolean active,
    boolean mustChangePassword,
    OffsetDateTime lastLoginAt,
    OffsetDateTime lockedUntil,
    OffsetDateTime passwordChangedAt,
    List<RoleResponse> roles
) {
}
