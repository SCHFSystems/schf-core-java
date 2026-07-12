package br.com.schf.migration.api;

import br.com.schf.migration.domain.MigrationError;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MigrationErrorResponse(UUID id, String entityType, UUID externalId,
                                     String errorCode, String message, OffsetDateTime createdAt) {
    public static MigrationErrorResponse from(MigrationError error) {
        return new MigrationErrorResponse(error.getId(), error.getEntityType(), error.getExternalId(),
            error.getErrorCode(), error.getSanitizedMessage(), error.getCreatedAt());
    }
}
