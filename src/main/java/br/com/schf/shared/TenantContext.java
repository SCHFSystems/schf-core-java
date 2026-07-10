package br.com.schf.shared;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_ORG = new ThreadLocal<>();

    public void setOrganizationId(UUID organizationId) {
        CURRENT_ORG.set(organizationId);
    }

    public UUID getOrganizationId() {
        return CURRENT_ORG.get();
    }

    public void clear() {
        CURRENT_ORG.remove();
    }
}