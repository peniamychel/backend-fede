package com.federa.backend.dto;

import com.federa.backend.model.Reunion;
import com.federa.backend.model.Veto;
import com.federa.backend.util.CodigoPadron;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Un veto, con lo necesario para reconocer a la persona sin pedir su ficha.
 * <p>
 * Trae el nombre, la cédula y los dos códigos porque así se pregunta en la
 * práctica: alguien llega con el carné en la mano, o dicta su nombre, y hay que
 * decirle en el momento si está observado.
 */
@Schema(description = "Productor vetado por decisión de asamblea.")
public record VetoResponse(

        Long id,

        @Schema(description = "Si el veto sigue rigiendo hoy.", example = "true")
        boolean vigente,

        Long productorId,

        @Schema(example = "CONSTANTINA HINOJOSA")
        String productorNombre,

        @Schema(description = "Cédula, para identificarlo con el documento en la mano.",
                example = "913516")
        String ci,

        @Schema(description = "Código de su credencial, el que dice el QR.",
                example = "AB12CD34EF")
        String codigo,

        @Schema(description = "Código en el padrón.", example = "2-IVI-1")
        String codigoPadron,

        @Schema(example = "LIBERTAD")
        String sindicato,

        @Schema(example = "IVIRGARZAMA")
        String central,

        @Schema(description = "Por qué se lo vetó.")
        String motivo,

        LocalDate desde,

        @Schema(description = "La reunión que lo decidió.")
        ReunionBreve reunion,

        @Schema(description = "Por qué se lo sacó de la lista. Null si sigue vetado.")
        String motivoLevantamiento,

        @Schema(description = "Cuándo dejó de regir. Null si sigue vetado.")
        LocalDate hasta,

        @Schema(description = "La reunión que lo levantó. Null si sigue vetado.")
        ReunionBreve reunionLevanta
) {

    /** Lo mínimo para nombrar una reunión sin arrastrar su lista de asistencia. */
    @Schema(description = "Referencia a una reunión.")
    public record ReunionBreve(Long id, String titulo, LocalDate fecha) {

        static ReunionBreve desde(Reunion r) {
            return r == null ? null : new ReunionBreve(r.getId(), r.getTitulo(), r.getFecha());
        }
    }

    public static VetoResponse desde(Veto v) {
        var productor = v.getProductor();
        var sindicato = productor.getSindicato();
        return new VetoResponse(
                v.getId(),
                v.estaVigente(),
                productor.getId(),
                productor.getNombreCompleto(),
                productor.getCi(),
                productor.getCodigo(),
                CodigoPadron.de(productor),
                sindicato.getNombre(),
                sindicato.getCentral().getNombre(),
                v.getMotivo(),
                v.getDesde(),
                ReunionBreve.desde(v.getReunion()),
                v.getMotivoLevantamiento(),
                v.getHasta(),
                ReunionBreve.desde(v.getReunionLevanta()));
    }
}
