package br.com.schf.user.admin;

import java.util.UUID;

public record RoleResponse(UUID id, String code, String name, String description, boolean active) {
}
