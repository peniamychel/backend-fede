package com.federa.backend.seguridad;

import com.federa.backend.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Responde 401 cuando la petición llega sin identificar.
 * <p>
 * Spring Security devuelve 403 por omisión, y eso confunde al cliente: 403
 * significa "sos vos y no te alcanza", mientras que acá el problema es que
 * <i>no sabemos quién sos</i>. La app reacciona distinto a cada uno — ante un
 * 401 manda a iniciar sesión, ante un 403 avisa que faltan permisos—, así que
 * la diferencia no es cosmética.
 * <p>
 * Además devuelve el mismo {@link ErrorResponse} que el resto de la API, para
 * que el cliente no tenga que interpretar dos formatos de error distintos.
 */
@Component
public class PuntoDeEntradaNoAutorizado implements AuthenticationEntryPoint {

    private final ObjectMapper json;

    public PuntoDeEntradaNoAutorizado(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest peticion,
                         HttpServletResponse respuesta,
                         AuthenticationException excepcion) throws IOException {

        respuesta.setStatus(HttpStatus.UNAUTHORIZED.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");

        json.writeValue(respuesta.getWriter(), ErrorResponse.de(
                HttpStatus.UNAUTHORIZED,
                "Hace falta iniciar sesión para acceder a este recurso."));
    }
}
