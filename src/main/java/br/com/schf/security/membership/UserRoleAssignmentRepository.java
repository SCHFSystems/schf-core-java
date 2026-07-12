package br.com.schf.security.membership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UUID> {

    List<UserRoleAssignment> findByUserId(UUID userId);

    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);

    Optional<UserRoleAssignment> findByUserIdAndRoleId(UUID userId, UUID roleId);

    @Query("SELECT COUNT(a) FROM UserRoleAssignment a "
        + "WHERE a.role.organization.id = :organizationId AND a.role.code = :roleCode")
    long countByOrganizationAndRoleCode(@Param("organizationId") UUID organizationId,
                                        @Param("roleCode") String roleCode);

    @Query("SELECT COUNT(a) FROM UserRoleAssignment a "
        + "WHERE a.role.organization.id = :organizationId AND a.role.code = :roleCode "
        + "AND a.user.active = true")
    long countActiveByOrganizationAndRoleCode(@Param("organizationId") UUID organizationId,
                                              @Param("roleCode") String roleCode);

    @Query("SELECT (COUNT(a) > 0) FROM UserRoleAssignment a "
        + "WHERE a.user.id = :userId AND a.role.code = :roleCode")
    boolean userHasRoleCode(@Param("userId") UUID userId, @Param("roleCode") String roleCode);

    @Query(value = """
        SELECT DISTINCT p.code
        FROM user_roles ur
        JOIN roles r ON r.id = ur.role_id
        JOIN role_permissions rp ON rp.role_id = r.id
        JOIN permissions p ON p.id = rp.permission_id
        WHERE ur.user_id = :userId AND r.active = TRUE
        """, nativeQuery = true)
    List<String> findPermissionCodesByUserId(@Param("userId") UUID userId);
}
