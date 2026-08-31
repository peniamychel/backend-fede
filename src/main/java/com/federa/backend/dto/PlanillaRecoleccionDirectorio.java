package com.federa.backend.dto;

import java.util.List;

/** Datos que se plasman en la planilla física de recolección del directorio. */
public record PlanillaRecoleccionDirectorio(
        Long centralId,
        String federacion,
        String central,
        String secretarioGeneral,
        boolean selloCentralCargado,
        boolean firmaCentralCargada,
        boolean pieFirmaCentralCargado,
        List<FilaSindicato> sindicatos) {

    public record FilaSindicato(
            Long sindicatoId,
            String sindicato,
            boolean selloCargado) {
    }
}
