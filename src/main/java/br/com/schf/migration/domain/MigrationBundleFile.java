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
@Table(name = "migration_bundle_files")
public class MigrationBundleFile {
    @Id private UUID id;
    @Column(name = "migration_job_id", nullable = false) private UUID migrationJobId;
    @Column(name = "file_name", nullable = false, length = 180) private String fileName;
    @Column(nullable = false, length = 64) private String checksum;
    @Column(name = "record_count", nullable = false) private long recordCount;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    protected MigrationBundleFile() {}
    public MigrationBundleFile(UUID jobId, String fileName, String checksum, long count) {
        id = UUID.randomUUID(); migrationJobId = jobId; this.fileName = fileName;
        this.checksum = checksum; recordCount = count;
    }
    @PrePersist void prePersist() { if (id == null) id = UUID.randomUUID(); createdAt = OffsetDateTime.now(ZoneOffset.UTC); }
}
