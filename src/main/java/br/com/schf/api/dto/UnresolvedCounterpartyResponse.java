package br.com.schf.api.dto;

import java.util.UUID;

public record UnresolvedCounterpartyResponse(
    UUID id, UUID externalId, String name, String type,
    String sourceReference, boolean active,
    String resolutionStatus,
    long payableCount, long paymentCount
) {}
