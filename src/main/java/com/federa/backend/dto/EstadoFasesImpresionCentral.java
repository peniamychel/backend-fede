package com.federa.backend.dto;

import com.federa.backend.model.enums.EstadoFaseImpresionCarnet;

import java.time.LocalDateTime;
import java.util.List;

/** Fase activa e historial de fases de impresión de una central. */
public record EstadoFasesImpresionCentral(
        Long centralId,
        String central,
        Fase faseActiva,
        List<Fase> historial) {

    public record Fase(
            Long id,
            int numero,
            EstadoFaseImpresionCarnet estado,
            LocalDateTime abiertaEn,
            LocalDateTime cerradaEn,
            int total,
            int impresos,
            int pendientes) {
    }
}
