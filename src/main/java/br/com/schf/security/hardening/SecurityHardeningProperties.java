package br.com.schf.security.hardening;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "schf.security.hardening")
@Validated
public class SecurityHardeningProperties {

    @Min(1)
    private int maximumFailedLogins = 5;
    @Min(1)
    private long lockoutSeconds = 900;
    @Min(1)
    private long rateLimitWindowSeconds = 60;
    @Min(1)
    private int loginRateLimit = 10;
    @Min(1)
    private int refreshRateLimit = 20;
    @Min(1)
    private int forgotPasswordRateLimit = 5;
    @Min(1)
    private int resetPasswordRateLimit = 10;

    public int getMaximumFailedLogins() {
        return maximumFailedLogins;
    }

    public void setMaximumFailedLogins(int maximumFailedLogins) {
        this.maximumFailedLogins = maximumFailedLogins;
    }

    public long getLockoutSeconds() {
        return lockoutSeconds;
    }

    public void setLockoutSeconds(long lockoutSeconds) {
        this.lockoutSeconds = lockoutSeconds;
    }

    public long getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    public void setRateLimitWindowSeconds(long rateLimitWindowSeconds) {
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
    }

    public int getLoginRateLimit() {
        return loginRateLimit;
    }

    public void setLoginRateLimit(int loginRateLimit) {
        this.loginRateLimit = loginRateLimit;
    }

    public int getRefreshRateLimit() {
        return refreshRateLimit;
    }

    public void setRefreshRateLimit(int refreshRateLimit) {
        this.refreshRateLimit = refreshRateLimit;
    }

    public int getForgotPasswordRateLimit() {
        return forgotPasswordRateLimit;
    }

    public void setForgotPasswordRateLimit(int forgotPasswordRateLimit) {
        this.forgotPasswordRateLimit = forgotPasswordRateLimit;
    }

    public int getResetPasswordRateLimit() {
        return resetPasswordRateLimit;
    }

    public void setResetPasswordRateLimit(int resetPasswordRateLimit) {
        this.resetPasswordRateLimit = resetPasswordRateLimit;
    }
}
