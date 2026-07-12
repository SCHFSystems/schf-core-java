package br.com.schf.security.auth;

import br.com.schf.audit.AuditService;
import br.com.schf.security.jwt.JwtService;
import br.com.schf.security.hardening.LoginAttemptService;
import br.com.schf.security.metrics.SecurityMetrics;
import br.com.schf.security.membership.UserRoleAssignmentRepository;
import br.com.schf.security.principal.AuthenticatedUserPrincipal;
import br.com.schf.security.reset.PasswordResetDelivery;
import br.com.schf.security.reset.PasswordResetDeliveryService;
import br.com.schf.security.token.PasswordResetToken;
import br.com.schf.security.token.PasswordResetTokenRepository;
import br.com.schf.security.token.RefreshToken;
import br.com.schf.security.token.RefreshTokenRepository;
import br.com.schf.user.UserAccount;
import br.com.schf.user.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserAccountRepository userRepository;
    private final UserRoleAssignmentRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordResetDeliveryService passwordResetDeliveryService;
    private final SecurityMetrics metrics;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(UserAccountRepository userRepository,
                       UserRoleAssignmentRepository userRoleRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService auditService,
                       LoginAttemptService loginAttemptService,
                       PasswordResetDeliveryService passwordResetDeliveryService,
                       SecurityMetrics metrics,
                       Clock clock) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.loginAttemptService = loginAttemptService;
        this.passwordResetDeliveryService = passwordResetDeliveryService;
        this.metrics = metrics;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(randomToken());
    }

    @Transactional
    public AuthResponse login(LoginRequest request, ClientRequestInfo client) {
        var user = userRepository.findByEmail(request.email().trim().toLowerCase()).orElse(null);
        var now = OffsetDateTime.now(clock);
        var locked = user != null && user.isTemporarilyLocked(now);
        var passwordMatches = passwordEncoder.matches(request.password(),
            user == null ? dummyPasswordHash : user.getPasswordHash());
        if (user == null || !user.isActive() || locked || !passwordMatches) {
            var newlyLocked = user != null && user.isActive() && !locked
                && loginAttemptService.recordFailure(user.getId());
            metrics.loginFailure();
            auditService.recordIndependent(user == null || user.getOrganization() == null ? null : user.getOrganization().getId(),
                user == null ? null : user.getId(), "LOGIN_FAILURE", "AUTH", null, "FAILURE",
                client.ipAddress(), client.userAgent(), "Invalid credentials");
            if (newlyLocked) {
                metrics.lockout();
                auditService.recordIndependent(user.getOrganization().getId(), user.getId(), "ACCOUNT_LOCKED",
                    "USER", user.getId().toString(), "FAILURE", client.ipAddress(), client.userAgent(),
                    "Temporary login lockout");
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        user.clearLoginFailures();
        user.markLoggedIn();
        userRepository.save(user);
        var permissions = userRoleRepository.findPermissionCodesByUserId(user.getId());
        var response = issueTokens(user, permissions, client);
        auditService.record(user.getOrganization().getId(), user.getId(), "LOGIN_SUCCESS", "AUTH",
            user.getId().toString(), "SUCCESS", client.ipAddress(), client.userAgent(), null);
        metrics.loginSuccess();
        return response;
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request, ClientRequestInfo client) {
        var stored = refreshTokenRepository.findByTokenHash(hash(request.refreshToken()))
            .orElseThrow(() -> deniedRefresh("Invalid refresh token"));
        var now = OffsetDateTime.now(clock);
        if (stored.isRevoked() || stored.isExpired(now)) {
            throw deniedRefresh("Invalid refresh token");
        }
        var user = userRepository.findById(stored.getUserId())
            .filter(UserAccount::isActive)
            .orElseThrow(() -> deniedRefresh("Invalid refresh token"));
        if (user.getOrganization() == null
            || !user.getOrganization().getId().equals(stored.getOrganizationId())) {
            throw deniedRefresh("Invalid refresh token");
        }

        stored.revoke("ROTATED");
        refreshTokenRepository.save(stored);
        var permissions = userRoleRepository.findPermissionCodesByUserId(user.getId());
        var response = issueTokens(user, permissions, client);
        auditService.record(stored.getOrganizationId(), user.getId(), "TOKEN_REFRESH", "AUTH",
            stored.getId().toString(), "SUCCESS", client.ipAddress(), client.userAgent(), null);
        return response;
    }

    @Transactional
    public void logout(LogoutRequest request, ClientRequestInfo client) {
        refreshTokenRepository.findByTokenHash(hash(request.refreshToken())).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.revoke("LOGOUT");
                refreshTokenRepository.save(token);
            }
            auditService.record(token.getOrganizationId(), token.getUserId(), "LOGOUT", "AUTH",
                token.getId().toString(), "SUCCESS", client.ipAddress(), client.userAgent(), null);
        });
    }

    @Transactional(readOnly = true)
    public AuthResponse.UserInfo me(AuthenticatedUserPrincipal principal) {
        var user = userRepository.findById(principal.getUserId())
            .orElseThrow(() -> unauthorized("User not found"));
        return toUserInfo(user, principal.getPermissions());
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, ClientRequestInfo client) {
        metrics.passwordResetRequested();
        userRepository.findByEmail(request.email().trim().toLowerCase())
            .filter(UserAccount::isActive)
            .ifPresent(user -> requestPasswordReset(user, client, "PASSWORD_RESET_REQUESTED"));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request, ClientRequestInfo client) {
        var reset = passwordResetTokenRepository.findByTokenHash(hash(request.token()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reset token"));
        if (reset.isUsed() || reset.isExpired(OffsetDateTime.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reset token");
        }
        var user = userRepository.findById(reset.getUserId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reset token"));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        reset.markUsed();
        passwordResetTokenRepository.save(reset);
        refreshTokenRepository.revokeAllForUser(user.getId(), "PASSWORD_RESET");
        auditService.record(user.getOrganization().getId(), user.getId(), "PASSWORD_RESET_COMPLETED",
            "USER", user.getId().toString(), "SUCCESS", client.ipAddress(), client.userAgent(), null);
    }

    @Transactional
    public void changePassword(AuthenticatedUserPrincipal principal, ChangePasswordRequest request,
                               ClientRequestInfo client) {
        var user = userRepository.findById(principal.getUserId())
            .orElseThrow(() -> unauthorized("User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is invalid");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.getId(), "PASSWORD_CHANGED");
        auditService.record(user.getOrganization().getId(), user.getId(), "PASSWORD_CHANGED", "USER",
            user.getId().toString(), "SUCCESS", client.ipAddress(), client.userAgent(), null);
    }

    @Transactional
    public void requestPasswordReset(UserAccount user, ClientRequestInfo client, String auditAction) {
        var rawToken = randomToken();
        var expiresAt = OffsetDateTime.now(clock).plusMinutes(30);
        passwordResetTokenRepository.save(new PasswordResetToken(user.getId(), hash(rawToken),
            expiresAt, client.ipAddress(), client.userAgent()));
        passwordResetDeliveryService.deliver(new PasswordResetDelivery(user.getEmail(), rawToken, expiresAt));
        auditService.record(user.getOrganization().getId(), user.getId(), auditAction,
            "USER", user.getId().toString(), "SUCCESS", client.ipAddress(), client.userAgent(), null);
    }

    private AuthResponse issueTokens(UserAccount user, List<String> permissions, ClientRequestInfo client) {
        var organizationId = user.getOrganization().getId();
        var accessToken = jwtService.createAccessToken(user.getId(), organizationId,
            user.getUsername(), permissions);
        var rawRefreshToken = randomToken();
        var now = OffsetDateTime.now(clock);
        refreshTokenRepository.save(new RefreshToken(user.getId(), organizationId, hash(rawRefreshToken),
            now, now.plusSeconds(jwtService.getRefreshTokenTtlSeconds()),
            client.ipAddress(), client.userAgent()));
        return new AuthResponse(accessToken, rawRefreshToken, jwtService.getAccessTokenTtlSeconds(),
            jwtService.getRefreshTokenTtlSeconds(), "Bearer", toUserInfo(user, permissions));
    }

    private AuthResponse.UserInfo toUserInfo(UserAccount user, List<String> permissions) {
        return new AuthResponse.UserInfo(user.getId(), user.getOrganization().getId(), user.getUsername(),
            user.getDisplayName(), user.isMustChangePassword(), List.copyOf(permissions));
    }

    private String randomToken() {
        var bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    private ResponseStatusException deniedRefresh(String message) {
        metrics.refreshDenied();
        return unauthorized(message);
    }
}
