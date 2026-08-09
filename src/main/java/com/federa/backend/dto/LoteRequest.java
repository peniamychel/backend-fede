package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta y edición de lotes.
 * <p>
 * {@code extension}, {@code estado} y {@code mercado} se reciben como texto
 * libre y se normalizan en el servicio con los {@code desde(...)} de cada enum.
 * Así el cliente puede mandar tal cual lo que dice la planilla ("C-S",
 * "SISTEMA", "FRANSIONADOS") sin tener que conocer la nomenclatura interna.
 */
@Schema(description = "Alta y edición de lotes. Los tres campos de clasificación se mandan "
        + "tal como los escribe la planilla y el backend los normaliza.")
public record LoteRequest(

        @Schema(description = "Número de lote. Es texto, no número: el padrón trae rangos "
                + "(`30-31`), extensión pegada (`21-A`) y códigos (`B.N47`). Puede repetirse "
                + "dentro de un sindicato; eso se anota como observación, no se bloquea.",
                example = "74", maxLength = 20)
        @Size(max = 20, message = "el número de lote no puede superar los 20 caracteres")
        String numero,

        @Schema(description = "Subdivisión del lote. Se acepta en minúscula. Valores útiles: "
                + "A, B, C, D, E. Cualquier otra cosa queda en null.",
                example = "A", allowableValues = {"A", "B", "C", "D", "E"}, maxLength = 5)
        @Size(max = 5, message = "la extensión debe ser una letra (A–E)")
        String extension,

        @Schema(description = "Estado del lote como figura en la planilla. Se normaliza: "
                + "`SISTEMA`/`C-S`/`SI` → CON_SISTEMA, `NO` → SIN_SISTEMA, `FRANSIONADOS` → "
                + "FRACCIONADO. Si no se reconoce queda como DESCONOCIDO y el texto original "
                + "se conserva en `estadoOriginal`, en vez de rechazar la carga.",
                example = "C-S", maxLength = 30)
        @Size(max = 30, message = "el estado del lote no puede superar los 30 caracteres")
        String estado,

        @Schema(description = "Mercado. Hoy la planilla solo usa DETALLISTA. Se acepta en "
                + "minúscula.",
                example = "DETALLISTA", maxLength = 20)
        @Size(max = 20, message = "el mercado no puede superar los 20 caracteres")
        String mercado,

        @Schema(description = "Productor al que se le asigna. Devuelve 404 si no existe.",
                example = "812", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "el lote debe pertenecer a un productor")
        Long productorId
) {
}
