package ar.com.avianto.landing.catalogo;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum TipoCatalogo {
    MOTOS("motos", DatosCatalogo.Moto.class),
    REPUESTOS("repuestos", DatosCatalogo.Repuesto.class),
    TRABAJOS("trabajos", DatosCatalogo.Trabajo.class);

    private final String ruta;
    private final Class<? extends DatosCatalogo> formato;

    TipoCatalogo(String ruta, Class<? extends DatosCatalogo> formato) {
        this.ruta = ruta;
        this.formato = formato;
    }

    public String ruta() { return ruta; }
    public Class<? extends DatosCatalogo> formato() { return formato; }

    public static TipoCatalogo desde(String ruta) {
        return switch (ruta) {
            case "motos" -> MOTOS;
            case "repuestos" -> REPUESTOS;
            case "trabajos" -> TRABAJOS;
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe ese catálogo.");
        };
    }
}
