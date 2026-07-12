package br.com.schf.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    @EntityGraph(attributePaths = "organization")
    Optional<UserAccount> findByUsername(String username);

    @EntityGraph(attributePaths = "organization")
    Optional<UserAccount> findByEmail(String email);

    @Override
    @EntityGraph(attributePaths = "organization")
    Optional<UserAccount> findById(UUID id);
}
