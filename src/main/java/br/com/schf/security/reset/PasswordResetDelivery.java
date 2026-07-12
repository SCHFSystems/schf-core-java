package br.com.schf.security.reset;

import java.time.OffsetDateTime;

public record PasswordResetDelivery(String email, String token, OffsetDateTime expiresAt) {
}
