package br.com.schf.config;

import br.com.schf.organization.OrganizationRepository;
import br.com.schf.shared.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "schf.tenant.strategy", havingValue = "auto")
public class TenantFilter extends OncePerRequestFilter {

    private final TenantContext tenantContext;
    private final OrganizationRepository organizationRepository;
    private volatile java.util.UUID defaultOrgId;

    public TenantFilter(TenantContext tenantContext, OrganizationRepository organizationRepository) {
        this.tenantContext = tenantContext;
        this.organizationRepository = organizationRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (defaultOrgId == null) {
            var orgs = organizationRepository.findAll();
            if (!orgs.isEmpty()) {
                defaultOrgId = orgs.getFirst().getId();
            }
        }
        if (defaultOrgId != null) {
            tenantContext.setOrganizationId(defaultOrgId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            tenantContext.clear();
        }
    }
}
