package br.com.schf.migration.infrastructure;

import br.com.schf.migration.domain.UnresolvedLegacyReference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnresolvedLegacyReferenceRepository extends JpaRepository<UnresolvedLegacyReference, UUID> {
    List<UnresolvedLegacyReference> findByOrganizationId(UUID organizationId);
    Optional<UnresolvedLegacyReference> findByOrganizationIdAndExternalId(UUID organizationId, UUID externalId);
    List<UnresolvedLegacyReference> findByMigrationJobId(UUID migrationJobId);
    long countByOrganizationId(UUID organizationId);
}
