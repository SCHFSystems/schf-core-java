package br.com.schf.migration.domain;

import java.util.UUID;

public record CanonicalCategory(UUID externalId, String name, String type, boolean active) {
}
