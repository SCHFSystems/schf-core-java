package br.com.schf.audit;

import br.com.schf.shared.TenantContext;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogQueryService {

    private final AuditLogRepository repository;
    private final TenantContext tenantContext;

    public AuditLogQueryService(AuditLogRepository repository, TenantContext tenantContext) {
        this.repository = repository;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> find(String action, UUID actor, String resourceType,
                                                OffsetDateTime from, OffsetDateTime to,
                                                int page, int size) {
        var safePage = Math.max(page, 0);
        var safeSize = Math.min(Math.max(size, 1), 100);
        Specification<AuditLog> specification = (root, query, builder) ->
            builder.equal(root.get("organizationId"), tenantContext.getOrganizationId());
        if (action != null && !action.isBlank()) {
            specification = specification.and((root, query, builder) ->
                builder.equal(root.get("action"), action));
        }
        if (actor != null) {
            specification = specification.and((root, query, builder) ->
                builder.equal(root.get("userId"), actor));
        }
        if (resourceType != null && !resourceType.isBlank()) {
            specification = specification.and((root, query, builder) ->
                builder.equal(root.get("resourceType"), resourceType));
        }
        if (from != null) {
            specification = specification.and((root, query, builder) ->
                builder.greaterThanOrEqualTo(root.get("occurredAt"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, builder) ->
                builder.lessThanOrEqualTo(root.get("occurredAt"), to));
        }
        var result = repository.findAll(specification,
            PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "occurredAt")));
        return new PageResponse<>(result.map(this::toResponse).getContent(), result.getNumber(),
            result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getOrganizationId(), log.getUserId(),
            log.getAction(), log.getResourceType(), log.getResourceId(), log.getOutcome(), log.getOccurredAt());
    }
}
