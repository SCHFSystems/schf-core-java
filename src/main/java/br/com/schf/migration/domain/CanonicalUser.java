package br.com.schf.migration.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CanonicalUser(UUID externalId, String username, String email,
                            String displayName, boolean active, List<String> roleCodes) {
}
