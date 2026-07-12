package br.com.schf.migration.api;

import br.com.schf.migration.application.MigrationApplicationService;
import br.com.schf.migration.validation.BundleValidationReport;
import br.com.schf.security.principal.AuthenticatedUserPrincipal;
import br.com.schf.migration.validation.BundleValidationException;
import br.com.schf.migration.validation.ValidationIssue;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/migrations")
public class MigrationController {
    private final MigrationApplicationService service;

    public MigrationController(MigrationApplicationService service) { this.service = service; }

    @PostMapping("/validate")
    public BundleValidationReport validate(@RequestParam("file") MultipartFile file) {
        return service.validate(file.getOriginalFilename(), bytes(file));
    }

    @PostMapping("/dry-run")
    public MigrationJobResponse dryRun(@RequestParam("file") MultipartFile file,
                                       @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return service.run(file.getOriginalFilename(), bytes(file), true, principal);
    }

    @PostMapping("/import")
    public MigrationJobResponse importBundle(@RequestParam("file") MultipartFile file,
                                             @AuthenticationPrincipal AuthenticatedUserPrincipal principal) {
        return service.run(file.getOriginalFilename(), bytes(file), false, principal);
    }

    @GetMapping
    public List<MigrationJobResponse> findAll() { return service.findAll(); }

    @GetMapping("/{id}")
    public MigrationJobResponse findById(@PathVariable UUID id) { return service.findById(id); }

    @GetMapping("/{id}/errors")
    public List<MigrationErrorResponse> errors(@PathVariable UUID id) { return service.errors(id); }

    @GetMapping("/{id}/report")
    public MigrationReportResponse report(@PathVariable UUID id) { return service.report(id); }

    private byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BundleValidationException(List.of(new ValidationIssue(
                "UPLOAD_IO_ERROR", null, null, "Migration upload could not be read")));
        }
    }
}
