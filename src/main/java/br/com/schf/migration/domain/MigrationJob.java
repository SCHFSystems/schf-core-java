package br.com.schf.migration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "migration_jobs")
public class MigrationJob {

    @Id private UUID id;
    @Column(name = "organization_id", nullable = false) private UUID organizationId;
    @Column(name = "bundle_id", nullable = false, length = 64) private String bundleId;
    @Column(name = "bundle_version", nullable = false, length = 20) private String bundleVersion;
    @Column(name = "source_system", nullable = false, length = 120) private String sourceSystem;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private MigrationStatus status;
    @Column(name = "dry_run", nullable = false) private boolean dryRun;
    @Column(name = "started_at", nullable = false) private OffsetDateTime startedAt;
    @Column(name = "completed_at") private OffsetDateTime completedAt;
    @Column(name = "total_records", nullable = false) private long totalRecords;
    @Column(name = "imported_records", nullable = false) private long importedRecords;
    @Column(name = "skipped_records", nullable = false) private long skippedRecords;
    @Column(name = "failed_records", nullable = false) private long failedRecords;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "correlation_id", nullable = false) private UUID correlationId;
    @Column(name = "error_summary", length = 500) private String errorSummary;
    @Column(name = "last_completed_phase", length = 80) private String lastCompletedPhase;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    protected MigrationJob() {
    }

    public MigrationJob(UUID organizationId, String bundleId, String bundleVersion,
                        String sourceSystem, boolean dryRun, UUID createdBy,
                        UUID correlationId, long totalRecords) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.bundleId = bundleId;
        this.bundleVersion = bundleVersion;
        this.sourceSystem = sourceSystem;
        this.dryRun = dryRun;
        this.createdBy = createdBy;
        this.correlationId = correlationId;
        this.totalRecords = totalRecords;
        this.status = MigrationStatus.CREATED;
        this.startedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PrePersist void prePersist() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        if (id == null) id = UUID.randomUUID();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate void preUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void status(MigrationStatus status) { this.status = status; }
    public void checkpoint(String phase, long imported, long skipped) {
        this.lastCompletedPhase = phase;
        this.importedRecords = imported;
        this.skippedRecords = skipped;
    }
    public void complete(MigrationStatus finalStatus) {
        status = finalStatus;
        completedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
    public void fail(String summary, long failed) {
        status = MigrationStatus.FAILED;
        errorSummary = summary;
        failedRecords = failed;
        completedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getBundleId() { return bundleId; }
    public String getBundleVersion() { return bundleVersion; }
    public String getSourceSystem() { return sourceSystem; }
    public MigrationStatus getStatus() { return status; }
    public boolean isDryRun() { return dryRun; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public long getTotalRecords() { return totalRecords; }
    public long getImportedRecords() { return importedRecords; }
    public long getSkippedRecords() { return skippedRecords; }
    public long getFailedRecords() { return failedRecords; }
    public UUID getCreatedBy() { return createdBy; }
    public UUID getCorrelationId() { return correlationId; }
    public String getErrorSummary() { return errorSummary; }
    public String getLastCompletedPhase() { return lastCompletedPhase; }
}
