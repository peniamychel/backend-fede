package com.federa.backend.dto;

import com.federa.backend.model.TenenciaLote;
import com.federa.backend.model.TenenciaSistema;
import com.federa.backend.model.enums.MotivoTraspaso;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Un período del historial, sirva para un lote o para un sistema.
 * <p>
 * Es el mismo record para los dos porque cuentan lo mismo: algo estuvo en
 * manos de alguien entre dos fechas, y por tal motivo. Lo que cambia es qué es
 * "algo" y quién es "alguien", y eso va en los dos campos de texto.
 *
 * @param queEs   qué cambió de manos: el código del lote o el del sistema.
 * @param conQuien quién o dónde estuvo: el productor, o el lote.
 */
@Schema(description = "Un período del historial de tenencia.")
public record TenenciaResponse(

        @Schema(description = "Identificador del período.", example = "45")
        Long id,

        @Schema(description = "Qué cambió de manos.", example = "74-A")
        String queEs,

        @Schema(description = "Id de eso mismo.", example = "1503")
        Long queEsId,

        @Schema(description = "En manos de quién estuvo, o en qué lote.",
                example = "CANDIDO COLQUECHAMBI MAMANI")
        String conQuien,

        @Schema(description = "Su id.", example = "812")
        Long conQuienId,

        @Schema(description = "Desde cuándo.", example = "2026-03-01")
        LocalDate desde,

        @Schema(description = "Hasta cuándo. Null si sigue vigente.", example = "2026-08-14")
        LocalDate hasta,

        @Schema(description = "Derivado: si el período sigue abierto.", example = "true")
        boolean vigente,

        @Schema(description = "Por qué cambió de manos.", example = "VENTA")
        MotivoTraspaso motivo,

        @Schema(description = "Cómo se escribe el motivo.", example = "Venta")
        String motivoEtiqueta,

        String observaciones
) {

    /** Un período de tenencia de un lote: el lote estuvo con un productor. */
    public static TenenciaResponse deLote(TenenciaLote t) {
        return new TenenciaResponse(
                t.getId(),
                t.getLote().getCodigo(),
                t.getLote().getId(),
                t.getProductor().getNombreCompleto(),
                t.getProductor().getId(),
                t.getDesde(),
                t.getHasta(),
                t.estaVigente(),
                t.getMotivo(),
                t.getMotivo() == null ? null : t.getMotivo().getEtiqueta(),
                t.getObservaciones());
    }

    /** Un período de un sistema: el sistema estuvo en un lote. */
    public static TenenciaResponse deSistema(TenenciaSistema t) {
        return new TenenciaResponse(
                t.getId(),
                t.getSistema().getCodigo(),
                t.getSistema().getId(),
                t.getLote().getCodigo(),
                t.getLote().getId(),
                t.getDesde(),
                t.getHasta(),
                t.estaVigente(),
                t.getMotivo(),
                t.getMotivo() == null ? null : t.getMotivo().getEtiqueta(),
                t.getObservaciones());
    }
}
