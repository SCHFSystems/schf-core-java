package br.com.schf.category;

import br.com.schf.audit.AuditService;
import br.com.schf.shared.CategoryRequest;
import br.com.schf.shared.CategoryResponse;
import br.com.schf.shared.TenantContext;
import br.com.schf.supplier.CategoryType;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository repository;
    private final TenantContext tenant;
    private final AuditService auditService;

    public CategoryService(CategoryRepository repository, TenantContext tenant, AuditService auditService) {
        this.repository = repository;
        this.tenant = tenant;
        this.auditService = auditService;
    }

    public List<CategoryResponse> findAll() {
        return repository.findByOrganizationId(tenant.getOrganizationId())
            .stream().map(this::toResponse).toList();
    }

    public CategoryResponse create(CategoryRequest request) {
        var type = CategoryType.valueOf(request.type());
        var category = new Category(tenant.getOrganizationId(), request.name(), type);
        var saved = repository.save(category);
        auditService.recordCurrent(tenant.getOrganizationId(), "CATEGORY_CREATED", "CATEGORY",
            saved.getId().toString(), null);
        return toResponse(saved);
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getOrganizationId(), c.getName(),
            c.getType().name(), c.isActive());
    }
}
