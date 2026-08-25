package com.federa.backend.service;

import com.federa.backend.model.enums.Ambito;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReglasDirectorioTest {

    @Test
    void valoresPorDefectoLimitanElPieYHacenOpcionalLaFirmaSindical() {
        ReglasDirectorio reglas = new ReglasDirectorio(false, false);

        assertThat(reglas.permitePieFirmaImagen(Ambito.FEDERACION)).isTrue();
        assertThat(reglas.permitePieFirmaImagen(Ambito.CENTRAL)).isTrue();
        assertThat(reglas.permitePieFirmaImagen(Ambito.SINDICATO)).isFalse();
        assertThat(reglas.firmaObligatoria(Ambito.FEDERACION)).isTrue();
        assertThat(reglas.firmaObligatoria(Ambito.CENTRAL)).isTrue();
        assertThat(reglas.firmaObligatoria(Ambito.SINDICATO)).isFalse();
        assertThat(reglas.selloObligatorio(Ambito.SINDICATO)).isTrue();
    }

    @Test
    void lasFuncionesDelSindicatoPuedenHabilitarsePorConfiguracion() {
        ReglasDirectorio reglas = new ReglasDirectorio(true, true);

        assertThat(reglas.permitePieFirmaImagen(Ambito.SINDICATO)).isTrue();
        assertThat(reglas.firmaObligatoria(Ambito.SINDICATO)).isTrue();
    }
}
