package br.com.schf.payment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByPayableId(UUID payableId);
    List<Payment> findByPayableIdAndOrganizationId(UUID payableId, UUID organizationId);
    List<Payment> findByOrganizationId(UUID organizationId);
}
