package br.com.schf.user;

import br.com.schf.organization.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class UserAccount {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false, unique = true, length = 180)
    private String email;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private UserRole role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "password_changed_at")
    private OffsetDateTime passwordChangedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserAccount() {
    }

    public UserAccount(Organization organization, String username, String email, String displayName, UserRole role) {
        this.id = UUID.randomUUID();
        this.organization = organization;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
    }

    @PrePersist
    void prePersist() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void markLoggedIn() {
        this.lastLoginAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
    }

    public OffsetDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public boolean isTemporarilyLocked(OffsetDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean registerFailedLogin(OffsetDateTime now, int maximumAttempts, long lockoutSeconds) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maximumAttempts) {
            lockedUntil = now.plusSeconds(lockoutSeconds);
            return true;
        }
        return false;
    }

    public void clearLoginFailures() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }
}
