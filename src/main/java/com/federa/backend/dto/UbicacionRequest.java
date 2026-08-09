package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Punto exacto donde está la sede de un sindicato, en grados decimales.
 * <p>
 * Las dos coordenadas son obligatorias juntas: media coordenada no ubica nada.
 */
@Schema(name = "UbicacionRequest",
        description = "Coordenadas de la sede, tal como salen del mapa.")
public record UbicacionRequest(

        @Schema(description = "Latitud en grados decimales. El trópico de Cochabamba ronda "
                + "los -17.", example = "-16.8574321",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "la latitud es obligatoria")
        @DecimalMin(value = "-90.0", message = "la latitud tiene que estar entre -90 y 90")
        @DecimalMax(value = "90.0", message = "la latitud tiene que estar entre -90 y 90")
        BigDecimal latitud,

        @Schema(description = "Longitud en grados decimales.", example = "-64.7891234",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "la longitud es obligatoria")
        @DecimalMin(value = "-180.0", message = "la longitud tiene que estar entre -180 y 180")
        @DecimalMax(value = "180.0", message = "la longitud tiene que estar entre -180 y 180")
        BigDecimal longitud
) {
}
