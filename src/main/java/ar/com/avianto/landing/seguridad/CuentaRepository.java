package ar.com.avianto.landing.seguridad;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
class CuentaRepository {
    record Cuenta(String usuario, String clave, boolean requiereActivacion, int revision) {}

    private final JdbcTemplate jdbc;

    CuentaRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Optional<Cuenta> buscar(String usuario) {
        return jdbc.query("""
                SELECT usuario, clave_hash, requiere_activacion, revision
                FROM cuenta_admin WHERE usuario = ?
                """, (rs, row) -> new Cuenta(rs.getString("usuario"), rs.getString("clave_hash"),
                rs.getBoolean("requiere_activacion"), rs.getInt("revision")), usuario).stream().findFirst();
    }

    void insertar(String usuario, String hash, boolean requiereActivacion) {
        jdbc.update("""
                INSERT INTO cuenta_admin (usuario, clave_hash, requiere_activacion)
                VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                """, usuario, hash, requiereActivacion);
    }

    boolean cambiar(Cuenta cuenta, String hash) {
        return jdbc.update("""
                UPDATE cuenta_admin SET clave_hash = ?, requiere_activacion = false, revision = revision + 1
                WHERE usuario = ? AND revision = ?
                """, hash, cuenta.usuario(), cuenta.revision()) == 1;
    }
}
