package br.com.schf.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.schf.audit.AuditLog;
import br.com.schf.audit.AuditLogRepository;
import br.com.schf.organization.Organization;
import br.com.schf.organization.OrganizationRepository;
import br.com.schf.security.membership.UserRoleAssignment;
import br.com.schf.security.membership.UserRoleAssignmentRepository;
import br.com.schf.security.permission.Permission;
import br.com.schf.security.permission.PermissionRepository;
import br.com.schf.security.permission.Permissions;
import br.com.schf.security.ratelimit.RateLimitService;
import br.com.schf.security.reset.CapturingPasswordResetDeliveryService;
import br.com.schf.security.role.Role;
import br.com.schf.security.role.RoleCodes;
import br.com.schf.security.role.RolePermission;
import br.com.schf.security.role.RolePermissionRepository;
import br.com.schf.security.role.RoleRepository;
import br.com.schf.user.UserAccount;
import br.com.schf.user.UserAccountRepository;
import br.com.schf.user.UserRole;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityHardeningIntegrationTest {

    private static final String PASSWORD = "valid-password-123";
    private static final String NEW_PASSWORD = "new-valid-password-456";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("schf_hardening_test")
        .withUsername("schf")
        .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("schf.security.jwt.secret", () ->
            "fake_test_jwt_secret_that_is_longer_than_thirty_two_bytes_1234");
        registry.add("schf.security.hardening.maximum-failed-logins", () -> 3);
        registry.add("schf.security.hardening.lockout-seconds", () -> 300);
        registry.add("schf.security.hardening.rate-limit-window-seconds", () -> 60);
        registry.add("schf.security.hardening.login-rate-limit", () -> 5);
        registry.add("schf.security.hardening.refresh-rate-limit", () -> 5);
        registry.add("schf.security.hardening.forgot-password-rate-limit", () -> 5);
        registry.add("schf.security.hardening.reset-password-rate-limit", () -> 5);
    }

    @LocalServerPort
    int port;

    @Autowired TestRestTemplate restTemplate;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired UserAccountRepository userRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired RolePermissionRepository rolePermissionRepository;
    @Autowired UserRoleAssignmentRepository assignmentRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RateLimitService rateLimitService;
    @Autowired CapturingPasswordResetDeliveryService resetDeliveryService;
    @Autowired MeterRegistry meterRegistry;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpClientState() {
        restTemplate.getRestTemplate().setRequestFactory(
            new JdkClientHttpRequestFactory(HttpClient.newHttpClient()));
        rateLimitService.reset();
        resetDeliveryService.reset();
    }

    @Test
    void userWithoutAdminPermissionCannotAccessUsers() {
        var organization = organization("NOADMIN");
        var user = user(organization, "plain", List.of());

        var response = get("/api/admin/users", login(user.getEmail(), PASSWORD).accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void userReadListsOnlyOwnOrganizationAndCannotReadForeignUser() {
        var ownOrganization = organization("USERREAD");
        var foreignOrganization = organization("FOREIGN");
        var reader = user(ownOrganization, "reader", List.of(Permissions.USER_READ));
        var ownUser = user(ownOrganization, "own-user", List.of());
        var foreignUser = user(foreignOrganization, "foreign-user", List.of());
        var token = login(reader.getEmail(), PASSWORD).accessToken();

        var list = get("/api/admin/users", token);
        var foreign = get("/api/admin/users/" + foreignUser.getId(), token);

        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains(ownUser.getId().toString());
        assertThat(list.getBody()).doesNotContain(foreignUser.getId().toString());
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void adminCrudDoesNotExposePasswordOrHash() {
        var organization = organization("CRUD");
        var admin = user(organization, "admin-crud", List.of(Permissions.USER_WRITE, Permissions.USER_READ));
        var token = login(admin.getEmail(), PASSWORD).accessToken();
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var createBody = Map.of(
            "username", "created-" + suffix,
            "email", "created-" + suffix + "@example.invalid",
            "displayName", "Created User",
            "temporaryPassword", "aaaaaaaaaaaa",
            "roleIds", List.of());

        var created = exchange("/api/admin/users", HttpMethod.POST, createBody, token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).doesNotContain("aaaaaaaaaaaa", "passwordHash");
        var createdId = jsonField(created.getBody(), "id");

        var updated = exchange("/api/admin/users/" + createdId, HttpMethod.PATCH,
            Map.of("email", "updated-" + suffix + "@example.invalid", "displayName", "Updated User"), token);
        var deactivated = exchange("/api/admin/users/" + createdId + "/deactivate",
            HttpMethod.POST, Map.of(), token);
        var activated = exchange("/api/admin/users/" + createdId + "/activate",
            HttpMethod.POST, Map.of(), token);
        var passwordReset = exchange("/api/admin/users/" + createdId + "/password-reset",
            HttpMethod.POST, Map.of(), token);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).contains("Updated User");
        assertThat(deactivated.getBody()).contains("\"active\":false");
        assertThat(activated.getBody()).contains("\"active\":true");
        assertThat(passwordReset.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resetDeliveryService.findLatest("updated-" + suffix + "@example.invalid")).isPresent();
    }

    @Test
    void roleAssignmentAndRemovalWork() {
        var organization = organization("ROLES");
        var admin = user(organization, "role-admin", List.of(Permissions.USER_WRITE));
        var target = user(organization, "role-target", List.of());
        var role = roleRepository.save(new Role(organization, "FINANCE-TEST", "Finance Test", null));
        var token = login(admin.getEmail(), PASSWORD).accessToken();

        var assigned = exchange("/api/admin/users/" + target.getId() + "/roles", HttpMethod.POST,
            Map.of("roleId", role.getId().toString()), token);
        var removed = exchange("/api/admin/users/" + target.getId() + "/roles/" + role.getId(),
            HttpMethod.DELETE, null, token);

        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assigned.getBody()).contains(role.getId().toString());
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(removed.getBody()).doesNotContain(role.getId().toString());
    }

    @Test
    void lastOwnerCannotRemoveOwnOwnerRole() {
        var organization = organization("OWNER");
        var ownerRole = roleRepository.save(new Role(organization, RoleCodes.OWNER, "Owner", null));
        grant(ownerRole, Permissions.USER_WRITE);
        var owner = userWithRole(organization, "last-owner", ownerRole);
        var token = login(owner.getEmail(), PASSWORD).accessToken();

        var response = exchange("/api/admin/users/" + owner.getId() + "/roles/" + ownerRole.getId(),
            HttpMethod.DELETE, null, token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(assignmentRepository.existsByUserIdAndRoleId(owner.getId(), ownerRole.getId())).isTrue();
    }

    @Test
    void passwordChangeRequiresCurrentPasswordAndRevokesRefreshTokens() {
        var organization = organization("PASSWORD");
        var user = user(organization, "password-user", List.of());
        var tokens = login(user.getEmail(), PASSWORD);

        var invalid = exchange("/api/auth/change-password", HttpMethod.POST,
            Map.of("currentPassword", "wrong-password", "newPassword", NEW_PASSWORD), tokens.accessToken());
        var changed = exchange("/api/auth/change-password", HttpMethod.POST,
            Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD), tokens.accessToken());
        var oldRefresh = exchange("/api/auth/refresh", HttpMethod.POST,
            Map.of("refreshToken", tokens.refreshToken()), null);
        var oldLogin = loginResponse(user.getEmail(), PASSWORD);
        var newLogin = loginResponse(user.getEmail(), NEW_PASSWORD);

        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(oldRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(oldLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(newLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void passwordResetDoesNotEnumerateAndTokenIsSingleUse() {
        var organization = organization("RESET");
        var user = user(organization, "reset-user", List.of());

        var existing = exchange("/api/auth/password/forgot", HttpMethod.POST,
            Map.of("email", user.getEmail()), null);
        var absent = exchange("/api/auth/password/forgot", HttpMethod.POST,
            Map.of("email", "absent-" + UUID.randomUUID() + "@example.invalid"), null);
        var delivery = resetDeliveryService.findLatest(user.getEmail()).orElseThrow();
        var resetBody = Map.of("token", delivery.token(), "newPassword", NEW_PASSWORD);
        var firstUse = exchange("/api/auth/password/reset", HttpMethod.POST, resetBody, null);
        var secondUse = exchange("/api/auth/password/reset", HttpMethod.POST, resetBody, null);

        assertThat(existing.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(absent.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(existing.getBody()).isEqualTo(absent.getBody());
        assertThat(firstUse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondUse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(loginResponse(user.getEmail(), NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void rateLimitBlocksExcessLoginRequests() {
        org.springframework.http.ResponseEntity<String> response = null;
        for (int attempt = 0; attempt < 6; attempt++) {
            response = loginResponse("missing-" + attempt + "@example.invalid", "wrong-password");
        }

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void repeatedFailuresTemporarilyLockAccount() {
        var organization = organization("LOCKOUT");
        var user = user(organization, "lock-user", List.of());

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(loginResponse(user.getEmail(), "wrong-password").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        var correctWhileLocked = loginResponse(user.getEmail(), PASSWORD);
        var reloaded = userRepository.findById(user.getId()).orElseThrow();

        assertThat(correctWhileLocked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reloaded.getLockedUntil()).isNotNull();
        assertThat(auditLogRepository.countByActionAndUserId("ACCOUNT_LOCKED", user.getId())).isEqualTo(1);
    }

    @Test
    void auditQueryIsPermissionedPaginatedFilteredAndSanitized() {
        var organization = organization("AUDITQUERY");
        var denied = user(organization, "audit-denied", List.of());
        var allowed = user(organization, "audit-reader", List.of(Permissions.AUDIT_READ));
        auditLogRepository.save(new AuditLog(organization.getId(), allowed.getId(), "FILTER_ACTION",
            "USER", allowed.getId().toString(), "SUCCESS", "127.0.0.1", "secret-agent",
            "password=never-return-this"));

        var deniedResponse = get("/api/admin/audit-logs", login(denied.getEmail(), PASSWORD).accessToken());
        var allowedResponse = get("/api/admin/audit-logs?action=FILTER_ACTION&page=0&size=1",
            login(allowed.getEmail(), PASSWORD).accessToken());

        assertThat(deniedResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(allowedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allowedResponse.getBody()).contains("FILTER_ACTION", "\"size\":1");
        assertThat(allowedResponse.getBody()).doesNotContain(
            "never-return-this", "secret-agent", "password", "token", "userAgent", "ipAddress", "details");
    }

    @Test
    void securityMetricsContainNoPiiTags() {
        var securityMeters = meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().startsWith("schf.auth."))
            .toList();

        assertThat(securityMeters).isNotEmpty();
        assertThat(securityMeters).allSatisfy(meter -> assertThat(meter.getId().getTags()).isEmpty());
    }

    @Test
    void flywayV4CreatesHardeningColumnsAndAuditPermission() {
        var columns = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.columns
            WHERE table_name = 'app_users'
              AND column_name IN ('failed_login_attempts', 'locked_until', 'password_changed_at')
            """, Integer.class);
        var auditPermission = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM permissions WHERE code = 'AUDIT_READ'", Integer.class);

        assertThat(columns).isEqualTo(3);
        assertThat(auditPermission).isEqualTo(1);
    }

    private Organization organization(String prefix) {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        return organizationRepository.save(new Organization(prefix + "-" + suffix, prefix + " Organization"));
    }

    private UserAccount user(Organization organization, String prefix, List<String> permissionCodes) {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var role = roleRepository.save(new Role(organization, "ROLE-" + suffix, "Test Role", null));
        for (String code : permissionCodes) {
            grant(role, code);
        }
        return userWithRole(organization, prefix + "-" + suffix, role);
    }

    private UserAccount userWithRole(Organization organization, String prefix, Role role) {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var user = new UserAccount(organization, prefix + "-" + suffix,
            prefix + "-" + suffix + "@example.invalid", prefix, UserRole.ADMIN);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user = userRepository.save(user);
        assignmentRepository.save(new UserRoleAssignment(user, role));
        return user;
    }

    private void grant(Role role, String permissionCode) {
        var permission = permissionRepository.findByCode(permissionCode)
            .orElseGet(() -> permissionRepository.save(new Permission(permissionCode, permissionCode, null)));
        if (!rolePermissionRepository.existsByRoleIdAndPermissionId(role.getId(), permission.getId())) {
            rolePermissionRepository.save(new RolePermission(role, permission));
        }
    }

    @SuppressWarnings("unchecked")
    private Tokens login(String email, String password) {
        var response = restTemplate.postForEntity(url("/api/auth/login"),
            Map.of("email", email, "password", password), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body).isNotNull();
        return new Tokens((String) body.get("accessToken"), (String) body.get("refreshToken"));
    }

    private org.springframework.http.ResponseEntity<String> loginResponse(String email, String password) {
        return exchange("/api/auth/login", HttpMethod.POST,
            Map.of("email", email, "password", password), null);
    }

    private org.springframework.http.ResponseEntity<String> get(String path, String token) {
        return exchange(path, HttpMethod.GET, null, token);
    }

    private org.springframework.http.ResponseEntity<String> exchange(String path, HttpMethod method,
                                                                     Object body, String token) {
        var headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        var entity = body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
        return restTemplate.exchange(url(path), method, entity, String.class);
    }

    private String jsonField(String json, String field) {
        var marker = "\"" + field + "\":\"";
        var start = json.indexOf(marker) + marker.length();
        return json.substring(start, json.indexOf('"', start));
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private record Tokens(String accessToken, String refreshToken) {
    }
}
