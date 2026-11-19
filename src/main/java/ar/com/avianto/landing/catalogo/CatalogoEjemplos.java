package ar.com.avianto.landing.catalogo;

import ar.com.avianto.landing.imagen.ImagenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashMap;

@Component
@ConditionalOnProperty(name = "avianto.cargar-ejemplos", havingValue = "true")
class CatalogoEjemplos implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(CatalogoEjemplos.class);

    private final ObjectMapper json;
    private final CatalogoService catalogo;
    private final CatalogoRepository repo;
    private final ImagenService imagenes;

    CatalogoEjemplos(ObjectMapper json, CatalogoService catalogo, CatalogoRepository repo, ImagenService imagenes) {
        this.json = json;
        this.catalogo = catalogo;
        this.repo = repo;
        this.imagenes = imagenes;
    }

    @Override
    @Transactional(rollbackFor = IOException.class)
    public void run(ApplicationArguments args) throws IOException {
        boolean cargarPublicaciones = repo.iniciarCarga("ejemplos-v1");
        boolean convertirFotos = imagenes.usaWebp() && repo.iniciarCarga("ejemplos-webp-v2");
        if (!cargarPublicaciones && !convertirFotos) return;
        JsonNode ejemplos;
        try (var fuente = new ClassPathResource("ejemplos/catalogos.json").getInputStream()) {
            ejemplos = json.readTree(fuente);
        }
        var claves = new HashMap<String, String>();
        for (JsonNode foto : ejemplos.path("fotos")) {
            String nombre = foto.asText();
            try (var fuente = new ClassPathResource("ejemplos/" + nombre).getInputStream()) {
                claves.put(nombre, imagenes.cargarEjemplo(fuente.readAllBytes()));
            }
        }
        for (var tipo : TipoCatalogo.values()) {
            repo.incrementarRevision(tipo);
            if (!cargarPublicaciones) continue;
            var entradas = ejemplos.path(tipo.ruta());
            for (JsonNode entrada : entradas) {
                var datos = (ObjectNode) entrada.path("datos");
                for (JsonNode ref : datos.path("imagenes")) {
                    var foto = (ObjectNode) ref;
                    String clave = claves.get(foto.remove("archivo").asText());
                    foto.put("clave", clave);
                }
                catalogo.insertar(tipo, entrada.path("id").asText(), datos, true);
            }
            LOG.info("Carga inicial de {}: {} ejemplos", tipo.ruta(), entradas.size());
        }
    }
}
