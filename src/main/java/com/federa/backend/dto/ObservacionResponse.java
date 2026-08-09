package com.federa.backend.dto;

import com.federa.backend.model.Observacion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Observación anotada sobre un productor.")
public record ObservacionResponse(

        @Schema(description = "Identificador interno.", example = "2041")
        Long id,

        @Schema(description = "Qué hay que corregir.", example = "falta foto")
        String mensaje,

        @Schema(description = "Si ya se dio por depurada.", example = "false")
        boolean resuelta,

        @Schema(description = "Cuándo se resolvió. Null mientras siga pendiente.",
                example = "2026-08-07T11:59:47.897")
        LocalDateTime resueltaEn,

        @Schema(description = "Productor observado.", example = "812")
        Long productorId
) {

    public static ObservacionResponse desde(Observacion o) {
        return new ObservacionResponse(
                o.getId(),
                o.getMensaje(),
                o.isResuelta(),
                o.getResueltaEn(),
                o.getProductor().getId());
    }
}
