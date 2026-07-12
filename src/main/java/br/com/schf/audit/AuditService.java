package br.com.schf.audit;

import br.com.schf.security.principal.AuthenticatedUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(UUID organizationId, UUID userId, String action,
                       String resourceType, String resourceId, String outcome,
                       String ipAddress, String userAgent, String details) {
        repository.save(new AuditLog(organizationId, userId, action,
            resourceType, resourceId, outcome, ipAddress, userAgent, details));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(UUID organizationId, UUID userId, String action,
                                  String resourceType, String resourceId, String outcome,
                                  String ipAddress, String userAgent, String details) {
        repository.save(new AuditLog(organizationId, userId, action,
            resourceType, resourceId, outcome, ipAddress, userAgent, details));
    }

    @Transactional
    public void recordCurrent(UUID organizationId, String action, String resourceType,
                              String resourceId, String details) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = null;
        if (authentication != null
            && authentication.getPrincipal() instanceof AuthenticatedUserPrincipal principal) {
            userId = principal.getUserId();
        }
        HttpServletRequest request = currentRequest();
        record(organizationId, userId, action, resourceType, resourceId, "SUCCESS",
            request == null ? null : clientIp(request),
            request == null ? null : request.getHeader("User-Agent"), details);
    }

    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes
            ? servletAttributes.getRequest()
            : null;
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
