package br.com.schf.security.reset;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!dev & !test")
public class NoopPasswordResetDeliveryService implements PasswordResetDeliveryService {

    @Override
    public void deliver(PasswordResetDelivery delivery) {
        // Production delivery is intentionally disabled until an SMTP provider is configured.
    }
}
