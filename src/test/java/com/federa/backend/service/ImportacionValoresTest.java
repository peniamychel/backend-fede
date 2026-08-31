package com.federa.backend.service;

import com.federa.backend.model.enums.EstadoLote;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImportacionValoresTest {

    @Test
    void aceptaSoloLasSeisClasificacionesDelImportador() {
        assertThat(ImportacionService.clasificacionImportada("SISTEMA"))
                .isEqualTo(EstadoLote.CON_SISTEMA);
        assertThat(ImportacionService.clasificacionImportada("sin sistema"))
                .isEqualTo(EstadoLote.SIN_SISTEMA);
        assertThat(ImportacionService.clasificacionImportada("BLANCO"))
                .isEqualTo(EstadoLote.BLANCO);
        assertThat(ImportacionService.clasificacionImportada("fraccionado"))
                .isEqualTo(EstadoLote.FRACCIONADO);
        assertThat(ImportacionService.clasificacionImportada("DETALLISTA"))
                .isEqualTo(EstadoLote.DETALLISTA);
        assertThat(ImportacionService.clasificacionImportada("COMUNITARIO"))
                .isEqualTo(EstadoLote.COMUNITARIO);
    }

    @Test
    void rechazaUnaClasificacionFueraDeLaLista() {
        assertThat(ImportacionService.clasificacionImportada("NUEVO")).isNull();
        assertThat(ImportacionService.clasificacionImportada("OTRO")).isNull();
    }
}
