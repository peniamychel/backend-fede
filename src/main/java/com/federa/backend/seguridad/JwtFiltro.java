package com.federa.backend.seguridad;

import io.jsonwebtoken.Claims;
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
import java.util.List;

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

    public JwtFiltro(JwtService jwtService) {
        this.jwtService = jwtService;
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
        String rol = contenido.get("rol", String.class);
        var autoridades = rol == null
                ? List.<SimpleGrantedAuthority>of()
                // Spring espera el prefijo ROLE_ para que funcione hasRole().
                : List.of(new SimpleGrantedAuthority("ROLE_" + rol));

        var autenticacion = new UsernamePasswordAuthenticationToken(
                contenido.getSubject(), null, autoridades);
        autenticacion.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(peticion));

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
