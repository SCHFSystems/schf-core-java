package br.com.schf.payable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "payables")
public class Payable {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "counterparty_id")
    private UUID counterpartyId;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "financial_account_id")
    private UUID financialAccountId;

    @Column
    private String description;

    @Column(name = "document_number", length = 80)
    private String documentNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PayableStatus status = PayableStatus.OPEN;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Payable() {}

    public Payable(UUID organizationId, UUID supplierId, UUID categoryId,
                   String description, LocalDate issueDate, LocalDate dueDate, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.organizationId = organizationId;
        this.supplierId = supplierId;
        this.categoryId = categoryId;
        this.description = description;
        this.issueDate = issueDate;
        this.dueDate = dueDate;
        this.amount = amount;
        this.status = PayableStatus.OPEN;
    }

    @PrePersist
    void prePersist() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = PayableStatus.OPEN;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getCounterpartyId() { return counterpartyId; }
    public void setCounterpartyId(UUID counterpartyId) { this.counterpartyId = counterpartyId; }
    public UUID getCategoryId() { return categoryId; }
    public UUID getFinancialAccountId() { return financialAccountId; }
    public void setFinancialAccountId(UUID financialAccountId) { this.financialAccountId = financialAccountId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getAmount() { return amount; }
    public PayableStatus getStatus() { return status; }
    public void setStatus(PayableStatus status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}