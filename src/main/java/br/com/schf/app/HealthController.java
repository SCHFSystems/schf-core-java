package br.com.schf.app;

import br.com.schf.shared.ApiMetadata;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public ApiMetadata health() {
        return new ApiMetadata("ok", "SCHF", "java-v2", Instant.now());
    }
}
