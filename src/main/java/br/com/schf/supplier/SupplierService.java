package br.com.schf.supplier;

import br.com.schf.audit.AuditService;
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
    private final AuditService auditService;

    public SupplierService(SupplierRepository repository, TenantContext tenant, AuditService auditService) {
        this.repository = repository;
        this.tenant = tenant;
        this.auditService = auditService;
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
        var saved = repository.save(supplier);
        auditService.recordCurrent(tenant.getOrganizationId(), "SUPPLIER_CREATED", "SUPPLIER",
            saved.getId().toString(), null);
        return toResponse(saved);
    }

    private SupplierResponse toResponse(Supplier s) {
        return new SupplierResponse(s.getId(), s.getOrganizationId(), s.getName(),
            s.getDocument(), s.getEmail(), s.getPhone(), s.isActive());
    }
}
