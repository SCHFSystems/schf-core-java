package br.com.schf.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SetupServiceTest {

    @Mock
    private InstanceSetupRepository setupRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private UserAccountRepository userRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RolePermissionRepository rolePermissionRepository;
    @Mock
    private UserRoleAssignmentRepository userRoleRepository;
    @Mock
    private AuditService auditService;

    private SetupService setupService;

    @BeforeEach
    void setUp() {
        setupService = new SetupService(setupRepository, organizationRepository, userRepository,
            permissionRepository, roleRepository, rolePermissionRepository, userRoleRepository,
            new BCryptPasswordEncoder(4), auditService);
    }

    @Test
    void getStatusReturnsSetupRequiredWhenNotCompleted() {
        when(setupRepository.findAll()).thenReturn(List.of(new InstanceSetup(UUID.randomUUID())));
        var status = setupService.getStatus();
        assertThat(status.setupRequired()).isTrue();
    }

    @Test
    void getStatusReturnsSetupNotRequiredWhenCompleted() {
        var setup = new InstanceSetup(UUID.randomUUID());
        setup.complete(UUID.randomUUID());
        when(setupRepository.findAll()).thenReturn(List.of(setup));
        var status = setupService.getStatus();
        assertThat(status.setupRequired()).isFalse();
    }

    @Test
    void initializeThrowsWhenAlreadyCompleted() {
        var setup = new InstanceSetup(UUID.randomUUID());
        setup.complete(UUID.randomUUID());
        when(setupRepository.findForUpdate()).thenReturn(Optional.of(setup));
        var request = new SetupRequest("ORG", "Org", "admin", "a@b.com", "password12345678");
        assertThatThrownBy(() -> setupService.initialize(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
    }

    @Test
    void initializeThrowsWhenOrgCodeExists() {
        var setup = new InstanceSetup(UUID.randomUUID());
        when(setupRepository.findForUpdate()).thenReturn(Optional.of(setup));
        when(organizationRepository.findByCode("DUPLICATE")).thenReturn(Optional.of(new Organization("DUPLICATE", "x")));
        var request = new SetupRequest("DUPLICATE", "Org", "admin", "a@b.com", "password12345678");
        assertThatThrownBy(() -> setupService.initialize(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
    }

    @Test
    void initializeCreatesOrganizationAndAdminSuccessfully() {
        var setup = new InstanceSetup(UUID.randomUUID());
        when(setupRepository.findForUpdate()).thenReturn(Optional.of(setup));
        when(organizationRepository.findByCode("NEW_ORG")).thenReturn(Optional.empty());
        var org = new Organization("NEW_ORG", "New Org");
        when(organizationRepository.save(any(Organization.class))).thenReturn(org);
        when(permissionRepository.findByCode(any())).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenAnswer(i -> i.getArgument(0));
        when(roleRepository.findByOrganizationIdAndCode(any(), any())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));
        when(rolePermissionRepository.existsByRoleIdAndPermissionId(any(), any())).thenReturn(false);
        when(userRepository.save(any(UserAccount.class))).thenAnswer(i -> i.getArgument(0));
        when(setupRepository.save(any(InstanceSetup.class))).thenReturn(setup);
        var request = new SetupRequest("NEW_ORG", "New Org", "admin", "admin@example.com", "super_strong_password_here");
        var response = setupService.initialize(request);
        assertThat(response.completed()).isTrue();
        assertThat(response.message()).contains("completed successfully");
        verify(setupRepository).save(any(InstanceSetup.class));
        verify(auditService).recordIndependent(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
