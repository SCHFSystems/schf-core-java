package br.com.schf.supplier;

import br.com.schf.shared.SupplierRequest;
import br.com.schf.shared.SupplierResponse;
import br.com.schf.shared.TenantContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SupplierService {

    private final SupplierRepository repository;
    private final TenantContext tenant;

    public SupplierService(SupplierRepository repository, TenantContext tenant) {
        this.repository = repository;
        this.tenant = tenant;
    }

    public List<SupplierResponse> findAll() {
        return repository.findByOrganizationId(tenant.getOrganizationId())
            .stream().map(this::toResponse).toList();
    }

    public SupplierResponse create(SupplierRequest request) {
        var supplier = new Supplier(tenant.getOrganizationId(), request.name());
        supplier.setDocument(request.document());
        supplier.setEmail(request.email());
        supplier.setPhone(request.phone());
        return toResponse(repository.save(supplier));
    }

    private SupplierResponse toResponse(Supplier s) {
        return new SupplierResponse(s.getId(), s.getOrganizationId(), s.getName(),
            s.getDocument(), s.getEmail(), s.getPhone(), s.isActive());
    }
}