package br.com.schf.security.tenant;

import br.com.schf.security.principal.AuthenticatedUserPrincipal;
import br.com.schf.shared.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(50)
public class TenantContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

    private final TenantContext tenantContext;

    public TenantContextFilter(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
            && authentication.isAuthenticated()
            && authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal
            && principal.getOrganizationId() != null) {
            tenantContext.setOrganizationId(principal.getOrganizationId());
            try {
                chain.doFilter(request, response);
            } finally {
                tenantContext.clear();
            }
        } else {
            log.trace("No authenticated principal with organization context; skipping tenant binding");
            chain.doFilter(request, response);
        }
    }
}
