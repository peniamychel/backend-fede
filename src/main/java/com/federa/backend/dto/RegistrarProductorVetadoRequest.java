package com.federa.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Alta de una persona que todavía no está en el padrón, directamente como vetada. */
public record RegistrarProductorVetadoRequest(
        @NotNull(message = "hay que indicar el sindicato") Long sindicatoId,
        @NotBlank(message = "la cédula es obligatoria")
        @Size(max = 20, message = "la cédula no puede superar los 20 caracteres") String ci,
        @NotBlank(message = "los nombres son obligatorios")
        @Size(max = 60, message = "los nombres no pueden superar los 60 caracteres") String nombres,
        @Size(max = 60, message = "los apellidos no pueden superar los 60 caracteres") String apellidos,
        @NotBlank(message = "hay que decir el motivo del veto")
        @Size(max = 1000, message = "el motivo no puede superar los 1000 caracteres") String motivo
) {
}
