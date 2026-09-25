package com.federa.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Resultado nominal conservado para una fase concreta de impresión. */
public record InformeFaseImpresion(
        Long centralId,
        String central,
        String federacion,
        Long faseId,
        int numeroFase,
        LocalDateTime abiertaEn,
        LocalDateTime cerradaEn,
        int totalImpresos,
        int totalPendientes,
        List<SeccionSindicato> sindicatos) {

    public record SeccionSindicato(
            Long sindicatoId,
            String sindicato,
            List<Fila> productores,
            int impresosEnFase,
            int impresosAcumulados) {
    }

    public record Fila(
            Long productorId,
            String nombres,
            String apellidos,
            String ci,
            String lotes,
            boolean observado,
            List<String> datosFaltantes,
            List<Integer> fasesImpresas,
            boolean reimpreso) {
    }
}
