package com.federa.backend.dto;

import com.federa.backend.model.EntidadAuditable;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Las tres columnas comunes, en las respuestas.
 * <p>
 * Va anidada dentro de cada respuesta en lugar de repetir tres campos en cada
 * record: agrupa lo que conceptualmente es una sola cosa, y agregar una cuarta
 * columna de control mañana se hace en un solo lugar.
 *
 * @param estado    si el registro sigue habilitado. Los deshabilitados se
 *                  siguen devolviendo: la lista los muestra marcados, para que
 *                  se los pueda volver a habilitar.
 * @param creadoEn  alta. No cambia nunca.
 * @param editadoEn última modificación. Solo se mueve si la fila cambió de
 *                  verdad.
 */
@Schema(description = "Control de la fila: si está habilitada y cuándo se tocó.")
public record Auditoria(

        @Schema(description = "Si el registro está habilitado.", example = "true")
        boolean estado,

        @Schema(description = "Fecha de alta.", example = "2026-08-09T01:56:54")
        LocalDateTime creadoEn,

        @Schema(description = "Fecha de la última modificación.",
                example = "2026-08-09T02:10:31")
        LocalDateTime editadoEn
) {

    public static Auditoria desde(EntidadAuditable entidad) {
        return new Auditoria(entidad.isEstado(), entidad.getCreatedAt(),
                entidad.getUpdatedAt());
    }
}
