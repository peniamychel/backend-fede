package com.federa.backend.dto;

import java.util.List;

/** Revisión nominal de todos los productores antes o después de imprimir. */
public record InformePreImpresionCentral(
        Long centralId,
        String central,
        String federacion,
        int total,
        List<SeccionSindicato> sindicatos) {

    public record SeccionSindicato(
            Long sindicatoId,
            String sindicato,
            List<Fila> productores) {
    }

    public record Fila(
            Long productorId,
            String nombres,
            String apellidos,
            String ci,
            String lotes,
            boolean observado,
            List<String> datosFaltantes) {
    }
}
