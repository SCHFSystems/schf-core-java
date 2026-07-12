package br.com.schf.user;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    @EntityGraph(attributePaths = "organization")
    Optional<UserAccount> findByUsername(String username);

    @EntityGraph(attributePaths = "organization")
    Optional<UserAccount> findByEmail(String email);

    @Override
    @EntityGraph(attributePaths = "organization")
    Optional<UserAccount> findById(UUID id);

    @EntityGraph(attributePaths = "organization")
    Optional<UserAccount> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @EntityGraph(attributePaths = "organization")
    List<UserAccount> findByOrganizationIdOrderByDisplayNameAsc(UUID organizationId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "organization")
    @Query("SELECT u FROM UserAccount u WHERE u.id = :id")
    Optional<UserAccount> findForUpdate(@Param("id") UUID id);
}
