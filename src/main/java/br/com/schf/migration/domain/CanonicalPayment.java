package br.com.schf.migration.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CanonicalPayment(UUID externalId, UUID payableExternalId,
                               UUID financialAccountExternalId, LocalDate paymentDate,
                               BigDecimal amount, String notes, String method) {
}
