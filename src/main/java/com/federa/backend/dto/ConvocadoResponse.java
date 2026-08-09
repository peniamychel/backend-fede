package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Una línea de la lista: alguien convocado, presente o no.
 *
 * @param cargo    el cargo por el que está convocado, o null si asiste como
 *                 afiliado. En las reuniones de dirigentes siempre viene.
 * @param presente si ya se registró.
 */
@Schema(description = "Una persona en la lista de una reunión.")
public record ConvocadoResponse(

        @Schema(description = "Id del productor.", example = "812")
        Long productorId,

        @Schema(description = "Su nombre.", example = "CANDIDO COLQUECHAMBI MAMANI")
        String nombre,

        @Schema(description = "Su cédula.", example = "3692655")
        String ci,

        @Schema(description = "Sindicato al que pertenece.", example = "ALTO SAN SALVADOR")
        String sindicato,

        @Schema(description = "Por qué está convocado, si es por un cargo.",
                example = "Presidente de ALTO SAN SALVADOR")
        String cargo,

        @Schema(description = "Si ya se registró en la reunión.", example = "true")
        boolean presente,

        @Schema(description = "Cuándo se escaneó su carnet. Null si no llegó.",
                example = "2026-08-15T09:12:44")
        LocalDateTime registradaEn
) {
}
