package ar.com.avianto.landing.seguridad;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration(proxyBeanMethods = false)
class SeguridadConfig {
    @Bean
    PasswordEncoder encoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    ApplicationRunner cuentaInicial(CuentaService cuentas) {
        return args -> cuentas.inicializar();
    }

    @Bean
    HttpSessionEventPublisher eventoSesion() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    SecurityFilterChain seguridad(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(reglas -> reglas
                        .requestMatchers("/api/admin/sesion", "/api/admin/login", "/api/admin/logout").permitAll()
                        .requestMatchers("/api/admin/clave").hasAnyRole("ADMIN", "SETUP")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/catalogo/**", "/media/**", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/**", "/actuator/**").denyAll()
                        .anyRequest().permitAll())
                .securityContext(contexto -> contexto.securityContextRepository(new HttpSessionSecurityContextRepository()))
                .csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint((req, resp, ex) -> resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((req, resp, ex) -> resp.setStatus(HttpServletResponse.SC_FORBIDDEN)))
                .formLogin(login -> login.loginPage("/admin")
                        .loginProcessingUrl("/api/admin/login")
                        .successHandler((req, resp, auth) -> resp.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .failureHandler((req, resp, ex) -> resp.setStatus(ex instanceof SessionAuthenticationException
                                ? HttpServletResponse.SC_CONFLICT : HttpServletResponse.SC_UNAUTHORIZED)))
                .logout(logout -> logout.logoutUrl("/api/admin/logout").deleteCookies("AVIANTOLANDINGSESSION")
                        .logoutSuccessHandler((req, resp, auth) -> resp.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .sessionManagement(sesion -> sesion.maximumSessions(1).expiredSessionStrategy(evento ->
                        evento.getResponse().setStatus(HttpServletResponse.SC_UNAUTHORIZED)));
        return http.build();
    }
}
