package br.com.schf.migration.domain;

import java.util.UUID;

public record CanonicalOrganization(UUID externalId, String code, String name) {
}
