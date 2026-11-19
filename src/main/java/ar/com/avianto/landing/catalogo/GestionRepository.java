package ar.com.avianto.landing.catalogo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class GestionRepository {
    record Opcion(long id, String tipo, String nombre) {}
    private static final RowMapper<Opcion> MAPA =
            (rs, fila) -> new Opcion(rs.getLong("id"), rs.getString("tipo"), rs.getString("nombre"));
    private final JdbcTemplate jdbc;

    GestionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void bloquear(boolean escritura) {
        jdbc.execute("LOCK TABLE opcion_gestion IN " + (escritura ? "SHARE ROW EXCLUSIVE" : "SHARE") + " MODE");
    }

    List<Opcion> listar() {
        return jdbc.query("SELECT * FROM opcion_gestion ORDER BY tipo, lower(nombre), id", MAPA);
    }

    Optional<Opcion> buscar(CampoGestion campo, long id) {
        return jdbc.query("SELECT * FROM opcion_gestion WHERE tipo = ? AND id = ?", MAPA, campo.tipo, id).stream().findFirst();
    }

    Opcion crear(CampoGestion campo, String nombre) {
        return jdbc.queryForObject("INSERT INTO opcion_gestion(tipo,nombre) VALUES (?,?) RETURNING *", MAPA, campo.tipo, nombre);
    }

    Opcion editar(Opcion opcion, String nombre) {
        return jdbc.queryForObject("UPDATE opcion_gestion SET nombre = ? WHERE id = ? RETURNING *", MAPA, nombre, opcion.id());
    }

    void eliminar(Opcion opcion) { jdbc.update("DELETE FROM opcion_gestion WHERE id = ?", opcion.id()); }

    void renombrarReferencias(CampoGestion campo, TipoCatalogo tipo, String antes, String nombre) {
        String dato = "datos #> " + campo.path(tipo);
        String reemplazo = campo.multiple
                ? "(SELECT jsonb_agg(to_jsonb(CASE WHEN lower(btrim(v.nombre)) = lower(?) THEN ?::text ELSE v.nombre END) ORDER BY v.pos)"
                    + " FROM jsonb_array_elements_text(" + arreglo(dato) + ") WITH ORDINALITY v(nombre,pos))"
                : "to_jsonb(?::text)";
        String sql = "UPDATE entrada_catalogo SET datos = jsonb_set(datos," + campo.path(tipo) + "," + reemplazo
                + "), version = version + 1, actualizada = now() WHERE tipo = ? AND " + condicion(campo, tipo);
        if (campo.multiple) jdbc.update(sql, antes, nombre, tipo.ruta(), antes);
        else jdbc.update(sql, nombre, tipo.ruta(), antes);
    }

    boolean enUso(CampoGestion campo, TipoCatalogo tipo, String nombre) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM entrada_catalogo WHERE "
                + "tipo = ? AND archivada IS NULL AND " + condicion(campo, tipo) + ")", Boolean.class, tipo.ruta(), nombre));
    }

    private String condicion(CampoGestion campo, TipoCatalogo tipo) {
        if (!campo.multiple) return "lower(btrim(datos #>> " + campo.path(tipo) + ")) = lower(?)";
        return "EXISTS(SELECT 1 FROM jsonb_array_elements_text(" + arreglo("datos #> " + campo.path(tipo))
                + ") v(nombre) WHERE lower(btrim(v.nombre)) = lower(?))";
    }

    private String arreglo(String dato) {
        return "CASE WHEN jsonb_typeof(" + dato + ") = 'array' THEN " + dato + " ELSE '[]'::jsonb END";
    }
}
