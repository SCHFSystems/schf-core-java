package br.com.schf.api.controller;

import br.com.schf.api.dto.PaymentDetailResponse;
import br.com.schf.api.service.UatReadService;
import br.com.schf.audit.PageResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentQueryController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final List<String> ALLOWED_SORT_FIELDS = List.of(
        "paymentDate", "amount", "createdAt");

    private final UatReadService uatReadService;

    public PaymentQueryController(UatReadService uatReadService) {
        this.uatReadService = uatReadService;
    }

    @GetMapping
    public PageResponse<PaymentDetailResponse> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size,
        @RequestParam(defaultValue = "paymentDate") String sort,
        @RequestParam(defaultValue = "desc") String direction) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (!ALLOWED_SORT_FIELDS.contains(sort)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "sort field not allowed: " + sort);
        }
        var dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        var pageable = PageRequest.of(page, size, Sort.by(dir, sort));
        var result = uatReadService.findPayments(pageable);
        return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
            result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    public PaymentDetailResponse getById(@PathVariable UUID id) {
        return uatReadService.findPaymentById(id);
    }
}