package com.federa.backend.dto;

import com.federa.backend.model.Sindicato;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Sindicato, con la central a la que pertenece resuelta.")
public record SindicatoResponse(

        @Schema(description = "Identificador interno.", example = "17")
        Long id,

        @Schema(description = "Nombre normalizado.", example = "1RO DE MAYO")
        String nombre,

        @Schema(description = "Número que le asigna la federación. Null si todavía no se cargó.",
                example = "47")
        String numero,

        @Schema(description = "Id de la central.", example = "4")
        Long centralId,

        @Schema(description = "Nombre de la central, para no tener que pedirla aparte.",
                example = "13 DE JUNIO")
        String centralNombre,

        @Schema(description = "Latitud de la sede en grados decimales. Null si todavía no se "
                + "marcó en el mapa.", example = "-16.8574321")
        BigDecimal latitud,

        @Schema(description = "Longitud de la sede en grados decimales.", example = "-64.7891234")
        BigDecimal longitud,

        @Schema(description = "Derivado: si tiene las dos coordenadas cargadas.", example = "true")
        boolean tieneUbicacion,

        @Schema(description = "Cuándo se marcó la ubicación por última vez.",
                example = "2026-08-08T12:30:00")
        LocalDateTime ubicacionActualizadaEn,

        Auditoria auditoria
) {

    public static SindicatoResponse desde(Sindicato sindicato) {
        return new SindicatoResponse(
                sindicato.getId(),
                sindicato.getNombre(),
                sindicato.getNumero(),
                sindicato.getCentral().getId(),
                sindicato.getCentral().getNombre(),
                sindicato.getLatitud(),
                sindicato.getLongitud(),
                sindicato.tieneUbicacion(),
                sindicato.getUbicacionActualizadaEn(),
                Auditoria.desde(sindicato));
    }
}
