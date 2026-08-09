package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Una fila de la planilla que no se pudo importar.
 * <p>
 * {@code fila} es el número tal como se ve en Excel, no un índice base cero: si
 * el informe dice 42, el usuario abre la planilla y va a la 42.
 */
@Schema(name = "ErrorFila", description = "Fila rechazada durante la importación, con el motivo.")
public record ErrorFila(

        @Schema(description = "Número de fila tal como lo muestra Excel. Con encabezado en la 1, "
                + "el primer dato es la 2.", example = "42")
        int fila,

        @Schema(description = "Columna que provocó el rechazo. Null si el problema es de la fila "
                + "entera y no de un campo puntual.", example = "nombres")
        String columna,

        @Schema(description = "Valor leído de esa celda, para poder reconocerla en la planilla.",
                example = "")
        String valor,

        @Schema(description = "Qué está mal, en lenguaje llano.",
                example = "los nombres son obligatorios")
        String mensaje
) {

    public static ErrorFila de(int fila, String columna, String valor, String mensaje) {
        return new ErrorFila(fila, columna, valor, mensaje);
    }
}
