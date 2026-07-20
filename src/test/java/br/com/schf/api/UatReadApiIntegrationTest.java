package br.com.schf.api;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.schf.account.FinancialAccount;
import br.com.schf.account.FinancialAccountRepository;
import br.com.schf.account.FinancialAccountType;
import br.com.schf.category.Category;
import br.com.schf.category.CategoryRepository;
import br.com.schf.migration.domain.UnresolvedLegacyReference;
import br.com.schf.migration.infrastructure.UnresolvedLegacyReferenceRepository;
import br.com.schf.organization.Organization;
import br.com.schf.organization.OrganizationRepository;
import br.com.schf.payable.Payable;
import br.com.schf.payable.PayableRepository;
import br.com.schf.payable.PayableStatus;
import br.com.schf.payment.Payment;
import br.com.schf.payment.PaymentRepository;
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
import java.net.http.HttpClient;
import java.time.LocalDate;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UatReadApiIntegrationTest {

    private static final String PASSWORD = "valid-password-123";
    private static final List<String> VIEWER_PERMS = List.of(
        Permissions.SUPPLIER_READ, Permissions.CATEGORY_READ,
        Permissions.ACCOUNT_READ, Permissions.PAYABLE_READ, Permissions.REPORT_READ);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("schf_uat_api_test")
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
        registry.add("schf.security.hardening.login-rate-limit", () -> "1000");
        registry.add("schf.security.hardening.rate-limit-window-seconds", () -> "1");
    }

    @LocalServerPort int port;
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
    @Autowired PaymentRepository paymentRepository;
    @Autowired UnresolvedLegacyReferenceRepository unresolvedRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Organization org;
    private String viewerToken;
    private Supplier supplier;
    private Category category;
    private FinancialAccount account;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(
            new JdkClientHttpRequestFactory(HttpClient.newHttpClient()));
        org = organizationRepository.save(new Organization("UAT-API-" + UUID.randomUUID().toString().substring(0, 6), "UAT API Test"));
        viewerToken = createViewerUser(org);
        supplier = supplierRepository.save(new Supplier(org.getId(), "Test Supplier"));
        category = categoryRepository.save(new Category(org.getId(), "Test Category", CategoryType.EXPENSE));
        account = financialAccountRepository.save(new FinancialAccount(org.getId(), "Test Account", FinancialAccountType.BANK));
    }

    @Test
    void unauthenticatedRequestsReturn401() {
        assertThat(restTemplate.getForEntity(url("/api/payables?page=0&size=10"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity(url("/api/payments?page=0&size=10"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity(url("/api/unresolved-legacy-references"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity(url("/api/summary"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity(url("/api/reports"), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void viewerCannotMutate() {
        var payable = createPayable(supplier, category, new BigDecimal("100.00"));
        var createBody = Map.of("supplierId", supplier.getId().toString(), "categoryId", category.getId().toString(),
            "description", "test", "issueDate", "2026-01-01", "dueDate", "2026-02-01", "amount", "100");

        assertThat(exchangePost("/api/payables", createBody, viewerToken).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchangePost("/api/payables/" + payable.getId() + "/payments",
            Map.of("financialAccountId", account.getId().toString(), "paymentDate", "2026-01-15", "amount", "50"),
            viewerToken).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchangePost("/api/suppliers", Map.of("name", "X"), viewerToken).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void payablesArePaginated() {
        for (int i = 0; i < 15; i++) {
            createPayable(supplier, category, new BigDecimal("10.00"));
        }

        var page0 = exchangeGet("/api/payables?page=0&size=10", viewerToken);
        assertThat(page0.getStatusCode()).isEqualTo(HttpStatus.OK);

        var page1 = exchangeGet("/api/payables?page=1&size=10", viewerToken);
        assertThat(page1.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(page0.getBody()).contains("\"size\":10");
        assertThat(page0.getBody()).contains("\"totalElements\":15");
        assertThat(page0.getBody()).contains("\"totalPages\":2");
    }

    @Test
    void payablesReturnSizeErrors() {
        assertThat(exchangeGet("/api/payables?page=0&size=0", viewerToken).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchangeGet("/api/payables?page=0&size=200", viewerToken).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(exchangeGet("/api/payables?page=0&size=25&sort=invalid", viewerToken).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void payableDetailReturnsEnrichedInfo() {
        var payable = createPayable(supplier, category, new BigDecimal("250.00"));

        var response = exchangeGet("/api/payables/" + payable.getId(), viewerToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(payable.getId().toString());
        assertThat(response.getBody()).contains("Test Supplier");
        assertThat(response.getBody()).contains("\"unresolved\":false");
        assertThat(response.getBody()).contains("\"paidAmount\":0");
    }

    @Test
    void payableDetailForNotFoundReturns404() {
        assertThat(exchangeGet("/api/payables/" + UUID.randomUUID(), viewerToken).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void payablesIncludeCounterpartyInfoForUnresolved() {
        var unresolved = unresolvedRepository.save(
            new UnresolvedLegacyReference(org.getId(), UUID.randomUUID(), "Unresolved Corp", "GOVERNMENT",
                "REF-001", null));
        var payable = new Payable(org.getId(), null, category.getId(), "Unresolved payable",
            LocalDate.now(), LocalDate.now().plusDays(10), new BigDecimal("500.00"));
        payable.setCounterpartyId(unresolved.getId());
        payable = payableRepository.save(payable);

        var response = exchangeGet("/api/payables?page=0&size=25", viewerToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"unresolved\":true");
        assertThat(response.getBody()).contains("\"counterpartyName\":\"Unresolved Corp\"");
        assertThat(response.getBody()).contains("\"counterpartyId\":\"" + unresolved.getId() + "\"");
    }

    @Test
    void payablesAreOrganizationIsolated() {
        var otherOrg = organizationRepository.save(new Organization("OTHER-" + UUID.randomUUID().toString().substring(0, 6), "Other"));
        var otherToken = createViewerUserForOrg(otherOrg, "other-viewer");
        var otherSupplier = supplierRepository.save(new Supplier(otherOrg.getId(), "Other Supplier"));
        var otherCat = categoryRepository.save(new Category(otherOrg.getId(), "Other Cat", CategoryType.EXPENSE));
        var otherPayable = new Payable(otherOrg.getId(), otherSupplier.getId(), otherCat.getId(),
            "Other payable", LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("60.00"));
        payableRepository.save(otherPayable);
        createPayable(supplier, category, new BigDecimal("30.00"));

        var ourResponse = exchangeGet("/api/payables?page=0&size=25", viewerToken);
        assertThat(ourResponse.getBody()).contains("\"totalElements\":1");

        var otherResponse = exchangeGet("/api/payables?page=0&size=25", otherToken);
        assertThat(otherResponse.getBody()).contains("\"totalElements\":1");
    }

    @Test
    void paymentsArePaginated() {
        var payable = createPayable(supplier, category, new BigDecimal("200.00"));
        for (int i = 0; i < 12; i++) {
            paymentRepository.save(new Payment(org.getId(), payable.getId(), account.getId(),
                LocalDate.now().minusDays(i), new BigDecimal("10.00")));
        }

        var page0 = exchangeGet("/api/payments?page=0&size=10", viewerToken);
        assertThat(page0.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page0.getBody()).contains("\"totalElements\":12");
        assertThat(page0.getBody()).contains("\"totalPages\":2");
    }

    @Test
    void paymentDetailReturnsPayableDescription() {
        var payable = createPayable(supplier, category, new BigDecimal("100.00"));
        var payment = paymentRepository.save(
            new Payment(org.getId(), payable.getId(), account.getId(), LocalDate.now(), new BigDecimal("100.00")));

        var response = exchangeGet("/api/payments/" + payment.getId(), viewerToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains(payment.getId().toString());
        assertThat(response.getBody()).contains(payable.getDescription());
    }

    @Test
    void paymentDetailForNotFoundReturns404() {
        assertThat(exchangeGet("/api/payments/" + UUID.randomUUID(), viewerToken).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void unresolvedCounterpartiesReturnPayableAndPaymentCounts() {
        var unresolved = unresolvedRepository.save(
            new UnresolvedLegacyReference(org.getId(), UUID.randomUUID(), "Unknown Co", "EMPLOYEE",
                "LEGACY-001", null));
        var payable = new Payable(org.getId(), null, category.getId(), "Unresolved ref payable",
            LocalDate.now(), LocalDate.now().plusDays(5), new BigDecimal("300.00"));
        payable.setCounterpartyId(unresolved.getId());
        payable = payableRepository.save(payable);
        paymentRepository.save(new Payment(org.getId(), payable.getId(), account.getId(),
            LocalDate.now(), new BigDecimal("300.00")));

        var response = exchangeGet("/api/unresolved-legacy-references", viewerToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"payableCount\":1");
        assertThat(response.getBody()).contains("\"paymentCount\":1");
        assertThat(response.getBody()).contains("\"resolutionStatus\":\"UNRESOLVED_LEGACY_REFERENCE\"");
        assertThat(response.getBody()).contains("\"name\":\"Unknown Co\"");
    }

    @Test
    void summaryReturnsAllCounts() {
        supplierRepository.save(new Supplier(org.getId(), "S1"));
        supplierRepository.save(new Supplier(org.getId(), "S2"));
        categoryRepository.save(new Category(org.getId(), "C1", CategoryType.EXPENSE));
        financialAccountRepository.save(new FinancialAccount(org.getId(), "A1", FinancialAccountType.BANK));
        var p = createPayable(supplier, category, new BigDecimal("50.00"));
        paymentRepository.save(new Payment(org.getId(), p.getId(), account.getId(), LocalDate.now(), new BigDecimal("50.00")));

        var response = exchangeGet("/api/summary", viewerToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"supplierCount\":3");
        assertThat(response.getBody()).contains("\"categoryCount\":2");
        assertThat(response.getBody()).contains("\"payableCount\":1");
        assertThat(response.getBody()).contains("\"paymentCount\":1");
        assertThat(response.getBody()).contains("\"unresolvedCounterpartyCount\":0");
        assertThat(response.getBody()).contains("\"serverTime\"");
    }

    @Test
    void reportReturnsWarningCounts() {
        var response = exchangeGet("/api/reports", viewerToken);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"totalWarnings\"");
        assertThat(response.getBody()).contains("\"totalPayables\"");
        assertThat(response.getBody()).contains("\"unresolvedCount\"");
    }

    @Test
    void viewerCannotAccessAdminEndpoints() {
        assertThat(exchangeGet("/api/admin/users", viewerToken).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchangeGet("/api/admin/audit-logs", viewerToken).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchangeGet("/api/admin/migrations", viewerToken).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void organizationIsolationAcrossAllEndpoints() {
        var otherOrg = organizationRepository.save(new Organization("ISO2-" + UUID.randomUUID().toString().substring(0, 6), "Isolation Org"));
        var otherToken = createViewerUserForOrg(otherOrg, "isolation-viewer");
        var otherSupplier = supplierRepository.save(new Supplier(otherOrg.getId(), "ISO Supplier"));
        var otherCat = categoryRepository.save(new Category(otherOrg.getId(), "ISO Cat", CategoryType.EXPENSE));
        var otherPayable = new Payable(otherOrg.getId(), otherSupplier.getId(), otherCat.getId(),
            "Other org payable", LocalDate.now(), LocalDate.now().plusDays(30), new BigDecimal("99.00"));
        payableRepository.save(otherPayable);

        var response = exchangeGet("/api/summary", otherToken);
        assertThat(response.getBody()).contains("\"payableCount\":1");
        assertThat(response.getBody()).contains("\"supplierCount\":1");

        var ourResponse = exchangeGet("/api/summary", viewerToken);
        assertThat(ourResponse.getBody()).contains("\"payableCount\":0");
    }

    private Payable createPayable(Supplier s, Category c, BigDecimal amount) {
        return payableRepository.save(new Payable(org.getId(), s.getId(), c.getId(),
            "Test payable " + UUID.randomUUID().toString().substring(0, 6),
            LocalDate.now(), LocalDate.now().plusDays(30), amount));
    }

    private String createViewerUser(Organization organization) {
        return createViewerUserForOrg(organization, "viewer");
    }

    private String createViewerUserForOrg(Organization organization, String prefix) {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var user = new UserAccount(organization, prefix + "-" + suffix,
            prefix + "-" + suffix + "@example.invalid", prefix, UserRole.ADMIN);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user = userRepository.save(user);
        var role = roleRepository.save(new Role(organization, "VR-" + suffix, "Viewer Role", null));
        userRoleRepository.save(new UserRoleAssignment(user, role));
        for (String code : VIEWER_PERMS) {
            var permission = permissionRepository.findByCode(code)
                .orElseGet(() -> permissionRepository.save(new Permission(code, code, null)));
            rolePermissionRepository.save(new RolePermission(role, permission));
        }
        return login(user.getEmail());
    }

    @SuppressWarnings("unchecked")
    private String login(String email) {
        var response = restTemplate.postForEntity(url("/api/auth/login"),
            Map.of("email", email, "password", PASSWORD), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return (String) response.getBody().get("accessToken");
    }

    private org.springframework.http.ResponseEntity<String> exchangeGet(String path, String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private org.springframework.http.ResponseEntity<String> exchangePost(String path, Object body, String token) {
        var headers = new HttpHeaders();
        if (token != null) headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}