package com.federa.backend.dto;

import com.federa.backend.model.Productor;
import java.time.LocalDateTime;

/** Resumen de la ficha conservada en la papelera. */
public record ProductorPapeleraResponse(
        Long id, String nombre, String ci, String sindicato, String central,
        LocalDateTime eliminadoEn, boolean restaurable, String impedimento
) {
    public static ProductorPapeleraResponse desde(Productor p, boolean restaurable) {
        return new ProductorPapeleraResponse(p.getId(), p.getNombreCompleto(), p.getCi(),
                p.getSindicato().getNombre(), p.getSindicato().getCentral().getNombre(),
                p.getEliminadoEn(), restaurable,
                restaurable ? null : "La cédula ya está registrada en otro productor activo.");
    }
}
