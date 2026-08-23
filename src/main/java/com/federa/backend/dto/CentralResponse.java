package com.federa.backend.dto;

import com.federa.backend.model.Central;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Central, con la federación a la que pertenece resuelta.")
public record CentralResponse(

        @Schema(description = "Identificador interno.", example = "4")
        Long id,

        @Schema(description = "Nombre normalizado.", example = "13 DE JUNIO")
        String nombre,

        @Schema(description = "Sigla de tres letras, en mayúsculas. Null si todavía no se cargó.",
                example = "IVI")
        String abreviatura,

        @Schema(description = "Id de la federación.", example = "1")
        Long federacionId,

        @Schema(description = "Nombre de la federación, para no tener que pedirla aparte.",
                example = "FEDERA")
        String federacionNombre,

        Auditoria auditoria
) {

    public static CentralResponse desde(Central central) {
        return new CentralResponse(
                central.getId(),
                central.getNombre(),
                central.getAbreviatura(),
                central.getFederacion().getId(),
                central.getFederacion().getNombre(),
                Auditoria.desde(central));
    }
}
