package com.federa.backend.dto;

import com.federa.backend.model.HojaActa;
import com.federa.backend.model.Reunion;
import com.federa.backend.model.enums.Ambito;
import com.federa.backend.model.enums.TipoReunion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * Una reunión, con el recuento de la lista.
 *
 * @param convocados cuántos deberían asistir según el tipo.
 * @param presentes  cuántos se registraron hasta ahora.
 */
@Schema(description = "Reunión convocada por un sindicato, una central o la federación.")
public record ReunionResponse(

        @Schema(description = "Identificador.", example = "12")
        Long id,

        @Schema(description = "Tipo de reunión.", example = "AMPLIADO")
        TipoReunion tipo,

        @Schema(description = "Cómo se escribe.", example = "Ampliado de central")
        String tipoEtiqueta,

        @Schema(description = "A quiénes convoca.")
        String tipoDetalle,

        @Schema(description = "Nivel que la convoca.", example = "CENTRAL")
        Ambito convoca,

        @Schema(description = "Id del sindicato, central o federación que convoca.",
                example = "21")
        Long convocanteId,

        @Schema(description = "Su nombre.", example = "1RO MAYO")
        String convocanteNombre,

        String titulo,
        LocalDate fecha,
        String lugar,
        String observaciones,

        @Schema(description = "Si ya no admite más asistencias.", example = "false")
        boolean cerrada,

        @Schema(description = "Cuántos deberían asistir.", example = "48")
        int convocados,

        @Schema(description = "Cuántos se registraron.", example = "31")
        int presentes,

        @Schema(description = "Si el acta ya está cargada. Sin ella no se pueden decidir "
                + "vetos en esta reunión.", example = "true")
        boolean tieneActa,

        @Schema(description = "El número del acta en el libro del sindicato. Null mientras no "
                + "haya acta. No es único: cada libro numera desde uno.",
                example = "12/2026")
        String codigoActa,

        @Schema(description = "Si en esta reunión se pueden decidir vetos. No toda asamblea "
                + "es para sancionar, así que se habilita a propósito.", example = "false")
        boolean vetosHabilitados,

        @Schema(description = "Las hojas del acta, en orden.")
        List<HojaResponse> hojasActa,

        Auditoria auditoria
) {

    public static ReunionResponse desde(Reunion r, int convocados, int presentes) {
        return new ReunionResponse(
                r.getId(),
                r.getTipo(),
                r.getTipo().getEtiqueta(),
                r.getTipo().getDetalle(),
                r.getConvoca(),
                r.getConvocanteId(),
                r.getConvocanteNombre(),
                r.getTitulo(),
                r.getFecha(),
                r.getLugar(),
                r.getObservaciones(),
                r.isCerrada(),
                convocados,
                presentes,
                r.isTieneActa(),
                r.getCodigoActa(),
                r.isVetosHabilitados(),
                r.getHojasActa().stream().map(HojaResponse::desde).toList(),
                Auditoria.desde(r));
    }

    /** Una hoja del acta, sin el binario: los bytes se piden aparte. */
    @Schema(description = "Una hoja del acta.")
    public record HojaResponse(Long id, int orden, String nombre, String tipoMime,
                               int tamanoBytes) {

        static HojaResponse desde(HojaActa h) {
            return new HojaResponse(h.getId(), h.getOrden(), h.getNombre(),
                    h.getTipoMime(), h.getTamanoBytes());
        }
    }
}
