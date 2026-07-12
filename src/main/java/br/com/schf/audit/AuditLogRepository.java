package br.com.schf.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    long countByActionAndUserId(String action, UUID userId);

    long countByActionAndOrganizationId(String action, UUID organizationId);
}
