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
@Table(name = "migration_external_ids")
public class MigrationExternalId {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "source_system", nullable = false, length = 120) private String sourceSystem;
    @Column(name = "entity_type", nullable = false, length = 80) private String entityType;
    @Column(name = "external_id", nullable = false) private UUID externalId;
    @Column(name = "internal_id", nullable = false) private UUID internalId;
    @Column(name = "migration_job_id", nullable = false) private UUID migrationJobId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;

    protected MigrationExternalId() {}
    public MigrationExternalId(UUID organizationId, String sourceSystem, String entityType,
                               UUID externalId, UUID internalId, UUID jobId) {
        this.id = UUID.randomUUID(); this.organizationId = organizationId; this.sourceSystem = sourceSystem;
        this.entityType = entityType; this.externalId = externalId; this.internalId = internalId;
        this.migrationJobId = jobId;
    }
    @PrePersist void prePersist() { if (id == null) id = UUID.randomUUID(); createdAt = OffsetDateTime.now(ZoneOffset.UTC); }
    public UUID getInternalId() { return internalId; }
}
