package br.com.schf.migration.infrastructure;

import br.com.schf.migration.domain.MigrationExternalId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationExternalIdRepository extends JpaRepository<MigrationExternalId, UUID> {
    Optional<MigrationExternalId> findByOrganizationIdAndSourceSystemAndEntityTypeAndExternalId(
        UUID organizationId, String sourceSystem, String entityType, UUID externalId);
    long countByOrganizationIdAndSourceSystem(UUID organizationId, String sourceSystem);
    long countByMigrationJobId(UUID migrationJobId);
}
