package br.com.schf.payable;

import br.com.schf.payment.PaymentService;
import br.com.schf.shared.PayableRequest;
import br.com.schf.shared.PayableResponse;
import br.com.schf.shared.PaymentRequest;
import br.com.schf.shared.PaymentResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payables")
public class PayableController {

    private final PayableService payableService;
    private final PaymentService paymentService;

    public PayableController(PayableService payableService, PaymentService paymentService) {
        this.payableService = payableService;
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<PayableResponse> getAll() {
        return payableService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PayableResponse create(@Valid @RequestBody PayableRequest request) {
        return payableService.create(request);
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse pay(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
        return paymentService.pay(id, request);
    }
}