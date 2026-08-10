package com.federa.backend.dto;

import com.federa.backend.model.enums.MotivoTraspaso;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Pasar un lote a otro productor, o dejarlo sin tenedor.
 *
 * @param productorId a quién pasa. Null deja el lote sin tenedor, que es una
 *                    situación real: alguien vende y el comprador todavía no
 *                    está cargado en el padrón.
 */
@Schema(description = "Traspaso de un lote.")
public record TraspasoLoteRequest(

        @Schema(description = "Nuevo tenedor. Si se omite, el lote queda sin tenedor.",
                example = "812")
        Long productorId,

        @Schema(description = "Desde cuándo. Si se omite, hoy.", example = "2026-08-15")
        LocalDate desde,

        @Schema(description = "Por qué cambió de manos.", example = "VENTA",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "hay que decir por qué cambia de manos")
        MotivoTraspaso motivo,

        @Schema(description = "Detalle libre: número de acta, precio, quién intervino.",
                maxLength = 300)
        @Size(max = 300, message = "las observaciones no pueden superar los 300 caracteres")
        String observaciones
) {

    public LocalDate desdeOHoy() {
        return desde != null ? desde : LocalDate.now();
    }
}
