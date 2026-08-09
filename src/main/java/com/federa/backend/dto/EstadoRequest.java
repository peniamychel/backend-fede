package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Habilita o deshabilita un registro.
 * <p>
 * Lleva el valor deseado en vez de ser un simple "alternar" para que la
 * operación sea idempotente: reintentar la misma llamada tras un corte de red
 * deja el registro como se quería, y no de vuelta como estaba.
 */
@Schema(description = "Nuevo estado de un registro.")
public record EstadoRequest(

        @Schema(description = "true lo habilita, false lo deshabilita.",
                example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "hay que decir si se habilita o se deshabilita")
        Boolean estado
) {
}
