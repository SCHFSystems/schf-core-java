package br.com.schf.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.schf.account.FinancialAccount;
import br.com.schf.account.FinancialAccountRepository;
import br.com.schf.account.FinancialAccountType;
import br.com.schf.audit.AuditLogRepository;
import br.com.schf.category.Category;
import br.com.schf.category.CategoryRepository;
import br.com.schf.supplier.CategoryType;
import br.com.schf.category.CategoryRepository;
import br.com.schf.organization.Organization;
import br.com.schf.organization.OrganizationRepository;
import br.com.schf.payable.Payable;
import br.com.schf.payable.PayableRepository;
import br.com.schf.payable.PayableStatus;
import br.com.schf.shared.PaymentRequest;
import br.com.schf.shared.TenantContext;
import br.com.schf.supplier.Supplier;
import br.com.schf.supplier.SupplierRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PayablePaymentIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("schf_v2_test")
        .withUsername("schf")
        .withPassword("test");

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("schf.tenant.strategy", () -> "auto");
        registry.add("schf.security.jwt.secret", () ->
            "fake_test_jwt_secret_that_is_longer_than_thirty_two_bytes_1234");
    }

    @Autowired SupplierRepository supplierRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired FinancialAccountRepository accountRepository;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired PayableRepository payableRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired PaymentService paymentService;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired TenantContext tenantContext;

    UUID orgId;

    @BeforeEach
    void setUp() {
        // Create a test organization and set tenant context
        var org = organizationRepository.save(new Organization("TEST", "Test Organization"));
        orgId = org.getId();
        tenantContext.setOrganizationId(orgId);
    }

    @Test
    void fullPaymentTransitionsToPaid() {
        var supplier = supplierRepository.save(new Supplier(orgId, "Acme Corp"));
        var category = categoryRepository.save(new Category(orgId, "Materials", CategoryType.EXPENSE));
        var account = accountRepository.save(new FinancialAccount(orgId, "Cash", FinancialAccountType.CASH));

        var payable = payableRepository.save(new Payable(orgId, supplier.getId(), category.getId(),
            "Invoice #123", LocalDate.now(), LocalDate.now().plusDays(30),
            new BigDecimal("150.00")));

        var req = new PaymentRequest(account.getId(), LocalDate.now(), new BigDecimal("150.00"), "Full payment");
        var resp = paymentService.pay(payable.getId(), req);

        assertThat(resp.amount()).isEqualByComparingTo("150.00");
        var updated = payableRepository.findById(payable.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PayableStatus.PAID);
        assertThat(auditLogRepository.countByActionAndOrganizationId("PAYMENT_CREATED", orgId))
            .isEqualTo(1);
    }

    @Test
    void partialPaymentKeepsOpen() {
        var supplier = supplierRepository.save(new Supplier(orgId, "Supplier A"));
        var category = categoryRepository.save(new Category(orgId, "Goods", CategoryType.EXPENSE));
        var account = accountRepository.save(new FinancialAccount(orgId, "Bank", FinancialAccountType.BANK));

        var payable = payableRepository.save(new Payable(orgId, supplier.getId(), category.getId(),
            "Partial test", LocalDate.now(), LocalDate.now().plusDays(15),
            new BigDecimal("200.00")));

        var req = new PaymentRequest(account.getId(), LocalDate.now(), new BigDecimal("50.00"), "Down payment");
        var resp = paymentService.pay(payable.getId(), req);

        assertThat(resp.amount()).isEqualByComparingTo("50.00");
        var updated = payableRepository.findById(payable.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PayableStatus.OPEN);
    }

    @Test
    void multiplePartialPaymentsComplete() {
        var supplier = supplierRepository.save(new Supplier(orgId, "Supplier B"));
        var category = categoryRepository.save(new Category(orgId, "Equipment", CategoryType.EXPENSE));
        var account = accountRepository.save(new FinancialAccount(orgId, "Bank Y", FinancialAccountType.BANK));

        var payable = payableRepository.save(new Payable(orgId, supplier.getId(), category.getId(),
            "Notebook", LocalDate.now(), LocalDate.now().plusDays(60),
            new BigDecimal("300.00")));

        var req1 = new PaymentRequest(account.getId(), LocalDate.now(), new BigDecimal("100.00"), "Installment 1");
        paymentService.pay(payable.getId(), req1);

        var req2 = new PaymentRequest(account.getId(), LocalDate.now(), new BigDecimal("200.00"), "Installment 2");
        paymentService.pay(payable.getId(), req2);

        var updated = payableRepository.findById(payable.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PayableStatus.PAID);
    }

    @Test
    void rejectOverPayment() {
        var supplier = supplierRepository.save(new Supplier(orgId, "Supplier C"));
        var category = categoryRepository.save(new Category(orgId, "Services", CategoryType.EXPENSE));
        var account = accountRepository.save(new FinancialAccount(orgId, "Card", FinancialAccountType.CREDIT_CARD));

        var payable = payableRepository.save(new Payable(orgId, supplier.getId(), category.getId(),
            "Over test", LocalDate.now(), LocalDate.now().plusDays(10),
            new BigDecimal("50.00")));

        var req = new PaymentRequest(account.getId(), LocalDate.now(), new BigDecimal("60.00"), "Excess");

        assertThatThrownBy(() -> paymentService.pay(payable.getId(), req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds remaining balance");
    }

    @Test
    void cannotPayCanceledPayable() {
        var supplier = supplierRepository.save(new Supplier(orgId, "Supplier D"));
        var category = categoryRepository.save(new Category(orgId, "Canceled", CategoryType.EXPENSE));
        var account = accountRepository.save(new FinancialAccount(orgId, "Cash", FinancialAccountType.CASH));

        var payable = payableRepository.save(new Payable(orgId, supplier.getId(), category.getId(),
            "Canceled order", LocalDate.now(), LocalDate.now().plusDays(5),
            new BigDecimal("80.00")));
        payable.setStatus(PayableStatus.CANCELED);
        payableRepository.save(payable);

        var req = new PaymentRequest(account.getId(), LocalDate.now(), new BigDecimal("80.00"), null);

        assertThatThrownBy(() -> paymentService.pay(payable.getId(), req))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not open for payment");
    }

    @Test
    void paymentPersistsAllFields() {
        var supplier = supplierRepository.save(new Supplier(orgId, "Supplier E"));
        var category = categoryRepository.save(new Category(orgId, "Test", CategoryType.EXPENSE));
        var account = accountRepository.save(new FinancialAccount(orgId, "Account", FinancialAccountType.OTHER));

        var payable = payableRepository.save(new Payable(orgId, supplier.getId(), category.getId(),
            "Persist test", LocalDate.now(), LocalDate.now().plusDays(20),
            new BigDecimal("123.45")));

        var req = new PaymentRequest(account.getId(), LocalDate.of(2026, 7, 15),
            new BigDecimal("123.45"), "Nota fiscal 123");
        var resp = paymentService.pay(payable.getId(), req);

        var payments = paymentRepository.findByPayableId(payable.getId());
        assertThat(payments).hasSize(1);
        var p = payments.getFirst();
        assertThat(p.getPayableId()).isEqualTo(payable.getId());
        assertThat(p.getFinancialAccountId()).isEqualTo(account.getId());
        assertThat(p.getPaymentDate()).isEqualTo(LocalDate.of(2026, 7, 15));
        assertThat(p.getAmount()).isEqualByComparingTo("123.45");
        assertThat(p.getNotes()).isEqualTo("Nota fiscal 123");
        assertThat(p.getOrganizationId()).isEqualTo(orgId);
    }
}
