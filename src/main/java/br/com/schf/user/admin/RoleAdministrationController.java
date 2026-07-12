package br.com.schf.user.admin;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/roles")
public class RoleAdministrationController {

    private final UserAdministrationService service;

    public RoleAdministrationController(UserAdministrationService service) {
        this.service = service;
    }

    @GetMapping
    public List<RoleResponse> findAll() {
        return service.findRoles();
    }
}
