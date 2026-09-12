package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado de la única revisión SIE de un productor importado.")
public record RevisionSieProductorResponse(
        Estado estado,
        boolean completada,
        boolean datosModificados,
        String mensaje,
        Datos actuales,
        Datos propuestos
) {
    public RevisionSieProductorResponse(Estado estado, boolean completada,
                                       boolean datosModificados, String mensaje) {
        this(estado, completada, datosModificados, mensaje, null, null);
    }

    public record Datos(String ci, String nombres, String apellidos) {}

    public enum Estado {
        REQUIERE_CONFIRMACION,
        CONSERVADA,
        CORREGIDA,
        CORREGIDA_MANUAL,
        APROBADA_MANUAL,
        VERIFICADA,
        ACEPTADA_SIN_COINCIDENCIA,
        ACEPTADA_SIN_CEDULA,
        NO_DISPONIBLE,
        YA_REALIZADA
    }
}
