package br.com.schf.migration.application;

import br.com.schf.account.FinancialAccountRepository;
import br.com.schf.migration.audit.MigrationAuditService;
import br.com.schf.category.CategoryRepository;
import br.com.schf.migration.api.MigrationErrorResponse;
import br.com.schf.migration.api.MigrationJobResponse;
import br.com.schf.migration.api.MigrationReportResponse;
import br.com.schf.migration.domain.BundlePaths;
import br.com.schf.migration.domain.MigrationBundleFile;
import br.com.schf.migration.domain.MigrationJob;
import br.com.schf.migration.domain.MigrationStatus;
import br.com.schf.migration.infrastructure.MigrationBundleFileRepository;
import br.com.schf.migration.infrastructure.MigrationErrorRepository;
import br.com.schf.migration.infrastructure.MigrationExternalIdRepository;
import br.com.schf.migration.infrastructure.MigrationJobRepository;
import br.com.schf.migration.infrastructure.MigrationUploadWorkspace;
import br.com.schf.migration.validation.BundleValidationReport;
import br.com.schf.migration.validation.CanonicalBundleValidator;
import br.com.schf.payable.PayableRepository;
import br.com.schf.payment.PaymentRepository;
import br.com.schf.security.principal.AuthenticatedUserPrincipal;
import br.com.schf.shared.TenantContext;
import br.com.schf.supplier.SupplierRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MigrationApplicationService {
    private final CanonicalBundleValidator validator;
    private final MigrationUploadWorkspace workspace;
    private final MigrationJobRepository jobRepository;
    private final MigrationBundleFileRepository bundleFileRepository;
    private final MigrationErrorRepository errorRepository;
    private final MigrationExternalIdRepository externalIdRepository;
    private final MigrationPhaseImporter phaseImporter;
    private final MigrationJobStateService stateService;
    private final TenantContext tenantContext;
    private final MigrationAuditService auditService;
    private final SupplierRepository supplierRepository;
    private final CategoryRepository categoryRepository;
    private final FinancialAccountRepository accountRepository;
    private final PayableRepository payableRepository;
    private final PaymentRepository paymentRepository;

    public MigrationApplicationService(CanonicalBundleValidator validator,
                                       MigrationUploadWorkspace workspace,
                                       MigrationJobRepository jobRepository,
                                       MigrationBundleFileRepository bundleFileRepository,
                                       MigrationErrorRepository errorRepository,
                                       MigrationExternalIdRepository externalIdRepository,
                                       MigrationPhaseImporter phaseImporter,
                                       MigrationJobStateService stateService,
                                       TenantContext tenantContext,
                                       MigrationAuditService auditService,
                                       SupplierRepository supplierRepository,
                                       CategoryRepository categoryRepository,
                                       FinancialAccountRepository accountRepository,
                                       PayableRepository payableRepository,
                                       PaymentRepository paymentRepository) {
        this.validator = validator; this.workspace = workspace; this.jobRepository = jobRepository;
        this.bundleFileRepository = bundleFileRepository; this.errorRepository = errorRepository;
        this.externalIdRepository = externalIdRepository; this.phaseImporter = phaseImporter;
        this.stateService = stateService; this.tenantContext = tenantContext; this.auditService = auditService;
        this.supplierRepository = supplierRepository; this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository; this.payableRepository = payableRepository;
        this.paymentRepository = paymentRepository;
    }

    public BundleValidationReport validate(String fileName, byte[] content) {
        return workspace.process(fileName, content, bytes -> validator.validate(bytes).report());
    }

    public MigrationJobResponse run(String fileName, byte[] content, boolean dryRun,
                                    AuthenticatedUserPrincipal principal) {
        return workspace.process(fileName, content, bytes -> execute(bytes, dryRun, principal));
    }

    private MigrationJobResponse execute(byte[] bytes, boolean dryRun, AuthenticatedUserPrincipal principal) {
        var validated = validator.validate(bytes);
        var bundle = validated.bundle();
        var organizationId = tenantContext.getOrganizationId();
        var existing = jobRepository.findByOrganizationIdAndBundleIdAndDryRun(
            organizationId, bundle.bundleId(), dryRun).orElse(null);
        if (existing != null && existing.getStatus() != MigrationStatus.FAILED)
            return MigrationJobResponse.from(existing);

        var job = existing == null ? createJob(organizationId, principal.getUserId(), bundle, dryRun) : existing;
        if (dryRun) {
            stateService.complete(job.getId(), MigrationStatus.COMPLETED);
            auditService.dryRunCompleted(organizationId, job.getId(), bundle.totalRecords());
            return response(job.getId());
        }

        stateService.status(job.getId(), MigrationStatus.IMPORTING);
        long imported = 0, skipped = 0;
        String phase = "ORGANIZATIONS";
        try {
            var result = phaseImporter.organizations(job.getId(), organizationId, bundle);
            imported += result.imported(); skipped += result.skipped();
            stateService.checkpoint(job.getId(), phase, imported, skipped);
            phase = "USERS"; result = phaseImporter.users(job.getId(), organizationId, bundle);
            imported += result.imported(); skipped += result.skipped(); stateService.checkpoint(job.getId(), phase, imported, skipped);
            phase = "SUPPLIERS"; result = phaseImporter.suppliers(job.getId(), organizationId, bundle);
            imported += result.imported(); skipped += result.skipped(); stateService.checkpoint(job.getId(), phase, imported, skipped);
            phase = "COUNTERPARTIES"; result = phaseImporter.counterparties(job.getId(), organizationId, bundle);
            imported += result.imported(); skipped += result.skipped(); stateService.checkpoint(job.getId(), phase, imported, skipped);
            phase = "CATEGORIES"; result = phaseImporter.categories(job.getId(), organizationId, bundle);
            imported += result.imported(); skipped += result.skipped(); stateService.checkpoint(job.getId(), phase, imported, skipped);
            phase = "FINANCIAL_ACCOUNTS"; result = phaseImporter.accounts(job.getId(), organizationId, bundle);
            imported += result.imported(); skipped += result.skipped(); stateService.checkpoint(job.getId(), phase, imported, skipped);
            phase = "PAYABLES"; result = phaseImporter.payables(job.getId(), organizationId, bundle);
            imported += result.imported(); skipped += result.skipped(); stateService.checkpoint(job.getId(), phase, imported, skipped);
            phase = "PAYMENTS"; result = phaseImporter.payments(job.getId(), organizationId, bundle);
            imported += result.imported(); skipped += result.skipped(); stateService.checkpoint(job.getId(), phase, imported, skipped);
            stateService.complete(job.getId(), skipped > 0 ? MigrationStatus.COMPLETED_WITH_WARNINGS : MigrationStatus.COMPLETED);
            auditService.completed(organizationId, job.getId(), imported, skipped);
        } catch (RuntimeException ex) {
            stateService.fail(job.getId(), phase);
            auditService.failed(organizationId, job.getId(), phase);
        }
        return response(job.getId());
    }

    @Transactional
    protected MigrationJob createJob(UUID organizationId, UUID actorId,
                                     br.com.schf.migration.domain.CanonicalBundle bundle,
                                     boolean dryRun) {
        var job = jobRepository.save(new MigrationJob(organizationId, bundle.bundleId(),
            bundle.manifest().bundleFormatVersion(), bundle.manifest().sourceSystem(), dryRun,
            actorId, bundle.manifest().correlationId(), bundle.totalRecords()));
        for (String path : BundlePaths.DATA_FILES) {
            bundleFileRepository.save(new MigrationBundleFile(job.getId(), path,
                bundle.verifiedChecksums().get(path), countFor(path, bundle)));
        }
        job.status(MigrationStatus.VALIDATED);
        return jobRepository.save(job);
    }

    @Transactional(readOnly = true)
    public List<MigrationJobResponse> findAll() {
        return jobRepository.findByOrganizationIdOrderByStartedAtDesc(tenantContext.getOrganizationId())
            .stream().map(MigrationJobResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MigrationJobResponse findById(UUID id) { return MigrationJobResponse.from(tenantJob(id)); }

    @Transactional(readOnly = true)
    public List<MigrationErrorResponse> errors(UUID id) {
        var job = tenantJob(id);
        return errorRepository.findByMigrationJobIdOrderByCreatedAtAsc(job.getId())
            .stream().map(MigrationErrorResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MigrationReportResponse report(UUID id) {
        var job = tenantJob(id);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("suppliers", (long) supplierRepository.findByOrganizationId(job.getOrganizationId()).size());
        counts.put("categories", (long) categoryRepository.findByOrganizationId(job.getOrganizationId()).size());
        counts.put("financialAccounts", (long) accountRepository.findByOrganizationId(job.getOrganizationId()).size());
        counts.put("counterparties", (long) supplierRepository.findByOrganizationId(job.getOrganizationId()).size());
        counts.put("payables", (long) payableRepository.findByOrganizationId(job.getOrganizationId()).size());
        counts.put("payments", (long) paymentRepository.findByOrganizationId(job.getOrganizationId()).size());
        counts.put("externalIds", externalIdRepository.countByMigrationJobId(job.getId()));
        return new MigrationReportResponse(job.getId(), job.getStatus().name(), job.getLastCompletedPhase(),
            job.getTotalRecords(), job.getImportedRecords(), job.getSkippedRecords(), job.getFailedRecords(), counts);
    }

    private MigrationJob tenantJob(UUID id) {
        return jobRepository.findByIdAndOrganizationId(id, tenantContext.getOrganizationId())
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Migration job not found"));
    }

    private MigrationJobResponse response(UUID id) {
        return MigrationJobResponse.from(jobRepository.findById(id).orElseThrow());
    }

    private long countFor(String path, br.com.schf.migration.domain.CanonicalBundle bundle) {
        if (path.equals(BundlePaths.ORGANIZATIONS)) return bundle.organizations().size();
        if (path.equals(BundlePaths.USERS)) return bundle.users().size();
        if (path.equals(BundlePaths.SUPPLIERS)) return bundle.suppliers().size();
        if (path.equals(BundlePaths.CATEGORIES)) return bundle.categories().size();
        if (path.equals(BundlePaths.ACCOUNTS)) return bundle.financialAccounts().size();
        if (path.equals(BundlePaths.COUNTERPARTIES)) return bundle.counterparties().size();
        if (path.equals(BundlePaths.PAYABLES)) return bundle.payables().size();
        return bundle.payments().size();
    }
}
