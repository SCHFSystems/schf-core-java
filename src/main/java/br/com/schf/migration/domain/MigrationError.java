package br.com.schf.migration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "migration_errors")
public class MigrationError {
    @Id private UUID id;
    @Column(name = "migration_job_id", nullable = false) private UUID migrationJobId;
    @Column(name = "entity_type", length = 80) private String entityType;
    @Column(name = "external_id") private UUID externalId;
    @Column(name = "error_code", nullable = false, length = 80) private String errorCode;
    @Column(name = "sanitized_message", nullable = false, length = 500) private String sanitizedMessage;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    protected MigrationError() {}
    public MigrationError(UUID jobId, String entityType, UUID externalId, String code, String message) {
        this.id = UUID.randomUUID(); this.migrationJobId = jobId; this.entityType = entityType;
        this.externalId = externalId; this.errorCode = code; this.sanitizedMessage = message;
    }
    @PrePersist void prePersist() { if (id == null) id = UUID.randomUUID(); createdAt = OffsetDateTime.now(ZoneOffset.UTC); }
    public UUID getId() { return id; }
    public UUID getMigrationJobId() { return migrationJobId; }
    public String getEntityType() { return entityType; }
    public UUID getExternalId() { return externalId; }
    public String getErrorCode() { return errorCode; }
    public String getSanitizedMessage() { return sanitizedMessage; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
