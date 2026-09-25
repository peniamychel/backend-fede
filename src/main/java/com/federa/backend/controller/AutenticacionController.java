package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.LoginResponse;
import com.federa.backend.dto.AccesoRequest;
import com.federa.backend.seguridad.JwtService;
import com.federa.backend.seguridad.LimiteIntentosAcceso;
import com.federa.backend.service.AutenticacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

@RestController
@RequestMapping(ApiRutas.V1 + "/auth")
@Tag(name = "Autenticación", description =
        "Inicio de sesión. Devuelve un token que se manda en cada petición como "
        + "`Authorization: Bearer <token>`.")
public class AutenticacionController {

    private final AutenticacionService autenticacionService;
    private final JwtService jwtService;
    private final LimiteIntentosAcceso limiteIntentos;

    public AutenticacionController(AutenticacionService autenticacionService,
                                   JwtService jwtService,
                                   LimiteIntentosAcceso limiteIntentos) {
        this.autenticacionService = autenticacionService;
        this.jwtService = jwtService;
        this.limiteIntentos = limiteIntentos;
    }

    @PostMapping("/acceso")
    @Operation(summary = "Inicia sesión con un único código de acceso")
    @SecurityRequirements
    public LoginResponse acceso(@Valid @RequestBody AccesoRequest peticion,
                                HttpServletRequest http) {
        String origen = http.getRemoteAddr();
        limiteIntentos.verificar(origen);
        try {
            LoginResponse respuesta = autenticacionService.acceder(peticion);
            limiteIntentos.exito(origen);
            return respuesta;
        } catch (BadCredentialsException e) {
            limiteIntentos.fallo(origen);
            throw e;
        }
    }

    @PostMapping("/logout")
    public void logout(@RequestHeader(value = "Authorization", required = false) String cabecera) {
        String token = cabecera != null && cabecera.startsWith("Bearer ")
                ? cabecera.substring(7).trim() : null;
        var contenido = token == null ? null : jwtService.validar(token);
        autenticacionService.cerrarSesion(
                contenido == null ? null : contenido.get("sid", String.class));
    }

    /**
     * Quién es el portador del token.
     * <p>
     * Le sirve al cliente para saber, al arrancar, si el token que tenía
     * guardado sigue valiendo, sin tener que pedir credenciales de nuevo.
     */
    @GetMapping("/yo")
    @Operation(summary = "Datos de la sesión actual",
            description = "Responde 401 si no hay token válido. El cliente lo usa al arrancar "
                    + "para saber si la sesión guardada sigue viva.")
    public Map<String, Object> yo(Authentication autenticacion) {
        if (autenticacion == null) {
            return Map.of("autenticado", false);
        }
        var alcance = com.federa.backend.seguridad.AlcanceCentral.actual();
        return Map.of(
                "autenticado", true,
                "centralId", alcance == null ? 0L : alcance.centralId(),
                "usuario", autenticacion.getName(),
                "roles", autenticacion.getAuthorities().stream()
                        .map(Object::toString).filter(a -> a.startsWith("ROLE_"))
                        .map(a -> a.substring(5)).toList(),
                "permisos", autenticacion.getAuthorities().stream()
                        .map(Object::toString).filter(a -> !a.startsWith("ROLE_"))
                        .toList());
    }
}
