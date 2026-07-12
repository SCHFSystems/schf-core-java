package br.com.schf.user.admin;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RoleAssignmentRequest(@NotNull UUID roleId) {
}
