package com.federa.backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Informe de una importación, sea simulada o real.
 * <p>
 * Los contadores significan "lo que se creó" cuando {@code simulacion} es
 * {@code false}, y "lo que se crearía" cuando es {@code true}. Ambos casos
 * recorren el mismo código, así que la simulación no puede prometer un
 * resultado distinto del que produciría la ejecución.
 */
@Schema(name = "ImportacionResponse",
        description = "Resultado de importar la planilla del padrón.")
public record ImportacionResponse(

        @Schema(description = "Si es true no se escribió nada: la transacción se deshizo al "
                + "terminar.", example = "true")
        boolean simulacion,

        @Schema(description = "Federación destino de la importación.", example = "3")
        Long federacionId,

        @Schema(description = "Nombre de esa federación.", example = "CARRASCO")
        String federacionNombre,

        @Schema(description = "Filas con datos que se leyeron. No cuenta el encabezado ni las "
                + "filas en blanco del final.", example = "4051")
        int filasLeidas,

        @Schema(description = "Filas que pasaron todas las validaciones.", example = "4038")
        int filasValidas,

        @Schema(description = "Filas descartadas. El detalle está en `errores`.", example = "13")
        int filasRechazadas,

        @Schema(description = "Productores creados, o que se crearían.", example = "4038")
        int productores,

        @Schema(description = "Lotes creados, o que se crearían. Solo las filas con número de "
                + "lote generan uno.", example = "3800")
        int lotes,

        @Schema(description = "Centrales que la planilla menciona y no están registradas. Sus "
                + "filas se rechazan: deben crearse manualmente con su abreviatura.",
                example = "[\"IVIRGARZAMA\", \"1RO MAYO\"]")
        List<String> centralesNuevas,

        @Schema(description = "Sindicatos que la planilla menciona y no existían.")
        List<SindicatoNuevo> sindicatosNuevos,

        @Schema(description = "Filas cuyo nombre, apellido y sindicato ya existen en la base. No "
                + "es un error: se importan igual. Sirve para detectar que la planilla se está "
                + "cargando por segunda vez.", example = "4")
        int posiblesDuplicados,

        @Schema(description = "Detalle de las filas rechazadas, hasta 200.")
        List<ErrorFila> errores,

        @Schema(description = "Errores que no entraron en la lista por el tope de 200.",
                example = "0")
        int erroresOmitidos,

        @Schema(description = "Cuánto tardó el proceso completo.", example = "1840")
        long duracionMs
) {
}
