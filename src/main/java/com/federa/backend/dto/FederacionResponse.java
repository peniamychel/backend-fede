package com.federa.backend.dto;

import com.federa.backend.model.Federacion;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Federación: el nivel más alto de la organización.")
public record FederacionResponse(

        @Schema(description = "Identificador interno.", example = "1")
        Long id,

        @Schema(description = "Nombre normalizado.", example = "FEDERA")
        String nombre,

        Auditoria auditoria
) {

    public static FederacionResponse desde(Federacion federacion) {
        return new FederacionResponse(federacion.getId(), federacion.getNombre(),
                Auditoria.desde(federacion));
    }
}
