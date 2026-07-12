package br.com.schf.security;

import br.com.schf.security.jwt.JwtProperties;
import br.com.schf.security.hardening.SecurityHardeningProperties;
import br.com.schf.migration.validation.MigrationProperties;
import br.com.schf.security.permission.Permissions;
import br.com.schf.security.principal.JwtAuthenticationFilter;
import br.com.schf.security.ratelimit.RateLimitFilter;
import br.com.schf.security.tenant.TenantContextFilter;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({BootstrapAdminProperties.class, JwtProperties.class,
    SecurityHardeningProperties.class, MigrationProperties.class})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtFilter,
                                            TenantContextFilter tenantFilter,
                                            RateLimitFilter rateLimitFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) ->
                    response.sendError(401, "Unauthorized"))
                .accessDeniedHandler((request, response, exception) ->
                    response.sendError(403, "Forbidden")))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/api/health", "/actuator/health", "/actuator/info", "/actuator/prometheus")
                    .permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                    "/api/auth/password/forgot", "/api/auth/password/reset").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/admin/migrations/**")
                    .hasAnyAuthority(authority(Permissions.MIGRATION_READ),
                        authority(Permissions.MIGRATION_IMPORT), authority(Permissions.ADMIN_ACCESS))
                .requestMatchers(HttpMethod.POST, "/api/admin/migrations/**")
                    .hasAnyAuthority(authority(Permissions.MIGRATION_IMPORT), authority(Permissions.ADMIN_ACCESS))
                .requestMatchers(HttpMethod.GET, "/api/admin/audit-logs")
                    .hasAnyAuthority(authority(Permissions.AUDIT_READ), authority(Permissions.ADMIN_ACCESS))
                .requestMatchers(HttpMethod.GET, "/api/admin/users/**", "/api/admin/roles/**")
                    .hasAnyAuthority(authority(Permissions.USER_READ), authority(Permissions.ADMIN_ACCESS))
                .requestMatchers("/api/admin/users/**")
                    .hasAnyAuthority(authority(Permissions.USER_WRITE), authority(Permissions.ADMIN_ACCESS))
                .requestMatchers(HttpMethod.GET, "/api/suppliers/**")
                    .hasAuthority(authority(Permissions.SUPPLIER_READ))
                .requestMatchers(HttpMethod.POST, "/api/suppliers/**")
                    .hasAuthority(authority(Permissions.SUPPLIER_WRITE))
                .requestMatchers(HttpMethod.GET, "/api/categories/**")
                    .hasAuthority(authority(Permissions.CATEGORY_READ))
                .requestMatchers(HttpMethod.POST, "/api/categories/**")
                    .hasAuthority(authority(Permissions.CATEGORY_WRITE))
                .requestMatchers(HttpMethod.GET, "/api/financial-accounts/**")
                    .hasAuthority(authority(Permissions.ACCOUNT_READ))
                .requestMatchers(HttpMethod.POST, "/api/financial-accounts/**")
                    .hasAuthority(authority(Permissions.ACCOUNT_WRITE))
                .requestMatchers(HttpMethod.GET, "/api/payables/**")
                    .hasAuthority(authority(Permissions.PAYABLE_READ))
                .requestMatchers(HttpMethod.POST, "/api/payables/*/payments")
                    .hasAuthority(authority(Permissions.PAYMENT_WRITE))
                .requestMatchers(HttpMethod.POST, "/api/payables/**")
                    .hasAuthority(authority(Permissions.PAYABLE_WRITE))
                .anyRequest().authenticated())
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(tenantFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<TenantContextFilter> tenantFilterRegistration(TenantContextFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private static String authority(String permission) {
        return "PERM_" + permission;
    }
}
