package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo escrito al observar manualmente a un productor. */
public record ObservacionProductorRequest(
        @Schema(description = "Motivo de la observacion manual.", maxLength = 500,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "el motivo de la observacion es obligatorio")
        @Size(max = 500, message = "la observacion no puede superar los 500 caracteres")
        String texto
) {
}
