package br.com.schf.audit;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    UUID organizationId,
    UUID actorId,
    String action,
    String resourceType,
    String resourceId,
    String outcome,
    OffsetDateTime occurredAt
) {
}
