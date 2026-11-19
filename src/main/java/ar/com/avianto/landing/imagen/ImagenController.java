package ar.com.avianto.landing.imagen;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

@RestController
class ImagenController {
    private static final CacheControl CACHE_IMAGEN = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();
    private final ImagenService imagenes;

    ImagenController(ImagenService imagenes) { this.imagenes = imagenes; }

    @PostMapping(value = "/api/admin/imagenes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<List<ImagenService.Imagen>> cargar(@RequestParam("archivos") List<MultipartFile> archivos) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(imagenes.cargar(archivos));
    }

    @GetMapping("/media/{clave:[a-f0-9]{64}}/{variante:160|360|720|1440}.{formato:jpg|webp}")
    ResponseEntity<byte[]> leer(@PathVariable String clave, @PathVariable int variante, @PathVariable String formato,
                                WebRequest request) {
        String etag = formato.equals("jpg") ? "\"" + clave + "-" + variante + "\""
                : "\"" + clave + "-" + variante + "-webp\"";
        // Recupera la variante existente antes de declarar que el recurso sigue vigente.
        var contenido = imagenes.leer(clave, variante, formato);
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(304).eTag(etag).cacheControl(CACHE_IMAGEN).build();
        }
        MediaType tipo = formato.equals("webp") ? MediaType.valueOf("image/webp") : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().cacheControl(CACHE_IMAGEN).eTag(etag).contentType(tipo).body(contenido);
    }
}
