package br.com.schf.api.controller;

import br.com.schf.api.dto.WarningReportResponse;
import br.com.schf.api.service.UatReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final UatReadService uatReadService;

    public ReportController(UatReadService uatReadService) {
        this.uatReadService = uatReadService;
    }

    @GetMapping
    public WarningReportResponse getWarnings() {
        return uatReadService.getWarningReport();
    }
}