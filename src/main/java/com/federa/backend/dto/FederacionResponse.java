package com.federa.backend.dto;

import com.federa.backend.model.Federacion;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Federación: el nivel más alto de la organización.")
public record FederacionResponse(

        @Schema(description = "Identificador interno.", example = "1")
        Long id,

        @Schema(description = "Nombre normalizado.", example = "FEDERA")
        String nombre,

        @Schema(description = "Número que la identifica. Null si todavía no se cargó.",
                example = "3")
        String numero,

        Auditoria auditoria
) {

    public static FederacionResponse desde(Federacion federacion) {
        return new FederacionResponse(federacion.getId(), federacion.getNombre(),
                federacion.getNumero(), Auditoria.desde(federacion));
    }
}
