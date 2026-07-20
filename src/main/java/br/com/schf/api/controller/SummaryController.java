package br.com.schf.api.controller;

import br.com.schf.api.dto.SummaryResponse;
import br.com.schf.api.service.UatReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/summary")
public class SummaryController {

    private final UatReadService uatReadService;

    public SummaryController(UatReadService uatReadService) {
        this.uatReadService = uatReadService;
    }

    @GetMapping
    public SummaryResponse getSummary() {
        return uatReadService.getSummary();
    }
}