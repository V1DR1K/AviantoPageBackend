package ar.com.avianto.landing.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
class CuentaService implements UserDetailsService {
    private final CuentaRepository repo;
    private final PasswordEncoder encoder;
    private final String usuario;
    private final String claveInicial;
    private final String codigoActivacion;

    CuentaService(CuentaRepository repo, PasswordEncoder encoder,
                  @Value("${avianto.admin.usuario}") String usuario,
                  @Value("${avianto.admin.clave-inicial}") String claveInicial,
                  @Value("${avianto.admin.codigo-activacion}") String codigoActivacion) {
        this.repo = repo;
        this.encoder = encoder;
        this.usuario = usuario;
        this.claveInicial = claveInicial;
        this.codigoActivacion = codigoActivacion;
    }

    @Transactional
    void inicializar() {
        var existente = repo.buscar(usuario);
        if (existente.isPresent() && !existente.get().requiereActivacion()) return;
        boolean claveInicialDebil = !claveFuerte(claveInicial) || claveInicial.equalsIgnoreCase(usuario);
        if ((existente.isPresent() || claveInicialDebil) && codigoActivacion.length() < 24) {
            throw new IllegalStateException("Configurá AVIANTO_ACTIVATION_CODE antes de usar una clave inicial breve.");
        }
        if (existente.isEmpty()) {
            repo.insertar(usuario, encoder.encode(claveInicial), claveInicialDebil);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usuario) {
        var cuenta = buscar(usuario);
        return User.withUsername(cuenta.usuario()).password(cuenta.clave())
                .roles(cuenta.requiereActivacion() ? "SETUP" : "ADMIN").build();
    }

    @Transactional
    void cambiar(String usuario, String actual, String nueva, String codigo) {
        var cuenta = buscar(usuario);
        if (actual.getBytes(StandardCharsets.UTF_8).length > 72 || !encoder.matches(actual, cuenta.clave())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña actual no coincide.");
        }
        if (cuenta.requiereActivacion() && !codigoValido(codigo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El código de activación no coincide.");
        }
        if (!claveFuerte(nueva) || actual.equals(nueva) || usuario.equalsIgnoreCase(nueva)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Elegí una contraseña nueva de al menos 12 caracteres y hasta 72 bytes.");
        }
        if (!repo.cambiar(cuenta, encoder.encode(nueva))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La contraseña cambió. Iniciá sesión otra vez.");
        }
    }

    private CuentaRepository.Cuenta buscar(String usuario) {
        return repo.buscar(usuario).orElseThrow(() -> new UsernameNotFoundException("Cuenta desconocida"));
    }

    private boolean claveFuerte(String clave) {
        return !clave.isBlank() && clave.length() >= 12 && clave.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    private boolean codigoValido(String codigo) {
        return codigo != null && MessageDigest.isEqual(codigoActivacion.getBytes(StandardCharsets.UTF_8),
                codigo.strip().getBytes(StandardCharsets.UTF_8));
    }
}
