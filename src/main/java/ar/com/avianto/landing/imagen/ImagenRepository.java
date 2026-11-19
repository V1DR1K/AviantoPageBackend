package ar.com.avianto.landing.imagen;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ImagenRepository {
    public record Dimension(String clave, int variante, int ancho, int alto, String formato) {}
    public record Variante(int nombre, int ancho, int alto, String formato, byte[] contenido) {}

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    ImagenRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    List<Dimension> dimensiones(Collection<String> claves) {
        if (claves.isEmpty()) return List.of();
        return named.query("""
                SELECT DISTINCT ON (clave, variante) clave, variante, ancho, alto, formato FROM imagen_variante
                WHERE clave IN (:claves) ORDER BY clave, variante, formato DESC
                """, Map.of("claves", claves), (rs, row) -> new Dimension(rs.getString("clave"),
                rs.getInt("variante"), rs.getInt("ancho"), rs.getInt("alto"), rs.getString("formato")));
    }

    Optional<byte[]> leer(String clave, int variante, String formato) {
        return jdbc.query("SELECT contenido FROM imagen_variante WHERE clave = ? AND variante = ? AND formato = ?",
                (rs, row) -> rs.getBytes(1), clave, variante, formato).stream().findFirst();
    }

    void insertar(String clave, List<Variante> variantes) {
        jdbc.batchUpdate("""
                INSERT INTO imagen_variante (clave, variante, ancho, alto, formato, contenido)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, variantes, variantes.size(), (ps, v) -> {
            ps.setString(1, clave);
            ps.setInt(2, v.nombre());
            ps.setInt(3, v.ancho());
            ps.setInt(4, v.alto());
            ps.setString(5, v.formato());
            ps.setBytes(6, v.contenido());
        });
    }
}
