package br.com.schf.migration.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CanonicalPayment(UUID externalId, UUID payableExternalId,
                               UUID financialAccountExternalId, LocalDate paymentDate,
                               BigDecimal amount, String notes) {
}
