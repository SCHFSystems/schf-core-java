package br.com.schf.user.admin;

import br.com.schf.audit.AuditService;
import br.com.schf.organization.OrganizationRepository;
import br.com.schf.security.auth.AuthService;
import br.com.schf.security.auth.ClientRequestInfo;
import br.com.schf.security.membership.UserRoleAssignment;
import br.com.schf.security.membership.UserRoleAssignmentRepository;
import br.com.schf.security.principal.AuthenticatedUserPrincipal;
import br.com.schf.security.role.Role;
import br.com.schf.security.role.RoleCodes;
import br.com.schf.security.role.RoleRepository;
import br.com.schf.security.token.RefreshTokenRepository;
import br.com.schf.shared.TenantContext;
import br.com.schf.user.UserAccount;
import br.com.schf.user.UserAccountRepository;
import br.com.schf.user.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class UserAdministrationService {

    private final UserAccountRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantContext tenantContext;
    private final AuditService auditService;
    private final AuthService authService;

    public UserAdministrationService(UserAccountRepository userRepository,
                                     OrganizationRepository organizationRepository,
                                     RoleRepository roleRepository,
                                     UserRoleAssignmentRepository assignmentRepository,
                                     RefreshTokenRepository refreshTokenRepository,
                                     PasswordEncoder passwordEncoder,
                                     TenantContext tenantContext,
                                     AuditService auditService,
                                     AuthService authService) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.roleRepository = roleRepository;
        this.assignmentRepository = assignmentRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantContext = tenantContext;
        this.auditService = auditService;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> findAll() {
        return userRepository.findByOrganizationIdOrderByDisplayNameAsc(organizationId())
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse findById(UUID id) {
        return toResponse(findTenantUser(id));
    }

    public AdminUserResponse create(AdminUserCreateRequest request) {
        var email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw conflict("Email is already in use");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw conflict("Username is already in use");
        }
        var organization = organizationRepository.findById(organizationId())
            .orElseThrow(() -> notFound("Organization not found"));
        var user = new UserAccount(organization, request.username(), email,
            request.displayName(), UserRole.CONSULTA);
        user.setPasswordHash(passwordEncoder.encode(request.temporaryPassword()));
        user.setMustChangePassword(true);
        user = userRepository.save(user);
        if (request.roleIds() != null) {
            for (UUID roleId : request.roleIds().stream().distinct().toList()) {
                assignRoleInternal(user, roleId);
            }
        }
        auditService.recordCurrent(organizationId(), "USER_CREATED", "USER", user.getId().toString(), null);
        return toResponse(user);
    }

    public AdminUserResponse update(UUID id, AdminUserUpdateRequest request) {
        var user = findTenantUser(id);
        var email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw conflict("Email is already in use");
        }
        user.setEmail(email);
        user.setDisplayName(request.displayName());
        userRepository.save(user);
        auditService.recordCurrent(organizationId(), "USER_UPDATED", "USER", id.toString(), null);
        return toResponse(user);
    }

    public AdminUserResponse activate(UUID id) {
        var user = findTenantUser(id);
        user.setActive(true);
        userRepository.save(user);
        auditService.recordCurrent(organizationId(), "USER_ACTIVATED", "USER", id.toString(), null);
        return toResponse(user);
    }

    public AdminUserResponse deactivate(UUID id, AuthenticatedUserPrincipal actor) {
        if (actor.getUserId().equals(id)) {
            throw conflict("A user cannot deactivate their own account");
        }
        var user = findTenantUser(id);
        if (assignmentRepository.userHasRoleCode(id, RoleCodes.OWNER)) {
            var ownerRole = roleRepository.findByOrganizationIdAndCode(organizationId(), RoleCodes.OWNER)
                .flatMap(role -> roleRepository.findForUpdate(role.getId(), organizationId()))
                .orElseThrow(() -> notFound("OWNER role not found"));
            if (ownerRole.isActive()
                && assignmentRepository.countActiveByOrganizationAndRoleCode(
                    organizationId(), RoleCodes.OWNER) <= 1) {
                throw conflict("The last active OWNER cannot be deactivated");
            }
        }
        user.setActive(false);
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(id, "USER_DEACTIVATED");
        auditService.recordCurrent(organizationId(), "USER_DEACTIVATED", "USER", id.toString(), null);
        return toResponse(user);
    }

    public AdminUserResponse assignRole(UUID userId, UUID roleId) {
        var user = findTenantUser(userId);
        assignRoleInternal(user, roleId);
        auditService.recordCurrent(organizationId(), "USER_ROLE_ASSIGNED", "USER", userId.toString(),
            "roleId=" + roleId);
        return toResponse(user);
    }

    public AdminUserResponse removeRole(UUID userId, UUID roleId) {
        var user = findTenantUser(userId);
        var role = roleRepository.findForUpdate(roleId, organizationId())
            .orElseThrow(() -> notFound("Role not found"));
        var assignment = assignmentRepository.findByUserIdAndRoleId(userId, roleId)
            .orElseThrow(() -> notFound("Role assignment not found"));
        if (RoleCodes.OWNER.equals(role.getCode())
            && user.isActive()
            && assignmentRepository.countActiveByOrganizationAndRoleCode(
                organizationId(), RoleCodes.OWNER) <= 1) {
            throw conflict("The last OWNER role cannot be removed");
        }
        assignmentRepository.delete(assignment);
        auditService.recordCurrent(organizationId(), "USER_ROLE_REMOVED", "USER", userId.toString(),
            "roleId=" + roleId);
        return toResponse(user);
    }

    public void requestPasswordReset(UUID userId, ClientRequestInfo client) {
        var user = findTenantUser(userId);
        authService.requestPasswordReset(user, client, "ADMIN_PASSWORD_RESET_REQUESTED");
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> findRoles() {
        return roleRepository.findByOrganizationIdOrderByNameAsc(organizationId())
            .stream().map(this::toRoleResponse).toList();
    }

    private void assignRoleInternal(UserAccount user, UUID roleId) {
        var role = roleRepository.findForUpdate(roleId, organizationId())
            .filter(Role::isActive)
            .orElseThrow(() -> notFound("Role not found"));
        if (!assignmentRepository.existsByUserIdAndRoleId(user.getId(), roleId)) {
            assignmentRepository.save(new UserRoleAssignment(user, role));
        }
    }

    private UserAccount findTenantUser(UUID id) {
        return userRepository.findByIdAndOrganizationId(id, organizationId())
            .orElseThrow(() -> notFound("User not found"));
    }

    private AdminUserResponse toResponse(UserAccount user) {
        var roles = assignmentRepository.findByUserId(user.getId()).stream()
            .map(UserRoleAssignment::getRole)
            .map(this::toRoleResponse)
            .toList();
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getEmail(),
            user.getDisplayName(), user.isActive(), user.isMustChangePassword(),
            user.getLastLoginAt(), user.getLockedUntil(), user.getPasswordChangedAt(), roles);
    }

    private RoleResponse toRoleResponse(Role role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(), role.isActive());
    }

    private UUID organizationId() {
        return tenantContext.getOrganizationId();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
