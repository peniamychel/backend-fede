package com.federa.backend.dto;

import com.federa.backend.model.Lote;
import com.federa.backend.model.enums.EstadoLote;
import com.federa.backend.model.enums.ExtensionLote;
import com.federa.backend.model.enums.Mercado;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Parcela asignada a un productor.")
public record LoteResponse(

        @Schema(description = "Identificador interno.", example = "1503")
        Long id,

        @Schema(description = "Número tal como está en el padrón.", example = "74")
        String numero,

        @Schema(description = "Subdivisión, ya normalizada.", example = "A")
        ExtensionLote extension,

        @Schema(description = "Derivado: número y extensión juntos, para mostrar.",
                example = "74-A")
        String codigo,

        @Schema(description = "Estado normalizado. DESCONOCIDO significa que la escritura de "
                + "origen no se reconoció; el texto está en `estadoOriginal`.",
                example = "CON_SISTEMA")
        EstadoLote estado,

        @Schema(description = "Texto del estado tal como vino, antes de normalizar.",
                example = "C-S")
        String estadoOriginal,

        @Schema(description = "Mercado normalizado. Puede ser null.", example = "DETALLISTA")
        Mercado mercado,

        @Schema(description = "Productor dueño del lote.", example = "812")
        Long productorId
) {

    public static LoteResponse desde(Lote lote) {
        return new LoteResponse(
                lote.getId(),
                lote.getNumero(),
                lote.getExtension(),
                lote.getCodigo(),
                lote.getEstadoLote(),
                lote.getEstadoOriginal(),
                lote.getMercado(),
                lote.getProductor().getId());
    }
}
