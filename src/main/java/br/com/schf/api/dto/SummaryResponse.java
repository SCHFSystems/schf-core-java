package br.com.schf.api.dto;

public record SummaryResponse(
    long supplierCount,
    long unresolvedCounterpartyCount,
    long categoryCount,
    long financialAccountCount,
    long payableCount,
    long paymentCount,
    String serverTime,
    long warningCount
) {}
