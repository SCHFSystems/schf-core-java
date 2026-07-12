package br.com.schf.migration.domain;

import java.util.UUID;

public record CanonicalFinancialAccount(UUID externalId, String name, String type,
                                        String bankName, String agency, String accountNumber,
                                        boolean active) {
}
