package br.com.schf.security.principal;

import br.com.schf.security.jwt.JwtService;
import br.com.schf.security.membership.UserRoleAssignmentRepository;
import br.com.schf.user.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserAccountRepository userRepository;
    private final UserRoleAssignmentRepository userRoleRepository;

    public JwtAuthenticationFilter(JwtService jwtService,
                                    UserAccountRepository userRepository,
                                    UserRoleAssignmentRepository userRoleRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        var header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }
        var token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        try {
            var claims = jwtService.parse(token);
            var tokenType = claims.get("type", String.class);
            if (!"access".equals(tokenType)) {
                chain.doFilter(request, response);
                return;
            }
            var userId = UUID.fromString(claims.getSubject());
            var user = userRepository.findById(userId).orElse(null);
            if (user == null || !user.isActive()) {
                log.debug("JWT references unknown or inactive user {}", userId);
                chain.doFilter(request, response);
                return;
            }
            var permissions = userRoleRepository.findPermissionCodesByUserId(user.getId());
            var organizationId = user.getOrganization() != null ? user.getOrganization().getId() : null;
            var principal = new AuthenticatedUserPrincipal(
                user.getId(),
                organizationId,
                user.getUsername(),
                user.isActive(),
                user.isMustChangePassword(),
                permissions);
            var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtService.InvalidJwtException | IllegalArgumentException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }
}
