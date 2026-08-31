package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado de la única revisión SIE de un productor importado.")
public record RevisionSieProductorResponse(
        Estado estado,
        boolean completada,
        boolean datosModificados,
        String mensaje
) {
    public enum Estado {
        CORREGIDA,
        VERIFICADA,
        ACEPTADA_SIN_COINCIDENCIA,
        ACEPTADA_SIN_CEDULA,
        NO_DISPONIBLE,
        YA_REALIZADA
    }
}
