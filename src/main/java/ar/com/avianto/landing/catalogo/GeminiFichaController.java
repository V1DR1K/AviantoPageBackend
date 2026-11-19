package ar.com.avianto.landing.catalogo;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GeminiFichaController {
    private final GeminiFichaService gemini;

    GeminiFichaController(GeminiFichaService gemini) { this.gemini = gemini; }

    @PostMapping("/api/admin/ficha-tecnica/gemini")
    ObjectNode consultar(@Valid @RequestBody GeminiFichaService.Identificacion datos) {
        return gemini.consultar(datos);
    }
}
