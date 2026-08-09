package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Sindicato que la planilla menciona y que todavía no existe en la base.
 * <p>
 * Lleva el nombre de la central porque el del sindicato no alcanza para
 * identificarlo: "1RO DE MAYO" existe en tres centrales distintas.
 */
@Schema(name = "SindicatoNuevo",
        description = "Sindicato que aparece en la planilla y aún no existe.")
public record SindicatoNuevo(

        @Schema(description = "Central a la que pertenecería.", example = "IVIRGARZAMA")
        String central,

        @Schema(description = "Nombre del sindicato, ya normalizado.", example = "LIBERTAD")
        String sindicato
) {
}
