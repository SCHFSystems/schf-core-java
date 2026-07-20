package br.com.schf.api.controller;

import br.com.schf.api.dto.UnresolvedCounterpartyResponse;
import br.com.schf.api.service.UatReadService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/unresolved-legacy-references")
public class UnresolvedCounterpartyController {

    private final UatReadService uatReadService;

    public UnresolvedCounterpartyController(UatReadService uatReadService) {
        this.uatReadService = uatReadService;
    }

    @GetMapping
    public List<UnresolvedCounterpartyResponse> getAll() {
        return uatReadService.findUnresolvedCounterparties();
    }
}