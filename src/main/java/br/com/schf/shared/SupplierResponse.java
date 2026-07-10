package br.com.schf.shared;

import java.util.UUID;

public record SupplierResponse(
    UUID id, UUID organizationId, String name, String document,
    String email, String phone, boolean active
) {}