package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Veto decidido en una reunión.")
public record VetoRequest(

        @Schema(description = "A quién se veta.", example = "812",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "hay que decir a quién se veta")
        Long productorId,

        @Schema(description = "La reunión que lo decidió. Tiene que tener su acta subida: "
                + "es el documento que respalda la sanción.",
                example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "hay que decir en qué reunión se decidió")
        Long reunionId,

        @Schema(description = "Por qué, con el detalle que dé el acta.",
                example = "Vendió coca fuera del cupo autorizado, según el acta del 12/03.",
                maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "hay que decir el motivo")
        @Size(max = 1000, message = "el motivo no puede superar los 1000 caracteres")
        String motivo,

        @Schema(description = "Desde cuándo rige. Si no viene, la fecha de la reunión.",
                example = "2026-03-12")
        LocalDate desde
) {
}
