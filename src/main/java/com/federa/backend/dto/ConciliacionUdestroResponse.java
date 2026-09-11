package com.federa.backend.dto;

import com.federa.backend.model.enums.EstadoConciliacionUdestro;

import java.time.LocalDateTime;
import java.util.List;

/** Resumen estable del borrador y del resultado aplicado. */
public record ConciliacionUdestroResponse(
        Long id,
        EstadoConciliacionUdestro estado,
        String archivo,
        String sha256,
        Long federacionId,
        String federacion,
        long filasExcel,
        long altasSistema,
        long cambiosASistema,
        long observadosPorIdentidad,
        long cambiosABlanco,
        long conservados,
        long conflictos,
        long conflictosPendientes,
        long errores,
        List<SindicatoNuevo> sindicatosNuevos,
        boolean listaParaAplicar,
        LocalDateTime creadaEn,
        LocalDateTime aplicadaEn
) {
}
