package br.com.schf.security.role;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    java.util.Optional<Role> findByOrganizationIdAndCode(java.util.UUID organizationId, String code);

    java.util.List<Role> findByOrganizationId(java.util.UUID organizationId);
}
