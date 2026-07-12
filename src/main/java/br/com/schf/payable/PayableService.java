package br.com.schf.payable;

import br.com.schf.account.FinancialAccountRepository;
import br.com.schf.audit.AuditService;
import br.com.schf.category.CategoryRepository;
import br.com.schf.payment.Payment;
import br.com.schf.payment.PaymentRepository;
import br.com.schf.shared.PayableRequest;
import br.com.schf.shared.PayableResponse;
import br.com.schf.shared.TenantContext;
import br.com.schf.supplier.SupplierRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PayableService {

    private final PayableRepository repository;
    private final PaymentRepository paymentRepository;
    private final SupplierRepository supplierRepository;
    private final CategoryRepository categoryRepository;
    private final FinancialAccountRepository financialAccountRepository;
    private final TenantContext tenant;
    private final AuditService auditService;

    public PayableService(PayableRepository repository, PaymentRepository paymentRepository,
                           SupplierRepository supplierRepository, CategoryRepository categoryRepository,
                           FinancialAccountRepository financialAccountRepository,
                           TenantContext tenant, AuditService auditService) {
        this.repository = repository;
        this.paymentRepository = paymentRepository;
        this.supplierRepository = supplierRepository;
        this.categoryRepository = categoryRepository;
        this.financialAccountRepository = financialAccountRepository;
        this.tenant = tenant;
        this.auditService = auditService;
    }

    public List<PayableResponse> findAll() {
        return repository.findByOrganizationId(tenant.getOrganizationId())
            .stream().map(this::toResponse).toList();
    }

    public PayableResponse create(PayableRequest request) {
        var organizationId = tenant.getOrganizationId();
        if (!supplierRepository.existsByIdAndOrganizationId(request.supplierId(), organizationId)
            || !categoryRepository.existsByIdAndOrganizationId(request.categoryId(), organizationId)
            || request.financialAccountId() != null
                && !financialAccountRepository.existsByIdAndOrganizationId(request.financialAccountId(), organizationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Financial reference not found");
        }
        var payable = new Payable(
            organizationId,
            request.supplierId(),
            request.categoryId(),
            request.description(),
            request.issueDate(),
            request.dueDate(),
            request.amount()
        );
        payable.setDocumentNumber(request.documentNumber());
        payable.setFinancialAccountId(request.financialAccountId());
        var saved = repository.save(payable);
        auditService.recordCurrent(organizationId, "PAYABLE_CREATED", "PAYABLE",
            saved.getId().toString(), null);
        return toResponse(saved);
    }

    public void cancel(UUID id) {
        var organizationId = tenant.getOrganizationId();
        var payable = repository.findByIdAndOrganizationId(id, organizationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payable not found"));
        if (payable.getStatus() == PayableStatus.PAID) {
            throw new IllegalStateException("Cannot cancel a paid payable");
        }
        payable.setStatus(PayableStatus.CANCELED);
        repository.save(payable);
        auditService.recordCurrent(organizationId, "PAYABLE_CANCELED", "PAYABLE", id.toString(), null);
    }

    public PayableResponse toResponse(Payable p) {
        var paid = paymentRepository.findByPayableIdAndOrganizationId(p.getId(), p.getOrganizationId()).stream()
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PayableResponse(
            p.getId(), p.getOrganizationId(), p.getSupplierId(), p.getCategoryId(),
            p.getFinancialAccountId(), p.getDescription(), p.getDocumentNumber(),
            p.getIssueDate(), p.getDueDate(), p.getAmount(),
            p.getStatus().name(), paid
        );
    }
}
