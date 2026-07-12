package br.com.schf.config;

import br.com.schf.organization.Organization;
import br.com.schf.organization.OrganizationRepository;
import br.com.schf.security.BootstrapAdminProperties;
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
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevSeed implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeed.class);
    private static final List<String> ALL_PERMISSIONS = List.of(
        Permissions.SUPPLIER_READ, Permissions.SUPPLIER_WRITE,
        Permissions.CATEGORY_READ, Permissions.CATEGORY_WRITE,
        Permissions.ACCOUNT_READ, Permissions.ACCOUNT_WRITE,
        Permissions.PAYABLE_READ, Permissions.PAYABLE_WRITE,
        Permissions.PAYMENT_WRITE, Permissions.REPORT_READ,
        Permissions.USER_READ, Permissions.USER_WRITE, Permissions.ADMIN_ACCESS,
        Permissions.AUDIT_READ);

    private final OrganizationRepository organizationRepository;
    private final UserAccountRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleAssignmentRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapAdminProperties adminProperties;

    public DevSeed(OrganizationRepository organizationRepository,
                   UserAccountRepository userRepository,
                   PermissionRepository permissionRepository,
                   RoleRepository roleRepository,
                   RolePermissionRepository rolePermissionRepository,
                   UserRoleAssignmentRepository userRoleRepository,
                   PasswordEncoder passwordEncoder,
                   BootstrapAdminProperties adminProperties) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminProperties = adminProperties;
    }

    @Override
    @Transactional
    public void run(String... args) {
        var organization = organizationRepository.findByCode("DEFAULT")
            .orElseGet(() -> organizationRepository.save(
                new Organization("DEFAULT", "Default Development Organization")));
        var permissions = seedPermissions();
        var roles = seedRoles(organization);
        assignPermissions(roles, permissions);
        seedAdmin(organization, roles.get(RoleCodes.OWNER));
        log.info("Development security seed is ready for organization {}", organization.getId());
    }

    private Map<String, Permission> seedPermissions() {
        var result = new LinkedHashMap<String, Permission>();
        for (String code : ALL_PERMISSIONS) {
            result.put(code, permissionRepository.findByCode(code)
                .orElseGet(() -> permissionRepository.save(new Permission(code, code, null))));
        }
        return result;
    }

    private Map<String, Role> seedRoles(Organization organization) {
        var result = new LinkedHashMap<String, Role>();
        for (String code : List.of(RoleCodes.OWNER, RoleCodes.ADMIN, RoleCodes.FINANCE, RoleCodes.VIEWER)) {
            result.put(code, roleRepository.findByOrganizationIdAndCode(organization.getId(), code)
                .orElseGet(() -> roleRepository.save(new Role(organization, code, code, null))));
        }
        return result;
    }

    private void assignPermissions(Map<String, Role> roles, Map<String, Permission> permissions) {
        assign(roles.get(RoleCodes.OWNER), permissions, ALL_PERMISSIONS);
        assign(roles.get(RoleCodes.ADMIN), permissions, ALL_PERMISSIONS);
        assign(roles.get(RoleCodes.FINANCE), permissions, List.of(
            Permissions.SUPPLIER_READ, Permissions.SUPPLIER_WRITE,
            Permissions.CATEGORY_READ, Permissions.CATEGORY_WRITE,
            Permissions.ACCOUNT_READ, Permissions.ACCOUNT_WRITE,
            Permissions.PAYABLE_READ, Permissions.PAYABLE_WRITE,
            Permissions.PAYMENT_WRITE, Permissions.REPORT_READ));
        assign(roles.get(RoleCodes.VIEWER), permissions, List.of(
            Permissions.SUPPLIER_READ, Permissions.CATEGORY_READ,
            Permissions.ACCOUNT_READ, Permissions.PAYABLE_READ, Permissions.REPORT_READ));
    }

    private void assign(Role role, Map<String, Permission> permissions, List<String> codes) {
        for (String code : codes) {
            var permission = permissions.get(code);
            if (!rolePermissionRepository.existsByRoleIdAndPermissionId(role.getId(), permission.getId())) {
                rolePermissionRepository.save(new RolePermission(role, permission));
            }
        }
    }

    private void seedAdmin(Organization organization, Role ownerRole) {
        var email = adminProperties.getEmail().trim().toLowerCase();
        var user = userRepository.findByEmail(email).orElseGet(() -> {
            var created = new UserAccount(organization, adminProperties.getUsername(), email,
                "Development Administrator", UserRole.ADMIN);
            created.setPasswordHash(passwordEncoder.encode(adminProperties.getPassword()));
            created.setMustChangePassword(true);
            return userRepository.save(created);
        });
        if ("CHANGEME_IN_BOOTSTRAP".equals(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(adminProperties.getPassword()));
            user.setMustChangePassword(true);
            user = userRepository.save(user);
        }
        if (!userRoleRepository.existsByUserIdAndRoleId(user.getId(), ownerRole.getId())) {
            userRoleRepository.save(new UserRoleAssignment(user, ownerRole));
        }
    }
}
