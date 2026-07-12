package br.com.schf.system;

import br.com.schf.setup.SetupService;
import java.util.List;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final InstanceProperties instanceProperties;
    private final SetupService setupService;
    private final BuildProperties buildProperties;

    public SystemController(InstanceProperties instanceProperties,
                            SetupService setupService,
                            BuildProperties buildProperties) {
        this.instanceProperties = instanceProperties;
        this.setupService = setupService;
        this.buildProperties = buildProperties;
    }

    @GetMapping("/info")
    public SystemInfoResponse info() {
        var status = setupService.getStatus();
        return new SystemInfoResponse(
            "SCHF Core",
            instanceProperties.getName(),
            instanceProperties.getId(),
            buildProperties.getVersion(),
            buildProperties.getVersion(),
            instanceProperties.getEnvironment(),
            status.setupRequired());
    }

    @GetMapping("/capabilities")
    public CapabilitiesResponse capabilities() {
        var status = setupService.getStatus();
        return new CapabilitiesResponse(
            "SCHF Core",
            buildProperties.getVersion(),
            List.of("authentication", "rbac", "suppliers", "categories",
                "financial-accounts", "payables", "payments", "reports", "audit",
                "migration-import", "tenant-isolation", "rate-limiting"),
            status.setupRequired());
    }
}
