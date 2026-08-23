package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Alta y edición de federaciones.")
public record FederacionRequest(

        @Schema(description = "Nombre de la federación. Se guarda normalizado a mayúsculas y "
                + "sin tildes, y no puede repetirse.",
                example = "FEDERA", maxLength = 80, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "el nombre de la federación es obligatorio")
        @Size(max = 80, message = "el nombre no puede superar los 80 caracteres")
        String nombre,

        @Schema(description = "Número que la identifica. Opcional, pero si se manda no puede "
                + "estar repetido en otra federación. Se guarda sin espacios sobrantes.",
                example = "3", maxLength = 20)
        @Size(max = 20, message = "el número no puede superar los 20 caracteres")
        String numero
) {
}
