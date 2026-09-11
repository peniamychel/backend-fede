package com.federa.backend.dto;

import com.federa.backend.model.FilaConciliacionUdestro;
import com.federa.backend.model.enums.AccionConciliacionUdestro;
import com.federa.backend.model.enums.DecisionConflictoUdestro;
import com.federa.backend.model.enums.EstadoLote;

import java.util.List;

public record FilaConciliacionUdestroResponse(
        Long id,
        Integer numeroFila,
        String central,
        String sindicato,
        String nombresUdestro,
        String apellidosUdestro,
        String ci,
        boolean sindicatoNuevo,
        AccionConciliacionUdestro accion,
        String motivo,
        Long productorId,
        EstadoLote clasificacionAnterior,
        Integer similitudNombre,
        DecisionConflictoUdestro decision,
        Long productorSeleccionadoId,
        List<CandidatoUdestro> candidatos
) {
    public static FilaConciliacionUdestroResponse desde(FilaConciliacionUdestro fila,
                                                         List<CandidatoUdestro> candidatos) {
        return new FilaConciliacionUdestroResponse(fila.getId(), fila.getNumeroFila(),
                fila.getCentralNombre(), fila.getSindicatoNombre(), fila.getNombresUdestro(),
                fila.getApellidosUdestro(), fila.getCi(), fila.isSindicatoNuevo(),
                fila.getAccion(), fila.getMotivo(), fila.getProductorId(),
                fila.getClasificacionAnterior(), fila.getSimilitudNombre(),
                fila.getDecisionConflicto(),
                fila.getProductorSeleccionadoId(), candidatos);
    }
}
