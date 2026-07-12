package br.com.schf.migration.domain;

import java.util.List;
import java.util.UUID;

public record CanonicalUser(UUID externalId, String username, String email,
                            String displayName, boolean active, List<String> roleCodes) {
}
