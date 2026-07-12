package br.com.schf.user.admin;

import br.com.schf.security.auth.ClientRequestInfo;
import br.com.schf.security.principal.AuthenticatedUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdministrationController {

    private final UserAdministrationService service;

    public UserAdministrationController(UserAdministrationService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminUserResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public AdminUserResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse create(@Valid @RequestBody AdminUserCreateRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}")
    public AdminUserResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody AdminUserUpdateRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/activate")
    public AdminUserResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    @PostMapping("/{id}/deactivate")
    public AdminUserResponse deactivate(@PathVariable UUID id,
                                        @AuthenticationPrincipal AuthenticatedUserPrincipal actor) {
        return service.deactivate(id, actor);
    }

    @PostMapping("/{id}/roles")
    public AdminUserResponse assignRole(@PathVariable UUID id,
                                        @Valid @RequestBody RoleAssignmentRequest request) {
        return service.assignRole(id, request.roleId());
    }

    @DeleteMapping("/{id}/roles/{roleId}")
    public AdminUserResponse removeRole(@PathVariable UUID id, @PathVariable UUID roleId) {
        return service.removeRole(id, roleId);
    }

    @PostMapping("/{id}/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@PathVariable UUID id, HttpServletRequest request) {
        service.requestPasswordReset(id,
            new ClientRequestInfo(request.getRemoteAddr(), request.getHeader("User-Agent")));
    }
}
