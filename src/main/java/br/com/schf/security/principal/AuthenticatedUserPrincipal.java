package br.com.schf.security.principal;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class AuthenticatedUserPrincipal implements UserDetails {

    private final UUID userId;
    private final UUID organizationId;
    private final String username;
    private final boolean active;
    private final boolean mustChangePassword;
    private final List<String> permissions;

    public AuthenticatedUserPrincipal(UUID userId, UUID organizationId, String username,
                                       boolean active, boolean mustChangePassword,
                                       List<String> permissions) {
        this.userId = userId;
        this.organizationId = organizationId;
        this.username = username;
        this.active = active;
        this.mustChangePassword = mustChangePassword;
        this.permissions = List.copyOf(permissions);
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    @Override
    public java.util.Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream()
            .map(p -> new SimpleGrantedAuthority("PERM_" + p))
            .collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
