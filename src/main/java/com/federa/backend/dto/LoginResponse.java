package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sesión iniciada. El cliente guarda el token y lo manda en cada petición como
 * {@code Authorization: Bearer <token>}.
 */
@Schema(name = "LoginResponse", description = "Token de sesión y datos de quien entró.")
public record LoginResponse(

        @Schema(description = "El JWT. Va en la cabecera Authorization con el prefijo Bearer.",
                example = "eyJhbGciOiJIUzI1NiJ9...")
        String token,

        @Schema(description = "Segundos que dura la sesión. El cliente lo usa para saber "
                + "cuándo tiene que volver a pedir credenciales.", example = "43200")
        long duracionSegundos,

        @Schema(description = "Nombre de usuario.", example = "admin")
        String usuario,

        @Schema(description = "Nombre para mostrar.", example = "Administrador")
        String nombreCompleto,

        @Schema(description = "Rol: ADMIN u OPERADOR.", example = "ADMIN")
        String rol
) {
}
