package br.com.schf.setup;

import br.com.schf.audit.AuditService;
import br.com.schf.organization.Organization;
import br.com.schf.organization.OrganizationRepository;
import br.com.schf.security.membership.UserRoleAssignment;
import br.com.schf.security.membership.UserRoleAssignmentRepository;
import br.com.schf.security.permission.Permission;
import br.com.schf.security.permission.PermissionRepository;
import br.com.schf.security.permission.Permissions;
import br.com.schf.security.role.Role;
import br.com.schf.security.role.RoleCodes;
import br.com.schf.security.role.RolePermission;
import br.com.schf.security.role.RolePermissionRepository;
import br.com.schf.security.role.RoleRepository;
import br.com.schf.user.UserAccount;
import br.com.schf.user.UserAccountRepository;
import br.com.schf.user.UserRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SetupService {

    private static final Logger log = LoggerFactory.getLogger(SetupService.class);
    private static final List<String> ALL_PERMISSIONS = List.of(
        Permissions.SUPPLIER_READ, Permissions.SUPPLIER_WRITE,
        Permissions.CATEGORY_READ, Permissions.CATEGORY_WRITE,
        Permissions.ACCOUNT_READ, Permissions.ACCOUNT_WRITE,
        Permissions.PAYABLE_READ, Permissions.PAYABLE_WRITE,
        Permissions.PAYMENT_WRITE, Permissions.REPORT_READ,
        Permissions.USER_READ, Permissions.USER_WRITE, Permissions.ADMIN_ACCESS,
        Permissions.AUDIT_READ, Permissions.MIGRATION_READ, Permissions.MIGRATION_IMPORT);

    private final InstanceSetupRepository setupRepository;
    private final OrganizationRepository organizationRepository;
    private final UserAccountRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleAssignmentRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public SetupService(InstanceSetupRepository setupRepository,
                        OrganizationRepository organizationRepository,
                        UserAccountRepository userRepository,
                        PermissionRepository permissionRepository,
                        RoleRepository roleRepository,
                        RolePermissionRepository rolePermissionRepository,
                        UserRoleAssignmentRepository userRoleRepository,
                        PasswordEncoder passwordEncoder,
                        AuditService auditService) {
        this.setupRepository = setupRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    public SetupStatusResponse getStatus() {
        var all = setupRepository.findAll();
        var completed = all.stream().anyMatch(InstanceSetup::isCompleted);
        return new SetupStatusResponse(!completed);
    }

    @Transactional
    public SetupResponse initialize(SetupRequest request) {
        var setupRow = setupRepository.findForUpdate()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Setup row not found"));

        if (setupRow.isCompleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Setup has already been completed");
        }

        if (organizationRepository.findByCode(request.organizationCode()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Organization code already exists");
        }

        var organization = organizationRepository.save(
            new Organization(request.organizationCode(), request.organizationName()));

        var permissions = seedPermissions();
        var ownerRole = seedOwnerRole(organization, permissions);
        seedAdminUser(organization, ownerRole, request);

        setupRow.complete(organization.getId());
        setupRepository.save(setupRow);

        auditService.record(organization.getId(), null, "INSTANCE_SETUP_COMPLETED",
            "SETUP", organization.getId().toString(), "SUCCESS", "127.0.0.1", "SCHF-SETUP",
            "Instance setup completed for organization " + organization.getCode());

        log.info("Instance setup completed for organization {} ({})", organization.getCode(), organization.getId());
        return SetupResponse.success(organization.getId());
    }

    private Map<String, Permission> seedPermissions() {
        var result = new LinkedHashMap<String, Permission>();
        for (String code : ALL_PERMISSIONS) {
            result.put(code, permissionRepository.findByCode(code)
                .orElseGet(() -> permissionRepository.save(new Permission(code, code, null))));
        }
        return result;
    }

    private Role seedOwnerRole(Organization organization, Map<String, Permission> permissions) {
        var role = roleRepository.findByOrganizationIdAndCode(organization.getId(), RoleCodes.OWNER)
            .orElseGet(() -> roleRepository.save(new Role(organization, RoleCodes.OWNER, "Owner", null)));
        for (String code : ALL_PERMISSIONS) {
            var permission = permissions.get(code);
            if (!rolePermissionRepository.existsByRoleIdAndPermissionId(role.getId(), permission.getId())) {
                rolePermissionRepository.save(new RolePermission(role, permission));
            }
        }
        return role;
    }

    private void seedAdminUser(Organization organization, Role ownerRole, SetupRequest request) {
        var user = new UserAccount(organization, request.adminUsername(), request.adminEmail(),
            request.adminUsername(), UserRole.ADMIN);
        user.setPasswordHash(passwordEncoder.encode(request.adminPassword()));
        user.setMustChangePassword(true);
        userRepository.save(user);
        userRoleRepository.save(new UserRoleAssignment(user, ownerRole));
    }
}
