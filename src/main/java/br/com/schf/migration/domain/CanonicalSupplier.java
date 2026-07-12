package br.com.schf.migration.domain;

import java.util.UUID;

public record CanonicalSupplier(UUID externalId, String name, String document,
                                String email, String phone, boolean active) {
}
