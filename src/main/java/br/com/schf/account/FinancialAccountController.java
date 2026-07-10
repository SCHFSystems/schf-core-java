package br.com.schf.account;

import br.com.schf.shared.FinancialAccountRequest;
import br.com.schf.shared.FinancialAccountResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/financial-accounts")
public class FinancialAccountController {

    private final FinancialAccountService service;

    public FinancialAccountController(FinancialAccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<FinancialAccountResponse> getAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FinancialAccountResponse create(@Valid @RequestBody FinancialAccountRequest request) {
        return service.create(request);
    }
}