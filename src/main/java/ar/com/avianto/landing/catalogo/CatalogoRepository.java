package ar.com.avianto.landing.catalogo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
class CatalogoRepository {
    record Entrada(String id, ObjectNode datos, boolean visible, int version, OffsetDateTime creada,
                   OffsetDateTime actualizada) {}

    private final JdbcTemplate jdbc;
    private final RowMapper<Entrada> mapper;

    CatalogoRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.mapper = (rs, row) -> {
            try {
                return new Entrada(rs.getString("id"), (ObjectNode) json.readTree(rs.getString("datos")),
                        rs.getBoolean("visible"), rs.getInt("version"),
                        rs.getObject("creada", OffsetDateTime.class), rs.getObject("actualizada", OffsetDateTime.class));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Publicación persistida inválida", ex);
            }
        };
    }

    List<Entrada> listar(TipoCatalogo tipo, boolean publico) {
        String sql = "SELECT * FROM entrada_catalogo WHERE tipo = ? AND archivada IS NULL"
                + (publico ? " AND visible" : "") + " ORDER BY creada DESC, id";
        return jdbc.query(sql, mapper, tipo.ruta());
    }

    Optional<Entrada> buscar(TipoCatalogo tipo, String id) {
        return jdbc.query("SELECT * FROM entrada_catalogo WHERE tipo = ? AND id = ? AND archivada IS NULL",
                mapper, tipo.ruta(), id).stream().findFirst();
    }

    void insertar(TipoCatalogo tipo, String id, ObjectNode datos, boolean visible) {
        jdbc.update("INSERT INTO entrada_catalogo (tipo, id, datos, visible) VALUES (?, ?, ?::jsonb, ?)",
                tipo.ruta(), id, datos.toString(), visible);
    }

    boolean actualizar(TipoCatalogo tipo, String id, ObjectNode datos, boolean visible, int version) {
        return jdbc.update("""
                UPDATE entrada_catalogo SET datos = ?::jsonb, visible = ?, version = version + 1, actualizada = now()
                WHERE tipo = ? AND id = ? AND archivada IS NULL AND version = ?
                """, datos.toString(), visible, tipo.ruta(), id, version) == 1;
    }

    boolean archivar(TipoCatalogo tipo, String id, int version) {
        return jdbc.update("""
                UPDATE entrada_catalogo SET archivada = now(), visible = false, version = version + 1, actualizada = now()
                WHERE tipo = ? AND id = ? AND archivada IS NULL AND version = ?
                """, tipo.ruta(), id, version) == 1;
    }

    long revision(TipoCatalogo tipo) {
        return jdbc.queryForObject("SELECT revision FROM revision_catalogo WHERE tipo = ?", Long.class, tipo.ruta());
    }

    void incrementarRevision(TipoCatalogo tipo) {
        jdbc.update("UPDATE revision_catalogo SET revision = revision + 1 WHERE tipo = ?", tipo.ruta());
    }

    boolean iniciarCarga(String nombre) {
        return jdbc.update("INSERT INTO carga_inicial (nombre) VALUES (?) ON CONFLICT DO NOTHING", nombre) == 1;
    }
}
