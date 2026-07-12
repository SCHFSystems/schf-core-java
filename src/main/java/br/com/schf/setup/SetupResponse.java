package br.com.schf.setup;

import java.util.UUID;

public record SetupResponse(
    boolean completed,
    UUID organizationId,
    String message
) {
    public static SetupResponse alreadyCompleted() {
        return new SetupResponse(true, null, "Setup has already been completed");
    }

    public static SetupResponse success(UUID organizationId) {
        return new SetupResponse(true, organizationId, "Instance setup completed successfully");
    }
}
