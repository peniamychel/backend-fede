package com.federa.backend.dto;

import com.federa.backend.model.Productor;
import com.federa.backend.model.enums.EstadoLote;

/** Productor actual que comparte la CI con una fila de UDESTRO. */
public record CandidatoUdestro(
        Long id,
        String nombres,
        String apellidos,
        String nombreCompleto,
        String ci,
        String central,
        String sindicato,
        EstadoLote clasificacion,
        String fotoUrl
) {
    public static CandidatoUdestro desde(Productor p, EstadoLote clasificacion,
                                         String fotoUrl) {
        return new CandidatoUdestro(p.getId(), p.getNombres(), p.getApellidos(),
                p.getNombreCompleto(), p.getCi(), p.getSindicato().getCentral().getNombre(),
                p.getSindicato().getNombre(), clasificacion, fotoUrl);
    }
}
