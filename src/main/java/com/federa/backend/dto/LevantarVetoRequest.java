package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Decisión de sacar a alguien de la lista de vetados.")
public record LevantarVetoRequest(

        @Schema(description = "La reunión que lo decidió. Tiene que tener su acta subida, y "
                + "no puede ser la misma que lo vetó.",
                example = "9", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "hay que decir en qué reunión se decidió")
        Long reunionId,

        @Schema(description = "Por qué se lo saca de la lista.",
                example = "Cumplió la sanción y regularizó su situación, según el acta del 20/06.",
                maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "hay que decir el motivo")
        @Size(max = 1000, message = "el motivo no puede superar los 1000 caracteres")
        String motivo,

        @Schema(description = "Desde cuándo deja de regir. Si no viene, la fecha de la reunión.",
                example = "2026-06-20")
        LocalDate hasta
) {
}
