package com.federa.backend.controller;

import com.federa.backend.config.ApiRutas;
import com.federa.backend.dto.LoginRequest;
import com.federa.backend.dto.LoginResponse;
import com.federa.backend.service.AutenticacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping(ApiRutas.V1 + "/auth")
@Tag(name = "Autenticación", description =
        "Inicio de sesión. Devuelve un token que se manda en cada petición como "
        + "`Authorization: Bearer <token>`.")
public class AutenticacionController {

    private final AutenticacionService autenticacionService;

    public AutenticacionController(AutenticacionService autenticacionService) {
        this.autenticacionService = autenticacionService;
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión y devuelve el token",
            description = "Con credenciales incorrectas responde 401 sin decir si falló el "
                    + "usuario o la contraseña: distinguirlos permitiría averiguar qué "
                    + "usuarios existen.")
    public LoginResponse login(@Valid @RequestBody LoginRequest peticion) {
        return autenticacionService.iniciarSesion(peticion);
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
        return Map.of(
                "autenticado", true,
                "usuario", autenticacion.getName(),
                "roles", autenticacion.getAuthorities().stream()
                        .map(Object::toString).toList());
    }
}
