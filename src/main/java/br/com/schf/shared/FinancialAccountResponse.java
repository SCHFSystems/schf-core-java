package br.com.schf.shared;

import java.util.UUID;

public record FinancialAccountResponse(
    UUID id, UUID organizationId, String name, String type,
    String bankName, String agency, String accountNumber, boolean active
) {}