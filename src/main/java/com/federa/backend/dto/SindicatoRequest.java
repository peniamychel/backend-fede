package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Alta y edición de sindicatos.")
public record SindicatoRequest(

        @Schema(description = "Nombre del sindicato. Solo tiene que ser único dentro de su "
                + "central: en el padrón hay homónimos en centrales distintas, y eso es válido.",
                example = "1RO DE MAYO", maxLength = 60, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "el nombre del sindicato es obligatorio")
        @Size(max = 60, message = "el nombre no puede superar los 60 caracteres")
        String nombre,

        @Schema(description = "Número que la federación le asigna. Opcional, pero si se manda "
                + "no puede estar repetido en otro sindicato. Se guarda sin espacios sobrantes.",
                example = "47", maxLength = 20)
        @Size(max = 20, message = "el número no puede superar los 20 caracteres")
        String numero,

        @Schema(description = "Central a la que pertenece. Devuelve 404 si no existe.",
                example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "el sindicato debe pertenecer a una central")
        Long centralId
) {
}
