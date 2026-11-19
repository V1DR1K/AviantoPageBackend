package ar.com.avianto.landing.catalogo;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admin/catalogos/{tipo:motos|repuestos|trabajos}")
class CatalogoAdminController {
    record Alta(@NotNull JsonNode datos, @NotNull Boolean visible) {}
    record Cambio(@NotNull JsonNode datos, @NotNull Boolean visible, @NotNull @Min(1) Integer version) {}

    private final CatalogoService catalogo;

    CatalogoAdminController(CatalogoService catalogo) { this.catalogo = catalogo; }

    @GetMapping
    ResponseEntity<List<CatalogoService.EntradaAdmin>> listar(@PathVariable String tipo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(catalogo.listar(TipoCatalogo.desde(tipo)));
    }

    @PostMapping
    ResponseEntity<CatalogoService.EntradaAdmin> crear(@PathVariable String tipo, @Valid @RequestBody Alta dto) {
        var entrada = catalogo.crear(TipoCatalogo.desde(tipo), dto.datos(), dto.visible());
        return ResponseEntity.created(URI.create("/api/admin/catalogos/" + tipo + "/" + entrada.id()))
                .cacheControl(CacheControl.noStore()).body(entrada);
    }

    @PutMapping("/{id:[0-9a-z-]{1,100}}")
    ResponseEntity<CatalogoService.EntradaAdmin> modificar(@PathVariable String tipo, @PathVariable String id,
                                                           @Valid @RequestBody Cambio dto) {
        var entrada = catalogo.modificar(TipoCatalogo.desde(tipo), id, dto.datos(), dto.visible(), dto.version());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(entrada);
    }

    @DeleteMapping("/{id:[0-9a-z-]{1,100}}")
    ResponseEntity<Void> archivar(@PathVariable String tipo, @PathVariable String id, @RequestParam @Min(1) int version) {
        catalogo.archivar(TipoCatalogo.desde(tipo), id, version);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
