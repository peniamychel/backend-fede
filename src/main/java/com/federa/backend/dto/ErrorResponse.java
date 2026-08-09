package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

/** Cuerpo único de todos los errores de la API. */
@Schema(name = "ErrorResponse", description = "Formato común de todas las respuestas de error.")
public record ErrorResponse(

        @Schema(description = "Código HTTP, repetido en el cuerpo para no depender de la cabecera.",
                example = "409")
        int estado,

        @Schema(description = "Explicación en lenguaje llano, apta para mostrar al usuario.",
                example = "La central 13 DE JUNIO tiene 4 sindicato(s); reasignálos o eliminálos antes de borrarla")
        String mensaje,

        @Schema(description = "Solo en los 400 de validación: qué campo falló y por qué. "
                + "Vacío en los demás errores.",
                example = "{\"nombres\":\"los nombres son obligatorios\"}")
        Map<String, String> errores,

        @Schema(description = "Cuándo se produjo el error, hora del servidor.",
                example = "2026-08-07T11:59:47.897")
        LocalDateTime momento
) {

    public static ErrorResponse de(HttpStatus estado, String mensaje) {
        return new ErrorResponse(estado.value(), mensaje, Map.of(), LocalDateTime.now());
    }

    public static ErrorResponse deValidacion(Map<String, String> errores) {
        return new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Datos inválidos", errores,
                LocalDateTime.now());
    }
}
