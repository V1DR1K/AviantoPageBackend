package ar.com.avianto.landing.catalogo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
class GestionService {
    private final GestionRepository opciones;
    private final CatalogoRepository catalogo;

    GestionService(GestionRepository opciones, CatalogoRepository catalogo) {
        this.opciones = opciones;
        this.catalogo = catalogo;
    }

    @Transactional(readOnly = true)
    List<GestionRepository.Opcion> listar() { return opciones.listar(); }

    @Transactional
    GestionRepository.Opcion crear(CampoGestion campo, String nombre) {
        opciones.bloquear(true);
        try { return opciones.crear(campo, validar(campo, nombre)); }
        catch (DuplicateKeyException ex) { throw duplicada(); }
    }

    @Transactional
    GestionRepository.Opcion editar(CampoGestion campo, long id, String nombre) {
        opciones.bloquear(true);
        var anterior = buscar(campo, id);
        try {
            var actual = opciones.editar(anterior, validar(campo, nombre));
            if (!anterior.nombre().equals(actual.nombre())) {
                for (var tipo : campo.rutas.keySet().stream().sorted().toList()) {
                    opciones.renombrarReferencias(campo, tipo, anterior.nombre(), actual.nombre());
                    catalogo.incrementarRevision(tipo);
                }
            }
            return actual;
        } catch (DuplicateKeyException ex) { throw duplicada(); }
    }

    @Transactional
    void eliminar(CampoGestion campo, long id) {
        opciones.bloquear(true);
        var opcion = buscar(campo, id);
        if (campo.rutas.keySet().stream().anyMatch(tipo -> opciones.enUso(campo, tipo, opcion.nombre()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La opción está asignada a una publicación. Cambiá esa asignación antes de eliminarla.");
        }
        opciones.eliminar(opcion);
    }

    void comprobar(TipoCatalogo tipo, ObjectNode datos) {
        opciones.bloquear(false);
        aplicar(tipo, datos, opciones.listar(), false);
    }

    void incorporarInicial(TipoCatalogo tipo, ObjectNode datos) {
        opciones.bloquear(true);
        aplicar(tipo, datos, opciones.listar(), true);
    }

    private void aplicar(TipoCatalogo tipo, ObjectNode datos, List<GestionRepository.Opcion> lista, boolean inicial) {
        for (var campo : CampoGestion.values()) {
            String path = campo.rutas.get(tipo);
            if (path == null) continue;
            String[] ruta = path.split(",");
            if (!campo.multiple) {
                String nombre = datos.path(ruta[0]).asText("").strip();
                if (!nombre.isEmpty()) datos.put(ruta[0], canonica(campo, path, nombre, lista, inicial));
            } else if (datos.path(ruta[0]) instanceof ObjectNode condiciones) {
                var valores = condiciones.path(ruta[1]);
                if (!valores.isArray()) continue;
                var nombres = new java.util.LinkedHashSet<String>();
                for (JsonNode valor : valores) nombres.add(canonica(campo, path, valor.asText(), lista, inicial));
                var destino = condiciones.putArray(ruta[1]);
                nombres.forEach(destino::add);
            }
        }
    }

    private String canonica(CampoGestion campo, String path, String nombre, List<GestionRepository.Opcion> lista, boolean inicial) {
        String normal = nombre.strip();
        return lista.stream().filter(o -> o.tipo().equals(campo.tipo) && o.nombre().equalsIgnoreCase(normal))
                .map(GestionRepository.Opcion::nombre).findFirst().orElseGet(() -> {
                    if (inicial) return opciones.crear(campo, validar(campo, normal)).nombre();
                    throw new CatalogoService.DatosInvalidos("Elegí una opción vigente de Gestión.",
                            List.of(new CatalogoService.CampoInvalido(path.replace(',', '.'), "Revisá las opciones disponibles.")));
                });
    }

    private GestionRepository.Opcion buscar(CampoGestion campo, long id) {
        return opciones.buscar(campo, id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "La opción ya no está disponible."));
    }

    private String validar(CampoGestion campo, String nombre) {
        String limpio = nombre.strip();
        if (limpio.isEmpty() || limpio.length() > campo.maximo) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ingresá un nombre de hasta " + campo.maximo + " caracteres.");
        }
        return limpio;
    }

    private ResponseStatusException duplicada() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una opción con ese nombre.");
    }
}
