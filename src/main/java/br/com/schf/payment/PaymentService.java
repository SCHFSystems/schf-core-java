package br.com.schf.payment;

import br.com.schf.account.FinancialAccountRepository;
import br.com.schf.audit.AuditService;
import br.com.schf.payable.PayableRepository;
import br.com.schf.payable.PayableStatus;
import br.com.schf.shared.PaymentRequest;
import br.com.schf.shared.PaymentResponse;
import br.com.schf.shared.TenantContext;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PaymentService {

    private final PaymentRepository repository;
    private final PayableRepository payableRepository;
    private final FinancialAccountRepository financialAccountRepository;
    private final TenantContext tenant;
    private final AuditService auditService;

    public PaymentService(PaymentRepository repository, PayableRepository payableRepository,
                           FinancialAccountRepository financialAccountRepository,
                           TenantContext tenant, AuditService auditService) {
        this.repository = repository;
        this.payableRepository = payableRepository;
        this.financialAccountRepository = financialAccountRepository;
        this.tenant = tenant;
        this.auditService = auditService;
    }

    public PaymentResponse pay(UUID payableId, PaymentRequest request) {
        var organizationId = tenant.getOrganizationId();
        var payable = payableRepository.findByIdAndOrganizationId(payableId, organizationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payable not found"));
        if (!financialAccountRepository.existsByIdAndOrganizationId(
            request.financialAccountId(), organizationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Financial account not found");
        }
        if (payable.getStatus() != PayableStatus.OPEN) {
            throw new IllegalStateException("Payable is not open for payment");
        }

        var totalPaid = repository.findByPayableIdAndOrganizationId(payableId, organizationId).stream()
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var remaining = payable.getAmount().subtract(totalPaid);

        if (request.amount().compareTo(remaining) > 0) {
            throw new IllegalArgumentException(
                "Payment amount exceeds remaining balance. Max allowed: " + remaining);
        }

        var payment = new Payment(organizationId, payableId,
            request.financialAccountId(), request.paymentDate(), request.amount());
        payment.setNotes(request.notes());
        payment = repository.save(payment);

        var newTotalPaid = totalPaid.add(request.amount());
        if (newTotalPaid.compareTo(payable.getAmount()) >= 0) {
            payable.setStatus(PayableStatus.PAID);
            payableRepository.save(payable);
        }

        auditService.recordCurrent(organizationId, "PAYMENT_CREATED", "PAYMENT",
            payment.getId().toString(), "payableId=" + payableId);
        return new PaymentResponse(payment.getId(), payment.getPayableId(),
            payment.getFinancialAccountId(), payment.getPaymentDate(),
            payment.getAmount(), payment.getNotes());
    }
}
