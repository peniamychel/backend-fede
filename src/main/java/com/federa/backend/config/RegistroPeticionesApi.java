package com.federa.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Deja una linea por cada llamada a la API para poder seguirla desde la
 * consola de ejecucion. No registra cuerpo, parametros ni cabeceras: pueden
 * contener cedulas, tokens o contrasenas.
 */
@Component
public class RegistroPeticionesApi extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RegistroPeticionesApi.class);

    @Value("${federa.registro-api.habilitado:true}")
    private boolean habilitado;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(ApiRutas.V1);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long inicio = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (habilitado) {
                long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
                log.info("API {} {} -> {} ({} ms)",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), duracionMs);
            }
        }
    }
}
