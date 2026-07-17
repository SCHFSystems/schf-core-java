package br.com.schf.migration.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CanonicalCounterparty(
    UUID externalId,
    String name,
    String type,
    String sourceReference,
    String resolutionStatus,
    boolean active
) {
    public CanonicalCounterparty(UUID externalId, String name, String type, String sourceReference) {
        this(externalId, name, type, sourceReference, null, true);
    }
}
