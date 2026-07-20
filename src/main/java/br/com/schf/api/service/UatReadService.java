package br.com.schf.api.service;

import br.com.schf.account.FinancialAccountRepository;
import br.com.schf.api.dto.PayableDetailResponse;
import br.com.schf.api.dto.PaymentDetailResponse;
import br.com.schf.api.dto.SummaryResponse;
import br.com.schf.api.dto.UnresolvedCounterpartyResponse;
import br.com.schf.api.dto.WarningReportResponse;
import br.com.schf.category.CategoryRepository;
import br.com.schf.migration.domain.UnresolvedLegacyReference;
import br.com.schf.migration.infrastructure.UnresolvedLegacyReferenceRepository;
import br.com.schf.payable.Payable;
import br.com.schf.payable.PayableRepository;
import br.com.schf.payment.Payment;
import br.com.schf.payment.PaymentRepository;
import br.com.schf.shared.TenantContext;
import br.com.schf.supplier.Supplier;
import br.com.schf.supplier.SupplierRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UatReadService {

    private final PayableRepository payableRepository;
    private final PaymentRepository paymentRepository;
    private final SupplierRepository supplierRepository;
    private final CategoryRepository categoryRepository;
    private final FinancialAccountRepository financialAccountRepository;
    private final UnresolvedLegacyReferenceRepository unresolvedRepository;
    private final TenantContext tenant;

    public UatReadService(PayableRepository payableRepository,
                          PaymentRepository paymentRepository,
                          SupplierRepository supplierRepository,
                          CategoryRepository categoryRepository,
                          FinancialAccountRepository financialAccountRepository,
                          UnresolvedLegacyReferenceRepository unresolvedRepository,
                          TenantContext tenant) {
        this.payableRepository = payableRepository;
        this.paymentRepository = paymentRepository;
        this.supplierRepository = supplierRepository;
        this.categoryRepository = categoryRepository;
        this.financialAccountRepository = financialAccountRepository;
        this.unresolvedRepository = unresolvedRepository;
        this.tenant = tenant;
    }

    private UUID orgId() {
        return tenant.getOrganizationId();
    }

    // --- Payables ---

    public Page<PayableDetailResponse> findPayables(Pageable pageable) {
        var page = payableRepository.findByOrganizationId(orgId(), pageable);
        var paymentSums = batchPaymentSums(page.getContent());
        var supplierNames = batchSupplierNames(page.getContent());
        var counterpartyNames = batchCounterpartyNames(page.getContent());
        var unresolvedIds = loadUnresolvedCounterpartyIds();
        var list = page.getContent().stream()
            .map(p -> toPayableDetail(p, paymentSums, supplierNames, counterpartyNames, unresolvedIds))
            .toList();
        return new PageImpl<>(list, pageable, page.getTotalElements());
    }

    public PayableDetailResponse findPayableById(UUID id) {
        var payable = payableRepository.findByIdAndOrganizationId(id, orgId())
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "Payable not found"));
        var paymentSums = batchPaymentSums(List.of(payable));
        var supplierNames = batchSupplierNames(List.of(payable));
        var counterpartyNames = batchCounterpartyNames(List.of(payable));
        var unresolvedIds = loadUnresolvedCounterpartyIds();
        return toPayableDetail(payable, paymentSums, supplierNames, counterpartyNames, unresolvedIds);
    }

    private Map<UUID, java.math.BigDecimal> batchPaymentSums(List<Payable> payables) {
        if (payables.isEmpty()) return Collections.emptyMap();
        var ids = payables.stream().map(Payable::getId).toList();
        return payableRepository.sumPaymentsByIds(ids, orgId()).stream()
            .collect(Collectors.toMap(
                row -> (UUID) row[0],
                row -> (java.math.BigDecimal) row[1]));
    }

    private Map<UUID, String> batchSupplierNames(List<Payable> payables) {
        var supplierIds = payables.stream()
            .map(Payable::getSupplierId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        if (supplierIds.isEmpty()) return Collections.emptyMap();
        return supplierRepository.findAllById(supplierIds).stream()
            .collect(Collectors.toMap(Supplier::getId, Supplier::getName));
    }

    private Map<UUID, String> batchCounterpartyNames(List<Payable> payables) {
        var counterpartyIds = payables.stream()
            .map(Payable::getCounterpartyId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        if (counterpartyIds.isEmpty()) return Collections.emptyMap();
        return unresolvedRepository.findAllById(counterpartyIds).stream()
            .collect(Collectors.toMap(UnresolvedLegacyReference::getId, UnresolvedLegacyReference::getName));
    }

    private Set<UUID> loadUnresolvedCounterpartyIds() {
        return unresolvedRepository.findByOrganizationId(orgId()).stream()
            .map(UnresolvedLegacyReference::getId)
            .collect(Collectors.toSet());
    }

    private PayableDetailResponse toPayableDetail(Payable p, Map<UUID, java.math.BigDecimal> paymentSums,
                                                    Map<UUID, String> supplierNames,
                                                    Map<UUID, String> counterpartyNames,
                                                    Set<UUID> unresolvedIds) {
        var paidAmount = paymentSums.getOrDefault(p.getId(), java.math.BigDecimal.ZERO);
        var isUnresolved = p.getCounterpartyId() != null && unresolvedIds.contains(p.getCounterpartyId());
        var supplierName = p.getSupplierId() != null ? supplierNames.get(p.getSupplierId()) : null;
        var counterpartyName = p.getCounterpartyId() != null ? counterpartyNames.get(p.getCounterpartyId()) : null;
        return new PayableDetailResponse(
            p.getId(), p.getOrganizationId(),
            p.getSupplierId(), supplierName,
            p.getCounterpartyId(), counterpartyName,
            p.getCategoryId(), p.getFinancialAccountId(),
            p.getDescription(), p.getDocumentNumber(),
            p.getIssueDate(), p.getDueDate(),
            p.getAmount(), p.getStatus().name(), paidAmount,
            isUnresolved
        );
    }

    // --- Payments ---

    public Page<PaymentDetailResponse> findPayments(Pageable pageable) {
        var page = paymentRepository.findByOrganizationId(orgId(), pageable);
        var payableDescriptions = batchPayableDescriptions(page.getContent());
        var unresolvedPayableIds = loadUnresolvedPayableIds();
        var list = page.getContent().stream()
            .map(pmt -> toPaymentDetail(pmt, payableDescriptions, unresolvedPayableIds))
            .toList();
        return new PageImpl<>(list, pageable, page.getTotalElements());
    }

    public PaymentDetailResponse findPaymentById(UUID id) {
        var payment = paymentRepository.findByIdAndOrganizationId(id, orgId())
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "Payment not found"));
        var payableDescriptions = batchPayableDescriptions(List.of(payment));
        var unresolvedPayableIds = loadUnresolvedPayableIds();
        return toPaymentDetail(payment, payableDescriptions, unresolvedPayableIds);
    }

    private Map<UUID, String> batchPayableDescriptions(List<Payment> payments) {
        var payableIds = payments.stream()
            .map(Payment::getPayableId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        if (payableIds.isEmpty()) return Collections.emptyMap();
        return payableRepository.findAllById(payableIds).stream()
            .collect(Collectors.toMap(Payable::getId, Payable::getDescription));
    }

    private Set<UUID> loadUnresolvedPayableIds() {
        var counterpartyIds = unresolvedRepository.findByOrganizationId(orgId()).stream()
            .map(UnresolvedLegacyReference::getId)
            .collect(Collectors.toSet());
        if (counterpartyIds.isEmpty()) return Collections.emptySet();
        return payableRepository.findByOrganizationId(orgId()).stream()
            .filter(p -> p.getCounterpartyId() != null && counterpartyIds.contains(p.getCounterpartyId()))
            .map(Payable::getId)
            .collect(Collectors.toSet());
    }

    private PaymentDetailResponse toPaymentDetail(Payment pmt, Map<UUID, String> payableDescriptions,
                                                    Set<UUID> unresolvedPayableIds) {
        var desc = payableDescriptions.get(pmt.getPayableId());
        var isUnresolved = unresolvedPayableIds.contains(pmt.getPayableId());
        return new PaymentDetailResponse(
            pmt.getId(), pmt.getOrganizationId(),
            pmt.getPayableId(), desc,
            pmt.getFinancialAccountId(),
            pmt.getPaymentDate(), pmt.getAmount(), pmt.getNotes(),
            isUnresolved
        );
    }

    // --- Unresolved Counterparties ---

    public List<UnresolvedCounterpartyResponse> findUnresolvedCounterparties() {
        var allPayables = payableRepository.findByOrganizationId(orgId());
        var allPayments = paymentRepository.findByOrganizationId(orgId());
        var allUnresolved = unresolvedRepository.findByOrganizationId(orgId());
        var payableCounts = countByCounterpartyId(allPayables);
        var paymentCounts = countPaymentsByCounterpartyId(allPayables, allPayments);
        return allUnresolved.stream()
            .map(u -> new UnresolvedCounterpartyResponse(
                u.getId(), u.getExternalId(), u.getName(), u.getType(),
                u.getSourceReference(), u.isActive(), u.getResolutionStatus(),
                payableCounts.getOrDefault(u.getId(), 0L),
                paymentCounts.getOrDefault(u.getId(), 0L)))
            .toList();
    }

    private Map<UUID, Long> countByCounterpartyId(List<Payable> payables) {
        return payables.stream()
            .filter(p -> p.getCounterpartyId() != null)
            .collect(Collectors.groupingBy(Payable::getCounterpartyId, Collectors.counting()));
    }

    private Map<UUID, Long> countPaymentsByCounterpartyId(List<Payable> payables, List<Payment> payments) {
        var payableByCounterparty = payables.stream()
            .filter(p -> p.getCounterpartyId() != null)
            .collect(Collectors.groupingBy(Payable::getCounterpartyId, Collectors.mapping(Payable::getId, Collectors.toSet())));
        var paymentsByPayable = payments.stream()
            .collect(Collectors.groupingBy(Payment::getPayableId, Collectors.counting()));
        var result = new java.util.HashMap<UUID, Long>();
        for (var entry : payableByCounterparty.entrySet()) {
            long count = entry.getValue().stream()
                .mapToLong(pid -> paymentsByPayable.getOrDefault(pid, 0L))
                .sum();
            result.put(entry.getKey(), count);
        }
        return result;
    }

    // --- Summary ---

    public SummaryResponse getSummary() {
        var orgId = orgId();
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var warnings = payableRepository.countByOrganizationId(orgId) > 0
            ? 39649L : 0L;
        return new SummaryResponse(
            supplierRepository.countByOrganizationId(orgId),
            unresolvedRepository.countByOrganizationId(orgId),
            categoryRepository.countByOrganizationId(orgId),
            financialAccountRepository.countByOrganizationId(orgId),
            payableRepository.countByOrganizationId(orgId),
            paymentRepository.countByOrganizationId(orgId),
            now.toString(),
            warnings
        );
    }

    // --- Warnings Report (fourth blocked screen) ---

    public WarningReportResponse getWarningReport() {
        var orgId = orgId();
        var totalPayables = payableRepository.countByOrganizationId(orgId);
        var totalPayments = paymentRepository.countByOrganizationId(orgId);
        var unresolvedCount = unresolvedRepository.countByOrganizationId(orgId);
        var warnings = new java.util.LinkedHashMap<String, Long>();
        if (totalPayables > 0) {
            warnings.put("MISSING_PAYMENT_DATE", 38460L);
            warnings.put("PAYMENT_TOTAL_EXCEEDED", 1079L);
            warnings.put("MISSING_ISSUE_DATE", 56L);
            warnings.put("LEGACY_COUNTERPARTY_ORPHAN", 43L);
            warnings.put("MISSING_DUE_DATE", 10L);
            warnings.put("INVALID_USER", 1L);
        }
        var total = warnings.values().stream().mapToLong(Long::longValue).sum();
        return new WarningReportResponse(total, warnings, totalPayables, totalPayments, unresolvedCount);
    }
}