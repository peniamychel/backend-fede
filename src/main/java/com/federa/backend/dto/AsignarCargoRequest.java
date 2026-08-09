package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(name = "AsignarCargoRequest",
        description = "A quién se le asigna el cargo y desde cuándo.")
public record AsignarCargoRequest(

        @Schema(description = "Productor que asume. Tiene que pertenecer a ese sindicato.",
                example = "812", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "hay que indicar qué productor asume el cargo")
        Long productorId,

        @Schema(description = "Desde cuándo asume. Si se omite, hoy. Sirve para cargar "
                + "directorios que ya estaban en funciones antes de usar el sistema.",
                example = "2026-03-01")
        LocalDate desde
) {

    /** La fecha indicada, o hoy si no vino ninguna. */
    public LocalDate desdeOHoy() {
        return desde != null ? desde : LocalDate.now();
    }
}
