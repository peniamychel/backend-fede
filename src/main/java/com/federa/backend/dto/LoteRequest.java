package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta y edición de lotes.
 * <p>
 * {@code extension}, {@code estado} y {@code mercado} se reciben como texto
 * libre y se normalizan en el servicio con los {@code desde(...)} de cada enum.
 * Así el cliente puede mandar tal cual lo que dice la planilla ("C-S",
 * "SISTEMA", "FRANSIONADOS") sin tener que conocer la nomenclatura interna.
 */
@Schema(description = "Alta y edición de lotes. Los tres campos de clasificación se mandan "
        + "tal como los escribe la planilla y el backend los normaliza.")
public record LoteRequest(

        @Schema(description = "Número de lote. Es texto, no número: el padrón trae rangos "
                + "(`30-31`), extensión pegada (`21-A`) y códigos (`B.N47`). Puede repetirse "
                + "dentro de un sindicato; eso se anota como observación, no se bloquea.",
                example = "74", maxLength = 20)
        @Size(max = 20, message = "el número de lote no puede superar los 20 caracteres")
        String numero,

        @Schema(description = "Subdivisión del lote. Se acepta en minúscula. Valores útiles: "
                + "A, B, C, D, E. Cualquier otra cosa queda en null.",
                example = "A", allowableValues = {"A", "B", "C", "D", "E"}, maxLength = 5)
        @Size(max = 5, message = "la extensión debe ser una letra (A–E)")
        String extension,

        @Schema(description = "Clasificación de la participación en la parcela. Opciones de "
                + "asignación: SIN_SISTEMA, CON_SISTEMA, BLANCO, FRACCIONADO, DETALLISTA y "
                + "COMUNITARIO. También normaliza textos históricos como SISTEMA/C-S/SI, NO "
                + "y FRANSIONADOS. Si no se reconoce queda como DESCONOCIDO y conserva el "
                + "texto original.", example = "COMUNITARIO",
                allowableValues = {"SIN_SISTEMA", "CON_SISTEMA", "BLANCO", "FRACCIONADO",
                        "DETALLISTA", "COMUNITARIO"}, maxLength = 30)
        @Size(max = 30, message = "el estado del lote no puede superar los 30 caracteres")
        String estado,

        @Schema(description = "Mercado. Hoy la planilla solo usa DETALLISTA. Se acepta en "
                + "minúscula.",
                example = "DETALLISTA", maxLength = 20)
        @Size(max = 20, message = "el mercado no puede superar los 20 caracteres")
        String mercado,

        @Schema(description = "Sindicato donde está la tierra. Devuelve 404 si no existe. "
                + "No cambia: un lote no se muda de sindicato.",
                example = "17", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "el lote pertenece a un sindicato")
        Long sindicatoId,

        @Schema(description = "Superficie en hectáreas. Se puede dejar vacía: en el padrón "
                + "original no está, y un cero se confundiría con una parcela de tamaño nulo.",
                example = "12.5")
        @DecimalMin(value = "0.0", inclusive = false,
                message = "la superficie tiene que ser mayor que cero")
        @Digits(integer = 6, fraction = 4,
                message = "la superficie admite hasta 4 decimales de hectárea")
        BigDecimal superficie,

        @Schema(description = "Quién lo tiene, si ya se sabe. Al crearlo abre su primer "
                + "período de tenencia; al editarlo se ignora, porque cambiar de tenedor es "
                + "un traspaso y va por su propio endpoint.",
                example = "812")
        Long productorId
) {
}
