package br.com.schf.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.schf.audit.AuditLogRepository;
import br.com.schf.migration.infrastructure.MigrationExternalIdRepository;
import br.com.schf.migration.infrastructure.UnresolvedLegacyReferenceRepository;
import br.com.schf.migration.domain.BundlePaths;
import br.com.schf.migration.application.MigrationPhaseImporter;
import br.com.schf.migration.validation.CanonicalBundleValidator;
import br.com.schf.organization.Organization;
import br.com.schf.organization.OrganizationRepository;
import br.com.schf.security.membership.UserRoleAssignment;
import br.com.schf.security.membership.UserRoleAssignmentRepository;
import br.com.schf.security.permission.Permission;
import br.com.schf.security.permission.PermissionRepository;
import br.com.schf.security.permission.Permissions;
import br.com.schf.security.ratelimit.RateLimitService;
import br.com.schf.security.role.Role;
import br.com.schf.security.role.RolePermission;
import br.com.schf.security.role.RolePermissionRepository;
import br.com.schf.security.role.RoleRepository;
import br.com.schf.supplier.SupplierRepository;
import br.com.schf.user.UserAccount;
import br.com.schf.user.UserAccountRepository;
import br.com.schf.user.UserRole;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MigrationImporterIntegrationTest {
    private static final String PASSWORD = "valid-password-123";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("schf_migration_test").withUsername("schf").withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("schf.security.jwt.secret", () ->
            "fake_test_jwt_secret_that_is_longer_than_thirty_two_bytes_1234");
    }

    @LocalServerPort int port;
    @Autowired TestRestTemplate restTemplate;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired UserAccountRepository userRepository;
    @Autowired PermissionRepository permissionRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired RolePermissionRepository rolePermissionRepository;
    @Autowired UserRoleAssignmentRepository assignmentRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired MigrationExternalIdRepository externalIdRepository;
    @Autowired MigrationPhaseImporter phaseImporter;
    @Autowired CanonicalBundleValidator bundleValidator;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired RateLimitService rateLimitService;
    @Autowired br.com.schf.payable.PayableRepository payableRepository;
    @Autowired br.com.schf.payment.PaymentRepository paymentRepository;
    @Autowired UnresolvedLegacyReferenceRepository unresolvedReferenceRepository;

    @BeforeEach
    void client() {
        restTemplate.getRestTemplate().setRequestFactory(
            new JdkClientHttpRequestFactory(HttpClient.newHttpClient()));
        rateLimitService.reset();
    }

    @Test
    void importRequiresMigrationPermission() {
        var organization = organization("DENIED");
        var user = user(organization, "denied", List.of());

        var response = upload("/api/admin/migrations/validate", SyntheticBundleFactory.validArchive("denied"),
            login(user).accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void dryRunValidatesWithoutFinancialPersistence() {
        var organization = organization("DRY");
        roleRepository.save(new Role(organization, "VIEWER", "Viewer", null));
        var admin = user(organization, "dry-admin", List.of(Permissions.MIGRATION_IMPORT));
        var before = supplierRepository.findByOrganizationId(organization.getId()).size();

        var response = upload("/api/admin/migrations/dry-run", SyntheticBundleFactory.validArchive("dry"),
            login(admin).accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"COMPLETED\"", "\"dryRun\":true");
        assertThat(supplierRepository.findByOrganizationId(organization.getId())).hasSize(before);
        assertThat(externalIdRepository.countByOrganizationIdAndSourceSystem(organization.getId(), "SYNTHETIC"))
            .isZero();
    }

    @Test
    void successfulImportIsIdempotentAuditedAndReported() {
        var organization = organization("IMPORT");
        roleRepository.save(new Role(organization, "VIEWER", "Viewer", null));
        var admin = user(organization, "import-admin",
            List.of(Permissions.MIGRATION_IMPORT, Permissions.MIGRATION_READ));
        var token = login(admin).accessToken();
        var archive = SyntheticBundleFactory.validArchive("success");

        var first = upload("/api/admin/migrations/import", archive, token);
        var firstId = jsonField(first.getBody(), "id");
        var supplierCount = supplierRepository.findByOrganizationId(organization.getId()).size();
        var second = upload("/api/admin/migrations/import", archive, token);
        var report = get("/api/admin/migrations/" + firstId + "/report", token);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).contains("\"status\":\"COMPLETED\"");
        assertThat(second.getBody()).contains("\"id\":\"" + firstId + "\"");
        assertThat(supplierRepository.findByOrganizationId(organization.getId())).hasSize(supplierCount);
        assertThat(report.getBody()).contains("\"status\":\"COMPLETED\"", "\"externalIds\":9");
        assertThat(auditLogRepository.countByActionAndOrganizationId(
            "MIGRATION_IMPORT_COMPLETED", organization.getId())).isEqualTo(1);
    }

    @Test
    void migrationJobCannotBeReadAcrossTenants() {
        var firstOrganization = organization("TENANT-A");
        var secondOrganization = organization("TENANT-B");
        roleRepository.save(new Role(firstOrganization, "VIEWER", "Viewer", null));
        var importer = user(firstOrganization, "tenant-import", List.of(Permissions.MIGRATION_IMPORT));
        var reader = user(secondOrganization, "tenant-reader", List.of(Permissions.MIGRATION_READ));
        var imported = upload("/api/admin/migrations/dry-run", SyntheticBundleFactory.validArchive("tenant"),
            login(importer).accessToken());
        var id = jsonField(imported.getBody(), "id");

        var response = get("/api/admin/migrations/" + id, login(reader).accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void controlledPhaseFailureKeepsKnownCheckpointAndSanitizedError() {
        var organization = organization("CHECKPOINT");
        var admin = user(organization, "checkpoint-admin",
            List.of(Permissions.MIGRATION_IMPORT, Permissions.MIGRATION_READ));
        var data = SyntheticBundleFactory.validData("checkpoint");
        data.get(br.com.schf.migration.domain.BundlePaths.USERS).set(0,
            data.get(br.com.schf.migration.domain.BundlePaths.USERS).getFirst().replace("VIEWER", "MISSING_ROLE"));
        var archive = SyntheticBundleFactory.zip(SyntheticBundleFactory.entries(data, "1.0"));
        var token = login(admin).accessToken();

        var response = upload("/api/admin/migrations/import", archive, token);
        var id = jsonField(response.getBody(), "id");
        var errors = get("/api/admin/migrations/" + id + "/errors", token);

        assertThat(response.getBody()).contains("\"status\":\"FAILED\"", "\"lastCompletedPhase\":\"ORGANIZATIONS\"");
        assertThat(errors.getBody()).contains("IMPORT_PHASE_FAILED").doesNotContain("MISSING_ROLE");
        assertThat(externalIdRepository.countByOrganizationIdAndSourceSystem(organization.getId(), "SYNTHETIC"))
            .isEqualTo(1);
        assertThat(supplierRepository.findByOrganizationId(organization.getId())).isEmpty();
    }

    @Test
    void invalidChecksumReturnsControlledValidationReport() {
        var organization = organization("INVALID");
        var admin = user(organization, "invalid-admin", List.of(Permissions.MIGRATION_IMPORT));
        var entries = SyntheticBundleFactory.entries(SyntheticBundleFactory.validData("invalid"), "1.0");
        entries.put(br.com.schf.migration.domain.BundlePaths.SUPPLIERS, "{}\n".getBytes());

        var response = upload("/api/admin/migrations/validate", SyntheticBundleFactory.zip(entries),
            login(admin).accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).contains("CHECKSUM_MISMATCH").doesNotContain("Synthetic Supplier");
    }

    @Test
    void nullDisplayNameFallsBackToUsername() {
        var organization = organization("NONDISPLAY");
        roleRepository.save(new Role(organization, "VIEWER", "Viewer", null));
        var admin = user(organization, "ndisplay-admin", List.of(Permissions.MIGRATION_IMPORT));
        var archive = SyntheticBundleFactory.richArchive("ndisplay");

        var response = upload("/api/admin/migrations/import", archive, login(admin).accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"COMPLETED");
        var noDisplay = userRepository.findByUsername("no-display-ndisplay");
        assertThat(noDisplay).isPresent();
        assertThat(noDisplay.get().getDisplayName()).isEqualTo("no-display-ndisplay");
    }

    @Test
    void nullDescriptionPersistsAsNull() {
        var organization = organization("NONDESC");
        roleRepository.save(new Role(organization, "VIEWER", "Viewer", null));
        var admin = user(organization, "nodesc-admin", List.of(Permissions.MIGRATION_IMPORT));

        var response = upload("/api/admin/migrations/import", SyntheticBundleFactory.richArchive("nodesc"),
            login(admin).accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"COMPLETED");
        var payables = payableRepository.findByOrganizationId(organization.getId());
        var noDesc = payables.stream()
            .filter(p -> p.getDocumentNumber() != null && p.getDocumentNumber().equals("SYN-NODESC"))
            .findFirst();
        assertThat(noDesc).isPresent();
        assertThat(noDesc.get().getDescription()).isNull();
    }

    @Test
    void payableThroughCounterpartyAliasResolvesSupplier() {
        var organization = organization("CPALIAS");
        roleRepository.save(new Role(organization, "VIEWER", "Viewer", null));
        var admin = user(organization, "cpalias-admin", List.of(Permissions.MIGRATION_IMPORT));

        var response = upload("/api/admin/migrations/import", SyntheticBundleFactory.richArchive("cpalias"),
            login(admin).accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"COMPLETED");
        var payables = payableRepository.findByOrganizationId(organization.getId());
        var cpPayable = payables.stream()
            .filter(p -> p.getDocumentNumber() != null && p.getDocumentNumber().equals("SYN-CP"))
            .findFirst();
        assertThat(cpPayable).isPresent();
        var aliases = supplierRepository.findByOrganizationId(organization.getId()).stream()
            .filter(s -> "Alias Supplier".equals(s.getName()))
            .collect(java.util.stream.Collectors.toList());
        assertThat(aliases).hasSize(1);
        assertThat(cpPayable.get().getSupplierId()).isEqualTo(aliases.getFirst().getId());
    }

    @Test
    void importPreservesUnresolvedCounterpartyPayablesAndPayments() {
        var organization = organization("PRESERVE");
        roleRepository.save(new Role(organization, "VIEWER", "Viewer", null));
        var admin = user(organization, "preserve-admin",
            List.of(Permissions.MIGRATION_IMPORT, Permissions.MIGRATION_READ));
        var data = SyntheticBundleFactory.validData("preserve");
        var empId = UUID.fromString("25000000-0000-0000-0000-000000000003");
        var payable1 = UUID.fromString("60000000-0000-0000-0000-000000000010");
        var payable2 = UUID.fromString("60000000-0000-0000-0000-000000000011");
        var payment1 = UUID.fromString("70000000-0000-0000-0000-000000000010");
        var payment2 = UUID.fromString("70000000-0000-0000-0000-000000000011");
        data.get(BundlePaths.COUNTERPARTIES).add(SyntheticBundleFactory.json(Map.of(
            "externalId", SyntheticBundleFactory.UNRESOLVED_COUNTERPARTY_ID,
            "name", "Unresolved Government", "type", "GOVERNMENT",
            "sourceReference", "7|999", "resolutionStatus", "UNRESOLVED_LEGACY_REFERENCE", "active", false)));
        data.get(BundlePaths.COUNTERPARTIES).add(SyntheticBundleFactory.json(Map.of(
            "externalId", empId, "name", "Unresolved Employee", "type", "EMPLOYEE",
            "sourceReference", "7|1000", "resolutionStatus", "UNRESOLVED_LEGACY_REFERENCE", "active", false)));
        for (var entry : List.of(
            Map.of("externalId", payable1, "cpId", SyntheticBundleFactory.UNRESOLVED_COUNTERPARTY_ID, "doc", "SYN-UNRES1"),
            Map.of("externalId", payable2, "cpId", empId, "doc", "SYN-UNRES2"))) {
            data.get(BundlePaths.PAYABLES).add(SyntheticBundleFactory.json(Map.of(
                "externalId", entry.get("externalId"),
                "counterpartyExternalId", entry.get("cpId"),
                "categoryExternalId", SyntheticBundleFactory.CATEGORY_ID,
                "financialAccountExternalId", SyntheticBundleFactory.ACCOUNT_ID,
                "description", "Unresolved payable", "documentNumber", entry.get("doc"),
                "issueDate", "2026-06-01", "dueDate", "2026-06-30", "amount", "100.00", "status", "PAID")));
        }
        for (var entry : List.of(
            Map.of("externalId", payment1, "payableId", payable1),
            Map.of("externalId", payment2, "payableId", payable2))) {
            data.get(BundlePaths.PAYMENTS).add(SyntheticBundleFactory.json(Map.of(
                "externalId", entry.get("externalId"),
                "payableExternalId", entry.get("payableId"),
                "financialAccountExternalId", SyntheticBundleFactory.ACCOUNT_ID,
                "paymentDate", "2026-06-20", "amount", "100.00", "notes", "Payment")));
        }
        var archive = SyntheticBundleFactory.zip(SyntheticBundleFactory.entries(data, "1.2"));
        var token = login(admin).accessToken();

        var first = upload("/api/admin/migrations/import", archive, token);
        var firstId = jsonField(first.getBody(), "id");
        var second = upload("/api/admin/migrations/import", archive, token);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).contains("\"status\":\"COMPLETED\"");
        assertThat(second.getBody()).contains("\"id\":\"" + firstId + "\"");
        assertThat(unresolvedReferenceRepository.countByOrganizationId(organization.getId())).isEqualTo(2);
        assertThat(externalIdRepository.findByOrganizationIdAndSourceSystemAndEntityTypeAndExternalId(
            organization.getId(), "SYNTHETIC", "COUNTERPARTY",
            SyntheticBundleFactory.UNRESOLVED_COUNTERPARTY_ID)).isPresent();
        assertThat(externalIdRepository.findByOrganizationIdAndSourceSystemAndEntityTypeAndExternalId(
            organization.getId(), "SYNTHETIC", "COUNTERPARTY", empId)).isPresent();
        assertThat(externalIdRepository.findByOrganizationIdAndSourceSystemAndEntityTypeAndExternalId(
            organization.getId(), "SYNTHETIC", "SUPPLIER",
            SyntheticBundleFactory.UNRESOLVED_COUNTERPARTY_ID)).isEmpty();
        assertThat(externalIdRepository.findByOrganizationIdAndSourceSystemAndEntityTypeAndExternalId(
            organization.getId(), "SYNTHETIC", "SUPPLIER", empId)).isEmpty();
        assertThat(payableRepository.findByOrganizationId(organization.getId())).hasSize(4);
        assertThat(payableRepository.findByOrganizationId(organization.getId()).stream()
            .filter(p -> p.getCounterpartyId() != null)).hasSize(2);
        assertThat(paymentRepository.findByOrganizationId(organization.getId())).hasSize(3);
        assertThat(externalIdRepository.findByOrganizationIdAndSourceSystemAndEntityTypeAndExternalId(
            organization.getId(), "SYNTHETIC", "PAYABLE", payable1)).isPresent();
        assertThat(externalIdRepository.findByOrganizationIdAndSourceSystemAndEntityTypeAndExternalId(
            organization.getId(), "SYNTHETIC", "PAYMENT", payment1)).isPresent();
        assertThat(payableRepository.findByOrganizationId(organization.getId())).hasSize(4);
        assertThat(paymentRepository.findByOrganizationId(organization.getId())).hasSize(3);
        assertThat(unresolvedReferenceRepository.countByOrganizationId(organization.getId())).isEqualTo(2);
    }

    @Test
    void importFailsWhenPayableReferencesNonexistentCounterparty() {
        var organization = organization("BADCP");
        roleRepository.save(new Role(organization, "VIEWER", "Viewer", null));
        var admin = user(organization, "badcp-admin", List.of(Permissions.MIGRATION_IMPORT));
        var data = SyntheticBundleFactory.validData("badcp");
        var badPayable = new LinkedHashMap<String, Object>();
        badPayable.put("externalId", UUID.fromString("60000000-0000-0000-0000-000000000099"));
        badPayable.put("supplierExternalId", null);
        badPayable.put("counterpartyExternalId", UUID.randomUUID());
        badPayable.put("categoryExternalId", SyntheticBundleFactory.CATEGORY_ID);
        badPayable.put("financialAccountExternalId", null);
        badPayable.put("description", "Nonexistent counterparty");
        badPayable.put("documentNumber", "SYN-BADCP");
        badPayable.put("issueDate", "2026-06-01");
        badPayable.put("dueDate", "2026-06-30");
        badPayable.put("amount", "99.99");
        badPayable.put("status", "OPEN");
        data.get(BundlePaths.PAYABLES).add(SyntheticBundleFactory.json(badPayable));
        var archive = SyntheticBundleFactory.zip(SyntheticBundleFactory.entries(data, "1.0"));

        var response = upload("/api/admin/migrations/import", archive, login(admin).accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"FAILED\"", "\"lastCompletedPhase\":\"FINANCIAL_ACCOUNTS\"");
        assertThat(payableRepository.findByOrganizationId(organization.getId())).isEmpty();
    }

    @Test
    void paymentPhaseRollsBackWhenFinancialAccountMissing() {
        var organization = organization("ROLLPAY");
        roleRepository.save(new Role(organization, "VIEWER", "Viewer", null));
        var admin = user(organization, "rollpay-admin",
            List.of(Permissions.MIGRATION_IMPORT, Permissions.MIGRATION_READ));
        var data = SyntheticBundleFactory.validData("rollpay");
        var cpId = UUID.fromString("25000000-0000-0000-0000-000000000004");
        var payableId = UUID.fromString("60000000-0000-0000-0000-000000000012");
        var paymentId = UUID.fromString("70000000-0000-0000-0000-000000000012");
        data.get(BundlePaths.COUNTERPARTIES).add(SyntheticBundleFactory.json(Map.of(
            "externalId", cpId, "name", "Rollback Counterparty", "type", "GOVERNMENT",
            "sourceReference", "7|ROLL", "resolutionStatus", "UNRESOLVED_LEGACY_REFERENCE", "active", false)));
        data.get(BundlePaths.PAYABLES).add(SyntheticBundleFactory.json(Map.of(
            "externalId", payableId, "counterpartyExternalId", cpId,
            "categoryExternalId", SyntheticBundleFactory.CATEGORY_ID,
            "financialAccountExternalId", SyntheticBundleFactory.ACCOUNT_ID,
            "description", "Rollback payable", "issueDate", "2026-06-01",
            "dueDate", "2026-06-30", "amount", "100.00", "status", "OPEN")));
        data.get(BundlePaths.PAYMENTS).add(SyntheticBundleFactory.json(Map.of(
            "externalId", paymentId, "payableExternalId", payableId,
            "financialAccountExternalId", UUID.randomUUID(),
            "paymentDate", "2026-06-20", "amount", "100.00", "notes", "Will roll back")));
        var archive = SyntheticBundleFactory.zip(SyntheticBundleFactory.entries(data, "1.0"));
        var token = login(admin).accessToken();

        var first = upload("/api/admin/migrations/import", archive, token);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody()).contains("\"status\":\"FAILED\"", "\"lastCompletedPhase\":\"PAYABLES\"");
        assertThat(unresolvedReferenceRepository.countByOrganizationId(organization.getId())).isEqualTo(1);
        assertThat(payableRepository.findByOrganizationId(organization.getId())).hasSize(3);
        assertThat(paymentRepository.findByOrganizationId(organization.getId())).isEmpty();
    }

    @Test
    void paymentWithoutPayableCheckpointStillFails() {
        var organization = organization("PAYMENT-CHECKPOINT");
        var bundle = bundleValidator.validate(SyntheticBundleFactory.validArchive("payment-checkpoint")).bundle();

        assertThatThrownBy(() -> phaseImporter.payments(UUID.randomUUID(), organization.getId(), bundle))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Canonical reference checkpoint is unavailable");
        assertThat(paymentRepository.findByOrganizationId(organization.getId())).isEmpty();
    }

    @Test
    void payableWithoutSupplierFailsAtPayablesPhase() {
        var organization = organization("NOSUPPLIER");
        roleRepository.save(new Role(organization, "VIEWER", "Viewer", null));
        var admin = user(organization, "nosup-admin", List.of(Permissions.MIGRATION_IMPORT));
        var data = SyntheticBundleFactory.richData("nosup");
        var badPayable = new LinkedHashMap<String, Object>();
        badPayable.put("externalId", UUID.fromString("60000000-0000-0000-0000-000000000099"));
        badPayable.put("supplierExternalId", null);
        badPayable.put("counterpartyExternalId", null);
        badPayable.put("categoryExternalId", SyntheticBundleFactory.CATEGORY_ID);
        badPayable.put("financialAccountExternalId", null);
        badPayable.put("description", "No supplier at all");
        badPayable.put("documentNumber", "SYN-NOSUP");
        badPayable.put("issueDate", "2026-06-01");
        badPayable.put("dueDate", "2026-06-30");
        badPayable.put("amount", "99.99");
        badPayable.put("status", "OPEN");
        data.get(br.com.schf.migration.domain.BundlePaths.PAYABLES).add(SyntheticBundleFactory.json(badPayable));
        var archive = SyntheticBundleFactory.zip(SyntheticBundleFactory.entries(data, "1.0"));

        var response = upload("/api/admin/migrations/import", archive, login(admin).accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"FAILED\"", "\"lastCompletedPhase\":\"FINANCIAL_ACCOUNTS\"");
    }

    @Test
    void nullDatesPersistAsNull() {
        var organization = organization("NULLDATES");
        roleRepository.save(new Role(organization, "VIEWER", "Viewer", null));
        var admin = user(organization, "ndates-admin", List.of(Permissions.MIGRATION_IMPORT));

        var response = upload("/api/admin/migrations/import", SyntheticBundleFactory.richArchive("nulldates"),
            login(admin).accessToken());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"COMPLETED");
        var payables = payableRepository.findByOrganizationId(organization.getId());
        var nullDates = payables.stream()
            .filter(p -> p.getDocumentNumber() != null && p.getDocumentNumber().equals("SYN-NULLDATE"))
            .findFirst();
        assertThat(nullDates).isPresent();
        assertThat(nullDates.get().getIssueDate()).isNull();
        assertThat(nullDates.get().getDueDate()).isNull();
    }

    @Test
    void flywayV5CreatesMigrationSchemaAndPermissions() {
        var tables = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'
              AND table_name IN ('migration_jobs', 'migration_bundle_files', 'migration_record_results',
                                 'migration_external_ids', 'migration_errors')
            """, Integer.class);
        var permissions = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM permissions WHERE code IN ('MIGRATION_READ', 'MIGRATION_IMPORT')", Integer.class);
        assertThat(tables).isEqualTo(5);
        assertThat(permissions).isEqualTo(2);
    }

    private Organization organization(String prefix) {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        return organizationRepository.save(new Organization(prefix + "-" + suffix, prefix + " Organization"));
    }

    private UserAccount user(Organization organization, String prefix, List<String> permissionCodes) {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var role = roleRepository.save(new Role(organization, "ROLE-" + suffix, "Migration Test Role", null));
        for (String code : permissionCodes) {
            var permission = permissionRepository.findByCode(code)
                .orElseGet(() -> permissionRepository.save(new Permission(code, code, null)));
            rolePermissionRepository.save(new RolePermission(role, permission));
        }
        var user = new UserAccount(organization, prefix + "-" + suffix,
            prefix + "-" + suffix + "@example.invalid", prefix, UserRole.ADMIN);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user = userRepository.save(user);
        assignmentRepository.save(new UserRoleAssignment(user, role));
        return user;
    }

    @SuppressWarnings("unchecked")
    private Tokens login(UserAccount user) {
        var response = restTemplate.postForEntity(url("/api/auth/login"),
            Map.of("email", user.getEmail(), "password", PASSWORD), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new Tokens((String) response.getBody().get("accessToken"));
    }

    private org.springframework.http.ResponseEntity<String> upload(String path, byte[] content, String token) {
        var body = new LinkedMultiValueMap<String, Object>();
        body.add("file", new ByteArrayResource(content) {
            @Override public String getFilename() { return "synthetic-bundle.schf"; }
        });
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private org.springframework.http.ResponseEntity<String> get(String path, String token) {
        var headers = new HttpHeaders(); headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String jsonField(String json, String field) {
        var marker = "\"" + field + "\":\"";
        var start = json.indexOf(marker) + marker.length();
        return json.substring(start, json.indexOf('"', start));
    }

    private String url(String path) { return "http://localhost:" + port + path; }
    private record Tokens(String accessToken) {}
}
