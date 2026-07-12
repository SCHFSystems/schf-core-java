package br.com.schf.security.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SecurityMetrics {

    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Counter lockouts;
    private final Counter passwordResetRequested;
    private final Counter refreshDenied;
    private final Counter rateLimitDenied;

    public SecurityMetrics(MeterRegistry registry) {
        loginSuccess = Counter.builder("schf.auth.login.success").register(registry);
        loginFailure = Counter.builder("schf.auth.login.failure").register(registry);
        lockouts = Counter.builder("schf.auth.lockout").register(registry);
        passwordResetRequested = Counter.builder("schf.auth.password.reset.requested").register(registry);
        refreshDenied = Counter.builder("schf.auth.refresh.denied").register(registry);
        rateLimitDenied = Counter.builder("schf.auth.rate.limit.denied").register(registry);
    }

    public void loginSuccess() {
        loginSuccess.increment();
    }

    public void loginFailure() {
        loginFailure.increment();
    }

    public void lockout() {
        lockouts.increment();
    }

    public void passwordResetRequested() {
        passwordResetRequested.increment();
    }

    public void refreshDenied() {
        refreshDenied.increment();
    }

    public void rateLimitDenied() {
        rateLimitDenied.increment();
    }
}
