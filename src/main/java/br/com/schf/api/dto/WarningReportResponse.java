package br.com.schf.api.dto;

import java.util.Map;

public record WarningReportResponse(
    long totalWarnings,
    Map<String, Long> warningCounts,
    long totalPayables,
    long totalPayments,
    long unresolvedCount
) {}
