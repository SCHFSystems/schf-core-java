package br.com.schf.security.ratelimit;

import br.com.schf.security.hardening.SecurityHardeningProperties;
import br.com.schf.security.metrics.SecurityMetrics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final SecurityHardeningProperties properties;
    private final SecurityMetrics metrics;

    public RateLimitFilter(RateLimitService rateLimitService,
                           SecurityHardeningProperties properties,
                           SecurityMetrics metrics) {
        this.rateLimitService = rateLimitService;
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || limitFor(request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var limit = limitFor(request.getRequestURI());
        var key = request.getRemoteAddr() + ":" + request.getRequestURI();
        if (!rateLimitService.allow(key, limit, properties.getRateLimitWindowSeconds())) {
            metrics.rateLimitDenied();
            response.sendError(429, "Too Many Requests");
            return;
        }
        chain.doFilter(request, response);
    }

    private Integer limitFor(String path) {
        return Map.of(
            "/api/auth/login", properties.getLoginRateLimit(),
            "/api/auth/refresh", properties.getRefreshRateLimit(),
            "/api/auth/password/forgot", properties.getForgotPasswordRateLimit(),
            "/api/auth/password/reset", properties.getResetPasswordRateLimit()
        ).get(path);
    }
}
