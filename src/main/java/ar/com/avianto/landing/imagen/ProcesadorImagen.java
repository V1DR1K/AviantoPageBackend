package ar.com.avianto.landing.imagen;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.exif.ExifIFD0Directory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
class ProcesadorImagen {
    private static final int[] VARIANTES = {160, 360, 720, 1440};
    private static final Set<String> FORMATOS = Set.of("jpeg", "jpg", "png", "webp");
    private static final long MAX_PIXELES = 40_000_000L;
    private final String cwebp;

    ProcesadorImagen(@Value("${avianto.cwebp}") String cwebp) {
        this.cwebp = cwebp;
    }

    boolean usaWebp() { return !cwebp.isBlank(); }

    record Preparada(String clave, List<ImagenRepository.Variante> variantes) {}

    Preparada preparar(byte[] archivo) throws IOException {
        var original = leer(archivo);
        int orientacion = orientacion(archivo);
        int ancho = original.getWidth();
        int alto = original.getHeight();
        var variantes = new ArrayList<ImagenRepository.Variante>(VARIANTES.length);
        try {
            for (int limite : VARIANTES) {
                var imagen = redimensionar(original, ancho, alto, limite, orientacion);
                try {
                    boolean webp = usaWebp() && limite > 160;
                    byte[] contenido = webp ? webp(imagen) : jpeg(imagen);
                    variantes.add(new ImagenRepository.Variante(limite, imagen.getWidth(), imagen.getHeight(),
                            webp ? "webp" : "jpg", contenido));
                } finally {
                    imagen.flush();
                }
            }
        } finally {
            original.flush();
        }
        return new Preparada(clave(archivo), List.copyOf(variantes));
    }

    private BufferedImage leer(byte[] archivo) throws IOException {
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(archivo))) {
            var lectores = ImageIO.getImageReaders(stream);
            if (!lectores.hasNext()) throw new IOException("Formato de imagen incompatible");
            ImageReader lector = lectores.next();
            try {
                if (!FORMATOS.contains(lector.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw new IOException("Formato de imagen incompatible");
                }
                lector.setInput(stream, true, true);
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                if (ancho <= 0 || alto <= 0 || (long) ancho * alto > MAX_PIXELES) {
                    throw new IOException("La imagen excede 40 megapíxeles");
                }
                var parametros = lector.getDefaultReadParam();
                int muestreo = Math.max(1, Math.max(ancho, alto) / 1920);
                parametros.setSourceSubsampling(muestreo, muestreo, 0, 0);
                return lector.read(0, parametros);
            } finally {
                lector.dispose();
            }
        }
    }

    private BufferedImage redimensionar(BufferedImage fuente, int ancho, int alto, int limite, int orientacion) {
        boolean girada = orientacion >= 5 && orientacion <= 8;
        double escala = Math.min(1d, (double) limite / Math.max(ancho, alto));
        int w = Math.max(1, (int) Math.round((girada ? alto : ancho) * escala));
        int h = Math.max(1, (int) Math.round((girada ? ancho : alto) * escala));
        var destino = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var grafico = destino.createGraphics();
        try {
            grafico.setColor(Color.WHITE);
            grafico.fillRect(0, 0, w, h);
            grafico.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            grafico.scale((double) w / (girada ? alto : ancho), (double) h / (girada ? ancho : alto));
            grafico.drawImage(fuente, transformacion(ancho, alto, orientacion), null);
        } finally {
            grafico.dispose();
        }
        return destino;
    }

    private AffineTransform transformacion(int ancho, int alto, int orientacion) {
        return switch (orientacion) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, ancho, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, ancho, alto);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, alto);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, alto, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, alto, ancho);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, ancho);
            default -> new AffineTransform();
        };
    }

    private int orientacion(byte[] archivo) {
        try {
            var metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(archivo));
            var exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            var valor = exif == null ? null : exif.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            return valor == null ? 1 : valor;
        } catch (IOException | ImageProcessingException ex) {
            return 1;
        }
    }

    private byte[] webp(BufferedImage imagen) throws IOException {
        var directorio = Files.createTempDirectory("avianto-webp-");
        var entrada = directorio.resolve("imagen.png");
        var salida = directorio.resolve("imagen.webp");
        Process conversor = null;
        try {
            ImageIO.write(imagen, "png", entrada.toFile());
            conversor = new ProcessBuilder(cwebp, "-quiet", "-q", "76", "-metadata", "none",
                    entrada.toString(), "-o", salida.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!conversor.waitFor(8, TimeUnit.SECONDS) || conversor.exitValue() != 0) {
                throw new IOException("Falló la codificación WebP");
            }
            return Files.readAllBytes(salida);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Se interrumpió la codificación WebP", ex);
        } finally {
            if (conversor != null && conversor.isAlive()) conversor.destroyForcibly();
            Files.deleteIfExists(salida);
            Files.deleteIfExists(entrada);
            Files.deleteIfExists(directorio);
        }
    }

    private byte[] jpeg(BufferedImage imagen) throws IOException {
        var escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        var bytes = new ByteArrayOutputStream();
        try (var stream = new MemoryCacheImageOutputStream(bytes)) {
            escritor.setOutput(stream);
            var parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(0.82f);
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
            stream.flush();
        } finally {
            escritor.dispose();
        }
        return bytes.toByteArray();
    }

    private String clave(byte[] archivo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archivo));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
