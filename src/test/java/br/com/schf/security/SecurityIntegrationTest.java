package br.com.schf.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.schf.account.FinancialAccount;
import br.com.schf.account.FinancialAccountRepository;
import br.com.schf.account.FinancialAccountType;
import br.com.schf.audit.AuditLogRepository;
import br.com.schf.category.Category;
import br.com.schf.category.CategoryRepository;
import br.com.schf.organization.Organization;
import br.com.schf.organization.OrganizationRepository;
import br.com.schf.payable.Payable;
import br.com.schf.payable.PayableRepository;
import br.com.schf.security.membership.UserRoleAssignment;
import br.com.schf.security.membership.UserRoleAssignmentRepository;
import br.com.schf.security.permission.Permission;
import br.com.schf.security.permission.PermissionRepository;
import br.com.schf.security.permission.Permissions;
import br.com.schf.security.role.Role;
import br.com.schf.security.role.RolePermission;
import br.com.schf.security.role.RolePermissionRepository;
import br.com.schf.security.role.RoleRepository;
import br.com.schf.supplier.CategoryType;
import br.com.schf.supplier.Supplier;
import br.com.schf.supplier.SupplierRepository;
import br.com.schf.user.UserAccount;
import br.com.schf.user.UserAccountRepository;
import br.com.schf.user.UserRole;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityIntegrationTest {

    private static final String PASSWORD = "valid-password-123";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("schf_security_test")
        .withUsername("schf")
        .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("schf.security.jwt.secret", () ->
            "fake_test_jwt_secret_that_is_longer_than_thirty_two_bytes_1234");
        registry.add("schf.tenant.strategy", () -> "authenticated");
    }

    @LocalServerPort
    int port;

    @Autowired TestRestTemplate restTemplate;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired UserAccountRepository userRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired RolePermissionRepository rolePermissionRepository;
    @Autowired UserRoleAssignmentRepository userRoleRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired FinancialAccountRepository financialAccountRepository;
    @Autowired PayableRepository payableRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void endpointWithoutTokenReturns401() {
        var response = restTemplate.getForEntity(url("/api/suppliers"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidTokenReturns401() {
        var response = exchangeGet("/api/suppliers", "not-a-valid-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userWithoutPermissionReturns403() {
        var organization = organization("NOPERM");
        var user = user(organization, "no-permission", List.of());
        var accessToken = login(user.getEmail()).accessToken();

        var response = exchangeGet("/api/suppliers", accessToken);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void userWithPermissionCanAccessOnlyOwnOrganizationData() {
        var ownOrganization = organization("OWN");
        var otherOrganization = organization("OTHER");
        var user = user(ownOrganization, "reader", List.of(Permissions.SUPPLIER_READ));
        var ownSupplier = supplierRepository.save(new Supplier(ownOrganization.getId(), "Own Supplier"));
        var otherSupplier = supplierRepository.save(new Supplier(otherOrganization.getId(), "Other Supplier"));

        var response = exchangeGet("/api/suppliers", login(user.getEmail()).accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(ownSupplier.getId().toString());
        assertThat(response.getBody()).doesNotContain(otherSupplier.getId().toString());
    }

    @Test
    void userCannotPayPayableFromAnotherOrganization() {
        var ownOrganization = organization("PAYOWN");
        var otherOrganization = organization("PAYOTHER");
        var user = user(ownOrganization, "payer", List.of(Permissions.PAYMENT_WRITE));
        var ownAccount = financialAccountRepository.save(
            new FinancialAccount(ownOrganization.getId(), "Own Account", FinancialAccountType.BANK));
        var supplier = supplierRepository.save(new Supplier(otherOrganization.getId(), "Other Supplier"));
        var category = categoryRepository.save(
            new Category(otherOrganization.getId(), "Other Category", CategoryType.EXPENSE));
        var payable = payableRepository.save(new Payable(otherOrganization.getId(), supplier.getId(),
            category.getId(), "Other payable", LocalDate.now(), LocalDate.now().plusDays(5),
            new BigDecimal("50.00")));
        var body = Map.of(
            "financialAccountId", ownAccount.getId().toString(),
            "paymentDate", LocalDate.now().toString(),
            "amount", "50.00",
            "notes", "must be denied");

        var response = post("/api/payables/" + payable.getId() + "/payments", body,
            login(user.getEmail()).accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void revokedRefreshTokenCannotBeUsed() {
        var organization = organization("REFRESH");
        var user = user(organization, "refresh-user", List.of(Permissions.SUPPLIER_READ));
        var tokens = login(user.getEmail());

        var logout = post("/api/auth/logout", Map.of("refreshToken", tokens.refreshToken()), null);
        var refresh = post("/api/auth/refresh", Map.of("refreshToken", tokens.refreshToken()), null);

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void importantActionCreatesAuditLog() {
        var organization = organization("AUDIT");
        var user = user(organization, "writer", List.of(Permissions.SUPPLIER_WRITE));
        var before = auditLogRepository.countByActionAndUserId("SUPPLIER_CREATED", user.getId());
        var body = Map.of("name", "Audited Supplier", "document", "FAKE-001",
            "email", "supplier@example.invalid", "phone", "0000");

        var response = post("/api/suppliers", body, login(user.getEmail()).accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(auditLogRepository.countByActionAndUserId("SUPPLIER_CREATED", user.getId()))
            .isEqualTo(before + 1);
        assertThat(auditLogRepository.countByActionAndUserId("LOGIN_SUCCESS", user.getId()))
            .isGreaterThanOrEqualTo(1);
    }

    @Test
    void flywayV3CreatesSecurityTables() {
        for (String table : List.of("roles", "permissions", "user_roles", "role_permissions",
            "refresh_tokens", "password_reset_tokens", "audit_logs")) {
            var existing = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.' || ?) IS NOT NULL", Boolean.class, table);
            assertThat(existing).as("table %s", table).isTrue();
        }
    }

    private Organization organization(String prefix) {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        return organizationRepository.save(new Organization(prefix + "-" + suffix, prefix + " Organization"));
    }

    private UserAccount user(Organization organization, String prefix, List<String> permissionCodes) {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var user = new UserAccount(organization, prefix + "-" + suffix,
            prefix + "-" + suffix + "@example.invalid", prefix, UserRole.ADMIN);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user = userRepository.save(user);
        var role = roleRepository.save(new Role(organization, "ROLE-" + suffix, "Test Role", null));
        userRoleRepository.save(new UserRoleAssignment(user, role));
        for (String code : permissionCodes) {
            var permission = permissionRepository.findByCode(code)
                .orElseGet(() -> permissionRepository.save(new Permission(code, code, null)));
            rolePermissionRepository.save(new RolePermission(role, permission));
        }
        return user;
    }

    @SuppressWarnings("unchecked")
    private Tokens login(String email) {
        var response = restTemplate.postForEntity(url("/api/auth/login"),
            Map.of("email", email, "password", PASSWORD), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = response.getBody();
        assertThat(body).isNotNull();
        return new Tokens((String) body.get("accessToken"), (String) body.get("refreshToken"));
    }

    private org.springframework.http.ResponseEntity<String> exchangeGet(String path, String accessToken) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private org.springframework.http.ResponseEntity<String> post(String path, Object body, String accessToken) {
        var headers = new HttpHeaders();
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private record Tokens(String accessToken, String refreshToken) {
    }
}
