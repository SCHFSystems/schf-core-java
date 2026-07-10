package br.com.schf.config;

import br.com.schf.organization.Organization;
import br.com.schf.organization.OrganizationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DevSeed implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeed.class);

    private final OrganizationRepository organizationRepository;

    public DevSeed(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Override
    public void run(String... args) {
        if (organizationRepository.count() == 0) {
            var org = new Organization("DEFAULT", "Default Organization");
            organizationRepository.save(org);
            log.info("Created default organization: {}", org.getId());
        }
    }
}