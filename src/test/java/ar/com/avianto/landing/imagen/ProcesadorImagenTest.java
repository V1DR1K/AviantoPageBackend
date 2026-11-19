package ar.com.avianto.landing.imagen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcesadorImagenTest {
    private final ProcesadorImagen procesador = new ProcesadorImagen("");

    @Test
    void generaMiniaturasYConservaProporcionSinAmpliarLaFoto() throws IOException {
        byte[] origen;
        try (var entrada = getClass().getResourceAsStream("/ejemplos/r1-roja-banco.webp")) {
            origen = entrada.readAllBytes();
        }

        var resultado = procesador.preparar(origen);

        assertThat(resultado.clave()).matches("[a-f0-9]{64}");
        assertThat(resultado.variantes()).extracting(ImagenRepository.Variante::nombre)
                .containsExactly(160, 360, 720, 1440);
        assertThat(resultado.variantes().getFirst().contenido().length)
                .isLessThan(resultado.variantes().getLast().contenido().length);
        for (var variante : resultado.variantes()) {
            var imagen = ImageIO.read(new ByteArrayInputStream(variante.contenido()));
            assertThat(imagen.getWidth()).isEqualTo(variante.ancho());
            assertThat(imagen.getHeight()).isEqualTo(variante.alto());
            assertThat(imagen.getHeight()).isLessThanOrEqualTo(variante.nombre()).isLessThanOrEqualTo(779);
            assertThat(imagen.getWidth()).isLessThan(imagen.getHeight());
        }
    }

    @Test
    void rechazaUnArchivoQueNoEsUnaImagen() {
        assertThatThrownBy(() -> procesador.preparar("archivo sin imagen".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(IOException.class);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AVIANTO_CWEBP", matches = ".+")
    void codificaVariantesWebpYConservaLaMiniaturaJpeg() throws IOException {
        byte[] origen;
        try (var entrada = getClass().getResourceAsStream("/ejemplos/r3-azul-lateral.webp")) {
            origen = entrada.readAllBytes();
        }
        var alternativas = new ProcesadorImagen(System.getenv("AVIANTO_CWEBP")).preparar(origen).variantes();
        assertThat(alternativas).extracting(ImagenRepository.Variante::formato)
                .containsExactly("jpg", "webp", "webp", "webp");
        var mediana = alternativas.get(2);
        var imagen = ImageIO.read(new ByteArrayInputStream(mediana.contenido()));
        assertThat(imagen.getHeight()).isEqualTo(720);
        assertThat(mediana.contenido().length).isLessThan(origen.length);
    }
}
