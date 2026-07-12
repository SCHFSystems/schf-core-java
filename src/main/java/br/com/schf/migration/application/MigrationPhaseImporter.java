package br.com.schf.migration.application;

import br.com.schf.account.FinancialAccount;
import br.com.schf.account.FinancialAccountRepository;
import br.com.schf.account.FinancialAccountType;
import br.com.schf.category.Category;
import br.com.schf.category.CategoryRepository;
import br.com.schf.migration.domain.CanonicalBundle;
import br.com.schf.migration.domain.MigrationExternalId;
import br.com.schf.migration.infrastructure.MigrationExternalIdRepository;
import br.com.schf.organization.OrganizationRepository;
import br.com.schf.payable.Payable;
import br.com.schf.payable.PayableRepository;
import br.com.schf.payable.PayableStatus;
import br.com.schf.payment.Payment;
import br.com.schf.payment.PaymentRepository;
import br.com.schf.security.membership.UserRoleAssignment;
import br.com.schf.security.membership.UserRoleAssignmentRepository;
import br.com.schf.security.role.RoleRepository;
import br.com.schf.supplier.CategoryType;
import br.com.schf.supplier.Supplier;
import br.com.schf.supplier.SupplierRepository;
import br.com.schf.user.UserAccount;
import br.com.schf.user.UserAccountRepository;
import br.com.schf.user.UserRole;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MigrationPhaseImporter {
    private final OrganizationRepository organizationRepository;
    private final UserAccountRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final SupplierRepository supplierRepository;
    private final CategoryRepository categoryRepository;
    private final FinancialAccountRepository accountRepository;
    private final PayableRepository payableRepository;
    private final PaymentRepository paymentRepository;
    private final MigrationExternalIdRepository externalIdRepository;
    private final PasswordEncoder passwordEncoder;

    public MigrationPhaseImporter(OrganizationRepository organizationRepository,
                                  UserAccountRepository userRepository,
                                  RoleRepository roleRepository,
                                  UserRoleAssignmentRepository assignmentRepository,
                                  SupplierRepository supplierRepository,
                                  CategoryRepository categoryRepository,
                                  FinancialAccountRepository accountRepository,
                                  PayableRepository payableRepository,
                                  PaymentRepository paymentRepository,
                                  MigrationExternalIdRepository externalIdRepository,
                                  PasswordEncoder passwordEncoder) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.supplierRepository = supplierRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.payableRepository = payableRepository;
        this.paymentRepository = paymentRepository;
        this.externalIdRepository = externalIdRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PhaseResult organizations(UUID jobId, UUID organizationId, CanonicalBundle bundle) {
        organizationRepository.findById(organizationId).orElseThrow();
        return map(jobId, organizationId, bundle, "ORGANIZATION",
            bundle.manifest().organizationExternalId(), organizationId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PhaseResult users(UUID jobId, UUID organizationId, CanonicalBundle bundle) {
        var organization = organizationRepository.findById(organizationId).orElseThrow();
        long imported = 0, skipped = 0;
        for (var record : bundle.users()) {
            if (mapped(organizationId, bundle, "USER", record.externalId()) != null) { skipped++; continue; }
            if (userRepository.existsByEmailIgnoreCase(record.email()) || userRepository.existsByUsername(record.username()))
                throw new IllegalStateException("Canonical user conflicts with an existing account");
            var user = new UserAccount(organization, record.username(), record.email().trim().toLowerCase(),
                record.displayName(), UserRole.CONSULTA);
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setMustChangePassword(true);
            user.setActive(record.active());
            user = userRepository.save(user);
            if (record.roleCodes() != null) {
                for (String code : record.roleCodes().stream().distinct().toList()) {
                    var role = roleRepository.findByOrganizationIdAndCode(organizationId, code)
                        .orElseThrow(() -> new IllegalStateException("Canonical user role is unavailable"));
                    assignmentRepository.save(new UserRoleAssignment(user, role));
                }
            }
            saveMap(jobId, organizationId, bundle, "USER", record.externalId(), user.getId());
            imported++;
        }
        return new PhaseResult(imported, skipped);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PhaseResult suppliers(UUID jobId, UUID organizationId, CanonicalBundle bundle) {
        long imported = 0, skipped = 0;
        for (var record : bundle.suppliers()) {
            if (mapped(organizationId, bundle, "SUPPLIER", record.externalId()) != null) { skipped++; continue; }
            var entity = new Supplier(organizationId, record.name());
            entity.setDocument(record.document()); entity.setEmail(record.email()); entity.setPhone(record.phone());
            entity.setActive(record.active()); entity = supplierRepository.save(entity);
            saveMap(jobId, organizationId, bundle, "SUPPLIER", record.externalId(), entity.getId()); imported++;
        }
        return new PhaseResult(imported, skipped);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PhaseResult categories(UUID jobId, UUID organizationId, CanonicalBundle bundle) {
        long imported = 0, skipped = 0;
        for (var record : bundle.categories()) {
            if (mapped(organizationId, bundle, "CATEGORY", record.externalId()) != null) { skipped++; continue; }
            var entity = new Category(organizationId, record.name(), CategoryType.valueOf(record.type()));
            entity.setActive(record.active()); entity = categoryRepository.save(entity);
            saveMap(jobId, organizationId, bundle, "CATEGORY", record.externalId(), entity.getId()); imported++;
        }
        return new PhaseResult(imported, skipped);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PhaseResult accounts(UUID jobId, UUID organizationId, CanonicalBundle bundle) {
        long imported = 0, skipped = 0;
        for (var record : bundle.financialAccounts()) {
            if (mapped(organizationId, bundle, "FINANCIAL_ACCOUNT", record.externalId()) != null) { skipped++; continue; }
            var entity = new FinancialAccount(organizationId, record.name(), FinancialAccountType.valueOf(record.type()));
            entity.setBankName(record.bankName()); entity.setAgency(record.agency());
            entity.setAccountNumber(record.accountNumber()); entity.setActive(record.active());
            entity = accountRepository.save(entity);
            saveMap(jobId, organizationId, bundle, "FINANCIAL_ACCOUNT", record.externalId(), entity.getId()); imported++;
        }
        return new PhaseResult(imported, skipped);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PhaseResult payables(UUID jobId, UUID organizationId, CanonicalBundle bundle) {
        long imported = 0, skipped = 0;
        for (var record : bundle.payables()) {
            if (mapped(organizationId, bundle, "PAYABLE", record.externalId()) != null) { skipped++; continue; }
            var supplierId = requiredMap(organizationId, bundle, "SUPPLIER", record.supplierExternalId());
            var categoryId = requiredMap(organizationId, bundle, "CATEGORY", record.categoryExternalId());
            var entity = new Payable(organizationId, supplierId, categoryId, record.description(),
                record.issueDate(), record.dueDate(), record.amount());
            entity.setDocumentNumber(record.documentNumber());
            if (record.financialAccountExternalId() != null)
                entity.setFinancialAccountId(requiredMap(organizationId, bundle, "FINANCIAL_ACCOUNT",
                    record.financialAccountExternalId()));
            entity.setStatus(PayableStatus.valueOf(record.status())); entity = payableRepository.save(entity);
            saveMap(jobId, organizationId, bundle, "PAYABLE", record.externalId(), entity.getId()); imported++;
        }
        return new PhaseResult(imported, skipped);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PhaseResult payments(UUID jobId, UUID organizationId, CanonicalBundle bundle) {
        long imported = 0, skipped = 0;
        for (var record : bundle.payments()) {
            if (mapped(organizationId, bundle, "PAYMENT", record.externalId()) != null) { skipped++; continue; }
            var payableId = requiredMap(organizationId, bundle, "PAYABLE", record.payableExternalId());
            var accountId = requiredMap(organizationId, bundle, "FINANCIAL_ACCOUNT", record.financialAccountExternalId());
            var entity = new Payment(organizationId, payableId, accountId, record.paymentDate(), record.amount());
            entity.setNotes(record.notes()); entity = paymentRepository.save(entity);
            saveMap(jobId, organizationId, bundle, "PAYMENT", record.externalId(), entity.getId()); imported++;
        }
        return new PhaseResult(imported, skipped);
    }

    private PhaseResult map(UUID jobId, UUID organizationId, CanonicalBundle bundle,
                            String type, UUID externalId, UUID internalId) {
        if (mapped(organizationId, bundle, type, externalId) != null) return new PhaseResult(0, 1);
        saveMap(jobId, organizationId, bundle, type, externalId, internalId);
        return new PhaseResult(1, 0);
    }

    private UUID mapped(UUID organizationId, CanonicalBundle bundle, String type, UUID externalId) {
        return externalIdRepository.findByOrganizationIdAndSourceSystemAndEntityTypeAndExternalId(
            organizationId, bundle.manifest().sourceSystem(), type, externalId)
            .map(MigrationExternalId::getInternalId).orElse(null);
    }

    private UUID requiredMap(UUID organizationId, CanonicalBundle bundle, String type, UUID externalId) {
        var value = mapped(organizationId, bundle, type, externalId);
        if (value == null) throw new IllegalStateException("Canonical reference checkpoint is unavailable");
        return value;
    }

    private void saveMap(UUID jobId, UUID organizationId, CanonicalBundle bundle,
                         String type, UUID externalId, UUID internalId) {
        externalIdRepository.save(new MigrationExternalId(organizationId,
            bundle.manifest().sourceSystem(), type, externalId, internalId, jobId));
    }
}
