package br.com.schf.migration.infrastructure;

import br.com.schf.migration.domain.MigrationJob;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationJobRepository extends JpaRepository<MigrationJob, UUID> {
    List<MigrationJob> findByOrganizationIdOrderByStartedAtDesc(UUID organizationId);
    Optional<MigrationJob> findByIdAndOrganizationId(UUID id, UUID organizationId);
    Optional<MigrationJob> findByOrganizationIdAndBundleIdAndDryRun(UUID organizationId,
                                                                     String bundleId,
                                                                     boolean dryRun);
}
