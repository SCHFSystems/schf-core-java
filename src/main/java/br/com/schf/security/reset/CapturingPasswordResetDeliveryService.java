package br.com.schf.security.reset;

import java.util.Optional;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"dev", "test"})
public class CapturingPasswordResetDeliveryService implements PasswordResetDeliveryService {

    private static final int MAXIMUM_DELIVERIES = 1000;

    private final Map<String, PasswordResetDelivery> deliveries = Collections.synchronizedMap(
        new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, PasswordResetDelivery> eldest) {
                return size() > MAXIMUM_DELIVERIES;
            }
        });

    @Override
    public void deliver(PasswordResetDelivery delivery) {
        deliveries.put(delivery.email().trim().toLowerCase(), delivery);
    }

    public Optional<PasswordResetDelivery> findLatest(String email) {
        return Optional.ofNullable(deliveries.get(email.trim().toLowerCase()));
    }

    public void reset() {
        deliveries.clear();
    }
}
