package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Alta y edición de observaciones. */
@Schema(description = "Alta y edición de observaciones.")
public record ObservacionRequest(

        @Schema(description = "Qué hay que corregir, en texto libre. Si un productor tiene "
                + "varios problemas, conviene una observación por cada uno para poder "
                + "resolverlos por separado.",
                example = "falta foto", maxLength = 500,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "el mensaje de la observación es obligatorio")
        @Size(max = 500, message = "el mensaje no puede superar los 500 caracteres")
        String mensaje,

        @Schema(description = "Productor observado. Devuelve 404 si no existe.",
                example = "812", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "la observación debe pertenecer a un productor")
        Long productorId
) {
}
