package br.com.schf.migration.infrastructure;

import br.com.schf.migration.domain.MigrationError;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationErrorRepository extends JpaRepository<MigrationError, UUID> {
    List<MigrationError> findByMigrationJobIdOrderByCreatedAtAsc(UUID migrationJobId);
}
