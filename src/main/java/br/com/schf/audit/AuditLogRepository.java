package br.com.schf.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    long countByActionAndUserId(String action, UUID userId);

    long countByActionAndOrganizationId(String action, UUID organizationId);
}
