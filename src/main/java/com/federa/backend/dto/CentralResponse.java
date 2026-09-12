package com.federa.backend.dto;

import com.federa.backend.model.Central;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Central, con la federación a la que pertenece resuelta.")
public record CentralResponse(

        @Schema(description = "Identificador interno.", example = "4")
        Long id,

        @Schema(description = "Nombre normalizado.", example = "13 DE JUNIO")
        String nombre,

        @Schema(description = "Código membretado en mayúsculas; admite tres letras o números, "
                + "o dos dígitos. Null si todavía no se cargó.", example = "IVI")
        String abreviatura,

        @Schema(description = "Id de la federación.", example = "1")
        Long federacionId,

        @Schema(description = "Nombre de la federación, para no tener que pedirla aparte.",
                example = "FEDERA")
        String federacionNombre,

        @Schema(description = "Número de la fase de impresión abierta. Null si no hay ninguna.")
        Integer faseImpresionActivaNumero,

        @Schema(description = "Última fase creada, incluida una fase ya cerrada.")
        int ultimaFaseImpresionNumero,

        Auditoria auditoria
) {

    public static CentralResponse desde(Central central) {
        return new CentralResponse(
                central.getId(),
                central.getNombre(),
                central.getAbreviatura(),
                central.getFederacion().getId(),
                central.getFederacion().getNombre(),
                central.getFaseImpresionActivaNumero(),
                central.getUltimaFaseImpresionNumero(),
                Auditoria.desde(central));
    }
}
