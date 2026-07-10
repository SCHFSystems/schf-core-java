package br.com.schf.payable;

import br.com.schf.payment.Payment;
import br.com.schf.payment.PaymentRepository;
import br.com.schf.shared.PayableRequest;
import br.com.schf.shared.PayableResponse;
import br.com.schf.shared.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PayableService {

    private final PayableRepository repository;
    private final PaymentRepository paymentRepository;
    private final TenantContext tenant;

    public PayableService(PayableRepository repository, PaymentRepository paymentRepository,
                          TenantContext tenant) {
        this.repository = repository;
        this.paymentRepository = paymentRepository;
        this.tenant = tenant;
    }

    public List<PayableResponse> findAll() {
        return repository.findByOrganizationId(tenant.getOrganizationId())
            .stream().map(this::toResponse).toList();
    }

    public PayableResponse create(PayableRequest request) {
        var payable = new Payable(
            tenant.getOrganizationId(),
            request.supplierId(),
            request.categoryId(),
            request.description(),
            request.issueDate(),
            request.dueDate(),
            request.amount()
        );
        payable.setDocumentNumber(request.documentNumber());
        payable.setFinancialAccountId(request.financialAccountId());
        return toResponse(repository.save(payable));
    }

    public void cancel(UUID id) {
        var payable = repository.findById(id).orElseThrow();
        if (payable.getStatus() == PayableStatus.PAID) {
            throw new IllegalStateException("Cannot cancel a paid payable");
        }
        payable.setStatus(PayableStatus.CANCELED);
        repository.save(payable);
    }

    public PayableResponse toResponse(Payable p) {
        var paid = paymentRepository.findByPayableId(p.getId()).stream()
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