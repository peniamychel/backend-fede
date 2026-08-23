package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta y edición de productores.
 * <p>
 * Solo {@code nombres} y {@code sindicatoId} son obligatorios: el padrón real
 * tiene 124 productores sin cédula, 1.837 sin carné y 5 sin apellido, así que
 * exigirlos impediría cargar registros que existen.
 */
@Schema(description = "Alta y edición de productores. Solo los nombres y el sindicato son "
        + "obligatorios: en el padrón real hay 124 productores sin cédula, 1.837 sin carné y "
        + "5 sin apellido.")
public record ProductorRequest(

        @Schema(description = "Se guarda en mayúsculas y sin tildes, como en la planilla.",
                example = "CONSTANTINA", maxLength = 60,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "los nombres son obligatorios")
        @Size(max = 60, message = "los nombres no pueden superar los 60 caracteres")
        String nombres,

        @Schema(description = "Se guarda en mayúsculas y sin tildes. Puede faltar.",
                example = "HINOJOSA LA FUENTE", maxLength = 60)
        @Size(max = 60, message = "los apellidos no pueden superar los 60 caracteres")
        String apellidos,

        @Schema(description = "Cédula de identidad. Es texto, no número: admite complemento "
                + "(`8005906-1V`). No se exige única, el padrón tiene 27 repetidas.",
                example = "913516", maxLength = 20)
        @Size(max = 20, message = "la cédula no puede superar los 20 caracteres")
        String ci,

        @Schema(description = "Nombre corregido propuesto en la revisión, cuando el de la "
                + "planilla no coincide con el documento. Columna `Nombre x`.",
                example = "CONSTANTINA", maxLength = 60)
        @Size(max = 60, message = "el nombre corregido no puede superar los 60 caracteres")
        String nombresCorregidos,

        @Schema(description = "Apellido corregido. Columna `Apellido x`.",
                example = "HINOJOSA LAFUENTE", maxLength = 60)
        @Size(max = 60, message = "el apellido corregido no puede superar los 60 caracteres")
        String apellidosCorregidos,

        @Schema(description = "Rótulo con el que se archivó la fotografía. Que venga vacío es "
                + "justamente lo que se observa como falta de foto.",
                example = "Constantina Hinojosa, 1ro de Mayo", maxLength = 120)
        @Size(max = 120, message = "la descripción de la foto no puede superar los 120 caracteres")
        String fotoDescripcion,

        @Schema(description = "Marca manual de seguimiento puesta durante la revisión "
                + "(columna `Columna1` de la planilla). Si se omite, queda en false.",
                example = "false", defaultValue = "false")
        Boolean marcado,

        @Schema(description = "Sindicato al que pertenece. Devuelve 404 si no existe.",
                example = "17", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "el productor debe pertenecer a un sindicato")
        Long sindicatoId
) {
}
