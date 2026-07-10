package br.com.schf.account;

import br.com.schf.shared.FinancialAccountRequest;
import br.com.schf.shared.FinancialAccountResponse;
import br.com.schf.shared.TenantContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FinancialAccountService {

    private final FinancialAccountRepository repository;
    private final TenantContext tenant;

    public FinancialAccountService(FinancialAccountRepository repository, TenantContext tenant) {
        this.repository = repository;
        this.tenant = tenant;
    }

    public List<FinancialAccountResponse> findAll() {
        return repository.findByOrganizationId(tenant.getOrganizationId())
            .stream().map(this::toResponse).toList();
    }

    public FinancialAccountResponse create(FinancialAccountRequest request) {
        var type = FinancialAccountType.valueOf(request.type());
        var account = new FinancialAccount(tenant.getOrganizationId(), request.name(), type);
        account.setBankName(request.bankName());
        account.setAgency(request.agency());
        account.setAccountNumber(request.accountNumber());
        return toResponse(repository.save(account));
    }

    private FinancialAccountResponse toResponse(FinancialAccount a) {
        return new FinancialAccountResponse(a.getId(), a.getOrganizationId(), a.getName(),
            a.getType().name(), a.getBankName(), a.getAgency(), a.getAccountNumber(), a.isActive());
    }
}