package ar.com.avianto.landing.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
class SesionController {
    record Sesion(String usuario, boolean activacion, String encabezadoCsrf, String csrf) {}
    record CambioClave(@NotBlank @Size(max = 72) String actual,
                       @NotBlank @Size(min = 12, max = 72) String nueva,
                       @Size(max = 160) String codigo) {}

    private final CuentaService cuentas;

    SesionController(CuentaService cuentas) { this.cuentas = cuentas; }

    @GetMapping({"/admin", "/admin/"})
    String panel() { return "forward:/admin/index.html"; }

    @GetMapping("/api/admin/sesion")
    @ResponseBody
    ResponseEntity<Sesion> sesion(Authentication auth, CsrfToken token) {
        boolean activa = auth != null && !(auth instanceof AnonymousAuthenticationToken);
        boolean activacion = activa && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SETUP"));
        var salida = new Sesion(activa ? auth.getName() : null, activacion, token.getHeaderName(), token.getToken());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(salida);
    }

    @PostMapping("/api/admin/clave")
    @ResponseBody
    ResponseEntity<Void> cambiar(@Valid @RequestBody CambioClave cambio, Authentication auth,
                                HttpServletRequest request, HttpServletResponse response) {
        cuentas.cambiar(auth.getName(), cambio.actual(), cambio.nueva(), cambio.codigo());
        new SecurityContextLogoutHandler().logout(request, response, auth);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
