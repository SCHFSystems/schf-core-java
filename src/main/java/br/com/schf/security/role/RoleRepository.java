package br.com.schf.security.role;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    java.util.Optional<Role> findByOrganizationIdAndCode(java.util.UUID organizationId, String code);

    java.util.List<Role> findByOrganizationId(java.util.UUID organizationId);

    java.util.List<Role> findByOrganizationIdOrderByNameAsc(UUID organizationId);

    java.util.Optional<Role> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Role r WHERE r.id = :id AND r.organization.id = :organizationId")
    java.util.Optional<Role> findForUpdate(@Param("id") UUID id,
                                           @Param("organizationId") UUID organizationId);
}
