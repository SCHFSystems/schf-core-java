package br.com.schf.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByPayableId(UUID payableId);
    List<Payment> findByPayableIdAndOrganizationId(UUID payableId, UUID organizationId);
    List<Payment> findByOrganizationId(UUID organizationId);
    Page<Payment> findByOrganizationId(UUID organizationId, Pageable pageable);
    Optional<Payment> findByIdAndOrganizationId(UUID id, UUID organizationId);
    long countByOrganizationId(UUID organizationId);
}
