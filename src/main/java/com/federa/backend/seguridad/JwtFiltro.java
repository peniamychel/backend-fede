package com.federa.backend.seguridad;

import io.jsonwebtoken.Claims;
import com.federa.backend.repository.SesionUsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * Lee el token de la cabecera {@code Authorization} y deja al usuario
 * autenticado para el resto de la petición.
 * <p>
 * No rechaza a nadie: si no hay token o es inválido, simplemente sigue sin
 * autenticar y decide Spring Security según lo que pida cada ruta. Rechazar acá
 * rompería los endpoints públicos.
 */
@Component
public class JwtFiltro extends OncePerRequestFilter {

    private static final String CABECERA = "Authorization";
    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;
    private final SesionUsuarioRepository sesionRepository;

    public JwtFiltro(JwtService jwtService, SesionUsuarioRepository sesionRepository) {
        this.jwtService = jwtService;
        this.sesionRepository = sesionRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest peticion,
                                    @NonNull HttpServletResponse respuesta,
                                    @NonNull FilterChain cadena)
            throws ServletException, IOException {

        String token = extraerToken(peticion);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Claims contenido = jwtService.validar(token);
            if (contenido != null) {
                autenticar(peticion, contenido);
            }
        }
        cadena.doFilter(peticion, respuesta);
    }

    private void autenticar(HttpServletRequest peticion, Claims contenido) {
        String sesionId = contenido.get("sid", String.class);
        if (sesionId == null) return;
        var sesion = sesionRepository
                .findByIdAndRevocadaFalseAndExpiraEnAfter(sesionId, LocalDateTime.now())
                .orElse(null);
        if (sesion == null || !sesion.getUsuario().isEstado()) return;

        var autoridades = new ArrayList<SimpleGrantedAuthority>();
        sesion.getUsuario().getRolesAcceso().stream()
                .filter(r -> r.isEstado())
                .forEach(rol -> {
                    autoridades.add(new SimpleGrantedAuthority("ROLE_" + rol.getCodigo()));
                });
        if (sesion.getUsuario().isPermisosPersonalizados()) autoridades.clear();
        AutorizacionesUsuario.permisos(sesion.getUsuario()).forEach(p -> autoridades.add(new SimpleGrantedAuthority(p)));
        // Compatibilidad con usuarios anteriores a los roles configurables.
        if (sesion.getUsuario().getRolesAcceso().isEmpty()
                && !sesion.getUsuario().isPermisosPersonalizados() && sesion.getUsuario().getRol() != null) {
            autoridades.add(new SimpleGrantedAuthority("ROLE_" + sesion.getUsuario().getRol()));
        }

        var central = sesion.getUsuario().getCentralAcceso();
        if (central != null) {
            autoridades.removeIf(a -> !AlcanceCentral.PERMISOS.contains(a.getAuthority()));
        }

        var autenticacion = new UsernamePasswordAuthenticationToken(
                sesion.getUsuario().getNombreUsuario(), null, autoridades);
        autenticacion.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(peticion));
        if (central != null) autenticacion.setDetails(
                new AlcanceCentral.Datos(central.getId(), central.getFederacion().getId(),
                        sesion.getUsuario().isTodosSindicatos(), sesion.getUsuario().getSindicatosAcceso().stream()
                        .map(s -> s.getId()).collect(java.util.stream.Collectors.toSet())));

        SecurityContextHolder.getContext().setAuthentication(autenticacion);
    }

    private String extraerToken(HttpServletRequest peticion) {
        String cabecera = peticion.getHeader(CABECERA);
        if (cabecera == null || !cabecera.startsWith(PREFIJO)) {
            return null;
        }
        String token = cabecera.substring(PREFIJO.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
