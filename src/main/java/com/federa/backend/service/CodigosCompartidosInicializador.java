package com.federa.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Normaliza una vez por arranque clasificaciones y códigos heredados. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodigosCompartidosInicializador implements ApplicationRunner {

    private final LoteService loteService;

    @Override
    public void run(ApplicationArguments args) {
        int clasificaciones = loteService.normalizarClasificacionesExistentes();
        if (clasificaciones > 0) {
            log.info("Se corrigieron {} clasificaciones históricas de lote.", clasificaciones);
        }
        int grupos = loteService.normalizarCodigosCompartidosExistentes();
        if (grupos > 0) {
            log.info("Códigos A-H verificados para {} números de lote compartidos.", grupos);
        }
        int correlativos = loteService.corregirCorrelativosDuplicadosDeLotesCompartidos();
        if (correlativos > 0) {
            log.info("Se asignaron correlativos únicos a {} productores con letra B-H.",
                    correlativos);
        }
    }
}
