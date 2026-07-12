package br.com.schf.security.hardening;

import br.com.schf.user.UserAccountRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAttemptService {

    private final UserAccountRepository userRepository;
    private final SecurityHardeningProperties properties;
    private final Clock clock;

    public LoginAttemptService(UserAccountRepository userRepository,
                               SecurityHardeningProperties properties,
                               Clock clock) {
        this.userRepository = userRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordFailure(UUID userId) {
        var user = userRepository.findForUpdate(userId).orElseThrow();
        var locked = user.registerFailedLogin(OffsetDateTime.now(clock),
            properties.getMaximumFailedLogins(), properties.getLockoutSeconds());
        userRepository.save(user);
        return locked;
    }
}
