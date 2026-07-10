package br.com.schf.payment;

import br.com.schf.payable.PayableRepository;
import br.com.schf.payable.PayableStatus;
import br.com.schf.shared.PaymentRequest;
import br.com.schf.shared.PaymentResponse;
import br.com.schf.shared.TenantContext;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentService {

    private final PaymentRepository repository;
    private final PayableRepository payableRepository;
    private final TenantContext tenant;

    public PaymentService(PaymentRepository repository, PayableRepository payableRepository,
                          TenantContext tenant) {
        this.repository = repository;
        this.payableRepository = payableRepository;
        this.tenant = tenant;
    }

    public PaymentResponse pay(UUID payableId, PaymentRequest request) {
        var payable = payableRepository.findById(payableId).orElseThrow();
        if (payable.getStatus() != PayableStatus.OPEN) {
            throw new IllegalStateException("Payable is not open for payment");
        }

        var totalPaid = repository.findByPayableId(payableId).stream()
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var remaining = payable.getAmount().subtract(totalPaid);

        if (request.amount().compareTo(remaining) > 0) {
            throw new IllegalArgumentException(
                "Payment amount exceeds remaining balance. Max allowed: " + remaining);
        }

        var payment = new Payment(tenant.getOrganizationId(), payableId,
            request.financialAccountId(), request.paymentDate(), request.amount());
        payment.setNotes(request.notes());
        payment = repository.save(payment);

        var newTotalPaid = totalPaid.add(request.amount());
        if (newTotalPaid.compareTo(payable.getAmount()) >= 0) {
            payable.setStatus(PayableStatus.PAID);
            payableRepository.save(payable);
        }

        return new PaymentResponse(payment.getId(), payment.getPayableId(),
            payment.getFinancialAccountId(), payment.getPaymentDate(),
            payment.getAmount(), payment.getNotes());
    }
}