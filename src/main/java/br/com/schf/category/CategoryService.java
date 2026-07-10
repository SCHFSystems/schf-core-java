package br.com.schf.category;

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

    public CategoryService(CategoryRepository repository, TenantContext tenant) {
        this.repository = repository;
        this.tenant = tenant;
    }

    public List<CategoryResponse> findAll() {
        return repository.findByOrganizationId(tenant.getOrganizationId())
            .stream().map(this::toResponse).toList();
    }

    public CategoryResponse create(CategoryRequest request) {
        var type = CategoryType.valueOf(request.type());
        var category = new Category(tenant.getOrganizationId(), request.name(), type);
        return toResponse(repository.save(category));
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getOrganizationId(), c.getName(),
            c.getType().name(), c.isActive());
    }
}