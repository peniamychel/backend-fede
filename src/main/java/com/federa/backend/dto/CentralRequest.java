package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Alta y edición de centrales.")
public record CentralRequest(

        @Schema(description = "Nombre de la central. Se guarda normalizado a mayúsculas y sin "
                + "tildes. Solo tiene que ser único dentro de su federación.",
                example = "13 DE JUNIO", maxLength = 60, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "el nombre de la central es obligatorio")
        @Size(max = 60, message = "el nombre no puede superar los 60 caracteres")
        String nombre,

        @Schema(description = "Sigla de tres caracteres, letras o números. Opcional, pero si se "
                + "manda no puede estar repetida en otra central. Se guarda en mayúsculas y sin "
                + "espacios sobrantes.",
                example = "1MO", maxLength = 3)
        // Se admiten dígitos porque varias centrales empiezan con uno: la sigla
        // de 1RO DE MAYO es 1MO.
        //
        // Y se admite el vacío además de los tres caracteres porque quien deja
        // el campo en blanco está diciendo «sin abreviatura», no mandando algo
        // mal escrito. El servicio lo convierte en null.
        @Pattern(regexp = "\\s*|\\s*[A-Za-z0-9]{3}\\s*",
                message = "la abreviatura son exactamente 3 caracteres, letras o números")
        String abreviatura,

        @Schema(description = "Federación a la que pertenece. Devuelve 404 si no existe.",
                example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "la central debe pertenecer a una federación")
        Long federacionId
) {
}
