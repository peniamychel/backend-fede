package com.federa.backend.dto;

import java.util.List;

/**
 * Avance consolidado de la impresión de credenciales de una central.
 *
 * <p>Los pendientes incluyen tanto a quienes ya tienen fotografía como a
 * quienes todavía no la tienen. De ese modo el total siempre cumple
 * {@code impresos + pendientes = total}.</p>
 */
public record InformeImpresionCentral(
        Long centralId,
        String central,
        String federacion,
        int sindicatos,
        int sindicatosSinSello,
        int total,
        int impresos,
        int pendientes,
        int pendientesConFoto,
        int sinFoto,
        int listosParaImprimir,
        double porcentajeAvance,
        List<FilaSindicato> detalle) {

    /** Cifras de un sindicato, calculadas con las reglas de impresión masiva. */
    public record FilaSindicato(
            Long sindicatoId,
            String sindicato,
            boolean selloCargado,
            int total,
            int impresos,
            int pendientes,
            int pendientesConFoto,
            int sinFoto,
            int listosParaImprimir,
            double porcentajeAvance) {
    }
}
