package ar.com.avianto.landing.catalogo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

sealed interface DatosCatalogo permits DatosCatalogo.Moto, DatosCatalogo.Repuesto, DatosCatalogo.Trabajo {
    List<Foto> imagenes();

    record Foto(
            @NotNull @Pattern(regexp = "[a-f0-9]{64}") String clave,
            @Size(max = 160) String alt) {}

    record Ficha(
            @Min(1) @Max(10000) int cc,
            @Min(0) @Max(16) int cilindros,
            @Size(max = 80) String motor,
            @Min(0) @Max(2000) int hp,
            @Min(0) @Max(2000) int nm,
            @Min(0) @Max(2000) int peso,
            @Min(0) @Max(200) int tanque,
            @Min(0) @Max(20) int marchas) {}

    record Condiciones(
            @Size(max = 10) List<@NotBlank @Size(max = 80) String> pagos,
            @Size(max = 10) List<@NotBlank @Size(max = 140) String> financiacion,
            @Size(max = 140) String permuta,
            @Size(max = 500) String notas) {}

    record Importe(
            @NotNull @Min(0) @Max(999999999999L) BigDecimal valor,
            @NotNull @Pattern(regexp = "ARS|USD") String moneda) {}

    record Moto(
            @NotBlank @Size(max = 72) String marcaNombre,
            @NotBlank @Size(max = 90) String modelo,
            @NotBlank @Size(max = 48) String tipo,
            @Min(1885) @Max(2100) int anio,
            @Min(0) @Max(9999999) int km,
            @NotBlank @Size(max = 40) String color,
            @NotNull @Pattern(regexp = "0 km|Usada") String estado,
            @NotNull @Pattern(regexp = "Disponible|Reservada|Vendida") String disponibilidad,
            @Min(0) @Max(999999999999L) BigDecimal precio,
            @NotNull @Pattern(regexp = "ARS|USD") String moneda,
            boolean destacada,
            boolean revisadaEnBanco,
            @NotNull @Valid Ficha ficha,
            @Valid Condiciones condiciones,
            @NotNull @Size(max = 5) List<@NotNull @Valid Foto> imagenes) implements DatosCatalogo {}

    record Repuesto(
            @NotBlank @Size(max = 96) String nombre,
            @NotBlank @Size(max = 48) String codigo,
            @Size(max = 72) String marca,
            @NotBlank @Size(max = 140) String compatibilidad,
            @NotBlank @Size(max = 72) String categoria,
            @NotNull @Pattern(regexp = "Repuesto|Accesorio") String tipo,
            @NotBlank @Size(max = 500) String descripcion,
            @NotNull @Pattern(regexp = "stock|encargo") String disponibilidad,
            @Valid Importe precio,
            @NotNull @Size(max = 1) List<@NotNull @Valid Foto> imagenes) implements DatosCatalogo {}

    record Trabajo(
            @NotBlank @Size(max = 100) String titulo,
            @NotBlank @Size(max = 72) String categoria,
            @NotBlank @Size(max = 80) String tipo,
            @Size(max = 72) String marca,
            LocalDate fecha,
            @NotBlank @Size(max = 360) String resumen,
            @NotBlank @Size(max = 2400) String detalle,
            @Size(max = 500) @Pattern(regexp = "|https://(?:www\\.)?instagram\\.com/(?:p|reel|tv)/[A-Za-z0-9_-]+/?(?:\\?[^\\s]*)?")
            String instagramUrl,
            @NotNull @Size(max = 5) List<@NotNull @Valid Foto> imagenes) implements DatosCatalogo {}
}
