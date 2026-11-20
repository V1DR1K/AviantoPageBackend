package ar.com.avianto.landing.catalogo;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

@RestController
class CatalogoPublicoController {
    private static final CacheControl CACHE_PUBLICA = CacheControl.maxAge(Duration.ofSeconds(15))
            .cachePublic().sMaxAge(Duration.ofSeconds(30));
    private final CatalogoService catalogo;

    record Listado(List<ObjectNode> items, int total) {}

    CatalogoPublicoController(CatalogoService catalogo) { this.catalogo = catalogo; }

    @GetMapping("/api/catalogo/{tipo:motos|repuestos|trabajos}")
    ResponseEntity<Listado> listar(@PathVariable String tipo) {
        var snapshot = catalogo.publicar(TipoCatalogo.desde(tipo));
        return cacheable(tipo, snapshot.revision(), "listado", new Listado(snapshot.items(), snapshot.items().size()));
    }

    @GetMapping("/api/catalogo/motos/{id:[0-9a-z-]{1,100}}")
    ResponseEntity<ObjectNode> moto(@PathVariable String id) {
        var snapshot = catalogo.publicar(TipoCatalogo.MOTOS);
        ObjectNode moto = snapshot.porId().get(id);
        if (moto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La publicación ya no está disponible.");
        }
        return cacheable("motos", snapshot.revision(), id, moto);
    }

    @PostMapping("/api/catalogo/motos/{id:[0-9a-z-]{1,100}}/vistas")
    ResponseEntity<Long> registrarVista(@PathVariable String id) {
        long vistas = catalogo.registrarVistaMoto(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(vistas);
    }

    private <T> ResponseEntity<T> cacheable(String tipo, long revision, String recurso, T body) {
        String etag = "W/\"catalogo-v1-" + tipo + "-" + revision + "-" + recurso + "\"";
        return ResponseEntity.ok().cacheControl(CACHE_PUBLICA).eTag(etag).body(body);
    }
}
