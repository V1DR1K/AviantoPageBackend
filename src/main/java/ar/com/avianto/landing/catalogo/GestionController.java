package ar.com.avianto.landing.catalogo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/gestion")
class GestionController {
    record Cambio(@NotBlank @Size(max = 140) String nombre) {}
    private final GestionService gestion;

    GestionController(GestionService gestion) { this.gestion = gestion; }

    @GetMapping
    List<GestionRepository.Opcion> listar() { return gestion.listar(); }

    @PostMapping("/{tipo}")
    ResponseEntity<GestionRepository.Opcion> crear(@PathVariable String tipo, @Valid @RequestBody Cambio dto) {
        return ResponseEntity.status(201).body(gestion.crear(CampoGestion.desde(tipo), dto.nombre()));
    }

    @PutMapping("/{tipo}/{id}")
    GestionRepository.Opcion editar(@PathVariable String tipo, @PathVariable long id, @Valid @RequestBody Cambio dto) {
        return gestion.editar(CampoGestion.desde(tipo), id, dto.nombre());
    }

    @DeleteMapping("/{tipo}/{id}")
    ResponseEntity<Void> eliminar(@PathVariable String tipo, @PathVariable long id) {
        gestion.eliminar(CampoGestion.desde(tipo), id);
        return ResponseEntity.noContent().build();
    }
}
