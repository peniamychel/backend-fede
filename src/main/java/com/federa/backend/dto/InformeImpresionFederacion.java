package com.federa.backend.dto;

import java.util.List;

/** Estado consolidado de la impresión de credenciales de una federación. */
public record InformeImpresionFederacion(
        Long federacionId,
        String federacion,
        int centrales,
        int sindicatos,
        int sindicatosSinSello,
        int total,
        int impresos,
        int pendientes,
        int pendientesConFoto,
        int sinFoto,
        int listosParaImprimir,
        double porcentajeAvance,
        List<InformeImpresionCentral> detalle) {
}
