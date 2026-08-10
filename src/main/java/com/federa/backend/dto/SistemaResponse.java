package com.federa.backend.dto;

import com.federa.backend.model.Sistema;
import com.federa.backend.model.TenenciaSistema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Un sistema y dónde está hoy.
 *
 * @param lote el lote donde está instalado, o null si está disponible.
 */
@Schema(description = "Sistema: el agregado que un lote puede tener, y que se puede trasladar.")
public record SistemaResponse(

        @Schema(description = "Identificador interno.", example = "3")
        Long id,

        @Schema(description = "Código con el que se lo identifica. Único.", example = "S-014")
        String codigo,

        String descripcion,

        @Schema(description = "Dónde está hoy. Null si no está instalado en ningún lote.")
        EnLote lote,

        Auditoria auditoria
) {

    /** El lote donde está el sistema, con su sindicato y quién lo tiene. */
    public record EnLote(
            @Schema(example = "1503") Long loteId,
            @Schema(example = "74-A") String codigo,
            @Schema(example = "ALTO SAN SALVADOR") String sindicato,
            @Schema(description = "Quién tiene ese lote hoy. Null si está sin tenedor.",
                    example = "CANDIDO COLQUECHAMBI MAMANI") String tenedor,
            @Schema(example = "2026-03-01") LocalDate desde) {
    }

    public static SistemaResponse desde(Sistema sistema, TenenciaSistema vigente) {
        EnLote donde = null;
        if (vigente != null) {
            var lote = vigente.getLote();
            var tenencia = lote.getTenenciaVigente();
            donde = new EnLote(
                    lote.getId(),
                    lote.getCodigo(),
                    lote.getSindicato().getNombre(),
                    tenencia == null ? null : tenencia.getProductor().getNombreCompleto(),
                    vigente.getDesde());
        }
        return new SistemaResponse(sistema.getId(), sistema.getCodigo(),
                sistema.getDescripcion(), donde, Auditoria.desde(sistema));
    }

    /** Alta y edición de un sistema. */
    @Schema(description = "Alta y edición de un sistema.")
    public record Peticion(

            @Schema(description = "Código con el que se lo identifica. No puede repetirse: es "
                    + "lo que se nombra en un acta de venta.",
                    example = "S-014", maxLength = 20,
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "el sistema necesita un código")
            @Size(max = 20, message = "el código no puede superar los 20 caracteres")
            String codigo,

            @Schema(description = "Detalle libre.", maxLength = 200)
            @Size(max = 200, message = "la descripción no puede superar los 200 caracteres")
            String descripcion
    ) {
    }
}
