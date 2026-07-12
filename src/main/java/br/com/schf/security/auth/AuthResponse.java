package br.com.schf.security.auth;

import java.util.List;
import java.util.UUID;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    long accessTokenExpiresInSeconds,
    long refreshTokenExpiresInSeconds,
    String tokenType,
    UserInfo user
) {

    public record UserInfo(UUID userId, UUID organizationId, String username,
                            String displayName, boolean mustChangePassword,
                            List<String> permissions) {
    }
}
