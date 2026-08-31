package com.federa.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Listas nominales de carnets impresos y de personas con datos faltantes. */
public record InformeNominalImpresionCentral(
        Long centralId,
        String central,
        String federacion,
        int totalImpresos,
        int totalFaltantesDatos,
        List<SeccionSindicato> sindicatos) {

    public record SeccionSindicato(
            Long sindicatoId,
            String sindicato,
            List<Fila> impresos,
            List<Fila> faltantesDatos) {
    }

    public record Fila(
            Long productorId,
            String nombres,
            String apellidos,
            String ci,
            String lotes,
            String codigoPadron,
            int impresiones,
            LocalDateTime ultimaImpresion,
            List<String> datosFaltantes) {
    }
}
