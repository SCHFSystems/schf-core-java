package br.com.schf.migration.application;

import br.com.schf.migration.domain.MigrationError;
import br.com.schf.migration.domain.MigrationStatus;
import br.com.schf.migration.infrastructure.MigrationErrorRepository;
import br.com.schf.migration.infrastructure.MigrationJobRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MigrationJobStateService {
    private final MigrationJobRepository jobRepository;
    private final MigrationErrorRepository errorRepository;

    public MigrationJobStateService(MigrationJobRepository jobRepository,
                                    MigrationErrorRepository errorRepository) {
        this.jobRepository = jobRepository;
        this.errorRepository = errorRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void status(UUID jobId, MigrationStatus status) {
        var job = jobRepository.findById(jobId).orElseThrow();
        job.status(status);
        jobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkpoint(UUID jobId, String phase, long imported, long skipped) {
        var job = jobRepository.findById(jobId).orElseThrow();
        job.checkpoint(phase, imported, skipped);
        jobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID jobId, MigrationStatus status) {
        var job = jobRepository.findById(jobId).orElseThrow();
        job.complete(status);
        jobRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID jobId, String phase) {
        var job = jobRepository.findById(jobId).orElseThrow();
        job.fail("Import failed during a controlled phase", 1);
        jobRepository.save(job);
        errorRepository.save(new MigrationError(jobId, phase, null,
            "IMPORT_PHASE_FAILED", "Import failed during a controlled phase"));
    }
}
