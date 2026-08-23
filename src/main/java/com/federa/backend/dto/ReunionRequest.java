package com.federa.backend.dto;

import com.federa.backend.model.enums.TipoReunion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Alta y edición de una reunión. */
@Schema(description = "Convocatoria a una reunión.")
public record ReunionRequest(

        @Schema(description = "Qué clase de reunión, y con eso a quiénes convoca.",
                example = "AMPLIADO", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "hay que decir qué tipo de reunión es")
        TipoReunion tipo,

        @Schema(description = "Quién convoca: el id del sindicato, la central o la "
                + "federación, según el tipo.", example = "17",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "hay que decir quién convoca")
        Long convocanteId,

        @Schema(description = "Título de la reunión.",
                example = "Ampliado ordinario de agosto", maxLength = 120,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "la reunión necesita un título")
        @Size(max = 120, message = "el título no puede superar los 120 caracteres")
        String titulo,

        @Schema(description = "Día en que se realiza.", example = "2026-08-15",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "la reunión necesita una fecha")
        LocalDate fecha,

        @Schema(description = "Dónde se realiza.", example = "Sede de la central",
                maxLength = 120)
        @Size(max = 120, message = "el lugar no puede superar los 120 caracteres")
        String lugar,

        @Schema(description = "Notas libres.", maxLength = 500)
        @Size(max = 500, message = "las observaciones no pueden superar los 500 caracteres")
        String observaciones,

        @Schema(description = "Si en esta reunión se pueden decidir vetos. No toda asamblea "
                + "es para sancionar: la mayoría es informativa, y ofrecer el veto en todas "
                + "invita a usarlo donde no corresponde. Por omisión, no.",
                example = "false", defaultValue = "false")
        Boolean vetosHabilitados
) {
}
