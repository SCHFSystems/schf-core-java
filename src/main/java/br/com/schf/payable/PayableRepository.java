package br.com.schf.payable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayableRepository extends JpaRepository<Payable, UUID> {
    List<Payable> findByOrganizationId(UUID organizationId);
    List<Payable> findByOrganizationIdAndStatus(UUID organizationId, PayableStatus status);
    Optional<Payable> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
