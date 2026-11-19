package ar.com.avianto.landing.catalogo;

import ar.com.avianto.landing.imagen.ImagenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
class CatalogoService {
    record EntradaAdmin(String id, ObjectNode datos, boolean visible, int version, OffsetDateTime actualizada) {}
    record CatalogoPublico(long revision, List<ObjectNode> items, Map<String, ObjectNode> porId) {}
    record CampoInvalido(String campo, String mensaje) {}

    static class DatosInvalidos extends RuntimeException {
        private final List<CampoInvalido> errores;

        DatosInvalidos(String mensaje, List<CampoInvalido> errores) {
            super(mensaje);
            this.errores = errores;
        }

        List<CampoInvalido> errores() { return errores; }
    }

    private final CatalogoRepository repositorio;
    private final ObjectMapper json;
    private final Validator validator;
    private final ImagenService imagenes;
    private final GestionService gestion;
    private final Map<TipoCatalogo, CatalogoPublico> snapshots = new ConcurrentHashMap<>();

    CatalogoService(CatalogoRepository repositorio, ObjectMapper json, Validator validator, ImagenService imagenes, GestionService gestion) {
        this.repositorio = repositorio;
        this.json = json;
        this.validator = validator;
        this.imagenes = imagenes;
        this.gestion = gestion;
    }

    @Transactional(readOnly = true)
    List<EntradaAdmin> listar(TipoCatalogo tipo) {
        return repositorio.listar(tipo, false).stream().map(this::admin).toList();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    CatalogoPublico publicar(TipoCatalogo tipo) {
        long revision = repositorio.revision(tipo);
        var cache = snapshots.get(tipo);
        if (cache != null && cache.revision() == revision) return cache;
        // Mantiene una sola instantánea por tipo y evita que una lectura antigua reemplace una nueva.
        var entradas = repositorio.listar(tipo, true);
        var claves = entradas.stream().flatMap(entrada -> java.util.stream.StreamSupport.stream(
                        entrada.datos().path("imagenes").spliterator(), false))
                .map(ref -> ref.path("clave").asText()).distinct().toList();
        var medios = imagenes.resumenes(claves);
        var items = entradas.stream().map(entrada -> documento(entrada, tipo, medios)).toList();
        var indice = items.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(n -> n.path("id").asText(), n -> n));
        var snapshot = new CatalogoPublico(revision, items, indice);
        snapshots.compute(tipo, (k, anterior) -> anterior == null || anterior.revision() < revision ? snapshot : anterior);
        return snapshot;
    }

    @Transactional
    EntradaAdmin crear(TipoCatalogo tipo, JsonNode datos, boolean visible) {
        String id = UUID.randomUUID().toString();
        repositorio.insertar(tipo, id, normalizar(tipo, datos), visible);
        repositorio.incrementarRevision(tipo);
        return admin(buscar(tipo, id));
    }

    @Transactional
    EntradaAdmin modificar(TipoCatalogo tipo, String id, JsonNode datos, boolean visible, int version) {
        if (!repositorio.actualizar(tipo, id, normalizar(tipo, datos), visible, version)) {
            throw conflicto(tipo, id);
        }
        repositorio.incrementarRevision(tipo);
        return admin(buscar(tipo, id));
    }

    @Transactional
    void archivar(TipoCatalogo tipo, String id, int version) {
        if (!repositorio.archivar(tipo, id, version)) throw conflicto(tipo, id);
        repositorio.incrementarRevision(tipo);
    }

    void insertar(TipoCatalogo tipo, String id, JsonNode datos, boolean visible) {
        var inicial = (ObjectNode) datos.deepCopy();
        gestion.incorporarInicial(tipo, inicial);
        repositorio.insertar(tipo, id, normalizar(tipo, inicial), visible);
    }

    private CatalogoRepository.Entrada buscar(TipoCatalogo tipo, String id) {
        return repositorio.buscar(tipo, id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa publicación."));
    }

    private ResponseStatusException conflicto(TipoCatalogo tipo, String id) {
        buscar(tipo, id);
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "La publicación cambió desde que la abriste. Recargá la lista y revisá los cambios.");
    }

    private ObjectNode normalizar(TipoCatalogo tipo, JsonNode datos) {
        if (datos == null || !datos.isObject()) {
            throw new DatosInvalidos("Completá los datos de la publicación.", List.of());
        }

        DatosCatalogo entrada;
        try {
            entrada = json.treeToValue(datos, tipo.formato());
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException ex) {
            throw new DatosInvalidos("Hay campos con formato incorrecto.", List.of());
        }
        var errores = validator.validate(entrada).stream()
                .map(error -> new CampoInvalido(error.getPropertyPath().toString(), error.getMessage()))
                .sorted(java.util.Comparator.comparing(CampoInvalido::campo)).toList();
        if (!errores.isEmpty()) {
            throw new DatosInvalidos("Revisá los campos de la publicación.", errores);
        }
        var claves = entrada.imagenes().stream().map(DatosCatalogo.Foto::clave).distinct().toList();
        if (claves.size() != entrada.imagenes().size() || imagenes.resumenes(claves).size() != claves.size()) {
            throw new DatosInvalidos("La publicación tiene imágenes repetidas o que todavía no se cargaron.",
                    List.of(new CampoInvalido("imagenes", "Revisá las fotos elegidas.")));
        }
        ObjectNode documento = json.valueToTree(entrada);
        gestion.comprobar(tipo, documento);
        return documento;
    }

    private ObjectNode documento(CatalogoRepository.Entrada entrada, TipoCatalogo tipo,
                                 Map<String, ImagenService.Imagen> medios) {
        var datos = entrada.datos().deepCopy();
        datos.put("id", entrada.id());
        datos.put("publicada", entrada.creada().toInstant().toString());
        var refs = datos.remove("imagenes");
        var fotos = datos.putArray("imagenes");
        for (JsonNode ref : refs) {
            var imagen = medios.get(ref.path("clave").asText());
            if (imagen == null) continue;
            var salida = fotos.addObject();
            salida.put("clave", imagen.clave());
            salida.put("alt", ref.path("alt").asText(""));
            salida.set("variantes", json.valueToTree(imagen.variantes()));
        }
        if (tipo == TipoCatalogo.MOTOS) {
            datos.put("marca", Normalizer.normalize(datos.path("marcaNombre").asText(), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT));
        }
        return datos;
    }

    private EntradaAdmin admin(CatalogoRepository.Entrada entrada) {
        return new EntradaAdmin(entrada.id(), entrada.datos(), entrada.visible(), entrada.version(), entrada.actualizada());
    }
}
