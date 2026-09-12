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

        @Schema(description = "Código membretado opcional. Admite tres letras o números, o dos "
                + "dígitos. Si se manda no puede estar repetido en otra central. Se guarda en "
                + "mayúsculas y sin espacios sobrantes.",
                example = "1MO", maxLength = 3)
        // Se admiten dígitos porque varias centrales empiezan con uno: la sigla
        // de 1RO DE MAYO es 1MO.
        //
        // También existen códigos membretados compuestos únicamente por dos
        // dígitos. El vacío sigue significando «sin código» y se convierte en
        // null dentro del servicio.
        @Pattern(regexp = "\\s*|\\s*(?:[0-9]{2}|[\\p{L}0-9]{3})\\s*",
                message = "el código membretado debe tener 3 letras o números, o 2 dígitos")
        String abreviatura,

        @Schema(description = "Federación a la que pertenece. Devuelve 404 si no existe.",
                example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "la central debe pertenecer a una federación")
        Long federacionId
) {
}
