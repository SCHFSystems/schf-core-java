package br.com.schf.security.auth;

import br.com.schf.security.principal.AuthenticatedUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, clientInfo(httpRequest));
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        return authService.refresh(request, clientInfo(httpRequest));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request, HttpServletRequest httpRequest) {
        authService.logout(request, clientInfo(httpRequest));
    }

    @GetMapping("/me")
    public AuthResponse.UserInfo me(@AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return authService.me(principal);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUserPrincipal principal,
                               @Valid @RequestBody ChangePasswordRequest request,
                               HttpServletRequest httpRequest) {
        authService.changePassword(principal, request, clientInfo(httpRequest));
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                           HttpServletRequest httpRequest) {
        authService.forgotPassword(request, clientInfo(httpRequest));
        return new MessageResponse("If the account exists, password reset instructions will be sent");
    }

    @PostMapping("/password/reset")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                                          HttpServletRequest httpRequest) {
        authService.resetPassword(request, clientInfo(httpRequest));
        return new MessageResponse("Password reset completed");
    }

    private ClientRequestInfo clientInfo(HttpServletRequest request) {
        return new ClientRequestInfo(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
