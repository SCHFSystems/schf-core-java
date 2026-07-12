package br.com.schf.audit;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

    private final AuditLogQueryService service;

    public AuditLogController(AuditLogQueryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AuditLogResponse> find(
        @RequestParam(required = false) String action,
        @RequestParam(required = false) UUID actor,
        @RequestParam(required = false) String resourceType,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return service.find(action, actor, resourceType, from, to, page, size);
    }
}
