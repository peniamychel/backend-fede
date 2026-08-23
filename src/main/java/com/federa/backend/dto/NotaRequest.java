package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Una nota suelta. Se usa al abrir una vuelta de lista, y es opcional. */
@Schema(description = "Nota opcional.")
public record NotaRequest(

        @Schema(description = "Para qué es esta vuelta.",
                example = "Después del cuarto intermedio", maxLength = 200)
        @Size(max = 200, message = "la nota no puede superar los 200 caracteres")
        String nota
) {
}
