package br.com.schf.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(UUID actorId, String action, String targetType, String targetId, Instant occurredAt) {
}
