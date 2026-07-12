package br.com.schf.migration.audit;

import br.com.schf.audit.AuditService;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MigrationAuditService {
    private final AuditService auditService;

    public MigrationAuditService(AuditService auditService) {
        this.auditService = auditService;
    }

    public void completed(UUID organizationId, UUID jobId, long imported, long skipped) {
        auditService.recordCurrent(organizationId, "MIGRATION_IMPORT_COMPLETED", "MIGRATION_JOB",
            jobId.toString(), "imported=" + imported + ";skipped=" + skipped);
    }

    public void dryRunCompleted(UUID organizationId, UUID jobId, long totalRecords) {
        auditService.recordCurrent(organizationId, "MIGRATION_DRY_RUN_COMPLETED", "MIGRATION_JOB",
            jobId.toString(), "totalRecords=" + totalRecords);
    }

    public void failed(UUID organizationId, UUID jobId, String phase) {
        auditService.recordCurrent(organizationId, "MIGRATION_IMPORT_FAILED", "MIGRATION_JOB",
            jobId.toString(), "phase=" + phase + ";code=IMPORT_PHASE_FAILED");
    }
}
