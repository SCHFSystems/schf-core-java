package br.com.schf.migration.infrastructure;

import br.com.schf.migration.domain.MigrationBundleFile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationBundleFileRepository extends JpaRepository<MigrationBundleFile, UUID> {
}
