package com.federa.backend.dto;

import com.federa.backend.model.LlamadaLista;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Una vuelta de lista, con cuántos se registraron en ella.
 * <p>
 * El recuento no sale de la entidad porque contar las asistencias cargadas
 * obligaría a traerlas todas; se cuenta en la base y se pasa acá.
 */
@Schema(description = "Una vuelta de lista dentro de una reunión.")
public record LlamadaResponse(

        @Schema(description = "Identificador.", example = "7")
        Long id,

        @Schema(description = "Id de la reunión a la que pertenece.", example = "12")
        Long reunionId,

        @Schema(description = "Primera, segunda, tercera… empieza en 1 en cada reunión.",
                example = "2")
        int numero,

        @Schema(description = "Cómo se lee en pantalla.", example = "Segunda llamada")
        String etiqueta,

        @Schema(description = "Si todavía admite registros.", example = "true")
        boolean abierta,

        @Schema(description = "Cuándo se cerró. Null si sigue abierta.")
        LocalDateTime cerradaEn,

        @Schema(description = "Nota libre.", example = "Después del cuarto intermedio")
        String nota,

        @Schema(description = "Cuántos se registraron en esta vuelta.", example = "31")
        int presentes
) {

    public static LlamadaResponse desde(LlamadaLista llamada, long presentes) {
        return new LlamadaResponse(
                llamada.getId(),
                llamada.getReunion().getId(),
                llamada.getNumero(),
                etiquetaDe(llamada.getNumero()),
                llamada.isAbierta(),
                llamada.getCerradaEn(),
                llamada.getNota(),
                (int) presentes);
    }

    /** "Primera llamada", "Segunda llamada"… como se dice en la asamblea. */
    static String etiquetaDe(int numero) {
        String ordinal = switch (numero) {
            case 1 -> "Primera";
            case 2 -> "Segunda";
            case 3 -> "Tercera";
            case 4 -> "Cuarta";
            case 5 -> "Quinta";
            case 6 -> "Sexta";
            default -> numero + "ª";
        };
        return ordinal + " llamada";
    }
}
