package br.com.schf.setup;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface InstanceSetupRepository extends JpaRepository<InstanceSetup, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InstanceSetup s ORDER BY s.createdAt ASC")
    Optional<InstanceSetup> findForUpdate();
}
