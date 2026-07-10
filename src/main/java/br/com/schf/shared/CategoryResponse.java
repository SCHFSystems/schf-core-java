package br.com.schf.shared;

import java.util.UUID;

public record CategoryResponse(
    UUID id, UUID organizationId, String name, String type, boolean active
) {}