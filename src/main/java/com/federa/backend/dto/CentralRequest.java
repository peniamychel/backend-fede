package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Alta y edición de centrales.")
public record CentralRequest(

        @Schema(description = "Nombre de la central. Se guarda normalizado a mayúsculas y sin "
                + "tildes. Solo tiene que ser único dentro de su federación.",
                example = "13 DE JUNIO", maxLength = 60, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "el nombre de la central es obligatorio")
        @Size(max = 60, message = "el nombre no puede superar los 60 caracteres")
        String nombre,

        @Schema(description = "Número que la federación le asigna. Opcional, pero si se manda "
                + "no puede estar repetido en otra central. Se guarda sin espacios sobrantes.",
                example = "12", maxLength = 20)
        @Size(max = 20, message = "el número no puede superar los 20 caracteres")
        String numero,

        @Schema(description = "Federación a la que pertenece. Devuelve 404 si no existe.",
                example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "la central debe pertenecer a una federación")
        Long federacionId
) {
}
