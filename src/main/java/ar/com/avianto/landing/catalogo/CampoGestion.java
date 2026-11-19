package ar.com.avianto.landing.catalogo;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

import static ar.com.avianto.landing.catalogo.TipoCatalogo.*;

enum CampoGestion {
    MARCA("marcas", 72, false, java.util.Map.of(MOTOS,"marcaNombre", REPUESTOS,"marca", TRABAJOS,"marca")),
    TIPO("tipos", 48, false, java.util.Map.of(MOTOS,"tipo")),
    COLOR("colores", 40, false, java.util.Map.of(MOTOS,"color")),
    PAGO("pagos", 80, true, java.util.Map.of(MOTOS,"condiciones,pagos")),
    FINANCIACION("financiaciones", 140, true, java.util.Map.of(MOTOS,"condiciones,financiacion")),
    CATEGORIA_REPUESTO("categorias-repuestos", 72, false, java.util.Map.of(REPUESTOS,"categoria")),
    COMPATIBILIDAD("compatibilidades", 140, false, java.util.Map.of(REPUESTOS,"compatibilidad")),
    CATEGORIA_TRABAJO("categorias-trabajos", 72, false, java.util.Map.of(TRABAJOS,"categoria")),
    INTERVENCION("intervenciones", 80, false, java.util.Map.of(TRABAJOS,"tipo"));

    final String tipo;
    final java.util.Map<TipoCatalogo, String> rutas;
    final int maximo;
    final boolean multiple;

    CampoGestion(String tipo, int maximo, boolean multiple, java.util.Map<TipoCatalogo, String> rutas) {
        this.tipo = tipo;
        this.rutas = rutas;
        this.maximo = maximo;
        this.multiple = multiple;
    }

    String path(TipoCatalogo catalogo) { return "'{" + rutas.get(catalogo) + "}'"; }

    static CampoGestion desde(String tipo) {
        return Arrays.stream(values()).filter(campo -> campo.tipo.equals(tipo)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe ese catálogo de gestión."));
    }
}
