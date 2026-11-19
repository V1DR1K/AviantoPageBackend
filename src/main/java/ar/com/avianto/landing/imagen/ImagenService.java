package ar.com.avianto.landing.imagen;

import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ImagenService {
    public record Fuente(String url, int ancho, int alto) {}
    public record Imagen(String clave, List<Fuente> variantes) {
        public String miniatura() { return variantes.getFirst().url(); }
    }

    private final ImagenRepository repositorio;
    private final ProcesadorImagen procesador;
    private final TransactionTemplate transaccion;
    private final ThreadPoolExecutor procesamiento = new ThreadPoolExecutor(
            2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10), Thread.ofPlatform().name("miniaturas-", 0).factory());

    ImagenService(ImagenRepository repositorio, ProcesadorImagen procesador, TransactionTemplate transaccion) {
        this.repositorio = repositorio;
        this.procesador = procesador;
        this.transaccion = transaccion;
    }

    public List<Imagen> cargar(List<MultipartFile> archivos) {
        if (archivos.isEmpty() || archivos.size() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elegí entre una y cinco imágenes.");
        }
        var tareas = new ArrayList<CompletableFuture<ProcesadorImagen.Preparada>>(archivos.size());
        try {
            for (var archivo : archivos) {
                tareas.add(CompletableFuture.supplyAsync(() -> preparar(archivo), procesamiento));
            }
        } catch (RejectedExecutionException ex) {
            CompletableFuture.allOf(tareas.toArray(CompletableFuture[]::new)).handle((ok, error) -> null).join();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Hay varias imágenes en proceso. Volvé a intentar.");
        }

        try {
            CompletableFuture.allOf(tareas.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof ResponseStatusException respuesta) throw respuesta;
            throw ex;
        }
        var preparadas = tareas.stream().map(CompletableFuture::join).toList();
        return transaccion.execute(status -> {
            // Orden constante para que dos cargas con las mismas imágenes no bloqueen la transacción mutuamente.
            preparadas.stream().sorted(java.util.Comparator.comparing(ProcesadorImagen.Preparada::clave))
                    .forEach(carga -> repositorio.insertar(carga.clave(), carga.variantes()));
            var guardadas = resumenes(preparadas.stream().map(ProcesadorImagen.Preparada::clave).distinct().toList());
            return preparadas.stream().map(carga -> guardadas.get(carga.clave())).toList();
        });
    }

    private ProcesadorImagen.Preparada preparar(MultipartFile archivo) {
        try {
            return procesador.preparar(archivo.getBytes());
        } catch (IOException | IllegalArgumentException | IndexOutOfBoundsException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No se pudo leer una foto. Usá JPEG, PNG o WebP de hasta 40 megapíxeles.");
        }
    }

    public String cargarEjemplo(byte[] archivo) throws IOException {
        var preparado = procesador.preparar(archivo);
        repositorio.insertar(preparado.clave(), preparado.variantes());
        return preparado.clave();
    }

    public boolean usaWebp() { return procesador.usaWebp(); }

    public Map<String, Imagen> resumenes(Collection<String> claves) {
        return repositorio.dimensiones(claves).stream().collect(Collectors.groupingBy(
                ImagenRepository.Dimension::clave, Collectors.collectingAndThen(Collectors.toList(), dimensiones ->
                        new Imagen(dimensiones.getFirst().clave(), dimensiones.stream()
                                .map(v -> new Fuente(url(v.clave(), v.variante(), v.formato()), v.ancho(), v.alto())).toList()))));
    }

    public byte[] leer(String clave, int variante, String formato) {
        return repositorio.leer(clave, variante, formato).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa imagen."));
    }

    private String url(String clave, int variante, String formato) {
        return "/media/" + clave + "/" + variante + "." + formato;
    }

    @PreDestroy
    void detener() {
        procesamiento.shutdown();
    }
}
