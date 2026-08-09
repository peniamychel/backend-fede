package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "Credenciales para iniciar sesión.")
public record LoginRequest(

        @Schema(description = "Nombre de usuario.", example = "admin",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "el usuario es obligatorio")
        String usuario,

        @Schema(description = "Contraseña en claro. Viaja en el cuerpo, nunca en la URL: los "
                + "parámetros quedan en los registros del servidor y en el historial.",
                example = "admin123", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "la contraseña es obligatoria")
        String contrasena
) {
}
