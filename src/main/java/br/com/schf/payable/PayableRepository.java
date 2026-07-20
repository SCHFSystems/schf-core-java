package br.com.schf.payable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayableRepository extends JpaRepository<Payable, UUID> {
    List<Payable> findByOrganizationId(UUID organizationId);
    Page<Payable> findByOrganizationId(UUID organizationId, Pageable pageable);
    List<Payable> findByOrganizationIdAndStatus(UUID organizationId, PayableStatus status);
    Optional<Payable> findByIdAndOrganizationId(UUID id, UUID organizationId);
    long countByOrganizationId(UUID organizationId);

    @Query("SELECT p.id, COALESCE(SUM(pmt.amount), 0) FROM Payable p " +
           "LEFT JOIN Payment pmt ON p.id = pmt.payableId AND pmt.organizationId = p.organizationId " +
           "WHERE p.id IN :ids AND p.organizationId = :orgId " +
           "GROUP BY p.id")
    List<Object[]> sumPaymentsByIds(@Param("ids") List<UUID> ids, @Param("orgId") UUID orgId);

    @Query("SELECT p.id, COALESCE(SUM(pmt.amount), 0) FROM Payable p " +
           "LEFT JOIN Payment pmt ON p.id = pmt.payableId AND pmt.organizationId = p.organizationId " +
           "WHERE p.organizationId = :orgId " +
           "GROUP BY p.id")
    List<Object[]> sumAllPaymentsByOrganization(@Param("orgId") UUID orgId);
}
