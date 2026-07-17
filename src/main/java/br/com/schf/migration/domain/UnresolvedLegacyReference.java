package br.com.schf.migration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "unresolved_legacy_references")
public class UnresolvedLegacyReference {
    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "external_id", nullable = false) private UUID externalId;
    @Column(name = "name", nullable = false, length = 255) private String name;
    @Column(name = "type", nullable = false, length = 60) private String type;
    @Column(name = "source_reference", nullable = false, length = 255) private String sourceReference;
    @Column(name = "active", nullable = false) private boolean active;
    @Column(name = "resolution_status", nullable = false, length = 60) private String resolutionStatus;
    @Column(name = "migration_job_id") private UUID migrationJobId;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    protected UnresolvedLegacyReference() {}
    public UnresolvedLegacyReference(UUID organizationId, UUID externalId, String name, String type,
                                     String sourceReference, UUID migrationJobId) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.externalId = externalId;
        this.name = name;
        this.type = type;
        this.sourceReference = sourceReference;
        this.active = false;
        this.resolutionStatus = "UNRESOLVED_LEGACY_REFERENCE";
        this.migrationJobId = migrationJobId;
    }
    @PrePersist void prePersist() {
        if (id == null) id = UUID.randomUUID();
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        createdAt = now; updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }
    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getExternalId() { return externalId; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getSourceReference() { return sourceReference; }
    public boolean isActive() { return active; }
    public String getResolutionStatus() { return resolutionStatus; }
    public UUID getMigrationJobId() { return migrationJobId; }
}
